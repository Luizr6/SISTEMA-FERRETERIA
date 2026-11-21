"""
FER-53 - Motor de consultas por chat (versión por reglas).

Reglas del documento de requerimientos (CU14-CU17):
  * Precios y stock salen SIEMPRE de la base de datos, nunca se inventan.
  * Si no se entiende el mensaje, se pide reformular.
  * Un pedido se registra como «Pedido por Confirmar» (PENDIENTE) sin tocar stock (RF32).

Está aislado en `interpretar()` para poder reemplazarlo por un modelo de IA (RF30)
sin tocar el resto del sistema (RNF05).
"""
import re
import unicodedata
from dataclasses import dataclass, field

from django.conf import settings
from django.db.models import Q

from productos.models import Producto
from ventas import services as ventas_services
from ventas.models import Pedido

PALABRAS_VACIAS = {
    'hola', 'buenas', 'buenos', 'dias', 'tardes', 'noches', 'precio', 'precios', 'cuanto', 'cuesta',
    'cuestan', 'vale', 'valen', 'stock', 'tienen', 'tiene', 'hay', 'venden', 'de', 'del', 'la', 'el',
    'los', 'las', 'un', 'una', 'unos', 'unas', 'por', 'favor', 'me', 'quiero', 'quisiera', 'necesito',
    'comprar', 'pedido', 'pedir', 'para', 'con', 'y', 'o', 'que', 'a', 'en', 'disponible', 'disponibles',
    'consulta', 'saber', 'si', 'sr', 'sra', 'gracias', 'busco', 'informacion', 'info', 'oye', 'x',
}

SALUDOS = ('hola', 'buenas', 'buenos dias', 'buenas tardes', 'buenas noches', 'menu', 'ayuda', 'inicio')
VERBOS_PEDIDO = ('quiero', 'pedido', 'pedir', 'comprar', 'quisiera', 'necesito', 'separar', 'reservar')

def _tienda():
    return settings.FERRETERIA['NOMBRE'].title()  # "FERRETERÍA C&R" -> "Ferretería C&R"


def _ayuda():
    return (f'¡Hola! Bienvenido a {_tienda()}. ¿Qué producto está buscando?\n'
            'Puede escribirme el nombre, por ejemplo: cemento, clavos o pintura.')


@dataclass
class Respuesta:
    texto: str
    intencion: str
    pedido: Pedido | None = None
    productos: list = field(default_factory=list)


def normalizar(texto):
    texto = unicodedata.normalize('NFKD', texto.lower())
    return ''.join(c for c in texto if not unicodedata.combining(c))


def _precio_con_igv(producto):
    return (producto.precio_venta * (1 + ventas_services.IGV)).quantize(ventas_services.CENT)


def _linea_producto(p):
    # No se muestra la cantidad exacta de stock al cliente (dato interno de la tienda)
    disp = 'agotado por ahora' if p.stock_actual <= 0 else 'disponible'
    return f'• *{p.nombre}*: S/ {_precio_con_igv(p)} ({disp}) · cód. {p.codigo}'


def buscar_productos(texto, limite=5):
    """Búsqueda por código exacto o por palabras clave en nombre/marca/categoría."""
    norm = normalizar(texto)
    codigos = re.findall(r'\b[a-z]{2,5}-\d{2,5}\b', norm)
    if codigos:
        return list(Producto.objects.filter(activo=True, codigo__in=[c.upper() for c in codigos]))
    palabras = [w for w in re.findall(r'[a-z0-9"/]+', norm) if w not in PALABRAS_VACIAS and len(w) > 2]
    if not palabras:
        return []
    qs = Producto.objects.filter(activo=True).select_related('marca', 'categoria')
    # primero intenta que coincidan TODAS las palabras; si no hay resultados, cualquiera
    todas = qs
    for w in palabras:
        raiz = w[:-1] if len(w) > 4 and w.endswith('s') else w  # "martillos" -> "martillo"
        todas = todas.filter(Q(nombre__icontains=raiz) | Q(marca__nombre__icontains=raiz) |
                             Q(categoria__nombre__icontains=raiz))
    resultado = list(todas[:limite])
    if resultado:
        return resultado
    cualquiera = Q()
    for w in palabras:
        raiz = w[:-1] if len(w) > 4 and w.endswith('s') else w
        cualquiera |= Q(nombre__icontains=raiz)
    return list(qs.filter(cualquiera)[:limite])


def _extraer_items_pedido(norm):
    """'pedido 2 mar-001, 1 pin-001' / 'quiero mar-001 x3' -> [(codigo, cantidad), ...]"""
    items = []
    for m in re.finditer(r'(?:(\d{1,4})\s*(?:x|unid\w*|und)?\s+)?([a-z]{2,5}-\d{2,5})(?:\s*x\s*(\d{1,4}))?', norm):
        cantidad = int(m.group(1) or m.group(3) or 1)
        items.append((m.group(2).upper(), cantidad))
    return items


def interpretar(texto, *, telefono='', nombre=''):
    norm = normalizar(texto).strip()

    # Estado de un pedido
    m = re.search(r'\bp-?(\d{1,6})\b', norm)
    if m and ('estado' in norm or 'mi pedido' in norm):
        pedido = Pedido.objects.filter(numero=f'P-{int(m.group(1)):06d}', contacto_telefono=telefono).first()
        if pedido is None:
            return Respuesta('No encuentro ese pedido asociado a su número. ¿Podría revisar el número, por favor?', 'ESTADO')
        estados = {'PENDIENTE': 'está pendiente: un asesor lo está revisando y le confirmará por aquí.',
                   'APROBADO': 'ya fue confirmado. Puede pasar a recogerlo y pagarlo en tienda.',
                   'RECHAZADO': f'no pudo ser atendido ({pedido.motivo_rechazo}).',
                   'CANCELADO': 'fue cancelado.'}
        return Respuesta(f'Su pedido *{pedido.numero}* {estados[pedido.estado]}', 'ESTADO', pedido=pedido)

    # Intención de compra con códigos
    if any(v in norm for v in VERBOS_PEDIDO):
        items = _extraer_items_pedido(norm)
        if items:
            productos = {p.codigo: p for p in Producto.objects.filter(activo=True, codigo__in=[c for c, _ in items])}
            faltan = [c for c, _ in items if c not in productos]
            if faltan:
                return Respuesta(f'No encuentro estos códigos: {", ".join(faltan)}. '
                                 'Escríbame el nombre del producto y le paso los correctos.', 'PEDIDO')
            pedido = ventas_services.crear_pedido(
                detalles=[{'producto': productos[c], 'cantidad': n} for c, n in items],
                origen='WHATSAPP', contacto_nombre=nombre or telefono, contacto_telefono=telefono,
                observacion=f'Mensaje: {texto[:200]}',
            )
            lineas = '\n'.join(f'• {d.cantidad} x {d.producto.nombre}' for d in pedido.detalles.select_related('producto'))
            avisos = [d.producto.nombre for d in pedido.detalles.select_related('producto')
                      if d.cantidad > d.producto.stock_actual]
            aviso = (f'\nDe {", ".join(avisos)} el asesor le confirmará cuántas unidades tenemos.'
                     if avisos else '')
            return Respuesta(
                f'Listo, su pedido *{pedido.numero}* quedó registrado:\n{lineas}\n'
                f'Total estimado: S/ {pedido.total_estimado} (incluye IGV).{aviso}\n'
                'En breve un asesor lo revisa y le confirma. El pago y el recojo son en tienda.',
                'PEDIDO', pedido=pedido)
        productos = buscar_productos(norm)
        if productos:
            return Respuesta('Con gusto. Para registrar su pedido necesito el código y la cantidad:\n' +
                             '\n'.join(_linea_producto(p) for p in productos) +
                             f'\nPor ejemplo, escriba: *pedido 2 {productos[0].codigo}*', 'PEDIDO', productos=productos)

    # Saludo / ayuda
    if any(norm == s or norm.startswith(s + ' ') or norm.startswith(s + ',') for s in SALUDOS) and \
            len(norm.split()) <= 4:
        return Respuesta(_ayuda(), 'SALUDO')

    # Consulta de producto / precio / stock
    productos = buscar_productos(norm)
    if productos:
        return Respuesta('Sí, tenemos estas opciones:\n' + '\n'.join(_linea_producto(p) for p in productos) +
                         f'\nPara hacer su pedido escriba la cantidad y el código, por ejemplo: '
                         f'*pedido 2 {productos[0].codigo}*', 'CONSULTA', productos=productos)

    return Respuesta('Disculpe, no logré entender su mensaje. ¿Qué producto está buscando?\n'
                     'Puede escribirme el nombre, por ejemplo: cemento, clavos o pintura.', 'DESCONOCIDA')

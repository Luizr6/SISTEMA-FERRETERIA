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

AYUDA = (
    '¡Hola! Soy el asistente de la ferretería 🛠️\n'
    'Puedes escribirme, por ejemplo:\n'
    '• *precio martillo*\n'
    '• *stock taladro*\n'
    '• *pedido 2 MAR-001, 1 PIN-001* (usa los códigos que te envío)\n'
    '• *estado P-000012*\n'
    'Un vendedor confirma cada pedido antes de separarlo.'
)


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
    if p.stock_actual == 0:
        disp = 'agotado ❌'
    elif p.stock_actual <= p.stock_minimo:
        disp = f'quedan pocas unidades ({p.stock_actual}) ⚠️'
    else:
        disp = f'disponible ({p.stock_actual}) ✅'
    return f'• *{p.nombre}* [{p.codigo}] - S/ {_precio_con_igv(p)} inc. IGV - {disp}'


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
            return Respuesta('No encontré ese pedido asociado a tu número. Revisa el código, por favor.', 'ESTADO')
        estados = {'PENDIENTE': 'está *pendiente* de confirmación por un vendedor ⏳',
                   'APROBADO': 'fue *confirmado* ✅. Ya puedes recogerlo en tienda.',
                   'RECHAZADO': f'fue *rechazado* ❌ ({pedido.motivo_rechazo})',
                   'CANCELADO': 'fue *cancelado*.'}
        return Respuesta(f'Tu pedido {pedido.numero} {estados[pedido.estado]}', 'ESTADO', pedido=pedido)

    # Intención de compra con códigos
    if any(v in norm for v in VERBOS_PEDIDO):
        items = _extraer_items_pedido(norm)
        if items:
            productos = {p.codigo: p for p in Producto.objects.filter(activo=True, codigo__in=[c for c, _ in items])}
            faltan = [c for c, _ in items if c not in productos]
            if faltan:
                return Respuesta(f'No encontré estos códigos: {", ".join(faltan)}. '
                                 'Escribe *precio <producto>* para ver los códigos correctos.', 'PEDIDO')
            pedido = ventas_services.crear_pedido(
                detalles=[{'producto': productos[c], 'cantidad': n} for c, n in items],
                origen='WHATSAPP', contacto_nombre=nombre or telefono, contacto_telefono=telefono,
                observacion=f'Mensaje: {texto[:200]}',
            )
            lineas = '\n'.join(f'• {d.cantidad} x {d.producto.nombre}' for d in pedido.detalles.select_related('producto'))
            avisos = [d.producto.nombre for d in pedido.detalles.select_related('producto')
                      if d.cantidad > d.producto.stock_actual]
            aviso = (f'\n⚠️ Ojo: no hay stock suficiente de {", ".join(avisos)}; el vendedor te contactará.'
                     if avisos else '')
            return Respuesta(
                f'📝 Registré tu pedido *{pedido.numero}*:\n{lineas}\n'
                f'Total estimado: *S/ {pedido.total_estimado}* (inc. IGV).{aviso}\n'
                'Un vendedor lo revisará y te confirmará. Consulta con: *estado ' + pedido.numero + '*',
                'PEDIDO', pedido=pedido)
        productos = buscar_productos(norm)
        if productos:
            return Respuesta('Para registrar tu pedido necesito el código y la cantidad. Encontré:\n' +
                             '\n'.join(_linea_producto(p) for p in productos) +
                             f'\nEjemplo: *pedido 2 {productos[0].codigo}*', 'PEDIDO', productos=productos)

    # Saludo / ayuda
    if any(norm == s or norm.startswith(s + ' ') or norm.startswith(s + ',') for s in SALUDOS) and \
            len(norm.split()) <= 4:
        return Respuesta(AYUDA, 'SALUDO')

    # Consulta de producto / precio / stock
    productos = buscar_productos(norm)
    if productos:
        return Respuesta('Esto encontré 🔎\n' + '\n'.join(_linea_producto(p) for p in productos) +
                         '\nPara pedir escribe: *pedido <cantidad> <código>*', 'CONSULTA', productos=productos)

    return Respuesta('No logré entender tu mensaje 🤔. ¿Puedes escribirlo de otra forma?\n'
                     'Ejemplos: *precio martillo*, *stock pintura*, *pedido 2 MAR-001*.', 'DESCONOCIDA')

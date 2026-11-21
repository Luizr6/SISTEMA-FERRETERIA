"""
Bot de WhatsApp con IA (RF30, CU14-CU16).

Cómo funciona:
  1. La IA lee el mensaje del cliente (y los últimos mensajes de la conversación) y decide qué
     necesita: buscar productos, registrar un pedido, consultar un pedido o dar datos de la tienda.
  2. Django ejecuta esas "herramientas" contra la BASE DE DATOS y le devuelve los datos reales.
  3. La IA redacta la respuesta con esos datos.

Garantías (requisitos del documento):
  * Precios y stock salen SIEMPRE de la base de datos; la IA no puede inventarlos.
  * El pedido queda como «Pedido por Confirmar» (PENDIENTE) sin tocar stock (RF32 / CU17).
  * Si la IA falla (sin clave, sin internet, cupo agotado), se usa el bot por reglas (bot.py).
"""
import json
import logging
import re
from datetime import timedelta
from decimal import Decimal

from django.conf import settings
from django.db.models import Q
from django.utils import timezone

from asistente import ia
from productos.models import Producto
from ventas import services as ventas_services
from ventas.models import Pedido
from . import bot
from .models import Mensaje

log = logging.getLogger('ferreteria.whatsapp')

MAX_RONDAS = 6          # llamadas máximas a la IA por mensaje (búsquedas + respuesta)
REINTENTOS_TEXTO = 2    # si la redacción sale dañada, se pide de nuevo hasta 2 veces
MAX_RESULTADOS = 6      # productos que se le muestran a la IA por búsqueda

HERRAMIENTAS = [
    {
        'type': 'function',
        'function': {
            'name': 'buscar_productos',
            'description': ('Busca productos (nombre, marca o categoría). Úsala siempre antes de dar precios. '
                            'Palabras simples en singular. codigo_interno es solo para agregar_al_pedido.'),
            'parameters': {
                'type': 'object',
                'properties': {
                    'consulta': {'type': 'string', 'description': 'Palabras clave o código del producto.'},
                },
                'required': ['consulta'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'agregar_al_pedido',
            'description': ('Agrega productos al pedido en armado (aún NO se registra); si ya estaba, '
                            'reemplaza su cantidad. Solo si el cliente pidió ese producto y cantidad.'),
            'parameters': {
                'type': 'object',
                'properties': {'items': {
                        'type': 'array',
                        'items': {
                            'type': 'object',
                            'properties': {
                                'codigo': {'type': 'string', 'description': 'codigo_interno'},
                                'cantidad': {'type': 'integer', 'minimum': 1, 'description': 'cantidad total'},
                            },
                            'required': ['codigo', 'cantidad'],
                        },
                    }},
                'required': ['items'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'quitar_del_pedido',
            'description': 'Quita productos del pedido en armado.',
            'parameters': {
                'type': 'object',
                'properties': {'codigos': {'type': 'array', 'items': {'type': 'string'}}},
                'required': ['codigos'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'ver_pedido_actual',
            'description': 'Resumen del pedido en armado.',
            'parameters': {'type': 'object', 'properties': {}},
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'confirmar_pedido',
            'description': 'Registra el pedido armado. Solo cuando el cliente confirmó tras ver el resumen.',
            'parameters': {'type': 'object', 'properties': {'nota': {'type': 'string'}}},
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'cancelar_pedido',
            'description': 'Cancela un pedido registrado y pendiente de este cliente.',
            'parameters': {
                'type': 'object',
                'properties': {'numero': {'type': 'string'}},
                'required': ['numero'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'consultar_pedido',
            'description': 'Estado de un pedido del cliente (ej. P-000012).',
            'parameters': {
                'type': 'object',
                'properties': {'numero': {'type': 'string'}},
                'required': ['numero'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'datos_tienda',
            'description': 'Dirección, teléfono y horario de la tienda.',
            'parameters': {'type': 'object', 'properties': {}},
        },
    },
]


def _instrucciones():
    # Texto compacto a propósito: se envía en cada llamada y el plan gratuito de Groq limita los tokens por minuto
    nombre = settings.FERRETERIA['NOMBRE'].title()  # "FERRETERÍA C&R" -> "Ferretería C&R"
    return f"""Eres el asesor de ventas de {nombre} (ferretería en Perú) y atiendes por WhatsApp como un buen vendedor de mostrador.

ESTILO: trato de "usted"; 2 a 6 líneas; español natural, nada robótico. *Negrita* (un asterisco) solo para nombres de producto o número de pedido; listas con "•". Sin tablas ni encabezados. Máximo un emoji. Termina con una pregunta breve que ayude a avanzar.

PRODUCTOS
• Muestra solo nombre, precio y si hay: "• *Martillo de uña 16 oz*: S/ 37.76". Los precios ya incluyen IGV; si es por kilo, metro o galón, acláralo.
• NUNCA muestres códigos internos ni cantidades de stock. Si no hay stock, dilo con amabilidad y ofrece una alternativa.
• Nunca inventes productos ni precios: usa buscar_productos. Si describe una necesidad, piensa como vendedor (martillo y clavos; taladro, tarugos y tornillos).
• Antes de decir que no hay, busca con sinónimos peruanos: guincha=wincha=cinta métrica · pegamento=cola=terokal=silicona (para tubos de PVC el correcto es el "pegamento para PVC"; el terokal es para madera o jebe) · taco=tarugo · caño=tubo · fierro=varilla · triplay=madera contrachapada · thinner=disolvente · teflón=cinta teflón.

PEDIDOS (en dos pasos; nada se registra sin confirmación)
1. Si el cliente quiere un producto y la cantidad es clara, usa agregar_al_pedido. Si solo pregunta ("¿de cuál tiene?", "¿qué marcas?") o duda, NO agregues: muestra opciones y pregunta cuál y cuántos.
2. Tras agregar o quitar, muestra el resumen (productos, cantidades, total) y pregunta: "¿Desea que registre su pedido así o necesita algo más?". El cliente puede seguir consultando y cambiando (agregar_al_pedido con la cantidad nueva, quitar_del_pedido).
3. Usa confirmar_pedido solo cuando responda que sí. El sistema escribe la confirmación; no la redactes ni digas que quedó registrado si no respondió ok.
4. Un pedido ya registrado no se modifica: si ya no lo quiere y sigue pendiente, usa cancelar_pedido.

LÍMITES: no ofrezcas descuentos, delivery, crédito ni fechas; eso lo ve un asesor, que responderá por este mismo chat en el horario de atención (datos_tienda). Nunca pidas teléfono, correo ni datos personales. Temas ajenos a la ferretería: responde con cortesía que solo ayudas con productos, precios y pedidos. Si solo saluda, preséntate en una línea y pregunta qué busca."""


# ----------------------------------------------------------------------------- herramientas

def _producto_dict(p):
    """Lo que la IA ve de un producto. No se le pasa la cantidad de stock para que nunca la revele."""
    return {
        'codigo_interno': p.codigo,  # solo para agregar_al_pedido; no se muestra al cliente
        'nombre': p.nombre,
        'marca': p.marca.nombre if p.marca_id else '',
        'categoria': p.categoria.nombre if p.categoria_id else '',
        'unidad': p.get_unidad_medida_display() if hasattr(p, 'get_unidad_medida_display') else p.unidad_medida,
        'precio_con_igv': str(bot._precio_con_igv(p)),
        'disponibilidad': 'sin stock' if p.stock_actual <= 0 else 'disponible',
    }


def buscar(consulta):
    """Búsqueda del bot por reglas y, si no hay resultados, palabra por palabra en nombre/descripción."""
    productos = bot.buscar_productos(consulta, limite=MAX_RESULTADOS)
    if not productos:
        palabras = [w for w in re.findall(r'[a-z0-9]+', bot.normalizar(consulta))
                    if len(w) > 2 and w not in bot.PALABRAS_VACIAS]
        filtro = Q()
        for w in palabras:
            raiz = w[:-1] if len(w) > 4 and w.endswith('s') else w
            filtro |= Q(nombre__icontains=raiz) | Q(descripcion__icontains=raiz) | Q(categoria__nombre__icontains=raiz)
        if filtro:
            productos = list(Producto.objects.filter(activo=True).filter(filtro)
                             .select_related('marca', 'categoria')[:MAX_RESULTADOS])
    return productos


class _Contexto:
    """Estado de un mensaje: datos del cliente y lo que hicieron las herramientas."""

    def __init__(self, telefono, nombre, texto, conversacion=None, entrante_id=None):
        self.telefono = telefono
        self.nombre = nombre
        self.texto = texto
        self.conversacion = conversacion
        self.entrante_id = entrante_id
        self.pedido = None
        self.productos = []
        self.usadas = set()
        self.carrito_cambio = False
        self.carrito = _cargar_carrito(conversacion)


CARRITO_VIGENCIA = timedelta(hours=24)   # un pedido armado y no confirmado se descarta al día siguiente

# "sí", "confírmelo", "así está bien"... y no "aún no", "espere", "quiero cambiar"
_AFIRMA = re.compile(r'\b(si|confirm\w*|dale|ok|okey|okay|correcto|de acuerdo|listo|claro|perfecto|'
                     r'registr\w*|hagalo|adelante|esta bien|asi esta bien|por favor|ya|exacto|eso es|va)\b')
_NIEGA = re.compile(r'\b(no|aun no|todavia|espere|espera|cambi\w*|quit\w*|agreg\w*|tambien|ademas|pero)\b')


def _cliente_confirma(texto):
    t = bot.normalizar(texto or '')
    return bool(_AFIRMA.search(t)) and not _NIEGA.search(t)


def _cargar_carrito(conversacion):
    datos = dict(getattr(conversacion, 'carrito', None) or {})
    actualizado = datos.get('actualizado')
    if actualizado:
        try:
            from datetime import datetime
            if timezone.now() - datetime.fromisoformat(actualizado) > CARRITO_VIGENCIA:
                datos = {}
        except (TypeError, ValueError):
            datos = {}
    datos.setdefault('items', {})
    return datos


def _guardar_carrito(ctx):
    ctx.carrito['actualizado'] = timezone.now().isoformat()
    ctx.carrito_cambio = True
    ctx.carrito['cambio_en'] = ctx.entrante_id
    if ctx.conversacion is not None and ctx.conversacion.pk:
        from .models import Conversacion
        Conversacion.objects.filter(pk=ctx.conversacion.pk).update(carrito=ctx.carrito)
        ctx.conversacion.carrito = ctx.carrito


def _resumen_carrito(ctx):
    items = ctx.carrito.get('items', {})
    productos = {p.codigo: p for p in Producto.objects.filter(activo=True, codigo__in=list(items))}
    lineas, total = [], Decimal('0')
    for codigo, cantidad in items.items():
        p = productos.get(codigo)
        if p is None:
            continue
        precio = bot._precio_con_igv(p)
        subtotal = (precio * cantidad).quantize(Decimal('0.01'))
        total += subtotal
        lineas.append({'codigo_interno': codigo, 'nombre': p.nombre, 'cantidad': cantidad,
                       'unidad': p.get_unidad_medida_display(), 'precio_con_igv': str(precio),
                       'subtotal_con_igv': str(subtotal), 'disponibilidad': 'sin stock' if p.stock_actual <= 0
                       else ('cantidad por confirmar' if cantidad > p.stock_actual else 'disponible')})
    return {'ok': True, 'registrado': False, 'productos': lineas, 'total_estimado_con_igv': str(total),
            'vacio': not lineas,
            'siguiente_paso': ('Muestra este resumen y pregunta si desea registrar el pedido así o necesita algo más.'
                               if lineas else 'El pedido está vacío.')}


def _texto_resumen(resumen):
    """Resumen escrito por Django, por si la IA no lo mostró."""
    lineas = '\n'.join(f'• {i["cantidad"]} x {i["nombre"]}: S/ {i["subtotal_con_igv"]}' for i in resumen['productos'])
    return (f'Su pedido hasta ahora:\n{lineas}\nTotal estimado: S/ {resumen["total_estimado_con_igv"]}.\n'
            '¿Desea que lo registre así o necesita algo más?')


def _leer_items(items):
    cantidades = {}
    for it in items or []:
        codigo = str((it or {}).get('codigo', '')).strip().upper()
        try:
            cantidad = int((it or {}).get('cantidad', 0))
        except (TypeError, ValueError):
            cantidad = 0
        if not codigo or not 1 <= cantidad <= 9999:
            raise ValueError(f'Cantidad o código inválido: {it}. Pregunta al cliente.')
        cantidades[codigo] = cantidad
    if not cantidades:
        raise ValueError('No indicaste productos.')
    return cantidades


def _agregar(ctx, items):
    try:
        cantidades = _leer_items(items)
    except ValueError as e:
        return {'ok': False, 'error': str(e)}
    existentes = set(Producto.objects.filter(activo=True, codigo__in=list(cantidades)).values_list('codigo', flat=True))
    faltan = [c for c in cantidades if c not in existentes]
    if faltan:
        return {'ok': False, 'error': f'Estos códigos no existen: {", ".join(faltan)}. Búscalos con buscar_productos.'}
    ctx.carrito['items'].update(cantidades)
    _guardar_carrito(ctx)
    return _resumen_carrito(ctx)


def _quitar(ctx, codigos):
    quitados = [c for c in (str(x).strip().upper() for x in codigos or []) if ctx.carrito['items'].pop(c, None)]
    if not quitados:
        return {**_resumen_carrito(ctx), 'ok': False, 'error': 'Ese producto no estaba en el pedido.'}
    _guardar_carrito(ctx)
    return _resumen_carrito(ctx)


def _confirmar(ctx, nota=''):
    if ctx.pedido is not None:
        return {'ok': False, 'error': f'El pedido {ctx.pedido.numero} ya se registró en este mensaje.'}
    items = ctx.carrito.get('items', {})
    if not items:
        return {'ok': False, 'error': 'No hay productos en el pedido. Pregunta al cliente qué desea.'}
    if ctx.carrito.get('cambio_en') == ctx.entrante_id or not _cliente_confirma(ctx.texto):
        return {**_resumen_carrito(ctx), 'ok': False,
                'error': ('El cliente todavía no confirmó este pedido. Muéstrale el resumen y '
                          'pregúntale si desea registrarlo así.')}
    productos = {p.codigo: p for p in Producto.objects.filter(activo=True, codigo__in=list(items))}
    detalles = [{'producto': productos[c], 'cantidad': n} for c, n in items.items() if c in productos]
    if not detalles:
        return {'ok': False, 'error': 'Los productos del pedido ya no están disponibles.'}
    ctx.pedido = ventas_services.crear_pedido(
        detalles=detalles, origen='WHATSAPP', contacto_nombre=ctx.nombre or ctx.telefono,
        contacto_telefono=ctx.telefono,
        observacion=(f'{nota.strip()} · ' if nota and nota.strip() else '') + 'Confirmado por el cliente en WhatsApp.',
    )
    ctx.carrito = {'items': {}}
    _guardar_carrito(ctx)
    return {'ok': True, 'registrado': True, 'numero': ctx.pedido.numero}


def _cancelar(ctx, numero):
    m = re.search(r'(\d{1,6})', str(numero))
    pedido = (Pedido.objects.filter(numero=f'P-{int(m.group(1)):06d}', contacto_telefono=ctx.telefono).first()
              if m else None)
    if pedido is None:
        return {'ok': False, 'error': 'No existe un pedido con ese número asociado a este WhatsApp.'}
    if pedido.estado != 'PENDIENTE':
        return {'ok': False, 'error': f'El pedido {pedido.numero} ya no se puede cancelar: está '
                                      f'{pedido.get_estado_display().lower()}. Un asesor puede ayudarle.'}
    ventas_services.rechazar_pedido(pedido=pedido, usuario=None, estado='CANCELADO',
                                    motivo='Cancelado por el cliente desde WhatsApp')
    return {'ok': True, 'numero': pedido.numero, 'estado': 'cancelado'}


def _consultar_pedido(ctx, numero):
    m = re.search(r'(\d{1,6})', str(numero))
    if not m:
        return {'ok': False, 'error': 'Número de pedido inválido.'}
    pedido = Pedido.objects.filter(numero=f'P-{int(m.group(1)):06d}', contacto_telefono=ctx.telefono).first()
    if pedido is None:
        return {'ok': False, 'error': 'No existe un pedido con ese número asociado a este WhatsApp.'}
    return {
        'ok': True, 'numero': pedido.numero, 'estado': pedido.estado,
        'significado': {
            'PENDIENTE': 'Un vendedor aún debe confirmarlo.',
            'APROBADO': 'Confirmado: ya puede recogerlo y pagarlo en tienda.',
            'RECHAZADO': 'Rechazado por el vendedor.',
            'CANCELADO': 'Cancelado.',
        }.get(pedido.estado, ''),
        'motivo': pedido.motivo_rechazo or '',
        'total_estimado_con_igv': str(pedido.total_estimado),
    }


def _ejecutar(ctx, nombre, argumentos):
    ctx.usadas.add(nombre)
    try:
        args = json.loads(argumentos or '{}') if isinstance(argumentos, str) else (argumentos or {})
    except ValueError:
        return {'ok': False, 'error': 'Argumentos inválidos.'}

    if nombre == 'buscar_productos':
        productos = buscar(str(args.get('consulta', '')))
        ctx.productos.extend(p for p in productos if p not in ctx.productos)
        return {'ok': True, 'resultados': [_producto_dict(p) for p in productos],
                'nota': '' if productos else 'Sin resultados. Prueba otra palabra.'}
    if nombre == 'agregar_al_pedido':
        return _agregar(ctx, args.get('items') or [])
    if nombre == 'quitar_del_pedido':
        return _quitar(ctx, args.get('codigos') or [])
    if nombre == 'ver_pedido_actual':
        return _resumen_carrito(ctx)
    if nombre == 'confirmar_pedido':
        return _confirmar(ctx, args.get('nota', ''))
    if nombre == 'cancelar_pedido':
        return _cancelar(ctx, args.get('numero', ''))
    if nombre == 'consultar_pedido':
        return _consultar_pedido(ctx, args.get('numero', ''))
    if nombre == 'datos_tienda':
        t = settings.FERRETERIA
        return {'ok': True, 'nombre': t['NOMBRE'], 'direccion': t['DIRECCION'],
                'telefono': t['TELEFONO'], 'horario': t['HORARIO']}
    return {'ok': False, 'error': f'Herramienta desconocida: {nombre}'}


# ----------------------------------------------------------------------------- flujo principal

def _historial(conversacion, excluir_id):
    if conversacion is None:
        return []
    n = settings.IA.get('HISTORIAL', 10)
    previos = list(Mensaje.objects.filter(conversacion=conversacion).exclude(pk=excluir_id)
                   .order_by('-fecha', '-id')[:n])
    return [{'role': 'user' if m.direccion == 'ENTRANTE' else 'assistant', 'content': m.texto[:1500]}
            for m in reversed(previos)
            if m.texto and (m.direccion == 'ENTRANTE' or ia.texto_valido(m.texto))]


def _sin_datos_internos(texto, ctx):
    """Red de seguridad: si la IA igual escribió un código interno ("[MAR-001]"), se quita."""
    vistos = list(ctx.productos) + list(Producto.objects.filter(codigo__in=list(ctx.carrito.get('items', {}))))
    for p in vistos:
        for patron in (f' [{p.codigo}]', f'[{p.codigo}] ', f'[{p.codigo}]', f' ({p.codigo})', f'({p.codigo})'):
            texto = texto.replace(patron, '')
    return texto.strip()


def _intencion(ctx):
    if ctx.pedido is not None:
        return 'PEDIDO'
    if ctx.carrito_cambio or {'consultar_pedido', 'cancelar_pedido'} & ctx.usadas:
        return 'PEDIDO' if ctx.carrito_cambio or 'cancelar_pedido' in ctx.usadas else 'ESTADO'
    if 'consultar_pedido' in ctx.usadas:
        return 'ESTADO'
    if 'buscar_productos' in ctx.usadas:
        return 'CONSULTA'
    return 'SALUDO'


def _confirmacion_basica(pedido):
    """Texto de respaldo si la IA falla DESPUÉS de haber registrado el pedido (mismo tono, sin códigos)."""
    lineas = '\n'.join(f'• {d.cantidad} {d.producto.get_unidad_medida_display().lower()} de {d.producto.nombre}'
                       if d.producto.unidad_medida != 'UND' else f'• {d.cantidad} x {d.producto.nombre}'
                       for d in pedido.detalles.select_related('producto'))
    faltan = [d.producto.nombre for d in pedido.detalles.select_related('producto')
              if d.cantidad > d.producto.stock_actual]
    aviso = (f'\nDe {", ".join(faltan)} el asesor le confirmará cuántas unidades tenemos.' if faltan else '')
    return (f'Listo, su pedido *{pedido.numero}* quedó registrado:\n{lineas}\n'
            f'Total estimado: S/ {pedido.total_estimado} (incluye IGV).{aviso}\n'
            'En breve un asesor lo revisa y le confirma. El pago y el recojo son en tienda.\n'
            '¿Le puedo ayudar con algo más?')


def responder_con_ia(texto, *, telefono, nombre='', conversacion=None, excluir_id=None):
    """Devuelve bot.Respuesta. Lanza ia.IAError si no se pudo usar la IA y aún no se registró nada."""
    ctx = _Contexto(telefono, nombre, texto, conversacion, excluir_id)
    mensajes = [{'role': 'system', 'content': _instrucciones()}]
    mensajes += _historial(conversacion, excluir_id)
    if nombre:
        mensajes.append({'role': 'system', 'content': f'El cliente se llama {nombre}.'})
    mensajes.append({'role': 'user', 'content': texto[:1500]})

    try:
        reintentos = 0
        for _ in range(MAX_RONDAS + REINTENTOS_TEXTO):
            msg = ia.chat(mensajes, herramientas=HERRAMIENTAS, max_tokens=700)
            llamadas = msg.get('tool_calls') or []
            if llamadas:
                mensajes.append({'role': 'assistant', 'content': msg.get('content') or None, 'tool_calls': llamadas})
                for ll in llamadas:
                    fn = ll.get('function', {})
                    resultado = _ejecutar(ctx, fn.get('name', ''), fn.get('arguments'))
                    mensajes.append({'role': 'tool', 'tool_call_id': ll.get('id'), 'name': fn.get('name', ''),
                                     'content': json.dumps(resultado, ensure_ascii=False, default=str)})
                if ctx.pedido is not None:
                    # El mensaje más importante lo escribe Django, no la IA: número, productos y total exactos
                    return bot.Respuesta(_confirmacion_basica(ctx.pedido), 'PEDIDO', pedido=ctx.pedido)
                continue

            respuesta = _sin_datos_internos(ia.formato_whatsapp(msg.get('content')), ctx)
            if not ia.texto_valido(respuesta):
                # El modelo a veces "se traba" (palabras sueltas, ¿ desea ?). Se pide otra redacción.
                log.warning('Respuesta de la IA descartada: %r', (msg.get('content') or '')[:300])
                reintentos += 1
                if reintentos > REINTENTOS_TEXTO:
                    raise ia.IAError('La IA devolvió una respuesta incoherente; se usa el respaldo.')
                continue
            if ctx.carrito_cambio and ctx.carrito.get('items'):
                resumen = _resumen_carrito(ctx)
                if f'S/ {resumen["total_estimado_con_igv"]}' not in respuesta:
                    respuesta += '\n\n' + _texto_resumen(resumen)
            return bot.Respuesta(respuesta, _intencion(ctx), pedido=ctx.pedido, productos=ctx.productos)
        raise ia.IAError('La IA no terminó de responder.')
    except Exception as e:  # noqa: BLE001
        if ctx.pedido is not None:  # el pedido ya existe: no se pierde, se confirma con el texto básico
            return bot.Respuesta(_confirmacion_basica(ctx.pedido), 'PEDIDO', pedido=ctx.pedido)
        if isinstance(e, ia.IAError):
            raise
        raise ia.IAError(f'Error inesperado: {e}') from e


def responder(texto, *, telefono, nombre='', conversacion=None, excluir_id=None):
    """Punto de entrada: IA si está disponible; si no (o si falla), el bot por reglas."""
    if ia.disponible():
        try:
            return responder_con_ia(texto, telefono=telefono, nombre=nombre,
                                    conversacion=conversacion, excluir_id=excluir_id)
        except ia.IAError as e:
            log.warning('Bot IA no disponible, se usa el bot por reglas: %s', e)
        except Exception:  # noqa: BLE001 - nunca dejar al cliente sin respuesta
            log.exception('Error inesperado en el bot IA; se usa el bot por reglas')
    return bot.interpretar(texto, telefono=telefono, nombre=nombre)

"""
Asistente del Administrador (app móvil).

  * foto_del_negocio(): datos REALES de la base de datos (ventas, stock, pedidos, compras).
  * alertas():          avisos calculados con reglas fijas -> notificaciones de la app.
  * responder():        la IA redacta un resumen o responde una pregunta usando SOLO esos datos.
                        Sin IA (o si falla) se devuelve un resumen básico armado con reglas.
"""
import json
import logging
from datetime import timedelta
from decimal import Decimal

from django.db.models import Count, F, Min, Sum
from django.utils import timezone

from compras.models import OrdenCompra
from productos.models import Producto
from ventas.models import DetalleVenta, Pedido, Venta
from . import ia

log = logging.getLogger('ferreteria.ia')


def _soles(valor):
    return f'S/ {Decimal(valor or 0):,.2f}'


def _num(valor):
    return float(valor or 0)


def foto_del_negocio():
    """Resumen numérico del negocio. Es lo único que la IA puede usar para responder."""
    ahora = timezone.localtime()
    hoy = ahora.date()
    ok = Venta.objects.filter(estado='COMPLETADA')

    def total(qs):
        r = qs.aggregate(n=Count('id'), t=Sum('total'))
        return {'ventas': r['n'] or 0, 'total': _num(r['t'])}

    ultimos_7 = total(ok.filter(fecha__date__gte=hoy - timedelta(days=7), fecha__date__lt=hoy))
    promedio_diario_7 = round(ultimos_7['total'] / 7, 2)

    agotados = list(Producto.objects.filter(activo=True, stock_actual=0)
                    .values('codigo', 'nombre', 'stock_minimo').order_by('nombre')[:15])
    bajos = list(Producto.objects.filter(activo=True, stock_actual__gt=0, stock_actual__lte=F('stock_minimo'))
                 .values('codigo', 'nombre', 'stock_actual', 'stock_minimo').order_by('stock_actual')[:15])

    pendientes = Pedido.objects.filter(estado='PENDIENTE')
    mas_antiguo = pendientes.aggregate(f=Min('fecha'))['f']

    por_vendedor = [
        {'vendedor': r['vendedor__username'], 'ventas': r['n'], 'total': _num(r['t'])}
        for r in ok.filter(fecha__date=hoy).values('vendedor__username')
        .annotate(n=Count('id'), t=Sum('total')).order_by('-t')
    ]
    top_semana = [
        {'producto': r['producto__nombre'], 'unidades': r['u'], 'monto': _num(r['m'])}
        for r in DetalleVenta.objects.filter(venta__estado='COMPLETADA', venta__fecha__date__gte=hoy - timedelta(days=6))
        .values('producto__nombre').annotate(u=Sum('cantidad'), m=Sum('subtotal')).order_by('-u')[:5]
    ]

    return {
        'fecha': ahora.strftime('%d/%m/%Y %H:%M'),
        'ventas_hoy': total(ok.filter(fecha__date=hoy)),
        'ventas_ayer': total(ok.filter(fecha__date=hoy - timedelta(days=1))),
        'promedio_diario_ultimos_7_dias': promedio_diario_7,
        'ventas_mes': total(ok.filter(fecha__year=ahora.year, fecha__month=ahora.month)),
        'ventas_anuladas_hoy': Venta.objects.filter(estado='ANULADA', fecha_anulacion__date=hoy).count(),
        'ventas_por_vendedor_hoy': por_vendedor,
        'mas_vendidos_7_dias': top_semana,
        'productos_agotados': agotados,
        'productos_stock_bajo': bajos,
        'total_agotados': Producto.objects.filter(activo=True, stock_actual=0).count(),
        'total_stock_bajo': Producto.objects.filter(activo=True, stock_actual__gt=0,
                                                    stock_actual__lte=F('stock_minimo')).count(),
        'pedidos_por_confirmar': pendientes.count(),
        'pedidos_por_confirmar_whatsapp': pendientes.filter(origen='WHATSAPP').count(),
        'minutos_esperando_pedido_mas_antiguo': int((timezone.now() - mas_antiguo).total_seconds() // 60)
        if mas_antiguo else 0,
        'ordenes_compra_por_recibir': OrdenCompra.objects.filter(estado__in=['PENDIENTE', 'PARCIAL']).count(),
        'ordenes_compra_atrasadas': OrdenCompra.objects.filter(
            estado__in=['PENDIENTE', 'PARCIAL'], fecha_entrega_estimada__lt=hoy).count(),
    }


def alertas(foto=None):
    """
    Avisos para las notificaciones de la app (sin IA: reglas fijas, siempre disponibles).
    Cada alerta tiene un 'id' estable mientras la situación no cambie, para que la app no
    repita la misma notificación.
    """
    f = foto or foto_del_negocio()
    hoy = timezone.localdate().isoformat()
    lista = []

    if f['total_agotados']:
        nombres = ', '.join(p['nombre'] for p in f['productos_agotados'][:3])
        lista.append({
            'id': f'agotados-{hoy}-{f["total_agotados"]}', 'tipo': 'STOCK_AGOTADO', 'nivel': 'CRITICO',
            'titulo': f'{f["total_agotados"]} producto(s) agotado(s)',
            'mensaje': f'Sin stock: {nombres}' + ('…' if f['total_agotados'] > 3 else '.'),
        })
    if f['total_stock_bajo']:
        nombres = ', '.join(p['nombre'] for p in f['productos_stock_bajo'][:3])
        lista.append({
            'id': f'bajo-{hoy}-{f["total_stock_bajo"]}', 'tipo': 'STOCK_BAJO', 'nivel': 'ALERTA',
            'titulo': f'{f["total_stock_bajo"]} producto(s) en stock mínimo',
            'mensaje': f'Por reponer: {nombres}' + ('…' if f['total_stock_bajo'] > 3 else '.'),
        })
    if f['pedidos_por_confirmar']:
        wa = f['pedidos_por_confirmar_whatsapp']
        espera = f['minutos_esperando_pedido_mas_antiguo']
        lista.append({
            'id': f'pedidos-{f["pedidos_por_confirmar"]}-{wa}', 'tipo': 'PEDIDOS_PENDIENTES',
            'nivel': 'ALERTA' if espera >= 30 else 'INFO',
            'titulo': f'{f["pedidos_por_confirmar"]} pedido(s) por confirmar',
            'mensaje': (f'{wa} por WhatsApp. ' if wa else '') +
                       (f'El más antiguo espera {espera} min.' if espera else 'Revísalos en Pedidos.'),
        })
    if f['ordenes_compra_atrasadas']:
        lista.append({
            'id': f'compras-atrasadas-{hoy}-{f["ordenes_compra_atrasadas"]}', 'tipo': 'COMPRAS_ATRASADAS',
            'nivel': 'ALERTA', 'titulo': f'{f["ordenes_compra_atrasadas"]} orden(es) de compra atrasada(s)',
            'mensaje': 'Pasó la fecha estimada de entrega. Contacta al proveedor.',
        })
    hora = timezone.localtime().hour
    if hora >= 15 and f['promedio_diario_ultimos_7_dias'] > 0 and \
            f['ventas_hoy']['total'] < 0.5 * f['promedio_diario_ultimos_7_dias']:
        lista.append({
            'id': f'ventas-bajas-{hoy}', 'tipo': 'VENTAS_BAJAS', 'nivel': 'INFO',
            'titulo': 'Ventas de hoy por debajo de lo normal',
            'mensaje': f'Llevas {_soles(f["ventas_hoy"]["total"])}; el promedio diario es '
                       f'{_soles(f["promedio_diario_ultimos_7_dias"])}.',
        })
    return lista


def resumen_basico(f):
    """Resumen sin IA (respaldo)."""
    partes = [
        f'Hoy llevas {_soles(f["ventas_hoy"]["total"])} en {f["ventas_hoy"]["ventas"]} venta(s) '
        f'(ayer: {_soles(f["ventas_ayer"]["total"])}; promedio diario: {_soles(f["promedio_diario_ultimos_7_dias"])}).',
        f'En el mes: {_soles(f["ventas_mes"]["total"])} en {f["ventas_mes"]["ventas"]} venta(s).',
    ]
    if f['total_agotados'] or f['total_stock_bajo']:
        partes.append(f'Inventario: {f["total_agotados"]} agotado(s) y {f["total_stock_bajo"]} en stock mínimo.')
        reponer = [p['nombre'] for p in (f['productos_agotados'] + f['productos_stock_bajo'])[:5]]
        partes.append('Prioriza reponer: ' + ', '.join(reponer) + '.')
    else:
        partes.append('Inventario en orden: ningún producto en stock mínimo.')
    if f['pedidos_por_confirmar']:
        partes.append(f'Tienes {f["pedidos_por_confirmar"]} pedido(s) por confirmar '
                      f'({f["pedidos_por_confirmar_whatsapp"]} por WhatsApp).')
    if f['ordenes_compra_por_recibir']:
        partes.append(f'Órdenes de compra por recibir: {f["ordenes_compra_por_recibir"]}'
                      + (f' ({f["ordenes_compra_atrasadas"]} atrasada(s)).' if f['ordenes_compra_atrasadas'] else '.'))
    if f['mas_vendidos_7_dias']:
        partes.append(f'Lo más vendido esta semana: {f["mas_vendidos_7_dias"][0]["producto"]}.')
    return '\n'.join(partes)


INSTRUCCIONES = """Eres el asistente del ADMINISTRADOR de una ferretería en Perú (FERRETERÍA C&R).
Recibes una foto en JSON con los datos reales del negocio en este momento.

REGLAS
1. Usa SOLO los datos del JSON. No inventes cifras, productos ni tendencias que no se deduzcan de ellos.
   Si la pregunta pide algo que no está en los datos, dilo y sugiere dónde verlo (panel web, reportes).
2. Montos en soles con formato "S/ 1,250.00".
3. Si no hay pregunta, da un resumen ejecutivo: cómo van las ventas (hoy vs ayer y vs el promedio),
   qué falta reponer (agotados primero), pedidos por confirmar y compras pendientes, y termina con
   2 o 3 acciones concretas y priorizadas.
4. Español peruano, claro y directo, máximo 12 líneas. Usa viñetas "•". Sin tablas ni encabezados."""


def responder(pregunta=''):
    """Devuelve {'respuesta', 'modo', 'alertas', 'datos'}."""
    f = foto_del_negocio()
    base = {'alertas': alertas(f), 'datos': f}
    if ia.disponible():
        mensajes = [
            {'role': 'system', 'content': INSTRUCCIONES},
            {'role': 'user', 'content': 'DATOS DEL NEGOCIO:\n' + json.dumps(f, ensure_ascii=False, default=str) +
                                        '\n\n' + (f'PREGUNTA DEL ADMINISTRADOR: {pregunta.strip()}' if pregunta.strip()
                                                  else 'Dame el resumen del negocio de hoy.')},
        ]
        try:
            for _ in range(2):  # si la redacción sale dañada, se intenta una vez más
                msg = ia.chat(mensajes, max_tokens=1500)
                texto = ia.formato_whatsapp(msg.get('content'))
                if ia.texto_valido(texto):
                    return {**base, 'respuesta': texto, 'modo': 'IA', 'modelo': ia.modelo()}
                log.warning('Resumen de la IA descartado: %r', texto[:300])
        except ia.IAError as e:
            log.warning('Asistente sin IA: %s', e)
            base['aviso'] = str(e)
    elif pregunta.strip():
        base['aviso'] = 'La IA no está configurada (GROQ_API_KEY); se muestra el resumen básico.'
    return {**base, 'respuesta': resumen_basico(f), 'modo': 'BASICO', 'modelo': None}

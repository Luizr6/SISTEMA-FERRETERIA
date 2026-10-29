"""
Lógica de negocio de ventas, carrito y pedidos.
Todo lo que toca stock ocurre aquí, dentro de una transacción atómica.
"""
from decimal import ROUND_HALF_UP, Decimal

from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework import serializers

from productos.models import MovimientoInventario, Producto
from .models import CarritoItem, DetallePedido, DetalleVenta, Pedido, Venta

IGV = Decimal(str(getattr(settings, 'IGV_TASA', '0.18')))
CENT = Decimal('0.01')


def _redondear(valor):
    return Decimal(valor).quantize(CENT, rounding=ROUND_HALF_UP)


def calcular_totales(lineas):
    """lineas = [(precio_sin_igv, cantidad), ...] -> (subtotal, igv, total)"""
    subtotal = sum((_redondear(Decimal(p) * c) for p, c in lineas), Decimal('0'))
    igv = _redondear(subtotal * IGV)
    return subtotal, igv, subtotal + igv


def _agrupar(detalles):
    cantidades = {}
    for d in detalles:
        cantidades[d['producto'].pk] = cantidades.get(d['producto'].pk, 0) + d['cantidad']
    return cantidades


# ---------------------------------------------------------------------------
# Ventas (CU08, CU12)
# ---------------------------------------------------------------------------
@transaction.atomic
def registrar_venta(*, vendedor, cliente, detalles, observacion='', metodo_pago='EFECTIVO', origen='MOSTRADOR'):
    """
    CU08 / RF17 / RF18. `detalles` = [{'producto': <Producto>, 'cantidad': int}, ...]
    Si cualquier producto no tiene stock suficiente, NO se registra nada (FA-01).
    El precio de venta se toma siempre del catálogo (el cliente no lo envía).
    El precio_venta del catálogo se considera SIN IGV; el IGV se suma al total.
    """
    cantidades = _agrupar(detalles)

    # Bloquear filas de productos (evita ventas simultáneas con el mismo stock)
    productos = {
        p.pk: p for p in Producto.objects.select_for_update().filter(pk__in=cantidades).order_by('pk')
    }

    errores = []
    for pk, cantidad in cantidades.items():
        p = productos[pk]
        if not p.activo:
            errores.append(f'"{p.nombre}" está inactivo y no se puede vender.')
        elif cantidad > p.stock_actual:
            errores.append(f'Stock insuficiente de "{p.nombre}": solicitado {cantidad}, disponible {p.stock_actual}.')
    if errores:
        raise serializers.ValidationError({'detalles': errores})

    venta = Venta.objects.create(vendedor=vendedor, cliente=cliente, observacion=observacion,
                                 metodo_pago=metodo_pago, origen=origen)

    for pk, cantidad in cantidades.items():
        p = productos[pk]
        DetalleVenta.objects.create(
            venta=venta, producto=p, cantidad=cantidad,
            precio_unitario=p.precio_venta, subtotal=_redondear(p.precio_venta * cantidad),
        )
        p.stock_actual -= cantidad
        p.save(update_fields=['stock_actual'])

    venta.subtotal, venta.igv, venta.total = calcular_totales(
        [(productos[pk].precio_venta, c) for pk, c in cantidades.items()])
    serie = settings.FERRETERIA.get('SERIE_BOLETA', 'B001')
    venta.numero = f'{serie}-{venta.pk:06d}'
    venta.save(update_fields=['subtotal', 'igv', 'total', 'numero'])

    for pk, cantidad in cantidades.items():
        p = productos[pk]
        MovimientoInventario.objects.create(
            producto=p, tipo='SALIDA', cantidad=-cantidad, stock_resultante=p.stock_actual,
            motivo=f'Venta {venta.numero}', usuario=vendedor,
        )
    return venta


@transaction.atomic
def anular_venta(*, venta, usuario, motivo):
    """CU12 / RF19: anula la venta y reingresa el stock. Solo Admin (se valida en la vista)."""
    venta = Venta.objects.select_for_update().get(pk=venta.pk)
    if venta.estado == 'ANULADA':
        raise serializers.ValidationError({'estado': 'La venta ya fue anulada.'})

    for d in venta.detalles.select_related('producto'):
        p = Producto.objects.select_for_update().get(pk=d.producto_id)
        p.stock_actual += d.cantidad
        p.save(update_fields=['stock_actual'])
        MovimientoInventario.objects.create(
            producto=p, tipo='INGRESO', cantidad=d.cantidad, stock_resultante=p.stock_actual,
            motivo=f'Anulación venta {venta.numero}', usuario=usuario,
        )

    venta.estado = 'ANULADA'
    venta.anulada_por = usuario
    venta.fecha_anulacion = timezone.now()
    venta.motivo_anulacion = motivo
    venta.save(update_fields=['estado', 'anulada_por', 'fecha_anulacion', 'motivo_anulacion'])
    return venta


# ---------------------------------------------------------------------------
# Pedidos por confirmar (FER-45, FER-46, RF32, RF33)
# ---------------------------------------------------------------------------
@transaction.atomic
def crear_pedido(*, detalles, origen='WEB', cliente=None, creado_por=None,
                 contacto_nombre='', contacto_telefono='', observacion=''):
    """Registra el pedido SIN tocar stock (RF32). El stock se valida recién al aprobar."""
    if not detalles:
        raise serializers.ValidationError({'detalles': 'El pedido debe tener al menos un producto.'})
    cantidades = _agrupar(detalles)
    productos = {p.pk: p for p in Producto.objects.filter(pk__in=cantidades)}
    inactivos = [f'"{p.nombre}" no está disponible.' for p in productos.values() if not p.activo]
    if inactivos:
        raise serializers.ValidationError({'detalles': inactivos})

    pedido = Pedido.objects.create(
        origen=origen, cliente=cliente, creado_por=creado_por, observacion=observacion,
        contacto_nombre=contacto_nombre or (cliente.nombre if cliente else ''),
        contacto_telefono=contacto_telefono or (cliente.telefono if cliente else ''),
    )
    for pk, cantidad in cantidades.items():
        p = productos[pk]
        DetallePedido.objects.create(pedido=pedido, producto=p, cantidad=cantidad,
                                     precio_unitario=p.precio_venta,
                                     subtotal=_redondear(p.precio_venta * cantidad))
    pedido.total_estimado = calcular_totales([(productos[pk].precio_venta, c) for pk, c in cantidades.items()])[2]
    pedido.numero = f'P-{pedido.pk:06d}'
    pedido.save(update_fields=['total_estimado', 'numero'])
    return pedido


@transaction.atomic
def aprobar_pedido(*, pedido, usuario, metodo_pago='EFECTIVO', cliente=None):
    """
    RF33 / CU17: el Vendedor confirma -> se genera la venta formal (CU08) y se descuenta stock.
    Si no hay stock suficiente, la venta no se crea y el pedido sigue PENDIENTE.
    Se cobra con el precio vigente del catálogo (puede diferir del total estimado).
    """
    pedido = Pedido.objects.select_for_update().get(pk=pedido.pk)
    if pedido.estado != 'PENDIENTE':
        raise serializers.ValidationError({'estado': f'El pedido ya está {pedido.get_estado_display().lower()}.'})
    if cliente is not None:
        pedido.cliente = cliente
    detalles = [{'producto': d.producto, 'cantidad': d.cantidad} for d in pedido.detalles.select_related('producto')]
    venta = registrar_venta(
        vendedor=usuario, cliente=pedido.cliente, detalles=detalles, metodo_pago=metodo_pago,
        origen=pedido.origen, observacion=f'Pedido {pedido.numero}' + (f' - {pedido.observacion}' if pedido.observacion else ''),
    )
    pedido.estado = 'APROBADO'
    pedido.venta = venta
    pedido.revisado_por = usuario
    pedido.fecha_revision = timezone.now()
    pedido.save(update_fields=['estado', 'venta', 'revisado_por', 'fecha_revision', 'cliente'])
    return pedido


@transaction.atomic
def rechazar_pedido(*, pedido, usuario, motivo, estado='RECHAZADO'):
    pedido = Pedido.objects.select_for_update().get(pk=pedido.pk)
    if pedido.estado != 'PENDIENTE':
        raise serializers.ValidationError({'estado': f'El pedido ya está {pedido.get_estado_display().lower()}.'})
    pedido.estado = estado
    pedido.motivo_rechazo = motivo
    pedido.revisado_por = usuario
    pedido.fecha_revision = timezone.now()
    pedido.save(update_fields=['estado', 'motivo_rechazo', 'revisado_por', 'fecha_revision'])
    return pedido


# ---------------------------------------------------------------------------
# Carrito (FER-45)
# ---------------------------------------------------------------------------
def resumen_carrito(usuario):
    items = list(CarritoItem.objects.filter(usuario=usuario).select_related('producto'))
    lineas, alertas = [], []
    for it in items:
        p = it.producto
        if not p.activo:
            alertas.append(f'"{p.nombre}" ya no está disponible.')
        elif it.cantidad > p.stock_actual:
            alertas.append(f'"{p.nombre}": pides {it.cantidad} y hay {p.stock_actual}.')
        lineas.append({
            'id': it.id, 'producto': p.id, 'codigo': p.codigo, 'nombre': p.nombre,
            'unidad_medida': p.unidad_medida, 'imagen_url': p.imagen_url,
            'precio_unitario': p.precio_venta, 'cantidad': it.cantidad,
            'stock_disponible': p.stock_actual, 'stock_suficiente': p.activo and it.cantidad <= p.stock_actual,
            'subtotal': _redondear(p.precio_venta * it.cantidad),
        })
    subtotal, igv, total = calcular_totales([(it.producto.precio_venta, it.cantidad) for it in items])
    return {
        'items': lineas,
        'total_items': sum(it.cantidad for it in items),
        'subtotal': subtotal, 'igv': igv, 'total': total,
        'puede_confirmar': bool(items) and not alertas,
        'alertas': alertas,
    }


def agregar_al_carrito(*, usuario, producto, cantidad, reemplazar=False):
    if not producto.activo:
        raise serializers.ValidationError({'producto': f'"{producto.nombre}" no está disponible.'})
    item, creado = CarritoItem.objects.get_or_create(usuario=usuario, producto=producto,
                                                     defaults={'cantidad': cantidad})
    if not creado:
        item.cantidad = cantidad if reemplazar else item.cantidad + cantidad
        item.save(update_fields=['cantidad'])
    return item


@transaction.atomic
def confirmar_carrito(*, usuario, cliente=None, metodo_pago='EFECTIVO', observacion='', origen='MOSTRADOR'):
    """Carrito -> venta directa (descuenta stock) y vacía el carrito."""
    items = list(CarritoItem.objects.select_for_update().filter(usuario=usuario).select_related('producto'))
    if not items:
        raise serializers.ValidationError({'carrito': 'El carrito está vacío.'})
    venta = registrar_venta(vendedor=usuario, cliente=cliente, observacion=observacion, metodo_pago=metodo_pago,
                            origen=origen, detalles=[{'producto': i.producto, 'cantidad': i.cantidad} for i in items])
    CarritoItem.objects.filter(usuario=usuario).delete()
    return venta


@transaction.atomic
def carrito_a_pedido(*, usuario, cliente=None, observacion='', origen='WEB', contacto_nombre='', contacto_telefono=''):
    """Carrito -> pedido PENDIENTE (no toca stock) y vacía el carrito."""
    items = list(CarritoItem.objects.select_for_update().filter(usuario=usuario).select_related('producto'))
    if not items:
        raise serializers.ValidationError({'carrito': 'El carrito está vacío.'})
    pedido = crear_pedido(detalles=[{'producto': i.producto, 'cantidad': i.cantidad} for i in items],
                          origen=origen, cliente=cliente, creado_por=usuario, observacion=observacion,
                          contacto_nombre=contacto_nombre, contacto_telefono=contacto_telefono)
    CarritoItem.objects.filter(usuario=usuario).delete()
    return pedido

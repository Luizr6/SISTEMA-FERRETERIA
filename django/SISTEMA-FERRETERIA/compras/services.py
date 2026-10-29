"""
Lógica de negocio de compras (FER-40 / FER-41).
Todo lo que toca stock ocurre aquí y dentro de una transacción atómica.
"""
from decimal import ROUND_HALF_UP, Decimal

from django.db import transaction
from django.utils import timezone
from rest_framework import serializers

from productos.models import MovimientoInventario, Producto
from .models import DetalleOrdenCompra, DetalleRecepcion, OrdenCompra, RecepcionCompra

CENT = Decimal('0.01')


def _redondear(valor):
    return Decimal(valor).quantize(CENT, rounding=ROUND_HALF_UP)


def _validar_detalles(detalles):
    if not detalles:
        raise serializers.ValidationError({'detalles': 'La orden debe tener al menos un producto.'})
    vistos, errores = set(), []
    for d in detalles:
        p = d['producto']
        if p.pk in vistos:
            errores.append(f'"{p.nombre}" está repetido; usa una sola línea por producto.')
        vistos.add(p.pk)
        if not p.activo:
            errores.append(f'"{p.nombre}" está inactivo.')
    if errores:
        raise serializers.ValidationError({'detalles': errores})


def _guardar_detalles(orden, detalles):
    total = Decimal('0')
    for d in detalles:
        precio = d.get('precio_compra')
        if precio is None:  # si no lo envían, se usa el último costo del producto
            precio = d['producto'].precio_compra
        linea = _redondear(Decimal(precio) * d['cantidad'])
        DetalleOrdenCompra.objects.create(
            orden=orden, producto=d['producto'], cantidad=d['cantidad'],
            precio_compra=precio, subtotal=linea,
        )
        total += linea
    orden.total = total


@transaction.atomic
def crear_orden(*, usuario, proveedor, detalles, observacion='', fecha_entrega_estimada=None):
    if not proveedor.activo:
        raise serializers.ValidationError({'proveedor': 'El proveedor está inactivo.'})
    _validar_detalles(detalles)
    orden = OrdenCompra.objects.create(
        proveedor=proveedor, creado_por=usuario, observacion=observacion,
        fecha_entrega_estimada=fecha_entrega_estimada,
    )
    _guardar_detalles(orden, detalles)
    orden.numero = f'OC-{orden.pk:06d}'
    orden.save(update_fields=['numero', 'total'])
    return orden


@transaction.atomic
def actualizar_orden(*, orden, datos):
    """Solo se edita una orden PENDIENTE (sin mercadería recibida)."""
    orden = OrdenCompra.objects.select_for_update().get(pk=orden.pk)
    if orden.estado != 'PENDIENTE':
        raise serializers.ValidationError({'estado': f'No se puede editar una orden {orden.get_estado_display().lower()}.'})
    for campo in ('proveedor', 'observacion', 'fecha_entrega_estimada'):
        if campo in datos:
            setattr(orden, campo, datos[campo])
    if 'detalles' in datos:
        _validar_detalles(datos['detalles'])
        orden.detalles.all().delete()
        _guardar_detalles(orden, datos['detalles'])
    orden.save()
    return orden


@transaction.atomic
def recibir_orden(*, orden, usuario, items=None, observacion='', actualizar_costo=True):
    """
    CU11 / RF14. `items` = [{'detalle': <id DetalleOrdenCompra>, 'cantidad': int}, ...]
    Si `items` es None o vacío se recibe TODO lo pendiente.
    - Incrementa stock y registra INGRESO en el kardex por cada producto.
    - Deja la orden en PARCIAL o RECIBIDA (CU11 FA-01).
    - Opcional: actualiza el precio_compra del producto con el costo de esta compra.
    """
    orden = OrdenCompra.objects.select_for_update().get(pk=orden.pk)
    if orden.estado in ('RECIBIDA', 'ANULADA'):
        raise serializers.ValidationError({'estado': f'La orden ya está {orden.get_estado_display().lower()}.'})

    detalles = {d.pk: d for d in orden.detalles.select_related('producto')}

    if items:
        a_recibir = {}
        errores = []
        for it in items:
            det = detalles.get(it['detalle'])
            if det is None:
                errores.append(f'La línea {it["detalle"]} no pertenece a la orden {orden.numero}.')
                continue
            a_recibir[det.pk] = a_recibir.get(det.pk, 0) + it['cantidad']
        for pk, cant in a_recibir.items():
            det = detalles[pk]
            if cant > det.cantidad_pendiente:
                errores.append(f'"{det.producto.nombre}": se intenta recibir {cant} y solo faltan {det.cantidad_pendiente}.')
        if errores:
            raise serializers.ValidationError({'items': errores})
    else:
        a_recibir = {pk: d.cantidad_pendiente for pk, d in detalles.items() if d.cantidad_pendiente > 0}

    a_recibir = {pk: c for pk, c in a_recibir.items() if c > 0}
    if not a_recibir:
        raise serializers.ValidationError({'items': 'No hay cantidades por recibir.'})

    recepcion = RecepcionCompra.objects.create(orden=orden, usuario=usuario, observacion=observacion)

    productos = {p.pk: p for p in Producto.objects.select_for_update().filter(
        pk__in=[detalles[pk].producto_id for pk in a_recibir]).order_by('pk')}

    for pk, cant in a_recibir.items():
        det = detalles[pk]
        p = productos[det.producto_id]
        p.stock_actual += cant
        campos = ['stock_actual']
        if actualizar_costo and p.precio_compra != det.precio_compra:
            p.precio_compra = det.precio_compra
            campos.append('precio_compra')
        p.save(update_fields=campos)
        MovimientoInventario.objects.create(
            producto=p, tipo='INGRESO', cantidad=cant, stock_resultante=p.stock_actual,
            motivo=f'Compra {orden.numero} ({orden.proveedor.razon_social})', usuario=usuario,
        )
        det.cantidad_recibida += cant
        det.save(update_fields=['cantidad_recibida'])
        DetalleRecepcion.objects.create(recepcion=recepcion, detalle_orden=det, cantidad=cant)

    if all(d.cantidad_pendiente == 0 for d in detalles.values()):
        orden.estado = 'RECIBIDA'
        orden.fecha_recepcion = timezone.now()
    else:
        orden.estado = 'PARCIAL'
    orden.save(update_fields=['estado', 'fecha_recepcion'])
    return orden, recepcion


@transaction.atomic
def anular_orden(*, orden, usuario, motivo):
    """Solo se anula si todavía no llegó nada (para no descuadrar el stock)."""
    orden = OrdenCompra.objects.select_for_update().get(pk=orden.pk)
    if orden.estado != 'PENDIENTE':
        raise serializers.ValidationError(
            {'estado': 'Solo se pueden anular órdenes PENDIENTES (sin mercadería recibida).'})
    orden.estado = 'ANULADA'
    orden.anulada_por = usuario
    orden.motivo_anulacion = motivo
    orden.save(update_fields=['estado', 'anulada_por', 'motivo_anulacion'])
    return orden

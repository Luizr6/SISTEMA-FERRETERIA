"""Reglas de la caja. Todo lo que cambia una caja pasa por aquí."""
from decimal import Decimal

from django.conf import settings
from django.db import transaction
from django.db.models import Count, Sum
from django.utils import timezone
from rest_framework import serializers

from cuentas.permissions import es_admin
from .models import Caja, MovimientoCaja

CERO = Decimal('0.00')


def caja_abierta(usuario):
    if usuario is None or not getattr(usuario, 'pk', None):
        return None
    return Caja.objects.filter(usuario=usuario, estado='ABIERTA').first()


def caja_para_venta(usuario):
    """
    Caja donde se registra una venta (mostrador, app o pedido aprobado).
    Si la caja es obligatoria y el vendedor no la abrió, no se puede cobrar.
    """
    caja = caja_abierta(usuario)
    if caja is None and settings.CAJA.get('OBLIGATORIA', True):
        raise serializers.ValidationError({'caja': 'Abre tu caja antes de cobrar (menú Caja).'})
    return caja


def _dinero(valor):
    try:
        d = Decimal(str(valor)).quantize(Decimal('0.01'))
    except Exception:  # noqa: BLE001
        raise serializers.ValidationError('Monto inválido.')
    return d


def resumen(caja):
    """Cuadre de la caja: lo que debería haber en efectivo y lo cobrado por otros medios."""
    ventas = caja.ventas.filter(estado='COMPLETADA')
    por_metodo = {
        r['metodo_pago']: {'total': r['t'] or CERO, 'cantidad': r['n']}
        for r in ventas.values('metodo_pago').annotate(t=Sum('total'), n=Count('id'))
    }
    efectivo = por_metodo.get('EFECTIVO', {}).get('total', CERO)
    otros = sum((v['total'] for k, v in por_metodo.items() if k != 'EFECTIVO'), CERO)
    movs = caja.movimientos.values('tipo').annotate(t=Sum('monto'))
    totales_mov = {m['tipo']: m['t'] or CERO for m in movs}
    ingresos = totales_mov.get('INGRESO', CERO)
    egresos = totales_mov.get('EGRESO', CERO)
    esperado = caja.monto_inicial + efectivo + ingresos - egresos
    return {
        'monto_inicial': caja.monto_inicial,
        'ventas_efectivo': efectivo,
        'ventas_otros_medios': otros,
        'total_vendido': efectivo + otros,
        'cantidad_ventas': sum(v['cantidad'] for v in por_metodo.values()),
        'ventas_anuladas': caja.ventas.filter(estado='ANULADA').count(),
        'por_metodo': [
            {'metodo_pago': k, 'total': v['total'], 'cantidad': v['cantidad']}
            for k, v in sorted(por_metodo.items(), key=lambda kv: -kv[1]['total'])
        ],
        'ingresos': ingresos,
        'egresos': egresos,
        # Abierta: se calcula en vivo. Cerrada: el valor guardado al cerrar.
        'efectivo_esperado': caja.monto_esperado if caja.estado == 'CERRADA' else esperado,
        'monto_contado': caja.monto_contado,
        'diferencia': caja.diferencia,
    }


@transaction.atomic
def abrir(*, usuario, monto_inicial):
    monto = _dinero(monto_inicial)
    if monto < 0:
        raise serializers.ValidationError({'monto_inicial': 'El monto inicial no puede ser negativo.'})
    if Caja.objects.select_for_update().filter(usuario=usuario, estado='ABIERTA').exists():
        raise serializers.ValidationError({'caja': 'Ya tienes una caja abierta. Ciérrala antes de abrir otra.'})
    return Caja.objects.create(usuario=usuario, monto_inicial=monto)


def _verificar_permiso(caja, usuario):
    if caja.usuario_id != usuario.pk and not es_admin(usuario):
        raise serializers.ValidationError({'caja': 'Solo el dueño de la caja o el Administrador pueden hacer esto.'})


@transaction.atomic
def registrar_movimiento(*, caja, usuario, tipo, monto, concepto):
    caja = Caja.objects.select_for_update().get(pk=caja.pk)
    _verificar_permiso(caja, usuario)
    if caja.estado != 'ABIERTA':
        raise serializers.ValidationError({'caja': 'La caja ya está cerrada.'})
    if tipo not in ('INGRESO', 'EGRESO'):
        raise serializers.ValidationError({'tipo': 'Debe ser INGRESO o EGRESO.'})
    monto = _dinero(monto)
    if monto <= 0:
        raise serializers.ValidationError({'monto': 'El monto debe ser mayor a 0.'})
    concepto = (concepto or '').strip()
    if len(concepto) < 3:
        raise serializers.ValidationError({'concepto': 'Escribe en qué se usó o de dónde vino el dinero.'})
    if tipo == 'EGRESO':
        disponible = resumen(caja)['efectivo_esperado']
        if monto > disponible:
            raise serializers.ValidationError(
                {'monto': f'No hay tanto efectivo en la caja (debería haber S/ {disponible}).'})
    return MovimientoCaja.objects.create(caja=caja, usuario=usuario, tipo=tipo, monto=monto, concepto=concepto[:150])


@transaction.atomic
def cerrar(*, caja, usuario, monto_contado, observacion=''):
    caja = Caja.objects.select_for_update().get(pk=caja.pk)
    _verificar_permiso(caja, usuario)
    if caja.estado != 'ABIERTA':
        raise serializers.ValidationError({'caja': 'La caja ya está cerrada.'})
    contado = _dinero(monto_contado)
    if contado < 0:
        raise serializers.ValidationError({'monto_contado': 'El monto contado no puede ser negativo.'})
    esperado = resumen(caja)['efectivo_esperado']
    caja.estado = 'CERRADA'
    caja.fecha_cierre = timezone.now()
    caja.cerrada_por = usuario
    caja.monto_esperado = esperado
    caja.monto_contado = contado
    caja.diferencia = contado - esperado
    caja.observacion = (observacion or '').strip()[:250]
    caja.save()
    return caja

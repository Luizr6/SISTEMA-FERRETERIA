from django.shortcuts import get_object_or_404
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import permissions, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from auditoria.utils import registrar
from cuentas.permissions import es_admin
from . import services
from .models import Caja
from .serializers import CajaDetalleSerializer, CajaSerializer, MovimientoCajaSerializer


class CajaViewSet(viewsets.ReadOnlyModelViewSet):
    """
    Caja de cada vendedor.
      GET  /api/cajas/                     historial (Admin: todas; Vendedor: las suyas)
           ?usuario=<id> &estado=ABIERTA|CERRADA &fecha_desde=2026-11-01 &fecha_hasta=2026-11-30
      GET  /api/cajas/<id>/                detalle con resumen y movimientos
      GET  /api/cajas/actual/              mi caja abierta ({"caja": null} si no hay)
      POST /api/cajas/abrir/               {"monto_inicial": 100}
      POST /api/cajas/<id>/movimientos/    {"tipo": "EGRESO", "monto": 30, "concepto": "Pago a cargador"}
      POST /api/cajas/<id>/cerrar/         {"monto_contado": 295, "observacion": "..."}
    Mientras la caja obligatoria esté activa (settings.CAJA), no se puede cobrar sin caja abierta.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get_serializer_class(self):
        return CajaDetalleSerializer if self.action in ('retrieve', 'actual') else CajaSerializer

    def get_queryset(self):
        qs = Caja.objects.select_related('usuario', 'cerrada_por').prefetch_related('movimientos__usuario')
        user = self.request.user
        if not es_admin(user):
            qs = qs.filter(usuario=user)
        p = self.request.query_params
        if p.get('usuario'):
            qs = qs.filter(usuario_id=p['usuario'])
        if p.get('estado'):
            qs = qs.filter(estado=p['estado'].upper())
        if p.get('fecha_desde'):
            qs = qs.filter(fecha_apertura__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha_apertura__date__lte=p['fecha_hasta'])
        return qs

    @extend_schema(responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['get'])
    def actual(self, request):
        caja = services.caja_abierta(request.user)
        return Response({'caja': CajaDetalleSerializer(caja).data if caja else None})

    @extend_schema(request=OpenApiTypes.OBJECT, responses=CajaDetalleSerializer)
    @action(detail=False, methods=['post'])
    def abrir(self, request):
        caja = services.abrir(usuario=request.user, monto_inicial=request.data.get('monto_inicial', 0))
        registrar('CREAR', caja, request=request, modelo='Caja',
                  descripcion=f'Abrió caja con S/ {caja.monto_inicial}')
        return Response(CajaDetalleSerializer(caja).data, status=201)

    @extend_schema(request=OpenApiTypes.OBJECT, responses=MovimientoCajaSerializer)
    @action(detail=True, methods=['post'])
    def movimientos(self, request, pk=None):
        caja = get_object_or_404(self.get_queryset(), pk=pk)
        mov = services.registrar_movimiento(
            caja=caja, usuario=request.user, tipo=str(request.data.get('tipo', '')).upper(),
            monto=request.data.get('monto', 0), concepto=request.data.get('concepto', ''))
        registrar('CREAR', mov, request=request, modelo='MovimientoCaja',
                  descripcion=f'{mov.get_tipo_display()} de S/ {mov.monto} en caja #{caja.pk}: {mov.concepto}')
        return Response(MovimientoCajaSerializer(mov).data, status=201)

    @extend_schema(request=OpenApiTypes.OBJECT, responses=CajaDetalleSerializer)
    @action(detail=True, methods=['post'])
    def cerrar(self, request, pk=None):
        caja = get_object_or_404(self.get_queryset(), pk=pk)
        caja = services.cerrar(caja=caja, usuario=request.user, monto_contado=request.data.get('monto_contado'),
                               observacion=request.data.get('observacion', ''))
        signo = 'sobrante' if caja.diferencia > 0 else ('faltante' if caja.diferencia < 0 else 'cuadrada')
        registrar('EDITAR', caja, request=request, modelo='Caja',
                  descripcion=f'Cerró caja #{caja.pk} de {caja.usuario}: esperado S/ {caja.monto_esperado}, '
                              f'contado S/ {caja.monto_contado} ({signo} S/ {abs(caja.diferencia)})')
        return Response(CajaDetalleSerializer(caja).data)

from django.db.models import F, Q
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from auditoria.utils import registrar
from cuentas.permissions import EsAdmin
from productos.models import Producto
from . import services
from .models import OrdenCompra
from .serializers import (
    AnularOrdenSerializer, OrdenCompraSerializer, RecepcionCompraSerializer,
    RecibirOrdenSerializer, SugerenciaCompraSerializer,
)


class OrdenCompraViewSet(mixins.CreateModelMixin, mixins.ListModelMixin, mixins.RetrieveModelMixin,
                         mixins.UpdateModelMixin, viewsets.GenericViewSet):
    """
    Módulo de compras (CU10, CU11, RF13-RF15). Solo Administrador.

    Historial con filtros (RF15):
      ?proveedor=<id> &estado=PENDIENTE|PARCIAL|RECIBIDA|ANULADA
      &fecha_desde=2026-10-01 &fecha_hasta=2026-10-31 &search=OC-000003

    Las órdenes no se eliminan: se anulan (si aún no llegó nada).
    """
    serializer_class = OrdenCompraSerializer
    permission_classes = [EsAdmin]
    filter_backends = [filters.SearchFilter]
    search_fields = ['numero', 'proveedor__razon_social', 'proveedor__ruc', 'observacion']

    def get_queryset(self):
        qs = (OrdenCompra.objects.select_related('proveedor', 'creado_por')
              .prefetch_related('detalles__producto'))
        p = self.request.query_params
        if p.get('proveedor'):
            qs = qs.filter(proveedor_id=p['proveedor'])
        if p.get('estado'):
            estados = [e.strip().upper() for e in p['estado'].split(',')]
            qs = qs.filter(estado__in=estados)
        if p.get('fecha_desde'):
            qs = qs.filter(fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha__date__lte=p['fecha_hasta'])
        if p.get('producto'):
            qs = qs.filter(detalles__producto_id=p['producto']).distinct()
        return qs

    def perform_create(self, serializer):
        super().perform_create(serializer)
        registrar('CREAR', serializer.instance, request=self.request,
                  descripcion=f'{serializer.instance.numero} por S/ {serializer.instance.total}')

    def perform_update(self, serializer):
        super().perform_update(serializer)
        registrar('EDITAR', serializer.instance, request=self.request)

    def _respuesta(self, orden, codigo=status.HTTP_200_OK):
        orden = self.get_queryset().get(pk=orden.pk)
        return Response(OrdenCompraSerializer(orden, context=self.get_serializer_context()).data, status=codigo)

    @extend_schema(request=RecibirOrdenSerializer, responses=OrdenCompraSerializer)
    @action(detail=True, methods=['post'])
    def recibir(self, request, pk=None):
        """FER-41: registra la llegada de mercadería y sube el stock automáticamente."""
        orden = self.get_object()
        ser = RecibirOrdenSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        orden, recepcion = services.recibir_orden(
            orden=orden, usuario=request.user,
            items=ser.validated_data.get('items'),
            observacion=ser.validated_data.get('observacion', ''),
            actualizar_costo=ser.validated_data.get('actualizar_costo', True),
        )
        unidades = sum(d.cantidad for d in recepcion.detalles.all())
        registrar('RECIBIR', orden, request=request,
                  descripcion=f'{orden.numero}: {unidades} unidades recibidas, estado {orden.estado}')
        return self._respuesta(orden)

    @extend_schema(request=AnularOrdenSerializer, responses=OrdenCompraSerializer)
    @action(detail=True, methods=['post'])
    def anular(self, request, pk=None):
        orden = self.get_object()
        ser = AnularOrdenSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        orden = services.anular_orden(orden=orden, usuario=request.user, motivo=ser.validated_data['motivo'])
        registrar('ANULAR', orden, request=request, descripcion=f'{orden.numero}: {orden.motivo_anulacion}')
        return self._respuesta(orden)

    @extend_schema(responses=RecepcionCompraSerializer(many=True))
    @action(detail=True, methods=['get'])
    def recepciones(self, request, pk=None):
        """Historial de llegadas de una orden (útil cuando fue parcial)."""
        orden = self.get_object()
        qs = orden.recepciones.select_related('usuario').prefetch_related('detalles__detalle_orden__producto')
        return Response(RecepcionCompraSerializer(qs, many=True).data)

    @extend_schema(responses=SugerenciaCompraSerializer(many=True))
    @action(detail=False, methods=['get'])
    def sugerencias(self, request):
        """
        Abastecimiento: productos activos con stock <= mínimo, con cantidad sugerida.
        ?proveedor=<id> para armar la orden de un proveedor concreto.
        """
        qs = (Producto.objects.select_related('proveedor')
              .filter(activo=True, stock_actual__lte=F('stock_minimo')).order_by('proveedor_id', 'stock_actual'))
        if request.query_params.get('proveedor'):
            qs = qs.filter(Q(proveedor_id=request.query_params['proveedor']))
        return Response(SugerenciaCompraSerializer(qs, many=True).data)

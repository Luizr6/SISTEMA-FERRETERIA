from rest_framework import filters, serializers, viewsets
from rest_framework.pagination import PageNumberPagination

from cuentas.permissions import EsAdmin
from .models import Bitacora


class BitacoraSerializer(serializers.ModelSerializer):
    usuario_nombre = serializers.CharField(source='usuario.username', read_only=True, default=None)

    class Meta:
        model = Bitacora
        fields = '__all__'


class BitacoraViewSet(viewsets.ReadOnlyModelViewSet):
    """
    RF04 - Bitácora de auditoría (solo Admin, solo lectura).
    Filtros: ?usuario=<id> &accion=CREAR &modelo=Producto &fecha_desde=2026-10-01 &fecha_hasta=2026-10-31 &search=
    """
    serializer_class = BitacoraSerializer
    permission_classes = [EsAdmin]
    filter_backends = [filters.SearchFilter]
    search_fields = ['descripcion', 'modelo', 'objeto_id']

    def get_queryset(self):
        qs = Bitacora.objects.select_related('usuario')
        p = self.request.query_params
        if p.get('usuario'):
            qs = qs.filter(usuario_id=p['usuario'])
        if p.get('accion'):
            qs = qs.filter(accion=p['accion'].upper())
        if p.get('modelo'):
            qs = qs.filter(modelo__iexact=p['modelo'])
        if p.get('fecha_desde'):
            qs = qs.filter(fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha__date__lte=p['fecha_hasta'])
        return qs


class BitacoraPaginacion(PageNumberPagination):
    page_size = 50
    page_size_query_param = 'page_size'
    max_page_size = 500


BitacoraViewSet.pagination_class = BitacoraPaginacion  # responde {count, next, previous, results}

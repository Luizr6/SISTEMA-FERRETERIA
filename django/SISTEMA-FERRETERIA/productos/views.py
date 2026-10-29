from django.db.models import Count, F
from django.http import HttpResponse
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.response import Response

from auditoria.utils import AuditoriaMixin, registrar
from cuentas.permissions import AdminEscribeTodosLeen, EsAdmin
from .importacion import PLANTILLA, importar_csv
from .models import Categoria, Marca, MovimientoInventario, Producto, Proveedor
from .serializers import (
    CategoriaSerializer, MarcaSerializer, MovimientoInventarioSerializer,
    ProductoSerializer, ProveedorSerializer,
)


def _bool_param(valor):
    return str(valor).lower() in ('1', 'true', 'si', 'sí')


class CategoriaViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    queryset = Categoria.objects.annotate(total_productos=Count('productos'))
    serializer_class = CategoriaSerializer
    permission_classes = [AdminEscribeTodosLeen]
    filter_backends = [filters.SearchFilter]
    search_fields = ['nombre', 'descripcion']


class MarcaViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    queryset = Marca.objects.all()
    serializer_class = MarcaSerializer
    permission_classes = [AdminEscribeTodosLeen]
    filter_backends = [filters.SearchFilter]
    search_fields = ['nombre']


class ProveedorViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    """CU09 / RF12 - Proveedores. ?activo=true  ?search=  ·  /proveedores/<id>/productos/"""
    serializer_class = ProveedorSerializer
    permission_classes = [AdminEscribeTodosLeen]
    filter_backends = [filters.SearchFilter]
    search_fields = ['razon_social', 'ruc', 'contacto']

    def get_queryset(self):
        qs = Proveedor.objects.annotate(total_productos=Count('productos', distinct=True))
        if self.request.query_params.get('activo') is not None:
            qs = qs.filter(activo=_bool_param(self.request.query_params['activo']))
        return qs

    @extend_schema(responses=ProductoSerializer(many=True))
    @action(detail=True, methods=['get'])
    def productos(self, request, pk=None):
        """Productos que suministra este proveedor (RF12)."""
        proveedor = self.get_object()
        qs = proveedor.productos.select_related('categoria', 'marca')
        return Response(ProductoSerializer(qs, many=True, context={'request': request}).data)


class ProductoViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    """
    Filtros por query string (RF09):
      ?search=martillo  ?categoria=1  ?marca=2  ?proveedor=3
      ?activo=true      ?stock=agotado|bajo|normal|critico
      ?ordering=precio_venta | -stock_actual
    Carga masiva (RF11): POST /productos/importar-csv/ (multipart: archivo=<csv>, actualizar=true|false)
    Plantilla:           GET  /productos/plantilla-csv/
    """
    serializer_class = ProductoSerializer
    permission_classes = [AdminEscribeTodosLeen]
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ['codigo', 'nombre', 'descripcion', 'marca__nombre']
    ordering_fields = ['nombre', 'codigo', 'precio_venta', 'stock_actual', 'fecha_creacion']

    def get_queryset(self):
        qs = Producto.objects.select_related('categoria', 'marca', 'proveedor')
        p = self.request.query_params
        if p.get('categoria'):
            qs = qs.filter(categoria_id=p['categoria'])
        if p.get('marca'):
            qs = qs.filter(marca_id=p['marca'])
        if p.get('proveedor'):
            qs = qs.filter(proveedor_id=p['proveedor'])
        if p.get('activo') is not None:
            qs = qs.filter(activo=_bool_param(p['activo']))
        estado = (p.get('stock') or '').lower()
        if estado == 'agotado':
            qs = qs.filter(stock_actual=0)
        elif estado == 'bajo':
            qs = qs.filter(stock_actual__gt=0, stock_actual__lte=F('stock_minimo'))
        elif estado == 'critico':  # bajo + agotado (alertas RF08)
            qs = qs.filter(stock_actual__lte=F('stock_minimo'))
        elif estado == 'normal':
            qs = qs.filter(stock_actual__gt=F('stock_minimo'))
        return qs

    @extend_schema(request={'multipart/form-data': {'type': 'object', 'properties': {'archivo': {'type': 'string', 'format': 'binary'}, 'actualizar': {'type': 'boolean'}}}}, responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['post'], url_path='importar-csv', permission_classes=[EsAdmin],
            parser_classes=[MultiPartParser, FormParser, JSONParser])
    def importar_csv(self, request):
        archivo = request.FILES.get('archivo')
        if archivo is None:
            raise serializers.ValidationError({'archivo': 'Adjunta un archivo CSV en el campo "archivo".'})
        if archivo.size > 5 * 1024 * 1024:
            raise serializers.ValidationError({'archivo': 'El archivo no puede pesar más de 5 MB.'})
        resultado = importar_csv(archivo, usuario=request.user,
                                 actualizar=_bool_param(request.data.get('actualizar', 'false')))
        registrar('IMPORTAR', request=request, modelo='Producto',
                  descripcion=f'CSV {archivo.name}: {resultado["creados"]} creados, '
                              f'{resultado["actualizados"]} actualizados, {len(resultado["errores"])} errores')
        codigo = status.HTTP_200_OK if (resultado['creados'] or resultado['actualizados']) else status.HTTP_400_BAD_REQUEST
        return Response(resultado, status=codigo)

    @extend_schema(responses={(200, 'text/csv'): OpenApiTypes.STR})
    @action(detail=False, methods=['get'], url_path='plantilla-csv')
    def plantilla_csv(self, request):
        resp = HttpResponse(PLANTILLA, content_type='text/csv; charset=utf-8')
        resp['Content-Disposition'] = 'attachment; filename="plantilla_productos.csv"'
        return resp

    @extend_schema(responses=MovimientoInventarioSerializer(many=True))
    @action(detail=True, methods=['get'])
    def kardex(self, request, pk=None):
        """Atajo: /productos/<id>/kardex/ = /movimientos/?producto=<id>"""
        producto = self.get_object()
        qs = producto.movimientos.select_related('usuario')
        return Response(MovimientoInventarioSerializer(qs, many=True).data)


class MovimientoInventarioViewSet(viewsets.ReadOnlyModelViewSet):
    """Kardex de solo lectura (RF10). ?producto=<id> &tipo=INGRESO|SALIDA|AJUSTE &fecha_desde= &fecha_hasta="""
    serializer_class = MovimientoInventarioSerializer

    def get_queryset(self):
        qs = MovimientoInventario.objects.select_related('producto', 'usuario')
        p = self.request.query_params
        if p.get('producto'):
            qs = qs.filter(producto_id=p['producto'])
        if p.get('tipo'):
            qs = qs.filter(tipo=p['tipo'].upper())
        if p.get('fecha_desde'):
            qs = qs.filter(fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha__date__lte=p['fecha_hasta'])
        return qs

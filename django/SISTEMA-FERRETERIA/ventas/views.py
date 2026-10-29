from datetime import timedelta
from decimal import Decimal

from django.db.models import Count, DecimalField, ExpressionWrapper, F, Q, Sum
from django.db.models.functions import Coalesce, TruncDate, TruncMonth, TruncWeek
from django.http import HttpResponse
from django.utils import timezone
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, mixins, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from auditoria.utils import AuditoriaMixin, registrar
from cuentas.permissions import EsAdmin, es_admin
from productos.models import Producto
from . import services
from .comprobante import generar_pdf_venta
from .models import CarritoItem, Cliente, DetalleVenta, Pedido, Venta
from .serializers import (
    AgregarCarritoSerializer, AnularVentaSerializer, AprobarPedidoSerializer, CantidadCarritoSerializer,
    CarritoAPedidoSerializer, ClienteSerializer, ConfirmarCarritoSerializer, PedidoSerializer,
    RechazarPedidoSerializer, VentaSerializer,
)


def _bool_param(valor):
    return str(valor).lower() in ('1', 'true', 'si', 'sí')


# ---------------------------------------------------------------------------
# Clientes (RF16)
# ---------------------------------------------------------------------------
class ClienteViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    serializer_class = ClienteSerializer
    filter_backends = [filters.SearchFilter]
    search_fields = ['nombre', 'numero_documento', 'telefono']

    def get_queryset(self):
        qs = Cliente.objects.annotate(total_compras=Count('ventas', filter=Q(ventas__estado='COMPLETADA')))
        if self.request.query_params.get('activo') is not None:
            qs = qs.filter(activo=_bool_param(self.request.query_params['activo']))
        return qs

    def destroy(self, request, *args, **kwargs):
        if not es_admin(request.user):
            self.permission_denied(request, message='Solo el Administrador puede eliminar clientes.')
        return super().destroy(request, *args, **kwargs)


# ---------------------------------------------------------------------------
# Ventas (CU08, CU12, CU13, RF17-RF21)
# ---------------------------------------------------------------------------
class VentaViewSet(mixins.CreateModelMixin, mixins.ListModelMixin,
                   mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """
    Las ventas no se editan ni se borran: se registran y, si hace falta, se anulan.
    Historial (RF21 / FER-47):
      ?fecha_desde=2026-10-01 &fecha_hasta=2026-10-31 &vendedor=<id> &cliente=<id>
      &estado=COMPLETADA|ANULADA &metodo_pago=YAPE &origen=WHATSAPP &mias=true &search=B001-000010
    Extras:
      GET  /ventas/resumen/            totales del filtro + desglose por vendedor
      GET  /ventas/<id>/comprobante/   PDF de la boleta interna (FER-49)
      POST /ventas/<id>/anular/        {"motivo": "..."} solo Admin
    """
    serializer_class = VentaSerializer
    filter_backends = [filters.SearchFilter]
    search_fields = ['numero', 'cliente__nombre', 'cliente__numero_documento', 'vendedor__username']

    def get_queryset(self):
        qs = (Venta.objects.select_related('cliente', 'vendedor', 'anulada_por', 'pedido')
              .prefetch_related('detalles__producto').order_by('-fecha'))
        p = self.request.query_params
        if p.get('fecha_desde'):
            qs = qs.filter(fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha__date__lte=p['fecha_hasta'])
        if p.get('vendedor'):
            qs = qs.filter(vendedor_id=p['vendedor'])
        if _bool_param(p.get('mias', '')):
            qs = qs.filter(vendedor=self.request.user)
        if p.get('cliente'):
            qs = qs.filter(cliente_id=p['cliente'])
        if p.get('estado'):
            qs = qs.filter(estado=p['estado'].upper())
        if p.get('metodo_pago'):
            qs = qs.filter(metodo_pago=p['metodo_pago'].upper())
        if p.get('origen'):
            qs = qs.filter(origen=p['origen'].upper())
        return qs

    def perform_create(self, serializer):
        super().perform_create(serializer)
        v = serializer.instance
        registrar('CREAR', v, request=self.request, descripcion=f'Venta {v.numero} por S/ {v.total}')

    @extend_schema(responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['get'])
    def resumen(self, request):
        """Totales del historial filtrado (mismos filtros que el listado)."""
        qs = self.filter_queryset(self.get_queryset())
        ok = qs.filter(estado='COMPLETADA')
        tot = ok.aggregate(cantidad=Count('id'), total=Sum('total'), igv=Sum('igv'))
        por_vendedor = (ok.values('vendedor_id', 'vendedor__username')
                        .annotate(cantidad=Count('id'), total=Sum('total')).order_by('-total'))
        por_metodo = ok.values('metodo_pago').annotate(cantidad=Count('id'), total=Sum('total')).order_by('-total')
        return Response({
            'ventas_completadas': tot['cantidad'] or 0,
            'ventas_anuladas': qs.filter(estado='ANULADA').count(),
            'total_vendido': tot['total'] or 0,
            'total_igv': tot['igv'] or 0,
            'ticket_promedio': round((tot['total'] or 0) / tot['cantidad'], 2) if tot['cantidad'] else 0,
            'por_vendedor': [{'vendedor_id': r['vendedor_id'], 'vendedor': r['vendedor__username'],
                              'cantidad': r['cantidad'], 'total': r['total']} for r in por_vendedor],
            'por_metodo_pago': list(por_metodo),
        })

    @extend_schema(responses={(200, 'application/pdf'): OpenApiTypes.BINARY})
    @action(detail=True, methods=['get'])
    def comprobante(self, request, pk=None):
        """FER-49: PDF de la boleta interna. ?descargar=true para forzar descarga."""
        venta = self.get_object()
        pdf = generar_pdf_venta(venta)
        resp = HttpResponse(pdf, content_type='application/pdf')
        modo = 'attachment' if _bool_param(request.query_params.get('descargar', '')) else 'inline'
        resp['Content-Disposition'] = f'{modo}; filename="comprobante_{venta.numero}.pdf"'
        return resp

    @extend_schema(request=AnularVentaSerializer, responses=VentaSerializer)
    @action(detail=True, methods=['post'], permission_classes=[EsAdmin])
    def anular(self, request, pk=None):
        """POST /api/ventas/<id>/anular/  {"motivo": "..."}  (solo Admin). Reingresa el stock."""
        venta = self.get_object()
        ser = AnularVentaSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        venta = services.anular_venta(venta=venta, usuario=request.user, motivo=ser.validated_data['motivo'])
        registrar('ANULAR', venta, request=request, descripcion=f'Venta {venta.numero}: {venta.motivo_anulacion}')
        venta = self.get_queryset().get(pk=venta.pk)
        return Response(VentaSerializer(venta, context={'request': request}).data)


# ---------------------------------------------------------------------------
# Pedidos por confirmar (FER-45, FER-46, RF32, RF33)
# ---------------------------------------------------------------------------
class PedidoViewSet(mixins.CreateModelMixin, mixins.ListModelMixin,
                    mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """
    Bandeja de pedidos. Un pedido NO descuenta stock hasta que se aprueba.
      ?estado=PENDIENTE &origen=WEB|MOVIL|WHATSAPP &fecha_desde= &fecha_hasta= &search=
      GET  /pedidos/resumen/               contadores por estado (para el badge de notificación)
      POST /pedidos/<id>/aprobar/          {"metodo_pago": "EFECTIVO", "cliente": 1} -> genera la venta
      POST /pedidos/<id>/rechazar/         {"motivo": "..."}
      POST /pedidos/<id>/cancelar/         {"motivo": "..."} (el creador o un Admin)
    """
    serializer_class = PedidoSerializer
    filter_backends = [filters.SearchFilter]
    search_fields = ['numero', 'contacto_nombre', 'contacto_telefono', 'cliente__nombre']

    def get_queryset(self):
        qs = (Pedido.objects.select_related('cliente', 'creado_por', 'revisado_por', 'venta')
              .prefetch_related('detalles__producto'))
        p = self.request.query_params
        if p.get('estado'):
            qs = qs.filter(estado__in=[e.strip().upper() for e in p['estado'].split(',')])
        if p.get('origen'):
            qs = qs.filter(origen=p['origen'].upper())
        if p.get('fecha_desde'):
            qs = qs.filter(fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            qs = qs.filter(fecha__date__lte=p['fecha_hasta'])
        return qs

    def _respuesta(self, pedido):
        pedido = self.get_queryset().get(pk=pedido.pk)
        return Response(PedidoSerializer(pedido, context=self.get_serializer_context()).data)

    def perform_create(self, serializer):
        super().perform_create(serializer)
        registrar('CREAR', serializer.instance, request=self.request)

    @extend_schema(responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['get'])
    def resumen(self, request):
        conteo = dict(Pedido.objects.values_list('estado').annotate(n=Count('id')))
        ultimo = Pedido.objects.filter(estado='PENDIENTE').order_by('-fecha').first()
        return Response({
            'pendientes': conteo.get('PENDIENTE', 0),
            'aprobados': conteo.get('APROBADO', 0),
            'rechazados': conteo.get('RECHAZADO', 0),
            'cancelados': conteo.get('CANCELADO', 0),
            'pendientes_por_origen': dict(Pedido.objects.filter(estado='PENDIENTE')
                                          .values_list('origen').annotate(n=Count('id'))),
            'ultimo_pendiente': ultimo.fecha if ultimo else None,
        })

    @extend_schema(request=AprobarPedidoSerializer, responses=PedidoSerializer)
    @action(detail=True, methods=['post'])
    def aprobar(self, request, pk=None):
        pedido = self.get_object()
        ser = AprobarPedidoSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        pedido = services.aprobar_pedido(pedido=pedido, usuario=request.user,
                                         metodo_pago=ser.validated_data.get('metodo_pago', 'EFECTIVO'),
                                         cliente=ser.validated_data.get('cliente'))
        registrar('APROBAR', pedido, request=request, descripcion=f'{pedido.numero} -> venta {pedido.venta.numero}')
        return self._respuesta(pedido)

    @extend_schema(request=RechazarPedidoSerializer, responses=PedidoSerializer)
    @action(detail=True, methods=['post'])
    def rechazar(self, request, pk=None):
        pedido = self.get_object()
        ser = RechazarPedidoSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        pedido = services.rechazar_pedido(pedido=pedido, usuario=request.user, motivo=ser.validated_data['motivo'])
        registrar('RECHAZAR', pedido, request=request, descripcion=f'{pedido.numero}: {pedido.motivo_rechazo}')
        return self._respuesta(pedido)

    @extend_schema(request=RechazarPedidoSerializer, responses=PedidoSerializer)
    @action(detail=True, methods=['post'])
    def cancelar(self, request, pk=None):
        pedido = self.get_object()
        if not (es_admin(request.user) or pedido.creado_por_id == request.user.id):
            self.permission_denied(request, message='Solo quien creó el pedido o un Administrador puede cancelarlo.')
        motivo = str(request.data.get('motivo', '')).strip() or 'Cancelado por el usuario'
        pedido = services.rechazar_pedido(pedido=pedido, usuario=request.user, motivo=motivo, estado='CANCELADO')
        registrar('ANULAR', pedido, request=request, descripcion=f'{pedido.numero} cancelado: {motivo}')
        return self._respuesta(pedido)


# ---------------------------------------------------------------------------
# Carrito (FER-45) - uno por usuario autenticado
# ---------------------------------------------------------------------------
class CarritoViewSet(viewsets.ViewSet):
    """
    GET    /carrito/                       contenido + subtotal, IGV, total y alertas de stock
    POST   /carrito/agregar/               {"producto": 3, "cantidad": 2, "reemplazar": false}
    PATCH  /carrito/items/<item_id>/       {"cantidad": 5}
    DELETE /carrito/items/<item_id>/
    POST   /carrito/vaciar/
    POST   /carrito/confirmar/             {"cliente": 1, "metodo_pago": "YAPE"} -> crea la VENTA
    POST   /carrito/enviar-pedido/         {"cliente": 1 | "contacto_nombre": "..."} -> crea PEDIDO pendiente
    """

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def list(self, request):
        return Response(services.resumen_carrito(request.user))

    @extend_schema(request=AgregarCarritoSerializer, responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['post'])
    def agregar(self, request):
        ser = AgregarCarritoSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        services.agregar_al_carrito(usuario=request.user, **ser.validated_data)
        return Response(services.resumen_carrito(request.user))

    @extend_schema(request=CantidadCarritoSerializer, responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['patch', 'delete'], url_path=r'items/(?P<item_id>\d+)')
    def item(self, request, item_id=None):
        item = CarritoItem.objects.filter(usuario=request.user, pk=item_id).first()
        if item is None:
            raise serializers.ValidationError({'item': 'Ese producto no está en tu carrito.'})
        if request.method == 'DELETE':
            item.delete()
        else:
            ser = CantidadCarritoSerializer(data=request.data)
            ser.is_valid(raise_exception=True)
            item.cantidad = ser.validated_data['cantidad']
            item.save(update_fields=['cantidad'])
        return Response(services.resumen_carrito(request.user))

    @extend_schema(request=None, responses=OpenApiTypes.OBJECT)
    @action(detail=False, methods=['post'])
    def vaciar(self, request):
        CarritoItem.objects.filter(usuario=request.user).delete()
        return Response(services.resumen_carrito(request.user))

    @extend_schema(request=ConfirmarCarritoSerializer, responses=VentaSerializer)
    @action(detail=False, methods=['post'])
    def confirmar(self, request):
        ser = ConfirmarCarritoSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        venta = services.confirmar_carrito(usuario=request.user, **ser.validated_data)
        registrar('CREAR', venta, request=request, descripcion=f'Venta {venta.numero} (carrito) por S/ {venta.total}')
        venta = Venta.objects.select_related('cliente', 'vendedor').prefetch_related('detalles__producto').get(pk=venta.pk)
        return Response(VentaSerializer(venta, context={'request': request}).data, status=201)

    @extend_schema(request=CarritoAPedidoSerializer, responses=PedidoSerializer)
    @action(detail=False, methods=['post'], url_path='enviar-pedido')
    def enviar_pedido(self, request):
        ser = CarritoAPedidoSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        if not ser.validated_data.get('cliente') and not ser.validated_data.get('contacto_nombre'):
            raise serializers.ValidationError({'cliente': 'Indica un cliente registrado o un nombre de contacto.'})
        pedido = services.carrito_a_pedido(usuario=request.user, **ser.validated_data)
        registrar('CREAR', pedido, request=request)
        return Response(PedidoSerializer(pedido, context={'request': request}).data, status=201)


# ---------------------------------------------------------------------------
# Dashboard (RF22) y reportes (RF23, RF24)
# ---------------------------------------------------------------------------
class DashboardView(APIView):
    """RF22: indicadores para el dashboard."""
    permission_classes = [IsAuthenticated]

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        from compras.models import OrdenCompra  # import local: evita dependencia circular entre apps

        ahora = timezone.localtime()
        hoy = ahora.date()
        ok = Venta.objects.filter(estado='COMPLETADA')

        def resumen(qs):
            r = qs.aggregate(cantidad=Count('id'), total=Sum('total'))
            return {'cantidad': r['cantidad'] or 0, 'total': r['total'] or 0}

        desde = hoy - timedelta(days=6)
        por_dia = {r['dia']: r for r in (ok.filter(fecha__date__gte=desde)
                                         .annotate(dia=TruncDate('fecha')).values('dia')
                                         .annotate(cantidad=Count('id'), total=Sum('total')))}
        ultimos_7 = []
        for i in range(7):  # se rellenan los días sin ventas con 0 para que el gráfico no tenga huecos
            dia = desde + timedelta(days=i)
            r = por_dia.get(dia)
            ultimos_7.append({'dia': dia, 'cantidad': r['cantidad'] if r else 0, 'total': r['total'] if r else 0})

        top = (DetalleVenta.objects.filter(venta__estado='COMPLETADA')
               .values('producto_id', 'producto__nombre')
               .annotate(unidades=Sum('cantidad'), monto=Sum('subtotal')).order_by('-unidades')[:5])

        criticos = (Producto.objects.filter(activo=True, stock_actual__lte=F('stock_minimo'))
                    .values('id', 'codigo', 'nombre', 'stock_actual', 'stock_minimo').order_by('stock_actual')[:20])

        valor_inventario = Producto.objects.filter(activo=True).aggregate(
            v=Sum(ExpressionWrapper(F('stock_actual') * F('precio_compra'),
                                    output_field=DecimalField(max_digits=14, decimal_places=2))))['v'] or 0

        return Response({
            'ventas_hoy': resumen(ok.filter(fecha__date=hoy)),
            'ventas_mes': resumen(ok.filter(fecha__year=ahora.year, fecha__month=ahora.month)),
            'ventas_ultimos_7_dias': ultimos_7,
            'productos_mas_vendidos': [
                {'producto_id': r['producto_id'], 'nombre': r['producto__nombre'],
                 'unidades': r['unidades'], 'monto': r['monto']} for r in top
            ],
            'stock_critico': list(criticos),
            'total_productos_activos': Producto.objects.filter(activo=True).count(),
            'valor_inventario': valor_inventario,
            'pedidos_pendientes': Pedido.objects.filter(estado='PENDIENTE').count(),
            'compras_por_recibir': OrdenCompra.objects.filter(estado__in=['PENDIENTE', 'PARCIAL']).count(),
        })


class ReporteVentasView(APIView):
    """
    RF23 - Ventas por periodo para gráficos.
    GET /api/reportes/ventas/?agrupar=dia|semana|mes &fecha_desde=2026-10-01 &fecha_hasta=2026-10-31 &vendedor=<id>
    Por defecto: últimos 30 días agrupado por día.
    """

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        p = request.query_params
        agrupar = (p.get('agrupar') or 'dia').lower()
        funciones = {'dia': TruncDate, 'semana': TruncWeek, 'mes': TruncMonth}
        if agrupar not in funciones:
            raise serializers.ValidationError({'agrupar': 'Usa dia, semana o mes.'})
        hoy = timezone.localdate()
        desde = p.get('fecha_desde') or (hoy - timedelta(days=29)).isoformat()
        hasta = p.get('fecha_hasta') or hoy.isoformat()
        qs = Venta.objects.filter(estado='COMPLETADA', fecha__date__gte=desde, fecha__date__lte=hasta)
        if p.get('vendedor'):
            qs = qs.filter(vendedor_id=p['vendedor'])
        filas = (qs.annotate(periodo=funciones[agrupar]('fecha')).values('periodo')
                 .annotate(cantidad=Count('id'), subtotal=Sum('subtotal'), igv=Sum('igv'), total=Sum('total'))
                 .order_by('periodo'))
        datos = []
        for r in filas:
            periodo = r['periodo']
            if hasattr(periodo, 'date'):
                periodo = timezone.localtime(periodo).date() if timezone.is_aware(periodo) else periodo.date()
            datos.append({**r, 'periodo': periodo})
        return Response({
            'agrupar': agrupar, 'fecha_desde': desde, 'fecha_hasta': hasta,
            'total_periodo': sum((r['total'] for r in datos), Decimal('0')),
            'datos': datos,
        })


class ReporteMargenView(APIView):
    """
    RF24 - Margen estimado por producto (solo Admin).
    GET /api/reportes/margen/?categoria=<id> &fecha_desde= &fecha_hasta= &ordenar=margen|utilidad|unidades
    margen = precio_venta - precio_compra (sin IGV). utilidad_estimada = margen * unidades vendidas.
    """
    permission_classes = [EsAdmin]

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        p = request.query_params
        filtro = Q(detalles_venta__venta__estado='COMPLETADA')
        if p.get('fecha_desde'):
            filtro &= Q(detalles_venta__venta__fecha__date__gte=p['fecha_desde'])
        if p.get('fecha_hasta'):
            filtro &= Q(detalles_venta__venta__fecha__date__lte=p['fecha_hasta'])
        qs = Producto.objects.select_related('categoria').annotate(
            unidades_vendidas=Coalesce(Sum('detalles_venta__cantidad', filter=filtro), 0))
        if p.get('categoria'):
            qs = qs.filter(categoria_id=p['categoria'])

        filas = []
        for prod in qs:
            margen = prod.precio_venta - prod.precio_compra
            filas.append({
                'id': prod.id, 'codigo': prod.codigo, 'nombre': prod.nombre, 'categoria': prod.categoria.nombre,
                'precio_compra': prod.precio_compra, 'precio_venta': prod.precio_venta,
                'margen': margen,
                'margen_porcentaje': round(margen * 100 / prod.precio_venta, 1) if prod.precio_venta else 0,
                'unidades_vendidas': prod.unidades_vendidas,
                'utilidad_estimada': margen * prod.unidades_vendidas,
                'alerta': 'MARGEN_NEGATIVO' if margen < 0 else ('MARGEN_BAJO' if prod.precio_venta and
                                                              margen * 100 / prod.precio_venta < 10 else None),
            })
        orden = {'margen': 'margen_porcentaje', 'unidades': 'unidades_vendidas'}.get(p.get('ordenar'), 'utilidad_estimada')
        filas.sort(key=lambda f: f[orden], reverse=True)
        return Response({
            'utilidad_total_estimada': sum((f['utilidad_estimada'] for f in filas), Decimal('0')),
            'productos': filas,
        })

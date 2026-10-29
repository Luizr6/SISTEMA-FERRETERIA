from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import (
    CarritoViewSet, ClienteViewSet, DashboardView, PedidoViewSet, ReporteMargenView,
    ReporteVentasView, VentaViewSet,
)

router = DefaultRouter()
router.register(r'clientes', ClienteViewSet, basename='cliente')
router.register(r'ventas', VentaViewSet, basename='venta')
router.register(r'pedidos', PedidoViewSet, basename='pedido')
router.register(r'carrito', CarritoViewSet, basename='carrito')

urlpatterns = [
    path('dashboard/', DashboardView.as_view(), name='dashboard'),
    path('reportes/ventas/', ReporteVentasView.as_view(), name='reporte-ventas'),
    path('reportes/margen/', ReporteMargenView.as_view(), name='reporte-margen'),
] + router.urls

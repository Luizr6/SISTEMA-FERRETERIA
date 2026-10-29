from rest_framework.routers import DefaultRouter

from .views import (
    CategoriaViewSet, MarcaViewSet, MovimientoInventarioViewSet,
    ProductoViewSet, ProveedorViewSet,
)

router = DefaultRouter()
router.register(r'productos', ProductoViewSet, basename='producto')
router.register(r'categorias', CategoriaViewSet, basename='categoria')
router.register(r'marcas', MarcaViewSet, basename='marca')
router.register(r'proveedores', ProveedorViewSet, basename='proveedor')
router.register(r'movimientos', MovimientoInventarioViewSet, basename='movimiento')

urlpatterns = router.urls

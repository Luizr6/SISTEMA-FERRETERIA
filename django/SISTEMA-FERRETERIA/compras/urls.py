from rest_framework.routers import DefaultRouter

from .views import OrdenCompraViewSet

router = DefaultRouter()
router.register(r'compras', OrdenCompraViewSet, basename='compra')

urlpatterns = router.urls

from rest_framework.routers import DefaultRouter

from .views import BitacoraViewSet

router = DefaultRouter()
router.register(r'bitacora', BitacoraViewSet, basename='bitacora')

urlpatterns = router.urls

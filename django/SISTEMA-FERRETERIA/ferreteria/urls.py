from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularRedocView, SpectacularSwaggerView

admin.site.site_header = 'Sistema Ferretería - Administración'
admin.site.site_title = 'Sistema Ferretería'

urlpatterns = [
    path('admin/', admin.site.urls),
    # Documentación OpenAPI / Swagger (RF25)
    path('api/schema/', SpectacularAPIView.as_view(), name='schema'),
    path('api/docs/', SpectacularSwaggerView.as_view(url_name='schema'), name='swagger-ui'),
    path('api/redoc/', SpectacularRedocView.as_view(url_name='schema'), name='redoc'),
    # API
    path('api/', include('cuentas.urls')),
    path('api/', include('auditoria.urls')),
    path('api/', include('productos.urls')),
    path('api/', include('compras.urls')),
    path('api/', include('ventas.urls')),
    path('api/', include('whatsapp.urls')),
]

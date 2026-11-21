from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularRedocView, SpectacularSwaggerView

from .legal import eliminacion_datos, politica_privacidad

admin.site.site_header = 'Sistema Ferretería - Administración'
admin.site.site_title = 'Sistema Ferretería'

urlpatterns = [
    path('admin/', admin.site.urls),
    # Páginas públicas que exige Meta para publicar la app de WhatsApp
    path('privacidad/', politica_privacidad, name='privacidad'),
    path('eliminacion-datos/', eliminacion_datos, name='eliminacion-datos'),
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
    path('api/', include('asistente.urls')),
    path('api/', include('caja.urls')),
]

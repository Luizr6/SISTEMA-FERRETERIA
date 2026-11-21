from django.urls import path

from .views import AlertasView, AsistenteView

urlpatterns = [
    path('asistente/', AsistenteView.as_view(), name='asistente'),
    path('asistente/alertas/', AlertasView.as_view(), name='asistente-alertas'),
]

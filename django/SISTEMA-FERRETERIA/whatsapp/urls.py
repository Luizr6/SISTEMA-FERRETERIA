from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import ConversacionViewSet, EstadoWhatsAppView, SimuladorView, WebhookView

router = DefaultRouter()
router.register(r'whatsapp/conversaciones', ConversacionViewSet, basename='whatsapp-conversacion')

urlpatterns = [
    path('whatsapp/webhook/', WebhookView.as_view(), name='whatsapp-webhook'),
    path('whatsapp/simular/', SimuladorView.as_view(), name='whatsapp-simular'),
    path('whatsapp/estado/', EstadoWhatsAppView.as_view(), name='whatsapp-estado'),
] + router.urls

import json

from django.conf import settings
from django.http import HttpResponse
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, mixins, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from . import cliente_meta, services
from .models import Conversacion, Mensaje


@method_decorator(csrf_exempt, name='dispatch')
class WebhookView(APIView):
    """
    FER-51 - Webhook de WhatsApp Cloud API (URL que se registra en Meta for Developers).
      GET  /api/whatsapp/webhook/  verificación (hub.mode, hub.verify_token, hub.challenge)
      POST /api/whatsapp/webhook/  mensajes entrantes
    Siempre responde 200 a Meta para que no reintente sin fin.
    """
    permission_classes = [AllowAny]
    authentication_classes = []

    @extend_schema(responses={(200, 'text/plain'): OpenApiTypes.STR})
    def get(self, request):
        p = request.query_params
        if p.get('hub.mode') == 'subscribe' and p.get('hub.verify_token') == settings.WHATSAPP['VERIFY_TOKEN']:
            return HttpResponse(p.get('hub.challenge', ''), content_type='text/plain')
        return HttpResponse('Token de verificación inválido', status=403, content_type='text/plain')

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        if not cliente_meta.firma_valida(request.body, request.headers.get('X-Hub-Signature-256')):
            return HttpResponse('Firma inválida', status=403, content_type='text/plain')
        try:
            payload = json.loads(request.body or b'{}')
        except ValueError:
            return Response({'ok': False, 'error': 'JSON inválido'})
        procesados = services.procesar_webhook(payload)
        return Response({'ok': True, 'procesados': procesados})


class MensajeSerializer(serializers.ModelSerializer):
    enviado_por_nombre = serializers.CharField(source='enviado_por.username', read_only=True, default=None)
    pedido_numero = serializers.CharField(source='pedido.numero', read_only=True, default=None)

    class Meta:
        model = Mensaje
        fields = ['id', 'direccion', 'texto', 'intencion', 'estado_envio', 'error', 'pedido',
                  'pedido_numero', 'enviado_por_nombre', 'fecha']


class ConversacionSerializer(serializers.ModelSerializer):
    cliente_nombre = serializers.CharField(source='cliente.nombre', read_only=True, default=None)
    ultimo_texto = serializers.SerializerMethodField()
    total_mensajes = serializers.SerializerMethodField()

    class Meta:
        model = Conversacion
        fields = ['id', 'telefono', 'nombre', 'cliente', 'cliente_nombre', 'ultimo_mensaje',
                  'ultimo_texto', 'no_leidos', 'total_mensajes', 'bot_activo']
        read_only_fields = ['telefono', 'ultimo_mensaje', 'no_leidos']

    def get_ultimo_texto(self, obj) -> str:
        m = obj.mensajes.order_by('-fecha', '-id').first()
        return m.texto[:80] if m else ''

    def get_total_mensajes(self, obj) -> int:
        return obj.mensajes.count()


class ConversacionViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin,
                          mixins.UpdateModelMixin, viewsets.GenericViewSet):
    """
    FER-54 - Panel de monitoreo del bot (usuarios autenticados).
      GET   /api/whatsapp/conversaciones/                 lista (?search=)
      GET   /api/whatsapp/conversaciones/<id>/mensajes/   historial (marca como leído)
      POST  /api/whatsapp/conversaciones/<id>/responder/  {"texto": "..."} respuesta manual
      PATCH /api/whatsapp/conversaciones/<id>/            {"bot_activo": false} para que atienda una persona
    """
    queryset = Conversacion.objects.select_related('cliente')
    serializer_class = ConversacionSerializer
    filter_backends = [filters.SearchFilter]
    search_fields = ['telefono', 'nombre', 'cliente__nombre']

    @extend_schema(responses=MensajeSerializer(many=True))
    @action(detail=True, methods=['get'])
    def mensajes(self, request, pk=None):
        conv = self.get_object()
        Conversacion.objects.filter(pk=conv.pk).update(no_leidos=0)
        qs = conv.mensajes.select_related('enviado_por', 'pedido')
        return Response(MensajeSerializer(qs, many=True).data)

    @extend_schema(request=OpenApiTypes.OBJECT, responses=MensajeSerializer)
    @action(detail=True, methods=['post'])
    def responder(self, request, pk=None):
        conv = self.get_object()
        texto = str(request.data.get('texto', '')).strip()
        if not texto:
            raise serializers.ValidationError({'texto': 'Escribe un mensaje.'})
        msg = services.enviar(conv, texto, intencion='MANUAL', usuario=request.user)
        return Response(MensajeSerializer(msg).data, status=201)


class SimuladorView(APIView):
    """
    Para la DEMO sin cuenta de Meta: simula que un cliente escribe por WhatsApp.
    POST /api/whatsapp/simular/  {"telefono": "51999111222", "nombre": "Juan", "texto": "precio martillo"}
    Ejecuta exactamente el mismo flujo que el webhook real.
    """

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        telefono = ''.join(ch for ch in str(request.data.get('telefono', '')) if ch.isdigit())
        texto = str(request.data.get('texto', '')).strip()
        if not telefono or not texto:
            raise serializers.ValidationError({'detalle': 'Envía "telefono" (solo dígitos) y "texto".'})
        entrante, salida = services.procesar_entrante(
            telefono=telefono, texto=texto, nombre=str(request.data.get('nombre', '')).strip())
        return Response({
            'entrante': MensajeSerializer(entrante).data,
            'respuesta': MensajeSerializer(salida).data if salida else None,
            'modo': 'REAL' if cliente_meta.credenciales_configuradas() else 'SIMULADO',
        })


class EstadoWhatsAppView(APIView):
    """GET /api/whatsapp/estado/ - si hay credenciales de Meta configuradas (para mostrar en el panel)."""

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        return Response({
            'modo': 'REAL' if cliente_meta.credenciales_configuradas() else 'SIMULADO',
            'api_version': settings.WHATSAPP['API_VERSION'],
            'firma_requerida': bool(settings.WHATSAPP['APP_SECRET']),
            'conversaciones': Conversacion.objects.count(),
            'mensajes_sin_leer': sum(Conversacion.objects.values_list('no_leidos', flat=True)),
        })

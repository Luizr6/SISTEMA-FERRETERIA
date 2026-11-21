import json
import logging
import threading

from django.conf import settings
from django.db import close_old_connections
from django.http import HttpResponse
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, mixins, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from asistente import ia
from auditoria.utils import registrar
from cuentas.permissions import es_admin

from . import cliente_meta, services
from .models import Conversacion, Mensaje

log = logging.getLogger('ferreteria.whatsapp')


def _procesar_en_segundo_plano(payload):
    """La IA puede tardar unos segundos: Meta exige respuesta rápida o reintenta el webhook."""
    try:
        services.procesar_webhook(payload)
    except Exception:  # noqa: BLE001
        log.exception('Error procesando webhook de WhatsApp')
    finally:
        close_old_connections()


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
        if ia.disponible():
            # Con IA la respuesta tarda: se responde 200 a Meta al instante y se procesa aparte
            threading.Thread(target=_procesar_en_segundo_plano, args=(payload,), daemon=True).start()
            return Response({'ok': True, 'procesados': 'en segundo plano'})
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
                  'ultimo_texto', 'no_leidos', 'total_mensajes', 'bot_activo',
                  'asesor_solicitado', 'pausa_automatica']
        read_only_fields = ['telefono', 'ultimo_mensaje', 'no_leidos', 'asesor_solicitado', 'pausa_automatica']

    def update(self, instance, validated_data):
        # Si el vendedor cambia el bot a mano, la pausa deja de ser automática
        if 'bot_activo' in validated_data:
            instance.pausa_automatica = False
            if validated_data['bot_activo']:
                instance.asesor_solicitado = None
        return super().update(instance, validated_data)

    def get_ultimo_texto(self, obj) -> str:
        m = obj.mensajes.order_by('-fecha', '-id').first()
        return m.texto[:80] if m else ''

    def get_total_mensajes(self, obj) -> int:
        return obj.mensajes.count()


class ConversacionViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin,
                          mixins.UpdateModelMixin, mixins.DestroyModelMixin, viewsets.GenericViewSet):
    """
    FER-54 - Panel de monitoreo del bot (usuarios autenticados).
      GET   /api/whatsapp/conversaciones/                 lista (?search=)
      GET   /api/whatsapp/conversaciones/<id>/mensajes/   historial (marca como leído)
      POST  /api/whatsapp/conversaciones/<id>/responder/  {"texto": "..."} respuesta manual
      PATCH /api/whatsapp/conversaciones/<id>/            {"bot_activo": false} para que atienda una persona
      DELETE /api/whatsapp/conversaciones/<id>/                       elimina el chat y sus mensajes (Admin)
      DELETE /api/whatsapp/conversaciones/<id>/mensajes/<mensaje_id>/ elimina un mensaje (Admin)

    Eliminar borra el registro del sistema (p. ej. solicitudes "ELIMINAR MIS DATOS" o chats de prueba).
    No borra nada del celular del cliente: WhatsApp no permite eliminar mensajes ya entregados.
    Los pedidos registrados no se eliminan (son ventas de la tienda).
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

    def _solo_admin(self, request):
        if not es_admin(request.user):
            self.permission_denied(request, message='Solo el Administrador puede eliminar conversaciones o mensajes.')

    def destroy(self, request, *args, **kwargs):
        self._solo_admin(request)
        conv = self.get_object()
        total = conv.mensajes.count()
        descripcion = f'Chat de WhatsApp +{conv.telefono} ({conv.nombre or "sin nombre"}), {total} mensaje(s)'
        conv.delete()
        registrar('ELIMINAR', request=request, modelo='Conversacion', descripcion=descripcion)
        return Response(status=204)

    @extend_schema(responses={204: None})
    @action(detail=True, methods=['delete'], url_path=r'mensajes/(?P<mensaje_id>\d+)')
    def eliminar_mensaje(self, request, pk=None, mensaje_id=None):
        self._solo_admin(request)
        conv = self.get_object()
        msg = conv.mensajes.filter(pk=mensaje_id).first()
        if msg is None:
            return Response({'detail': 'El mensaje no existe.'}, status=404)
        msg.delete()
        ultimo = conv.mensajes.order_by('-fecha').values_list('fecha', flat=True).first()
        if ultimo:
            Conversacion.objects.filter(pk=conv.pk).update(ultimo_mensaje=ultimo)
        registrar('ELIMINAR', request=request, modelo='Mensaje',
                  descripcion=f'Mensaje de WhatsApp en chat +{conv.telefono}')
        return Response(status=204)

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
        if cliente_meta.credenciales_configuradas() and not settings.DEBUG:
            # Con WhatsApp real conectado, simular enviaría mensajes de verdad a cualquier número
            raise PermissionDenied('El simulador solo está disponible en modo de prueba (sin WhatsApp conectado).')
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
            'ia': {'activa': ia.disponible(), 'modelo': ia.modelo() if ia.disponible() else None,
                   'motor': 'IA (Groq)' if ia.disponible() else 'Reglas'},
            'api_version': settings.WHATSAPP['API_VERSION'],
            'firma_requerida': bool(settings.WHATSAPP['APP_SECRET']),
            'conversaciones': Conversacion.objects.count(),
            'mensajes_sin_leer': sum(Conversacion.objects.values_list('no_leidos', flat=True)),
        })

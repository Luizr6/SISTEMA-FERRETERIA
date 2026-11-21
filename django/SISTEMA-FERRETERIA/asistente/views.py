from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework.response import Response
from rest_framework.views import APIView

from cuentas.permissions import EsAdmin
from . import ia, services


class AsistenteView(APIView):
    """
    Asistente IA del Administrador (app móvil).
      GET  /api/asistente/                      -> resumen del negocio de hoy
      POST /api/asistente/ {"pregunta": "..."}  -> responde usando solo datos reales
    Respuesta: {respuesta, modo: IA|BASICO, modelo, alertas[], datos{}}
    """
    permission_classes = [EsAdmin]

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        return Response(services.responder())

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        pregunta = str(request.data.get('pregunta', ''))[:500]
        return Response(services.responder(pregunta))


class AlertasView(APIView):
    """
    GET /api/asistente/alertas/ -> avisos para las notificaciones de la app (stock agotado / mínimo,
    pedidos por confirmar, compras atrasadas, ventas bajas). Sin IA: siempre responde rápido.
    """
    permission_classes = [EsAdmin]

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        lista = services.alertas()
        return Response({'alertas': lista, 'total': len(lista), 'ia_activa': ia.disponible()})

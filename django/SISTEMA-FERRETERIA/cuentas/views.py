from django.conf import settings
from django.contrib.auth.password_validation import validate_password
from django.contrib.auth.tokens import default_token_generator
from django.core.exceptions import ValidationError as DjangoValidationError
from django.core.mail import send_mail
from django.utils.encoding import force_bytes, force_str
from django.utils.http import urlsafe_base64_decode, urlsafe_base64_encode
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import filters, serializers, viewsets
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.views import TokenObtainPairView

from auditoria.ms_cliente import enviar_evento
from auditoria.utils import AuditoriaMixin, registrar
from .models import Usuario
from .permissions import EsAdmin, es_admin
from .serializers import LoginSerializer, UsuarioSerializer


class LoginView(TokenObtainPairView):
    """CU01 - POST {username, password} -> {access, refresh, user_id, username, rol}"""
    serializer_class = LoginSerializer

    def post(self, request, *args, **kwargs):
        try:
            resp = super().post(request, *args, **kwargs)
        except AuthenticationFailed:
            # Nunca se envía la contraseña: solo el usuario que se intentó usar
            intento = str(request.data.get('username', ''))[:150]
            enviar_evento('LOGIN_FALLIDO', f'Intento de inicio de sesión fallido para "{intento}"',
                          request=request, usuario=intento or None, modulo='cuentas')
            raise
        if resp.status_code == 200:
            usuario = Usuario.objects.filter(pk=resp.data.get('user_id')).first()
            registrar('LOGIN', usuario, request=request, usuario=usuario, descripcion=f'Inicio de sesión de {usuario}')
        return resp


class MeView(APIView):
    """Datos del usuario autenticado (útil para que React/Android sepan el rol)."""

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request):
        u = request.user
        return Response({
            'id': u.id, 'username': u.username, 'email': u.email,
            'first_name': u.first_name, 'last_name': u.last_name,
            'nombre_completo': u.get_full_name() or u.username,
            'rol': 'ADMIN' if es_admin(u) else u.rol,
        })


class UsuarioViewSet(AuditoriaMixin, viewsets.ModelViewSet):
    """CU02 - Gestionar usuarios y roles (solo Admin). ?rol=VENDEDOR ?search="""
    serializer_class = UsuarioSerializer
    permission_classes = [EsAdmin]
    filter_backends = [filters.SearchFilter]
    search_fields = ['username', 'first_name', 'last_name', 'email']

    def get_queryset(self):
        qs = Usuario.objects.all().order_by('username')
        if self.request.query_params.get('rol'):
            qs = qs.filter(rol=self.request.query_params['rol'].upper())
        return qs

    def perform_destroy(self, instance):
        if instance.pk == self.request.user.pk:
            raise serializers.ValidationError({'usuario': 'No puedes eliminar tu propio usuario.'})
        super().perform_destroy(instance)


# ---------------------------------------------------------------------------
# RF03 / CU03 - Recuperación y cambio de contraseña
# ---------------------------------------------------------------------------
def _validar_clave(password, usuario=None):
    try:
        validate_password(password, usuario)
    except DjangoValidationError as e:
        raise serializers.ValidationError({'new_password': list(e.messages)})


class PasswordResetView(APIView):
    """
    POST /api/auth/password/reset/  {"email": "usuario@correo.com"}
    Envía un enlace con uid y token. Siempre responde 200 para no revelar qué correos existen.
    En desarrollo el correo aparece en la consola donde corre `runserver`.
    """
    permission_classes = [AllowAny]
    authentication_classes = []

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        email = str(request.data.get('email', '')).strip()
        if not email:
            raise serializers.ValidationError({'email': 'Ingresa tu correo.'})
        for usuario in Usuario.objects.filter(email__iexact=email, is_active=True):
            uid = urlsafe_base64_encode(force_bytes(usuario.pk))
            token = default_token_generator.make_token(usuario)
            enlace = f'{settings.FRONTEND_URL.rstrip("/")}/reset-password?uid={uid}&token={token}'
            send_mail(
                subject='Recuperación de contraseña - Sistema Ferretería',
                message=(f'Hola {usuario.get_full_name() or usuario.username},\n\n'
                         f'Para crear una nueva contraseña entra a:\n{enlace}\n\n'
                         f'Si usas la API directamente: uid={uid} token={token}\n\n'
                         'Si no lo solicitaste, ignora este mensaje.'),
                from_email=settings.DEFAULT_FROM_EMAIL, recipient_list=[usuario.email], fail_silently=True,
            )
        return Response({'mensaje': 'Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.'})


class PasswordResetConfirmView(APIView):
    """POST /api/auth/password/reset/confirm/  {"uid": "...", "token": "...", "new_password": "..."}"""
    permission_classes = [AllowAny]
    authentication_classes = []

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        uid, token = request.data.get('uid', ''), request.data.get('token', '')
        password = request.data.get('new_password', '')
        try:
            usuario = Usuario.objects.get(pk=force_str(urlsafe_base64_decode(uid)), is_active=True)
        except (Usuario.DoesNotExist, ValueError, TypeError, OverflowError):
            usuario = None
        if usuario is None or not default_token_generator.check_token(usuario, token):
            raise serializers.ValidationError({'token': 'El enlace no es válido o ya expiró.'})
        _validar_clave(password, usuario)
        usuario.set_password(password)
        usuario.save(update_fields=['password'])
        registrar('PASSWORD', usuario, request=request, usuario=usuario, descripcion='Contraseña restablecida por correo')
        return Response({'mensaje': 'Contraseña actualizada. Ya puedes iniciar sesión.'})


class PasswordChangeView(APIView):
    """POST /api/auth/password/change/  {"old_password": "...", "new_password": "..."} (autenticado)"""

    @extend_schema(request=OpenApiTypes.OBJECT, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        u = request.user
        if not u.check_password(request.data.get('old_password', '')):
            raise serializers.ValidationError({'old_password': 'La contraseña actual no es correcta.'})
        password = request.data.get('new_password', '')
        _validar_clave(password, u)
        u.set_password(password)
        u.save(update_fields=['password'])
        registrar('PASSWORD', u, request=request, descripcion='Cambio de contraseña')
        return Response({'mensaje': 'Contraseña actualizada.'})

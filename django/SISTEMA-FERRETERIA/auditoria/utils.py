"""Helpers para registrar en la bitácora sin repetir código en cada vista."""
from .models import Bitacora
from .ms_cliente import desde_bitacora


def _ip(request):
    if request is None:
        return None
    reenviada = request.META.get('HTTP_X_FORWARDED_FOR')
    if reenviada:
        return reenviada.split(',')[0].strip()
    return request.META.get('REMOTE_ADDR')


def registrar(accion, obj=None, *, request=None, usuario=None, descripcion='', modelo=None):
    """Registra una acción. Nunca debe romper la operación principal."""
    try:
        if usuario is None and request is not None and request.user.is_authenticated:
            usuario = request.user
        Bitacora.objects.create(
            usuario=usuario,
            accion=accion,
            modelo=modelo or (obj._meta.verbose_name.title() if obj is not None else ''),
            objeto_id=str(obj.pk) if obj is not None and obj.pk is not None else '',
            descripcion=(descripcion or (str(obj) if obj is not None else ''))[:255],
            ip=_ip(request),
        )
    except Exception:  # noqa: BLE001 - la auditoría no debe tumbar una venta
        pass
    # Copia al microservicio de auditoría (Spring Boot), si está configurado
    desde_bitacora(accion, obj, request=request, usuario=usuario, descripcion=descripcion)


class AuditoriaMixin:
    """Añádelo a un ModelViewSet para auditar crear / editar / eliminar automáticamente."""

    def perform_create(self, serializer):
        super().perform_create(serializer)
        registrar('CREAR', serializer.instance, request=self.request)

    def perform_update(self, serializer):
        super().perform_update(serializer)
        registrar('EDITAR', serializer.instance, request=self.request)

    def perform_destroy(self, instance):
        descripcion, pk = str(instance), instance.pk
        modelo = instance._meta.verbose_name.title()
        super().perform_destroy(instance)
        instance.pk = pk
        registrar('ELIMINAR', instance, request=self.request, descripcion=descripcion, modelo=modelo)

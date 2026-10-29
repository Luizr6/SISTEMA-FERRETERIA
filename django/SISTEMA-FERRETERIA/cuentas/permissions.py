from rest_framework.permissions import SAFE_METHODS, BasePermission


def es_admin(user):
    """Administrador = rol ADMIN o superusuario de Django (createsuperuser)."""
    return bool(
        user and user.is_authenticated
        and (user.is_superuser or getattr(user, 'rol', None) == 'ADMIN')
    )


class EsAdmin(BasePermission):
    message = 'Esta acción es exclusiva del Administrador.'

    def has_permission(self, request, view):
        return es_admin(request.user)


class AdminEscribeTodosLeen(BasePermission):
    """Cualquier usuario autenticado puede consultar; solo el Admin crea/edita/elimina."""
    message = 'Solo el Administrador puede crear, editar o eliminar.'

    def has_permission(self, request, view):
        if not (request.user and request.user.is_authenticated):
            return False
        if request.method in SAFE_METHODS:
            return True
        return es_admin(request.user)

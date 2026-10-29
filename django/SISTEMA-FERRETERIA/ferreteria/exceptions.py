from django.db.models import ProtectedError
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler


def manejador_errores(exc, context):
    """Todas las respuestas de error salen como {codigo, mensaje, detalle} (RF27)."""
    if isinstance(exc, ProtectedError):
        return Response(
            {'codigo': 409,
             'mensaje': 'No se puede eliminar: el registro está siendo usado por otros datos.',
             'detalle': 'Desactívalo (activo=false) en lugar de eliminarlo.'},
            status=status.HTTP_409_CONFLICT,
        )

    response = exception_handler(exc, context)
    if response is None:
        return None

    detalle = response.data
    if response.status_code == 400:
        mensaje = 'Datos inválidos.'
    elif isinstance(detalle, dict) and 'detail' in detalle:
        mensaje = str(detalle['detail'])
    else:
        mensaje = 'Error en la solicitud.'
    response.data = {'codigo': response.status_code, 'mensaje': mensaje, 'detalle': detalle}
    return response

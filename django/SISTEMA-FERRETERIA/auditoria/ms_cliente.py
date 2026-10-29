"""
Envío de eventos al microservicio de auditoría en Spring Boot (ferreteria-reportes-ms).

- Si MS_AUDITORIA_URL está vacío, no hace nada (el sistema funciona igual sin el microservicio).
- El envío va en un hilo aparte con timeout corto: una caída del microservicio nunca
  bloquea ni rompe una venta, un login o una aprobación.
"""
import json
import logging
import threading
import urllib.error
import urllib.request
from datetime import timezone as dt_timezone

from django.conf import settings
from django.utils import timezone

log = logging.getLogger('ferreteria')

# Acción de la bitácora de Django -> tipo de evento del microservicio
TIPOS = {
    'LOGIN': 'LOGIN_EXITOSO',
    'APROBAR': 'PEDIDO_APROBADO',
    'RECHAZAR': 'PEDIDO_RECHAZADO',
    'RECIBIR': 'COMPRA_RECIBIDA',
    'IMPORTAR': 'IMPORTACION_CSV',
    'PASSWORD': 'CONTRASENA_CAMBIADA',
    'CREAR': 'REGISTRO_CREADO',
    'EDITAR': 'REGISTRO_EDITADO',
    'ELIMINAR': 'REGISTRO_ELIMINADO',
    'ANULAR': 'REGISTRO_ANULADO',
}


def _config():
    return getattr(settings, 'MS_AUDITORIA', {}) or {}


def _ip(request):
    if request is None:
        return None
    reenviada = request.META.get('HTTP_X_FORWARDED_FOR')
    return reenviada.split(',')[0].strip() if reenviada else request.META.get('REMOTE_ADDR')


def _post(url, api_key, payload, timeout):
    datos = json.dumps(payload, default=str).encode('utf-8')
    req = urllib.request.Request(url, data=datos, method='POST', headers={
        'Content-Type': 'application/json', 'X-API-Key': api_key})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        log.warning('Microservicio de auditoría respondió %s: %s', e.code, e.read()[:200])
    except Exception as e:  # noqa: BLE001 - caído, timeout, DNS…
        log.warning('No se pudo enviar el evento de auditoría: %s', e)
    return None


def enviar_evento(tipo, descripcion, *, request=None, usuario=None, nivel=None, modulo=None,
                  entidad=None, entidad_id=None, datos=None):
    """Envía un evento. Nunca lanza excepciones."""
    cfg = _config()
    url = cfg.get('URL')
    if not url:
        return
    try:
        if usuario is None and request is not None and getattr(request, 'user', None) is not None \
                and request.user.is_authenticated:
            usuario = request.user
        payload = {
            'tipo': tipo,
            'descripcion': (descripcion or tipo)[:500],
            'origen': 'DJANGO',
            'modulo': modulo,
            'usuario': usuario if isinstance(usuario, str) else getattr(usuario, 'username', None),
            'usuario_id': None if isinstance(usuario, str) else getattr(usuario, 'pk', None),
            'entidad': entidad,
            'entidad_id': str(entidad_id) if entidad_id is not None else None,
            'datos': datos,
            'ip': _ip(request),
            # formato ISO con 'Z' (UTC), el que Jackson lee directamente como Instant
            'fecha': timezone.now().astimezone(dt_timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%fZ'),
        }
        if nivel:
            payload['nivel'] = nivel
        payload = {k: v for k, v in payload.items() if v is not None}
        args = (url, cfg.get('API_KEY', ''), payload, cfg.get('TIMEOUT', 3))
        if cfg.get('SINCRONO'):
            _post(*args)  # pruebas automáticas
        else:
            threading.Thread(target=_post, args=args, daemon=True).start()
    except Exception as e:  # noqa: BLE001
        log.warning('Error preparando evento de auditoría: %s', e)


def desde_bitacora(accion, obj, *, request=None, usuario=None, descripcion=''):
    """Reenvía al microservicio lo que se registra en la bitácora de Django."""
    tipo = TIPOS.get(accion, accion)
    clase = type(obj).__name__ if obj is not None else None
    if clase == 'Venta':
        tipo = {'CREAR': 'VENTA_REGISTRADA', 'ANULAR': 'VENTA_ANULADA'}.get(accion, tipo)
    enviar_evento(
        tipo, descripcion or (str(obj) if obj is not None else accion),
        request=request, usuario=usuario,
        modulo=obj._meta.app_label if obj is not None else None,
        entidad=clase, entidad_id=getattr(obj, 'pk', None),
    )

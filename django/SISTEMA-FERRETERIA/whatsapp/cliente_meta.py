"""
FER-52 - Envío de mensajes por la WhatsApp Cloud API oficial de Meta.
Sin credenciales (WHATSAPP_TOKEN / WHATSAPP_PHONE_NUMBER_ID) funciona en modo SIMULADO.
Se usa urllib para no añadir dependencias.
"""
import hashlib
import hmac
import json
import logging
import urllib.error
import urllib.request

from django.conf import settings

log = logging.getLogger('ferreteria.whatsapp')


def credenciales_configuradas():
    cfg = settings.WHATSAPP
    return bool(cfg['TOKEN'] and cfg['PHONE_NUMBER_ID'])


def enviar_texto(telefono, texto):
    """Devuelve (estado, wa_message_id, error). estado: ENVIADO | SIMULADO | ERROR"""
    if not credenciales_configuradas():
        log.info('[WhatsApp SIMULADO] -> %s: %s', telefono, texto.replace('\n', ' | '))
        return 'SIMULADO', None, ''
    cfg = settings.WHATSAPP
    url = f'https://graph.facebook.com/{cfg["API_VERSION"]}/{cfg["PHONE_NUMBER_ID"]}/messages'
    cuerpo = json.dumps({
        'messaging_product': 'whatsapp', 'recipient_type': 'individual', 'to': telefono,
        'type': 'text', 'text': {'preview_url': False, 'body': texto[:4096]},
    }).encode()
    req = urllib.request.Request(url, data=cuerpo, method='POST', headers={
        'Authorization': f'Bearer {cfg["TOKEN"]}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            datos = json.loads(resp.read().decode())
            return 'ENVIADO', (datos.get('messages') or [{}])[0].get('id'), ''
    except urllib.error.HTTPError as e:
        detalle = e.read().decode(errors='ignore')[:250]
        log.warning('WhatsApp HTTP %s: %s', e.code, detalle)
        return 'ERROR', None, f'HTTP {e.code}: {detalle}'
    except Exception as e:  # noqa: BLE001 - red caída, timeout, etc.
        log.warning('WhatsApp error: %s', e)
        return 'ERROR', None, str(e)[:250]


def firma_valida(cuerpo_bytes, cabecera_firma):
    """Verifica X-Hub-Signature-256 si se configuró WHATSAPP_APP_SECRET."""
    secreto = settings.WHATSAPP['APP_SECRET']
    if not secreto:
        return True  # en desarrollo no se exige
    if not cabecera_firma or not cabecera_firma.startswith('sha256='):
        return False
    esperado = hmac.new(secreto.encode(), cuerpo_bytes, hashlib.sha256).hexdigest()
    return hmac.compare_digest(esperado, cabecera_firma.split('=', 1)[1])

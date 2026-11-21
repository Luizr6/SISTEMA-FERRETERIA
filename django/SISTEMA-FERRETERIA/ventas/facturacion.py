"""
Cliente del servicio de facturación en Spring Boot (ferreteria-reportes-ms).

Django registra la venta (stock, caja, número de serie). Spring la "factura": arma el XML UBL 2.1,
calcula el hash, genera el QR y la representación impresa (A5 o ticket de 80 mm).

Si Spring no está configurado o no responde, estas funciones devuelven None y quien las llama usa
el respaldo local, así ninguna venta ni ningún comprobante se queda sin atender.
"""
import logging
import urllib.error
import urllib.request

from django.conf import settings

log = logging.getLogger('ferreteria')


def _base():
    return (getattr(settings, 'MS_REPORTES', {}) or {}).get('URL') or ''


def pdf_comprobante(venta_id, formato, autorizacion):
    """PDF del comprobante generado por Spring, o None si no se pudo (Spring apagado, error, etc.)."""
    base = _base()
    if not base or not autorizacion:
        return None
    formato = 'TICKET' if str(formato).upper() == 'TICKET' else 'A5'
    url = f'{base}/comprobantes/venta/{int(venta_id)}/pdf?formato={formato}'
    req = urllib.request.Request(url, headers={'Authorization': autorizacion, 'Accept': 'application/pdf'})
    timeout = settings.MS_REPORTES.get('TIMEOUT', 8)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            datos = resp.read()
            if resp.status == 200 and datos[:4] == b'%PDF':
                return datos
    except urllib.error.HTTPError as e:
        log.warning('Facturación (Spring) respondió %s para la venta %s', e.code, venta_id)
    except Exception as e:  # noqa: BLE001 - caído, timeout, DNS…
        log.warning('Facturación (Spring) no disponible: %s', e)
    return None

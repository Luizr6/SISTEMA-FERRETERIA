"""
Horario de atención de la tienda y pedido de asesor por WhatsApp.

Cuando el cliente pide hablar con una persona:
- Dentro del horario: el bot se pausa en ese chat y avisa que un asesor le responderá. Si ningún
  vendedor contesta en 15 minutos, el bot vuelve a atender (para que el cliente no quede esperando).
- Fuera del horario: el bot sigue atendiendo (precios, stock, pedidos) y el chat queda marcado como
  "pidió asesor" para que lo atiendan al abrir.
"""
import re
import unicodedata
from datetime import time, timedelta

from django.conf import settings
from django.utils import timezone

ESPERA_ASESOR = timedelta(minutes=15)

_PIDE_ASESOR = re.compile(
    r'\b(asesor(a|es)?|vendedor(a)?|encargad[oa]|humano|operador(a)?|persona real|una persona'
    r'|alguien (real|que me atienda)|hablar con (alguien|usted|ustedes|el dueno|la duena)'
    r'|atencion personal(izada)?|me atienda (alguien|una persona)|quiero que me llamen|llamenme)\b'
)


def _normalizar(texto):
    t = unicodedata.normalize('NFKD', texto or '').encode('ascii', 'ignore').decode().lower()
    return re.sub(r'\s+', ' ', t)


def pide_asesor(texto):
    return bool(_PIDE_ASESOR.search(_normalizar(texto)))


def _hora(texto, defecto):
    try:
        h, m = (int(x) for x in str(texto).strip().split(':'))
        return time(h, m)
    except (TypeError, ValueError):
        return defecto


def _dias(texto):
    """'0-5' (lunes a sábado) o '0,1,2,3,4' -> {0,1,...}. 0 = lunes, 6 = domingo."""
    dias = set()
    for parte in str(texto or '0-5').split(','):
        parte = parte.strip()
        if '-' in parte:
            a, b = parte.split('-', 1)
            if a.strip().isdigit() and b.strip().isdigit():
                dias.update(range(int(a), int(b) + 1))
        elif parte.isdigit():
            dias.add(int(parte))
    return dias or {0, 1, 2, 3, 4, 5}


def en_horario(momento=None):
    cfg = settings.FERRETERIA
    ahora = timezone.localtime(momento or timezone.now())
    abre = _hora(cfg.get('HORA_APERTURA'), time(8, 0))
    cierra = _hora(cfg.get('HORA_CIERRE'), time(19, 0))
    return ahora.weekday() in _dias(cfg.get('DIAS_ATENCION')) and abre <= ahora.time() < cierra


def debe_reactivar(conv, momento=None):
    """El bot se pausó solo y ningún vendedor respondió en 15 minutos."""
    ahora = momento or timezone.now()
    return (not conv.bot_activo and conv.pausa_automatica and conv.asesor_solicitado is not None
            and ahora - conv.asesor_solicitado >= ESPERA_ASESOR)


def mensaje_asesor(en_atencion):
    horario = settings.FERRETERIA.get('HORARIO', '')
    if en_atencion:
        return ('Claro, le comunico con un asesor. En unos minutos le responde por este mismo chat. '
                'Si prefiere, mientras tanto puede seguir escribiéndome su consulta.')
    return (f'En este momento estamos fuera del horario de atención ({horario}). '
            'Dejé su pedido de asesor anotado y le escribiremos apenas abramos. '
            'Mientras tanto le puedo ayudar con precios, stock y pedidos: ¿qué está buscando?')

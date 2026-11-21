"""
Cliente de IA (RF30) - Groq, API compatible con OpenAI.

Lo usan:
  * whatsapp/bot_ia.py   -> entender al cliente y redactar la respuesta.
  * asistente/services.py -> resumen y consultas del Administrador.

Regla del sistema: la IA NO es fuente de datos. Precios, stock y ventas salen siempre de la base
de datos (Django se los entrega); la IA solo entiende la intención y redacta.

Se usa urllib para no añadir dependencias. Sin GROQ_API_KEY, `disponible()` es False y quien la
llama usa su versión sin IA (bot por reglas / resumen básico).
"""
import json
import logging
import re
import time
import urllib.error
import urllib.request

from django.conf import settings

log = logging.getLogger('ferreteria.ia')


class IAError(Exception):
    """Cualquier fallo de la IA (sin clave, red, cupo agotado, respuesta inválida)."""


class _LimiteAlcanzado(IAError):
    """HTTP 429: se pasó el límite gratuito por minuto (o por día) de ese modelo."""

    def __init__(self, mensaje, espera):
        super().__init__(mensaje)
        self.espera = espera


# modelo -> momento (time.time()) hasta el que no se le vuelve a llamar por haber dado 429
_pausado_hasta = {}
# Si todos los modelos están en su límite por minuto y Groq pide esperar hasta esto, se espera y se
# reintenta (el webhook ya respondió a Meta; el cliente solo nota unos segundos más de demora).
ESPERA_MAXIMA = 15


def disponible():
    return bool(settings.IA.get('API_KEY'))


def modelo():
    return settings.IA.get('MODELO')


def _es_gpt_oss(nombre):
    return (nombre or '').startswith('openai/gpt-oss')


def _modelos():
    cfg = settings.IA
    lista = [cfg['MODELO']]
    respaldo = cfg.get('MODELO_RESPALDO')
    if respaldo and respaldo != cfg['MODELO']:
        lista.append(respaldo)
    return lista


def _segundos(valor, defecto=20.0):
    """'retry-after' puede venir como '7', '7.5' o '1m30s'."""
    if not valor:
        return defecto
    try:
        return float(valor)
    except ValueError:
        m = re.fullmatch(r'(?:(\d+)m)?(?:([\d.]+)s)?', valor.strip())
        if m and (m.group(1) or m.group(2)):
            return int(m.group(1) or 0) * 60 + float(m.group(2) or 0)
        return defecto


def chat(mensajes, *, herramientas=None, max_tokens=1500, temperatura=0.6):
    """
    Llama a /chat/completions y devuelve el mensaje del asistente (dict con 'content' y/o 'tool_calls').

    El plan gratuito de Groq limita los tokens por minuto de cada modelo. Si el modelo principal
    responde 429: se espera unos segundos y se reintenta, o se pasa al modelo de respaldo (que tiene
    su propio cupo). Así el cliente sigue atendido por la IA en vez de caer al bot por reglas.
    Lanza IAError si ningún modelo pudo responder, para que el llamador use su respaldo.
    """
    if not settings.IA.get('API_KEY'):
        raise IAError('IA no configurada (falta GROQ_API_KEY).')

    error = IAError('No se pudo conectar con la IA.')
    for vuelta in range(2):
        for nombre in _modelos():
            if vuelta == 0 and _pausado_hasta.get(nombre, 0) > time.time():
                error = IAError('Se alcanzó el límite gratuito de la IA; se usa el modo básico.')
                continue
            try:
                return _llamar(nombre, mensajes, herramientas, max_tokens, temperatura)
            except _LimiteAlcanzado as e:
                error = e
                _pausado_hasta[nombre] = time.time() + min(max(e.espera, 1), 300)
                log.warning('IA: %s en pausa %.0f s por límite gratuito', nombre, e.espera)
            except IAError as e:
                if getattr(e, 'definitivo', False):
                    raise
                error = e          # red caída, timeout o error del servidor: se prueba el otro modelo
        if vuelta == 1:
            break
        # Todos en su límite: si el más próximo se libera pronto, se espera y se intenta una vez más
        pausas = [_pausado_hasta.get(n, 0) for n in _modelos()]
        restante = min(pausas) - time.time()
        if not all(p > time.time() for p in pausas) or restante > ESPERA_MAXIMA:
            break
        log.warning('IA: todos los modelos en su límite; se espera %.0f s', restante)
        time.sleep(max(restante, 0) + 0.3)
    raise error


def _llamar(modelo_nombre, mensajes, herramientas, max_tokens, temperatura):
    cfg = settings.IA
    cuerpo = {
        'model': modelo_nombre,
        'messages': mensajes,
        'temperature': temperatura,
        'top_p': 0.95,
        # incluye el razonamiento interno del modelo: hay que dejarle margen
        'max_completion_tokens': max_tokens,
    }
    if _es_gpt_oss(modelo_nombre):
        # Groq recomienda temperatura entre 0.5 y 0.7 para gpt-oss: fuera de ese rango el modelo
        # puede repetirse o escribir texto incoherente.
        cuerpo['temperature'] = min(max(temperatura, 0.5), 0.7)
        cuerpo['reasoning_effort'] = 'low'      # respuestas rápidas para chat
        cuerpo['include_reasoning'] = False     # solo la respuesta final
    if herramientas:
        cuerpo['tools'] = herramientas

    req = urllib.request.Request(
        cfg['URL'], data=json.dumps(cuerpo).encode(), method='POST',
        headers={
            'Authorization': f'Bearer {cfg["API_KEY"]}',
            'Content-Type': 'application/json',
            # Algunos proxies/CDN rechazan el User-Agent por defecto de urllib
            'User-Agent': 'ferreteria-cr/1.0',
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=cfg.get('TIMEOUT', 20)) as resp:
            datos = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        detalle = e.read().decode(errors='ignore')[:300]
        log.warning('IA HTTP %s (%s): %s', e.code, modelo_nombre, detalle)
        if e.code == 429:
            raise _LimiteAlcanzado('Se alcanzó el límite gratuito de la IA; se usa el modo básico.',
                                   _segundos(e.headers.get('retry-after') if e.headers else None)) from e
        if e.code in (401, 403):
            err = IAError('La clave GROQ_API_KEY no es válida.')
            err.definitivo = True
            raise err from e
        raise IAError(f'IA respondió HTTP {e.code}.') from e
    except Exception as e:  # noqa: BLE001 - red caída, timeout, DNS, etc.
        log.warning('IA sin respuesta: %s', e)
        raise IAError('No se pudo conectar con la IA.') from e

    try:
        return datos['choices'][0]['message']
    except (KeyError, IndexError, TypeError) as e:
        raise IAError('Respuesta de la IA con formato inesperado.') from e


def texto_valido(texto):
    """
    Detecta respuestas degeneradas (puntos suspensivos sin fin, texto en inglés mezclado, demasiado
    largas). Si no es válida, el llamador usa su respuesta de respaldo en vez de enviarla al cliente.
    """
    t = (texto or '').strip()
    if not t or len(t) > 1500:
        return False
    if re.search(r'(?:[.…]\s*){6,}', t):                     # "………" o "......" repetidos
        return False
    letras = sum(c.isalpha() for c in t)
    if letras < 0.55 * len(t.replace(' ', '').replace('\n', '')):
        return False
    if re.search(r'\b(?:Okay|Probably|It looks|Sorry|We need|Let me|I think|The user)\b', t):
        return False
    if re.search(r'contin[uú]a\s*[…)]|\(contin', t, re.IGNORECASE):   # "... (continúa…)" cortado
        return False
    if re.search(r'\b(\w{3,})\s+\1\b', t, re.IGNORECASE):              # "negro negro"
        return False
    # frases trabadas: "¿ desea ?", "¿ se refería", "disponible¿" sin pregunta
    if re.search(r'¿\s|\s\?|¿[^?\n]{0,3}\?', t):
        return False
    if t.count('¿') > t.count('?'):          # pregunta abierta y nunca cerrada
        return False
    return True


def formato_whatsapp(texto):
    """WhatsApp usa *negrita* (un asterisco). Los modelos a veces escriben **negrita** o encabezados #."""
    texto = (texto or '').strip().replace('**', '*').replace('__', '_')
    lineas = [ln.lstrip('#').strip() if ln.lstrip().startswith('#') else ln for ln in texto.splitlines()]
    return '\n'.join(lineas)[:4000]

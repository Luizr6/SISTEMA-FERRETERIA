"""Orquestación: mensaje entrante -> guardar -> bot (IA o reglas) -> responder (CU14-CU16)."""
from django.db import IntegrityError, transaction
from django.db.models import F
from django.utils import timezone

from ventas.models import Cliente
from . import bot_ia, cliente_meta, horario
from .models import Conversacion, Mensaje


def _conversacion(telefono, nombre=''):
    conv, creada = Conversacion.objects.get_or_create(telefono=telefono, defaults={'nombre': nombre})
    if nombre and conv.nombre != nombre:
        conv.nombre = nombre
        conv.save(update_fields=['nombre'])
    if conv.cliente_id is None:
        sufijo = telefono[-9:]
        cliente = Cliente.objects.filter(telefono__endswith=sufijo).first() if len(sufijo) == 9 else None
        if cliente:
            conv.cliente = cliente
            conv.save(update_fields=['cliente'])
    return conv


def enviar(conversacion, texto, *, intencion='MANUAL', pedido=None, usuario=None):
    estado, wa_id, error = cliente_meta.enviar_texto(conversacion.telefono, texto)
    msg = Mensaje.objects.create(conversacion=conversacion, direccion='SALIENTE', texto=texto,
                                 wa_message_id=wa_id, intencion=intencion, estado_envio=estado,
                                 error=error, pedido=pedido, enviado_por=usuario)
    cambios = {'ultimo_mensaje': timezone.now()}
    if usuario is not None:
        # Respondió una persona: el pedido de asesor quedó atendido y la pausa ya no se levanta sola
        cambios.update(asesor_solicitado=None, pausa_automatica=False)
    Conversacion.objects.filter(pk=conversacion.pk).update(**cambios)
    return msg


def procesar_entrante(*, telefono, texto, nombre='', wa_message_id=None):
    """Devuelve (mensaje_entrante, mensaje_respuesta | None). Idempotente por wa_message_id."""
    if wa_message_id and Mensaje.objects.filter(wa_message_id=wa_message_id).exists():
        return None, None  # Meta reintenta webhooks: no procesar dos veces
    with transaction.atomic():
        conv = _conversacion(telefono, nombre)
        try:
            entrante = Mensaje.objects.create(conversacion=conv, direccion='ENTRANTE', texto=texto,
                                              wa_message_id=wa_message_id, estado_envio='RECIBIDO')
        except IntegrityError:
            return None, None
        Conversacion.objects.filter(pk=conv.pk).update(ultimo_mensaje=timezone.now(), no_leidos=F('no_leidos') + 1)

    conv.refresh_from_db(fields=['bot_activo', 'pausa_automatica', 'asesor_solicitado'])
    ahora = timezone.now()

    # Se pausó solo porque pidieron asesor y nadie respondió en 15 minutos: el bot vuelve a atender
    if horario.debe_reactivar(conv, ahora):
        conv.bot_activo, conv.pausa_automatica = True, False
        conv.save(update_fields=['bot_activo', 'pausa_automatica'])

    if not conv.bot_activo:
        return entrante, None

    # El cliente pide hablar con una persona
    if horario.pide_asesor(texto):
        atiende = horario.en_horario(ahora)
        conv.asesor_solicitado = ahora
        campos = ['asesor_solicitado']
        if atiende:  # dentro del horario: el bot se calla y espera al vendedor
            conv.bot_activo, conv.pausa_automatica = False, True
            campos += ['bot_activo', 'pausa_automatica']
        conv.save(update_fields=campos)
        entrante.intencion = 'ASESOR'
        entrante.save(update_fields=['intencion'])
        return entrante, enviar(conv, horario.mensaje_asesor(atiende), intencion='ASESOR')

    # IA si hay GROQ_API_KEY (entiende lenguaje natural); si no o si falla, el bot por reglas
    resp = bot_ia.responder(texto, telefono=telefono, nombre=nombre or conv.nombre,
                            conversacion=conv, excluir_id=entrante.pk)
    entrante.intencion = resp.intencion
    entrante.pedido = resp.pedido
    entrante.save(update_fields=['intencion', 'pedido'])
    salida = enviar(conv, resp.texto, intencion=resp.intencion, pedido=resp.pedido)
    return entrante, salida


def procesar_webhook(payload):
    """Extrae los mensajes de texto del formato de la Cloud API. Devuelve cuántos procesó."""
    procesados = 0
    for entry in payload.get('entry', []):
        for change in entry.get('changes', []):
            value = change.get('value', {})
            nombres = {c.get('wa_id'): c.get('profile', {}).get('name', '') for c in value.get('contacts', [])}
            for m in value.get('messages', []):
                telefono = m.get('from', '')
                if m.get('type') == 'text':
                    texto = m.get('text', {}).get('body', '')
                elif m.get('type') == 'interactive':
                    inter = m.get('interactive', {})
                    texto = (inter.get('button_reply') or inter.get('list_reply') or {}).get('title', '')
                else:
                    texto = f'[{m.get("type", "mensaje")} no soportado]'
                if telefono and texto:
                    procesar_entrante(telefono=telefono, texto=texto, nombre=nombres.get(telefono, ''),
                                      wa_message_id=m.get('id'))
                    procesados += 1
            # value.statuses (entregado / leído) se ignoran en esta versión
    return procesados

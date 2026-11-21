"""El cliente pide un asesor: pausa dentro del horario, marca fuera del horario, vuelve el bot a los 15 min."""
from datetime import datetime, timedelta
from unittest import mock
from zoneinfo import ZoneInfo

from rest_framework.test import APITestCase

from cuentas.models import Usuario
from whatsapp import horario, services
from whatsapp.models import Conversacion, Mensaje

LIMA = ZoneInfo('America/Lima')
MARTES_10AM = datetime(2026, 11, 17, 10, 0, tzinfo=LIMA)
MARTES_10PM = datetime(2026, 11, 17, 22, 0, tzinfo=LIMA)
DOMINGO_10AM = datetime(2026, 11, 22, 10, 0, tzinfo=LIMA)


class AsesorTests(APITestCase):

    def _escribe(self, texto, cuando, wa_id):
        with mock.patch('whatsapp.services.timezone.now', return_value=cuando):
            return services.procesar_entrante(telefono='51999111222', texto=texto, nombre='Juan', wa_message_id=wa_id)

    def test_detecta_pedido_de_asesor(self):
        for t in ['Quiero hablar con un asesor', 'me atiende una persona?', 'pásame con el vendedor', 'ASESORA porfa']:
            self.assertTrue(horario.pide_asesor(t), t)
        for t in ['precio del martillo', 'tienen cemento sol', 'hola buenas']:
            self.assertFalse(horario.pide_asesor(t), t)

    def test_horario(self):
        self.assertTrue(horario.en_horario(MARTES_10AM))
        self.assertFalse(horario.en_horario(MARTES_10PM))
        self.assertFalse(horario.en_horario(DOMINGO_10AM))

    def test_en_horario_pausa_el_bot_y_vuelve_a_los_15_minutos(self):
        _, salida = self._escribe('quiero hablar con un asesor', MARTES_10AM, 'w1')
        conv = Conversacion.objects.get()
        self.assertFalse(conv.bot_activo)
        self.assertTrue(conv.pausa_automatica)
        self.assertIn('asesor', salida.texto)
        # a los 5 minutos el bot sigue callado
        _, salida = self._escribe('hola?', MARTES_10AM + timedelta(minutes=5), 'w2')
        self.assertIsNone(salida)
        # a los 16 minutos, sin respuesta de nadie, vuelve a atender
        _, salida = self._escribe('hola', MARTES_10AM + timedelta(minutes=16), 'w3')
        conv.refresh_from_db()
        self.assertTrue(conv.bot_activo)
        self.assertIsNotNone(salida)

    def test_si_responde_un_vendedor_la_pausa_ya_no_se_levanta_sola(self):
        self._escribe('quiero hablar con un asesor', MARTES_10AM, 'w1')
        conv = Conversacion.objects.get()
        vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        services.enviar(conv, 'Hola Juan, soy Rosa. ¿En qué le ayudo?', usuario=vend)
        conv.refresh_from_db()
        self.assertIsNone(conv.asesor_solicitado)
        self.assertFalse(conv.pausa_automatica)
        _, salida = self._escribe('gracias', MARTES_10AM + timedelta(minutes=30), 'w2')
        self.assertIsNone(salida)  # sigue atendiendo la persona

    def test_fuera_de_horario_el_bot_sigue_y_queda_marcado(self):
        _, salida = self._escribe('necesito un asesor', MARTES_10PM, 'w1')
        conv = Conversacion.objects.get()
        self.assertTrue(conv.bot_activo)
        self.assertIsNotNone(conv.asesor_solicitado)
        self.assertIn('fuera del horario', salida.texto)
        self.assertEqual(Mensaje.objects.filter(intencion='ASESOR').count(), 2)

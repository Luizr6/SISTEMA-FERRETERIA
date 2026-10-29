from decimal import Decimal

from django.test import override_settings
from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Pedido
from whatsapp.models import Conversacion, Mensaje


def payload(texto, telefono='51999111222', wa_id='wamid.1', nombre='Juan'):
    return {'object': 'whatsapp_business_account', 'entry': [{'id': '1', 'changes': [{'field': 'messages', 'value': {
        'messaging_product': 'whatsapp',
        'contacts': [{'wa_id': telefono, 'profile': {'name': nombre}}],
        'messages': [{'from': telefono, 'id': wa_id, 'type': 'text', 'text': {'body': texto}}]}}]}]}


class WhatsAppTests(APITestCase):
    """Sprint 7 (base): webhook (FER-51), API Meta (FER-52), motor de consultas (FER-53), panel (FER-54)."""

    def setUp(self):
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Herramientas manuales')
        self.p = Producto.objects.create(codigo='MAR-001', nombre='Martillo de uña 16 oz', categoria=cat,
                                         precio_compra=18, precio_venta=Decimal('32.00'), stock_actual=25)
        Producto.objects.create(codigo='TAL-001', nombre='Taladro percutor', categoria=cat,
                                precio_compra=190, precio_venta=289, stock_actual=0)

    @override_settings(WHATSAPP={'TOKEN': '', 'PHONE_NUMBER_ID': '', 'VERIFY_TOKEN': 'abc',
                                 'APP_SECRET': '', 'API_VERSION': 'v21.0'})
    def test_verificacion_webhook(self):
        r = self.client.get('/api/whatsapp/webhook/?hub.mode=subscribe&hub.verify_token=abc&hub.challenge=777')
        self.assertEqual((r.status_code, r.content), (200, b'777'))
        r = self.client.get('/api/whatsapp/webhook/?hub.mode=subscribe&hub.verify_token=mal&hub.challenge=777')
        self.assertEqual(r.status_code, 403)

    def test_consulta_de_precio_responde_con_datos_reales(self):
        r = self.client.post('/api/whatsapp/webhook/', payload('Hola, cuánto cuesta el martillo?'), format='json')
        self.assertEqual(r.data['procesados'], 1)
        resp = Mensaje.objects.filter(direccion='SALIENTE').get()
        self.assertEqual(resp.intencion, 'CONSULTA')
        self.assertIn('MAR-001', resp.texto)
        self.assertIn('37.76', resp.texto)  # 32.00 + 18% IGV
        self.assertEqual(resp.estado_envio, 'SIMULADO')
        # Meta reintenta el mismo mensaje: no se duplica
        self.client.post('/api/whatsapp/webhook/', payload('Hola, cuánto cuesta el martillo?'), format='json')
        self.assertEqual(Mensaje.objects.count(), 2)

    def test_pedido_por_chat_queda_pendiente_sin_tocar_stock(self):
        self.client.post('/api/whatsapp/webhook/', payload('quiero 3 MAR-001', wa_id='w2'), format='json')
        pedido = Pedido.objects.get()
        self.assertEqual((pedido.origen, pedido.estado, pedido.contacto_telefono), ('WHATSAPP', 'PENDIENTE', '51999111222'))
        self.p.refresh_from_db()
        self.assertEqual(self.p.stock_actual, 25)
        self.client.post('/api/whatsapp/webhook/', payload(f'estado {pedido.numero}', wa_id='w3'), format='json')
        self.assertIn('pendiente', Mensaje.objects.filter(direccion='SALIENTE').last().texto)

    def test_mensaje_no_entendido_pide_reformular(self):
        self.client.post('/api/whatsapp/webhook/', payload('asdfgh qwerty', wa_id='w4'), format='json')
        self.assertEqual(Mensaje.objects.filter(direccion='SALIENTE').get().intencion, 'DESCONOCIDA')

    def test_simulador_y_panel(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/whatsapp/simular/', {'telefono': '51988777666', 'nombre': 'Ana', 'texto': 'stock taladro'},
                             format='json')
        self.assertIn('agotado', r.data['respuesta']['texto'])
        conv = self.client.get('/api/whatsapp/conversaciones/').data[0]
        self.assertEqual((conv['nombre'], conv['no_leidos']), ('Ana', 1))
        msgs = self.client.get(f"/api/whatsapp/conversaciones/{conv['id']}/mensajes/").data
        self.assertEqual(len(msgs), 2)
        self.assertEqual(Conversacion.objects.get().no_leidos, 0)
        r = self.client.post(f"/api/whatsapp/conversaciones/{conv['id']}/responder/", {'texto': 'Llega el lunes'}, format='json')
        self.assertEqual(r.data['intencion'], 'MANUAL')
        self.assertEqual(self.client.get('/api/whatsapp/estado/').data['modo'], 'SIMULADO')

    @override_settings(WHATSAPP={'TOKEN': '', 'PHONE_NUMBER_ID': '', 'VERIFY_TOKEN': 'abc',
                                 'APP_SECRET': 'secreto', 'API_VERSION': 'v21.0'})
    def test_firma_invalida_rechazada(self):
        r = self.client.post('/api/whatsapp/webhook/', payload('hola'), format='json',
                             HTTP_X_HUB_SIGNATURE_256='sha256=000')
        self.assertEqual(r.status_code, 403)

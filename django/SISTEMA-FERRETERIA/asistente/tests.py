from decimal import Decimal
from unittest import mock

from django.test import override_settings
from rest_framework.test import APITestCase

from asistente import ia
from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Pedido

IA_ON = {'API_KEY': 'test', 'MODELO': 'modelo-test', 'URL': 'http://ia.test', 'TIMEOUT': 5, 'HISTORIAL': 10}


class AsistenteTests(APITestCase):

    def setUp(self):
        self.admin = Usuario.objects.create_user('admin1', password='x', rol='ADMIN')
        self.vend = Usuario.objects.create_user('vend1', password='x', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Construcción')
        self.cemento = Producto.objects.create(codigo='CEM-001', nombre='Cemento Sol 42.5 kg', categoria=cat,
                                               precio_compra=25, precio_venta=Decimal('32.00'),
                                               stock_actual=0, stock_minimo=10)
        Producto.objects.create(codigo='CLA-001', nombre='Clavo 2"', categoria=cat, precio_compra=1,
                                precio_venta=2, stock_actual=3, stock_minimo=5)
        Pedido.objects.create(origen='WHATSAPP', contacto_nombre='María', contacto_telefono='51999')

    def test_solo_admin(self):
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.get('/api/asistente/').status_code, 403)
        self.assertEqual(self.client.get('/api/asistente/alertas/').status_code, 403)

    def test_alertas_para_notificaciones(self):
        self.client.force_authenticate(self.admin)
        r = self.client.get('/api/asistente/alertas/')
        tipos = {a['tipo'] for a in r.data['alertas']}
        self.assertTrue({'STOCK_AGOTADO', 'STOCK_BAJO', 'PEDIDOS_PENDIENTES'} <= tipos)
        self.assertTrue(all(a['id'] for a in r.data['alertas']))

    @override_settings(IA={**IA_ON, 'API_KEY': ''})
    def test_resumen_basico_sin_ia(self):
        self.client.force_authenticate(self.admin)
        r = self.client.get('/api/asistente/')
        self.assertEqual(r.data['modo'], 'BASICO')
        self.assertIn('Cemento Sol', r.data['respuesta'])
        self.assertIn('pedido(s) por confirmar', r.data['respuesta'])

    @override_settings(IA=IA_ON)
    def test_resumen_con_ia_usa_solo_datos_reales(self):
        self.client.force_authenticate(self.admin)
        capturado = {}

        def falsa_ia(mensajes, **kw):
            capturado['m'] = mensajes
            return {'content': '• **Repón** Cemento Sol hoy.'}

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            r = self.client.post('/api/asistente/', {'pregunta': '¿Qué me falta comprar?'}, format='json')
        self.assertEqual(r.data['modo'], 'IA')
        self.assertEqual(r.data['respuesta'], '• *Repón* Cemento Sol hoy.')
        enviado = capturado['m'][-1]['content']
        self.assertIn('CEM-001', enviado)              # la IA recibe los datos de la BD
        self.assertIn('¿Qué me falta comprar?', enviado)

    @override_settings(IA=IA_ON)
    def test_si_la_ia_falla_devuelve_resumen_basico(self):
        self.client.force_authenticate(self.admin)
        with mock.patch.object(ia, 'chat', side_effect=ia.IAError('límite')):
            r = self.client.get('/api/asistente/')
        self.assertEqual(r.data['modo'], 'BASICO')
        self.assertIn('aviso', r.data)


class PaginasLegalesTests(APITestCase):
    """Meta exige una política de privacidad y una página de eliminación de datos públicas."""

    def test_paginas_publicas(self):
        for url, texto in (('/privacidad/', 'Política de privacidad'), ('/eliminacion-datos/', 'ELIMINAR MIS DATOS')):
            r = self.client.get(url)
            self.assertEqual(r.status_code, 200)
            self.assertIn(texto, r.content.decode())

from decimal import Decimal
from unittest import mock

from django.test import override_settings
from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Pedido
from ventas import services as ventas_services

MS = {'URL': 'http://ms.prueba/api/auditoria/eventos', 'API_KEY': 'clave-x', 'TIMEOUT': 1, 'SINCRONO': True}


@override_settings(MS_AUDITORIA=MS)
class EnvioAlMicroservicioTests(APITestCase):
    """Django -> microservicio Spring Boot de auditoría (los envíos se capturan, no salen a la red)."""

    def setUp(self):
        self.admin = Usuario.objects.create_user('admin', password='x12345', rol='ADMIN')
        cat = Categoria.objects.create(nombre='Herramientas')
        self.p = Producto.objects.create(codigo='A1', nombre='Martillo', categoria=cat,
                                         precio_compra=10, precio_venta=Decimal('20.00'), stock_actual=10)
        parche = mock.patch('auditoria.ms_cliente._post', return_value=201)
        self.post = parche.start()
        self.addCleanup(parche.stop)

    def _eventos(self):
        return [c.args[2] for c in self.post.call_args_list]

    def test_login_fallido_envia_evento_sin_contrasena(self):
        r = self.client.post('/api/auth/login/', {'username': 'admin', 'password': 'mala'}, format='json')
        self.assertEqual(r.status_code, 401)
        ev = self._eventos()[-1]
        self.assertEqual((ev['tipo'], ev['usuario'], ev['origen']), ('LOGIN_FALLIDO', 'admin', 'DJANGO'))
        self.assertNotIn('mala', str(ev))
        url, api_key = self.post.call_args.args[:2]
        self.assertEqual((url, api_key), (MS['URL'], 'clave-x'))

    def test_login_exitoso(self):
        self.client.post('/api/auth/login/', {'username': 'admin', 'password': 'x12345'}, format='json')
        self.assertIn('LOGIN_EXITOSO', [e['tipo'] for e in self._eventos()])

    def test_cambio_de_precio(self):
        self.client.force_authenticate(self.admin)
        r = self.client.patch(f'/api/productos/{self.p.id}/', {'precio_venta': '22.50'}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        ev = next(e for e in self._eventos() if e['tipo'] == 'PRECIO_MODIFICADO')
        self.assertEqual(ev['datos']['precio_venta_antes'], '20.00')
        self.assertEqual(ev['datos']['precio_venta_despues'], '22.50')
        self.assertEqual((ev['usuario'], ev['entidad'], ev['entidad_id']), ('admin', 'Producto', str(self.p.id)))

    def test_editar_sin_cambiar_precio_no_envia_precio_modificado(self):
        self.client.force_authenticate(self.admin)
        self.client.patch(f'/api/productos/{self.p.id}/', {'nombre': 'Martillo grande'}, format='json')
        self.assertNotIn('PRECIO_MODIFICADO', [e['tipo'] for e in self._eventos()])

    def test_aprobar_pedido(self):
        pedido = ventas_services.crear_pedido(detalles=[{'producto': self.p, 'cantidad': 1}], contacto_nombre='Ana')
        self.client.force_authenticate(self.admin)
        self.client.post(f'/api/pedidos/{pedido.id}/aprobar/', {}, format='json')
        self.assertEqual(Pedido.objects.get(pk=pedido.pk).estado, 'APROBADO')
        ev = next(e for e in self._eventos() if e['tipo'] == 'PEDIDO_APROBADO')
        self.assertEqual((ev['entidad'], ev['modulo']), ('Pedido', 'ventas'))

    def test_microservicio_caido_no_rompe_la_venta(self):
        self.post.side_effect = OSError('conexión rechazada')
        self.client.force_authenticate(self.admin)
        r = self.client.post('/api/ventas/', {'detalles': [{'producto': self.p.id, 'cantidad': 1}]}, format='json')
        self.assertEqual(r.status_code, 201)

    @override_settings(MS_AUDITORIA={'URL': ''})
    def test_sin_url_no_envia_nada(self):
        self.client.post('/api/auth/login/', {'username': 'admin', 'password': 'x12345'}, format='json')
        self.post.assert_not_called()

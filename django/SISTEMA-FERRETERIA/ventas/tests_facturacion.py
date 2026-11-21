"""
Facturación en Spring Boot: Django pide el comprobante a Spring y, si no responde, entrega su PDF interno.
Se usa un servidor HTTP falso en un hilo para simular a Spring.
"""
import threading
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, HTTPServer

from django.test import override_settings
from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Cliente


class _SpringFalso(BaseHTTPRequestHandler):
    recibido = {}

    def do_GET(self):  # noqa: N802
        _SpringFalso.recibido = {'ruta': self.path, 'auth': self.headers.get('Authorization')}
        cuerpo = b'%PDF-1.4 comprobante de Spring'
        self.send_response(200)
        self.send_header('Content-Type', 'application/pdf')
        self.send_header('Content-Length', str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def log_message(self, *args):  # silencio en las pruebas
        pass


class FacturacionTests(APITestCase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.servidor = HTTPServer(('127.0.0.1', 0), _SpringFalso)
        cls.puerto = cls.servidor.server_address[1]
        threading.Thread(target=cls.servidor.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.servidor.shutdown()
        cls.servidor.server_close()
        super().tearDownClass()

    def setUp(self):
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Construcción')
        self.p = Producto.objects.create(codigo='C1', nombre='Cemento', categoria=cat, precio_compra=20,
                                         precio_venta=Decimal('25.00'), stock_actual=50)
        self.client.force_authenticate(self.vend)
        self.client.credentials(HTTP_AUTHORIZATION='Bearer token-de-prueba')

    def _vender(self, cliente=None):
        datos = {'detalles': [{'producto': self.p.id, 'cantidad': 2}]}
        if cliente:
            datos['cliente'] = cliente.id
        r = self.client.post('/api/ventas/', datos, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        return r.data

    def test_cliente_con_ruc_lleva_serie_de_factura(self):
        empresa = Cliente.objects.create(nombre='Constructora Andina SAC', tipo_documento='RUC',
                                         numero_documento='20123456789', direccion='Jr. Ayacucho 120')
        self.assertTrue(self._vender(empresa)['numero'].startswith('F001-'))
        self.assertTrue(self._vender()['numero'].startswith('B001-'))

    def test_comprobante_lo_genera_spring_y_reenvia_el_token(self):
        v = self._vender()
        with override_settings(MS_REPORTES={'URL': f'http://127.0.0.1:{self.puerto}/api', 'TIMEOUT': 3}):
            r = self.client.get(f"/api/ventas/{v['id']}/comprobante/?formato=ticket")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r['X-Emisor'], 'spring')
        self.assertEqual(r.content, b'%PDF-1.4 comprobante de Spring')
        self.assertIn('ticket_', r['Content-Disposition'])
        self.assertEqual(_SpringFalso.recibido['ruta'], f"/api/comprobantes/venta/{v['id']}/pdf?formato=TICKET")
        self.assertEqual(_SpringFalso.recibido['auth'], 'Bearer token-de-prueba')

    def test_si_spring_esta_apagado_entrega_el_pdf_de_django(self):
        v = self._vender()
        # puerto 9 (discard): nadie escucha -> conexión rechazada
        with override_settings(MS_REPORTES={'URL': 'http://127.0.0.1:9/api', 'TIMEOUT': 1}):
            r = self.client.get(f"/api/ventas/{v['id']}/comprobante/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r['X-Emisor'], 'django')
        self.assertTrue(r.content.startswith(b'%PDF'))

    def test_sin_spring_configurado_usa_django(self):
        v = self._vender()
        with override_settings(MS_REPORTES={'URL': '', 'TIMEOUT': 1}):
            r = self.client.get(f"/api/ventas/{v['id']}/comprobante/")
        self.assertEqual(r['X-Emisor'], 'django')

"""Retoques finales: numeración sin saltos por serie, reglas de SUNAT y sesión de 30 días."""
from datetime import timedelta
from decimal import Decimal

from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Cliente


class ComprobanteReglasTests(APITestCase):

    def setUp(self):
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Construcción')
        self.barato = Producto.objects.create(codigo='C1', nombre='Cemento', categoria=cat, precio_compra=20,
                                              precio_venta=Decimal('25.00'), stock_actual=500)
        self.caro = Producto.objects.create(codigo='T1', nombre='Taladro', categoria=cat, precio_compra=500,
                                            precio_venta=Decimal('600.00'), stock_actual=10)
        self.empresa = Cliente.objects.create(nombre='Constructora Andina SAC', tipo_documento='RUC',
                                              numero_documento='20123456789', direccion='Jr. Ayacucho 120')
        self.dni = Cliente.objects.create(nombre='Rosa Díaz', tipo_documento='DNI', numero_documento='44556677')
        self.client.force_authenticate(self.vend)

    def _vender(self, producto, cantidad=1, cliente=None):
        datos = {'detalles': [{'producto': producto.id, 'cantidad': cantidad}]}
        if cliente:
            datos['cliente'] = cliente.id
        return self.client.post('/api/ventas/', datos, format='json')

    def test_cada_serie_avanza_sin_saltos(self):
        numeros = [self._vender(self.barato).data['numero'],
                   self._vender(self.barato, cliente=self.empresa).data['numero'],
                   self._vender(self.barato).data['numero'],
                   self._vender(self.barato, cliente=self.empresa).data['numero']]
        self.assertEqual(numeros, ['B001-000001', 'F001-000001', 'B001-000002', 'F001-000002'])

    def test_boleta_mayor_a_700_exige_dni(self):
        r = self._vender(self.caro, 2)   # 1200 + IGV sin cliente
        self.assertEqual(r.status_code, 400)
        self.assertIn('cliente', r.data['detalle'])
        self.assertEqual(Producto.objects.get(pk=self.caro.pk).stock_actual, 10)  # no se tocó el stock
        self.assertEqual(self._vender(self.caro, 2, cliente=self.dni).status_code, 201)
        self.assertEqual(self._vender(self.barato).status_code, 201)  # monto chico: sin DNI está bien

    def test_factura_exige_direccion_y_ruc_valido(self):
        sin_dir = Cliente.objects.create(nombre='Ferremax EIRL', tipo_documento='RUC', numero_documento='20999888777')
        r = self._vender(self.barato, cliente=sin_dir)
        self.assertEqual(r.status_code, 400)
        self.assertIn('dirección', str(r.data['detalle']['cliente']))
        mal = Cliente.objects.create(nombre='X SAC', tipo_documento='RUC', numero_documento='2012', direccion='Av. 1')
        self.assertIn('11 dígitos', str(self._vender(self.barato, cliente=mal).data['detalle']['cliente']))
        self.assertEqual(self._vender(self.barato, cliente=self.empresa).status_code, 201)


class SesionTests(APITestCase):

    def setUp(self):
        Usuario.objects.create_user('ana', password='Clave-segura-1', rol='VENDEDOR')

    def test_sesion_de_30_dias_rota_el_refresh_y_logout_lo_anula(self):
        from django.conf import settings
        self.assertEqual(settings.SIMPLE_JWT['REFRESH_TOKEN_LIFETIME'], timedelta(days=30))
        login = self.client.post('/api/auth/login/', {'username': 'ana', 'password': 'Clave-segura-1'}).data
        r = self.client.post('/api/auth/refresh/', {'refresh': login['refresh']})
        self.assertEqual(r.status_code, 200)
        nuevo = r.data['refresh']
        self.assertNotEqual(nuevo, login['refresh'])
        # el refresh anterior quedó anulado
        self.assertEqual(self.client.post('/api/auth/refresh/', {'refresh': login['refresh']}).status_code, 401)
        # cerrar sesión anula el vigente
        self.assertEqual(self.client.post('/api/auth/logout/', {'refresh': nuevo}).status_code, 200)
        self.assertEqual(self.client.post('/api/auth/refresh/', {'refresh': nuevo}).status_code, 401)

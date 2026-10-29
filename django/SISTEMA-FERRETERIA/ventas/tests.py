from decimal import Decimal

from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, MovimientoInventario, Producto


class VentasApiTests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create_user('admin', password='x12345', rol='ADMIN')
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Herramientas')
        self.p1 = Producto.objects.create(codigo='A1', nombre='Martillo', categoria=cat,
                                          precio_compra=10, precio_venta=Decimal('20.00'), stock_actual=10)
        self.p2 = Producto.objects.create(codigo='A2', nombre='Clavos', categoria=cat,
                                          precio_compra=1, precio_venta=Decimal('5.00'), stock_actual=2)

    def test_venta_descuenta_stock_y_calcula_igv(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/ventas/', {'detalles': [
            {'producto': self.p1.id, 'cantidad': 3}, {'producto': self.p2.id, 'cantidad': 2}]}, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(Decimal(r.data['subtotal']), Decimal('70.00'))
        self.assertEqual(Decimal(r.data['igv']), Decimal('12.60'))
        self.assertEqual(Decimal(r.data['total']), Decimal('82.60'))
        self.p1.refresh_from_db(); self.p2.refresh_from_db()
        self.assertEqual((self.p1.stock_actual, self.p2.stock_actual), (7, 0))
        self.assertEqual(MovimientoInventario.objects.filter(tipo='SALIDA').count(), 2)

    def test_stock_insuficiente_no_registra_nada(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/ventas/', {'detalles': [
            {'producto': self.p1.id, 'cantidad': 1}, {'producto': self.p2.id, 'cantidad': 5}]}, format='json')
        self.assertEqual(r.status_code, 400)
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 10)  # transacción atómica: p1 intacto

    def test_anular_solo_admin_y_reingresa_stock(self):
        self.client.force_authenticate(self.vend)
        v = self.client.post('/api/ventas/', {'detalles': [{'producto': self.p1.id, 'cantidad': 4}]},
                             format='json').data
        r = self.client.post(f"/api/ventas/{v['id']}/anular/", {'motivo': 'error'}, format='json')
        self.assertEqual(r.status_code, 403)
        self.client.force_authenticate(self.admin)
        r = self.client.post(f"/api/ventas/{v['id']}/anular/", {'motivo': 'error'}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 10)
        r = self.client.post(f"/api/ventas/{v['id']}/anular/", {'motivo': 'otra vez'}, format='json')
        self.assertEqual(r.status_code, 400)

    def test_vendedor_no_crea_productos_admin_si_y_valida_negativos(self):
        datos = {'codigo': 'z9', 'nombre': 'Sierra', 'categoria': self.p1.categoria_id,
                 'precio_compra': '10', 'precio_venta': '15', 'stock_actual': 5}
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.post('/api/productos/', datos, format='json').status_code, 403)
        self.client.force_authenticate(self.admin)
        r = self.client.post('/api/productos/', {**datos, 'precio_venta': '-1'}, format='json')
        self.assertEqual(r.status_code, 400)
        self.assertIn('precio_venta', r.data['detalle'])
        r = self.client.post('/api/productos/', datos, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data['codigo'], 'Z9')

    def test_login_jwt_devuelve_rol(self):
        r = self.client.post('/api/auth/login/', {'username': 'admin', 'password': 'x12345'}, format='json')
        self.assertEqual(r.status_code, 200)
        self.assertIn('access', r.data); self.assertEqual(r.data['rol'], 'ADMIN')

    def test_eliminar_categoria_con_productos_da_409(self):
        self.client.force_authenticate(self.admin)
        r = self.client.delete(f'/api/categorias/{self.p1.categoria_id}/')
        self.assertEqual(r.status_code, 409)

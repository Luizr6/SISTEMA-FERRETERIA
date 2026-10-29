from decimal import Decimal

from rest_framework.test import APITestCase

from auditoria.models import Bitacora
from cuentas.models import Usuario
from productos.models import Categoria, MovimientoInventario, Producto, Proveedor


class ComprasApiTests(APITestCase):
    """Sprint 5: FER-40 (órdenes de compra) y FER-41 (stock automático por compras)."""

    def setUp(self):
        self.admin = Usuario.objects.create_user('admin', password='x12345', rol='ADMIN')
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Herramientas')
        self.prov = Proveedor.objects.create(razon_social='Distribuidora Norte', ruc='20123456789')
        self.p1 = Producto.objects.create(codigo='A1', nombre='Martillo', categoria=cat, proveedor=self.prov,
                                          precio_compra=10, precio_venta=20, stock_actual=2, stock_minimo=5)
        self.p2 = Producto.objects.create(codigo='A2', nombre='Clavos', categoria=cat,
                                          precio_compra=1, precio_venta=5, stock_actual=50)
        self.client.force_authenticate(self.admin)

    def _crear_orden(self):
        r = self.client.post('/api/compras/', {'proveedor': self.prov.id, 'detalles': [
            {'producto': self.p1.id, 'cantidad': 10, 'precio_compra': '12.50'},
            {'producto': self.p2.id, 'cantidad': 100}]}, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        return r.data

    def test_crear_orden_calcula_total_y_no_toca_stock(self):
        o = self._crear_orden()
        self.assertEqual(o['numero'], f'OC-{o["id"]:06d}')
        self.assertEqual(o['estado'], 'PENDIENTE')
        self.assertEqual(Decimal(o['total']), Decimal('225.00'))  # 10*12.50 + 100*1.00
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 2)
        self.assertTrue(Bitacora.objects.filter(accion='CREAR', modelo__icontains='orden').exists())

    def test_recepcion_parcial_y_luego_total(self):
        o = self._crear_orden()
        linea_p1 = next(d for d in o['detalles'] if d['producto'] == self.p1.id)
        r = self.client.post(f"/api/compras/{o['id']}/recibir/",
                             {'items': [{'detalle': linea_p1['id'], 'cantidad': 4}]}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(r.data['estado'], 'PARCIAL')
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 6)
        self.assertEqual(self.p1.precio_compra, Decimal('12.50'))  # se actualiza el costo
        mov = MovimientoInventario.objects.filter(producto=self.p1).first()
        self.assertEqual((mov.tipo, mov.cantidad, mov.stock_resultante), ('INGRESO', 4, 6))

        # no se puede recibir más de lo pendiente
        r = self.client.post(f"/api/compras/{o['id']}/recibir/",
                             {'items': [{'detalle': linea_p1['id'], 'cantidad': 7}]}, format='json')
        self.assertEqual(r.status_code, 400)

        r = self.client.post(f"/api/compras/{o['id']}/recibir/", {}, format='json')  # todo lo que falta
        self.assertEqual(r.data['estado'], 'RECIBIDA')
        self.assertEqual(r.data['avance_recepcion'], 100)
        self.p1.refresh_from_db(); self.p2.refresh_from_db()
        self.assertEqual((self.p1.stock_actual, self.p2.stock_actual), (12, 150))
        r = self.client.get(f"/api/compras/{o['id']}/recepciones/")
        self.assertEqual(len(r.data), 2)

        r = self.client.post(f"/api/compras/{o['id']}/recibir/", {}, format='json')
        self.assertEqual(r.status_code, 400)  # ya recibida

    def test_anular_solo_pendiente_y_editar_solo_pendiente(self):
        o = self._crear_orden()
        r = self.client.patch(f"/api/compras/{o['id']}/", {'observacion': 'urgente',
                              'detalles': [{'producto': self.p1.id, 'cantidad': 3}]}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(len(r.data['detalles']), 1)
        self.assertEqual(Decimal(r.data['total']), Decimal('30.00'))
        r = self.client.post(f"/api/compras/{o['id']}/anular/", {'motivo': 'proveedor sin stock'}, format='json')
        self.assertEqual(r.data['estado'], 'ANULADA')
        r = self.client.post(f"/api/compras/{o['id']}/recibir/", {}, format='json')
        self.assertEqual(r.status_code, 400)

    def test_filtros_historial_y_sugerencias(self):
        self._crear_orden()
        self.assertEqual(len(self.client.get(f'/api/compras/?proveedor={self.prov.id}').data), 1)
        self.assertEqual(len(self.client.get('/api/compras/?estado=RECIBIDA').data), 0)
        self.assertEqual(len(self.client.get('/api/compras/?fecha_desde=2000-01-01').data), 1)
        sug = self.client.get('/api/compras/sugerencias/').data
        self.assertEqual([s['codigo'] for s in sug], ['A1'])
        self.assertEqual(sug[0]['cantidad_sugerida'], 8)

    def test_vendedor_no_accede_a_compras(self):
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.get('/api/compras/').status_code, 403)

    def test_productos_del_proveedor(self):
        r = self.client.get(f'/api/proveedores/{self.prov.id}/productos/')
        self.assertEqual([p['codigo'] for p in r.data], ['A1'])
        self.assertEqual(self.client.get('/api/proveedores/').data[0]['total_productos'], 1)

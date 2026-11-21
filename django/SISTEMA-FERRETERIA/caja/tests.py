from decimal import Decimal

from django.test import override_settings
from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto
from ventas.models import Pedido, Venta

CAJA_ON = {'OBLIGATORIA': True}


@override_settings(CAJA=CAJA_ON)
class CajaTests(APITestCase):
    """Caso del día: abre con S/ 100, vende en efectivo y con Yape, paga S/ 30, anulan una venta y cierra."""

    def setUp(self):
        self.admin = Usuario.objects.create_user('dueno', password='x', rol='ADMIN')
        self.juan = Usuario.objects.create_user('juan', password='x', rol='VENDEDOR')
        self.rosa = Usuario.objects.create_user('rosa', password='x', rol='VENDEDOR')
        cat = Categoria.objects.create(nombre='Cementos')
        # precio sin IGV 100 -> total con IGV 118.00
        self.cemento = Producto.objects.create(codigo='CEM-001', nombre='Cemento', categoria=cat,
                                               precio_compra=80, precio_venta=Decimal('100.00'),
                                               stock_actual=50, stock_minimo=5)

    def _vender(self, usuario, metodo='EFECTIVO', cantidad=1):
        self.client.force_authenticate(usuario)
        return self.client.post('/api/ventas/', {
            'metodo_pago': metodo, 'detalles': [{'producto': self.cemento.pk, 'cantidad': cantidad}],
        }, format='json')

    def _abrir(self, usuario, monto=100):
        self.client.force_authenticate(usuario)
        return self.client.post('/api/cajas/abrir/', {'monto_inicial': monto}, format='json')

    def test_no_se_puede_cobrar_sin_caja_abierta(self):
        r = self._vender(self.juan)
        self.assertEqual(r.status_code, 400)
        self.assertIn('caja', r.data['detalle'])
        self.assertEqual(Venta.objects.count(), 0)
        self.cemento.refresh_from_db()
        self.assertEqual(self.cemento.stock_actual, 50)

    def test_dia_completo_con_arqueo(self):
        self.assertEqual(self._abrir(self.juan).status_code, 201)
        self.assertEqual(self._vender(self.juan, 'EFECTIVO', 2).status_code, 201)   # 236.00
        self.assertEqual(self._vender(self.juan, 'YAPE', 1).status_code, 201)       # 118.00 (no está en el cajón)
        anular = self._vender(self.juan, 'EFECTIVO', 1).data['id']                  # 118.00 que se anula

        self.client.force_authenticate(self.admin)
        self.client.post(f'/api/ventas/{anular}/anular/', {'motivo': 'cantidad mal registrada'}, format='json')

        self.client.force_authenticate(self.juan)
        caja = self.client.get('/api/cajas/actual/').data['caja']
        r = self.client.post(f'/api/cajas/{caja["id"]}/movimientos/',
                             {'tipo': 'EGRESO', 'monto': 30, 'concepto': 'Pago a cargador'}, format='json')
        self.assertEqual(r.status_code, 201)

        res = self.client.get('/api/cajas/actual/').data['caja']['resumen']
        self.assertEqual(Decimal(res['ventas_efectivo']), Decimal('236.00'))
        self.assertEqual(Decimal(res['ventas_otros_medios']), Decimal('118.00'))
        self.assertEqual(res['ventas_anuladas'], 1)
        self.assertEqual(Decimal(res['efectivo_esperado']), Decimal('306.00'))     # 100 + 236 - 30

        r = self.client.post(f'/api/cajas/{caja["id"]}/cerrar/', {'monto_contado': 300, 'observacion': 'Di mal un vuelto'},
                             format='json')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data['estado'], 'CERRADA')
        self.assertEqual(Decimal(r.data['diferencia']), Decimal('-6.00'))          # faltante
        # Cerrada: ya no se puede cobrar en ella
        self.assertEqual(self._vender(self.juan).status_code, 400)

    def test_una_sola_caja_abierta_por_vendedor_y_cada_uno_la_suya(self):
        self.assertEqual(self._abrir(self.juan).status_code, 201)
        self.assertEqual(self._abrir(self.juan).status_code, 400)
        self.assertEqual(self._abrir(self.rosa, 50).status_code, 201)
        self._vender(self.rosa, 'EFECTIVO', 1)
        venta = Venta.objects.get()
        self.assertEqual(venta.caja.usuario, self.rosa)

    def test_egreso_no_puede_superar_el_efectivo(self):
        self._abrir(self.juan, 20)
        caja = self.client.get('/api/cajas/actual/').data['caja']
        r = self.client.post(f'/api/cajas/{caja["id"]}/movimientos/',
                             {'tipo': 'EGRESO', 'monto': 50, 'concepto': 'Compra de útiles'}, format='json')
        self.assertEqual(r.status_code, 400)

    def test_pedido_aprobado_entra_en_la_caja_de_quien_aprueba(self):
        from ventas import services as ventas_services
        pedido = ventas_services.crear_pedido(detalles=[{'producto': self.cemento, 'cantidad': 1}], origen='WHATSAPP',
                                              contacto_telefono='51999')
        self.client.force_authenticate(self.rosa)
        r = self.client.post(f'/api/pedidos/{pedido.pk}/aprobar/', {'metodo_pago': 'EFECTIVO'}, format='json')
        self.assertEqual(r.status_code, 400)          # sin caja no se cobra
        pedido.refresh_from_db()
        self.assertEqual(pedido.estado, 'PENDIENTE')
        self._abrir(self.rosa, 50)
        r = self.client.post(f'/api/pedidos/{pedido.pk}/aprobar/', {'metodo_pago': 'EFECTIVO'}, format='json')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(Venta.objects.get().caja.usuario, self.rosa)

    def test_vendedor_solo_ve_sus_cajas_y_admin_ve_todas(self):
        self._abrir(self.juan)
        self._abrir(self.rosa)
        self.client.force_authenticate(self.juan)
        self.assertEqual(len(self.client.get('/api/cajas/').data), 1)
        self.client.force_authenticate(self.admin)
        self.assertEqual(len(self.client.get('/api/cajas/').data), 2)

    def test_vendedor_no_puede_cerrar_caja_ajena_pero_admin_si(self):
        self._abrir(self.rosa)
        caja_id = self.client.get('/api/cajas/actual/').data['caja']['id']
        self.client.force_authenticate(self.juan)
        self.assertEqual(self.client.post(f'/api/cajas/{caja_id}/cerrar/', {'monto_contado': 100}).status_code, 404)
        self.client.force_authenticate(self.admin)
        r = self.client.post(f'/api/cajas/{caja_id}/cerrar/', {'monto_contado': 100})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(Decimal(r.data['diferencia']), Decimal('0.00'))

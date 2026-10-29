from decimal import Decimal

from django.core import mail
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APITestCase

from cuentas.models import Usuario
from productos.models import Categoria, Producto, Proveedor
from ventas.models import Cliente, Pedido


class Sprint6Tests(APITestCase):
    """Sprint 6: carrito (FER-45), aprobación de pedidos (FER-46), historial (FER-47), PDF (FER-49)."""

    def setUp(self):
        self.admin = Usuario.objects.create_user('admin', password='x12345', rol='ADMIN', email='admin@test.pe')
        self.vend = Usuario.objects.create_user('vend', password='x12345', rol='VENDEDOR')
        self.cat = Categoria.objects.create(nombre='Herramientas')
        self.p1 = Producto.objects.create(codigo='A1', nombre='Martillo', categoria=self.cat,
                                          precio_compra=10, precio_venta=Decimal('20.00'), stock_actual=10)
        self.p2 = Producto.objects.create(codigo='A2', nombre='Clavos', categoria=self.cat,
                                          precio_compra=1, precio_venta=Decimal('5.00'), stock_actual=2)
        self.cliente = Cliente.objects.create(nombre='Juan Pérez', numero_documento='12345678', telefono='999111222')

    # ---------------- Carrito ----------------
    def test_carrito_completo_hasta_venta(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/carrito/agregar/', {'producto': self.p1.id, 'cantidad': 2}, format='json')
        self.assertEqual(r.data['total_items'], 2)
        r = self.client.post('/api/carrito/agregar/', {'producto': self.p1.id, 'cantidad': 1}, format='json')
        self.assertEqual(r.data['items'][0]['cantidad'], 3)  # suma
        r = self.client.post('/api/carrito/agregar/', {'producto': self.p2.id, 'cantidad': 5}, format='json')
        self.assertFalse(r.data['puede_confirmar'])  # pide 5 y hay 2
        self.assertEqual(len(r.data['alertas']), 1)
        item_p2 = next(i for i in r.data['items'] if i['producto'] == self.p2.id)
        r = self.client.patch(f"/api/carrito/items/{item_p2['id']}/", {'cantidad': 2}, format='json')
        self.assertTrue(r.data['puede_confirmar'])
        self.assertEqual(Decimal(r.data['subtotal']), Decimal('70.00'))
        self.assertEqual(Decimal(r.data['total']), Decimal('82.60'))

        r = self.client.post('/api/carrito/confirmar/', {'cliente': self.cliente.id, 'metodo_pago': 'YAPE'}, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data['metodo_pago'], 'YAPE')
        self.assertEqual(Decimal(r.data['total']), Decimal('82.60'))
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 7)
        self.assertEqual(self.client.get('/api/carrito/').data['items'], [])  # se vació

    def test_carrito_vacio_no_confirma_y_borrar_item(self):
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.post('/api/carrito/confirmar/', {}, format='json').status_code, 400)
        r = self.client.post('/api/carrito/agregar/', {'producto': self.p1.id}, format='json')
        r = self.client.delete(f"/api/carrito/items/{r.data['items'][0]['id']}/")
        self.assertEqual(r.data['total_items'], 0)

    # ---------------- Pedidos ----------------
    def test_pedido_no_toca_stock_y_al_aprobar_genera_venta(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/pedidos/', {'cliente': self.cliente.id,
                             'detalles': [{'producto': self.p1.id, 'cantidad': 4}]}, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data['estado'], 'PENDIENTE')
        self.assertEqual(Decimal(r.data['total_estimado']), Decimal('94.40'))
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 10)  # RF32
        self.assertEqual(self.client.get('/api/pedidos/resumen/').data['pendientes'], 1)

        r = self.client.post(f"/api/pedidos/{r.data['id']}/aprobar/", {'metodo_pago': 'EFECTIVO'}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(r.data['estado'], 'APROBADO')
        self.assertTrue(r.data['venta_numero'].startswith('B001-'))
        self.p1.refresh_from_db()
        self.assertEqual(self.p1.stock_actual, 6)
        r2 = self.client.post(f"/api/pedidos/{r.data['id']}/aprobar/", {}, format='json')
        self.assertEqual(r2.status_code, 400)  # no se aprueba dos veces
        venta = self.client.get(f"/api/ventas/{r.data['venta']}/").data
        self.assertEqual(venta['pedido_numero'], r.data['numero'])

    def test_pedido_sin_stock_no_se_aprueba_y_se_puede_rechazar(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/pedidos/', {'contacto_nombre': 'Ana', 'contacto_telefono': '988777666',
                             'detalles': [{'producto': self.p2.id, 'cantidad': 9}]}, format='json')
        self.assertFalse(r.data['puede_aprobarse'])
        pid = r.data['id']
        self.assertEqual(self.client.post(f'/api/pedidos/{pid}/aprobar/', {}, format='json').status_code, 400)
        self.assertEqual(Pedido.objects.get(pk=pid).estado, 'PENDIENTE')
        r = self.client.post(f'/api/pedidos/{pid}/rechazar/', {'motivo': 'Sin stock'}, format='json')
        self.assertEqual(r.data['estado'], 'RECHAZADO')

    def test_carrito_a_pedido_y_pedido_requiere_contacto(self):
        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/pedidos/', {'detalles': [{'producto': self.p1.id, 'cantidad': 1}]}, format='json')
        self.assertEqual(r.status_code, 400)
        self.client.post('/api/carrito/agregar/', {'producto': self.p1.id, 'cantidad': 2}, format='json')
        r = self.client.post('/api/carrito/enviar-pedido/', {'cliente': self.cliente.id}, format='json')
        self.assertEqual(r.status_code, 201, r.data)
        self.assertEqual(r.data['origen'], 'WEB')
        self.assertEqual(r.data['contacto_telefono'], '999111222')

    # ---------------- Historial y PDF ----------------
    def test_historial_filtros_resumen_y_pdf(self):
        self.client.force_authenticate(self.vend)
        v = self.client.post('/api/ventas/', {'metodo_pago': 'PLIN', 'origen': 'MOVIL',
                             'detalles': [{'producto': self.p1.id, 'cantidad': 1}]}, format='json').data
        self.client.force_authenticate(self.admin)
        self.client.post('/api/ventas/', {'detalles': [{'producto': self.p1.id, 'cantidad': 2}]}, format='json')

        self.assertEqual(len(self.client.get(f'/api/ventas/?vendedor={self.vend.id}').data), 1)
        self.assertEqual(len(self.client.get('/api/ventas/?metodo_pago=plin').data), 1)
        self.assertEqual(len(self.client.get('/api/ventas/?origen=MOVIL').data), 1)
        self.assertEqual(len(self.client.get('/api/ventas/?mias=true').data), 1)
        self.assertEqual(len(self.client.get(f"/api/ventas/?search={v['numero']}").data), 1)
        res = self.client.get('/api/ventas/resumen/').data
        self.assertEqual(res['ventas_completadas'], 2)
        self.assertEqual(len(res['por_vendedor']), 2)

        r = self.client.get(f"/api/ventas/{v['id']}/comprobante/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r['Content-Type'], 'application/pdf')
        self.assertTrue(r.content.startswith(b'%PDF'))

    def test_dashboard_y_reportes(self):
        self.client.force_authenticate(self.admin)
        self.client.post('/api/ventas/', {'detalles': [{'producto': self.p1.id, 'cantidad': 2}]}, format='json')
        d = self.client.get('/api/dashboard/').data
        self.assertEqual(len(d['ventas_ultimos_7_dias']), 7)
        self.assertIn('pedidos_pendientes', d)
        for agrupar in ('dia', 'semana', 'mes'):
            r = self.client.get(f'/api/reportes/ventas/?agrupar={agrupar}')
            self.assertEqual(r.status_code, 200, r.data)
            self.assertEqual(Decimal(r.data['total_periodo']), Decimal('47.20'))
        m = self.client.get('/api/reportes/margen/').data
        martillo = next(x for x in m['productos'] if x['codigo'] == 'A1')
        self.assertEqual((martillo['unidades_vendidas'], Decimal(martillo['utilidad_estimada'])), (2, Decimal('20.00')))
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.get('/api/reportes/margen/').status_code, 403)

    # ---------------- RF11, RF03, RF04 ----------------
    def test_importar_csv(self):
        Proveedor.objects.create(razon_social='Prov', ruc='20123456789')
        csv = ('codigo;nombre;categoria;marca;proveedor_ruc;unidad_medida;precio_compra;precio_venta;stock_actual;stock_minimo\n'
               'n1;Serrucho;Herramientas;Truper;20123456789;UND;15;25,50;4;2\n'
               'A1;Repetido;Herramientas;;;UND;1;2;0;1\n'
               'N2;Malo;Otra;;;XYZ;1;2;0;1\n')
        self.client.force_authenticate(self.admin)
        r = self.client.post('/api/productos/importar-csv/',
                             {'archivo': SimpleUploadedFile('p.csv', csv.encode(), content_type='text/csv')},
                             format='multipart')
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(r.data['creados'], 1)
        self.assertEqual([e['fila'] for e in r.data['errores']], [3, 4])
        p = Producto.objects.get(codigo='N1')
        self.assertEqual((p.precio_venta, p.stock_actual, p.marca.nombre), (Decimal('25.50'), 4, 'Truper'))
        self.assertEqual(p.movimientos.count(), 1)
        self.assertEqual(self.client.get('/api/productos/plantilla-csv/').status_code, 200)

    def test_recuperar_y_cambiar_contrasena(self):
        r = self.client.post('/api/auth/password/reset/', {'email': 'admin@test.pe'}, format='json')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(len(mail.outbox), 1)
        cuerpo = mail.outbox[0].body
        uid = cuerpo.split('uid=')[-1].split()[0]
        token = cuerpo.split('token=')[-1].split()[0]
        r = self.client.post('/api/auth/password/reset/confirm/',
                             {'uid': uid, 'token': token, 'new_password': 'NuevaClave#2026'}, format='json')
        self.assertEqual(r.status_code, 200, r.data)
        r = self.client.post('/api/auth/login/', {'username': 'admin', 'password': 'NuevaClave#2026'}, format='json')
        self.assertEqual(r.status_code, 200)
        r = self.client.post('/api/auth/password/reset/confirm/',
                             {'uid': uid, 'token': token, 'new_password': 'OtraClave#2026'}, format='json')
        self.assertEqual(r.status_code, 400)  # token de un solo uso

        self.client.force_authenticate(self.vend)
        r = self.client.post('/api/auth/password/change/', {'old_password': 'mal', 'new_password': 'x'}, format='json')
        self.assertEqual(r.status_code, 400)
        r = self.client.post('/api/auth/password/change/',
                             {'old_password': 'x12345', 'new_password': 'ClaveSegura#99'}, format='json')
        self.assertEqual(r.status_code, 200, r.data)

    def test_bitacora_registra_y_solo_admin_la_ve(self):
        self.client.force_authenticate(self.admin)
        self.client.patch(f'/api/productos/{self.p1.id}/', {'precio_venta': '21.00'}, format='json')
        r = self.client.get('/api/bitacora/?accion=EDITAR')
        self.assertEqual(r.data['count'], 1)
        self.assertEqual(r.data['results'][0]['usuario_nombre'], 'admin')
        self.client.force_authenticate(self.vend)
        self.assertEqual(self.client.get('/api/bitacora/').status_code, 403)

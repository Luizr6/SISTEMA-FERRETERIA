"""
python manage.py seed_demo --usuarios

Carga datos de ejemplo para la demo. Es idempotente: se puede ejecutar varias veces.
Con --movimientos (por defecto si la BD no tiene ventas) genera compras, ventas de los
últimos 7 días y pedidos pendientes para que el dashboard tenga contenido.
"""
import random
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from compras import services as compras_services
from cuentas.models import Usuario
from productos.models import Categoria, Marca, MovimientoInventario, Producto, Proveedor
from ventas import services as ventas_services
from ventas.models import Cliente, Pedido, Venta


class Command(BaseCommand):
    help = 'Carga datos de ejemplo (catálogo, proveedores, clientes, compras, ventas y pedidos) para la demo.'

    def add_arguments(self, parser):
        parser.add_argument('--usuarios', action='store_true',
                            help='Crea también demo_admin y demo_vendedor (clave: Demo1234!)')
        parser.add_argument('--movimientos', action='store_true',
                            help='Fuerza la creación de compras, ventas y pedidos de ejemplo')

    @transaction.atomic
    def handle(self, *args, **opts):
        cats = {n: Categoria.objects.get_or_create(nombre=n)[0]
                for n in ['Herramientas manuales', 'Eléctricos', 'Pinturas', 'Gasfitería', 'Fijaciones', 'Construcción']}
        marcas = {n: Marca.objects.get_or_create(nombre=n)[0]
                  for n in ['Stanley', 'Bosch', 'Truper', 'Celima', 'CPP', 'Pavco', 'Sol']}
        prov_norte, _ = Proveedor.objects.get_or_create(
            razon_social='Distribuidora Ferretera Norte SAC',
            defaults={'ruc': '20123456789', 'contacto': 'Carlos Rojas', 'telefono': '044123456',
                      'email': 'ventas@ferrenorte.pe', 'direccion': 'Av. América Norte 1450, Trujillo'})
        prov_lima, _ = Proveedor.objects.get_or_create(
            razon_social='Importaciones Herramientas Lima EIRL',
            defaults={'ruc': '20987654321', 'contacto': 'María Salazar', 'telefono': '014567890',
                      'email': 'pedidos@herralima.pe', 'direccion': 'Jr. Paruro 820, Lima'})
        prov_const, _ = Proveedor.objects.get_or_create(
            razon_social='Materiales de Construcción del Sur SAC',
            defaults={'ruc': '20456789123', 'contacto': 'Pedro Quispe', 'telefono': '054321987'})

        datos = [
            ('MAR-001', 'Martillo de uña 16 oz', 'Herramientas manuales', 'Stanley', prov_lima, 18, 32.00, 25, 5, 'UND'),
            ('DES-001', 'Destornillador Phillips #2', 'Herramientas manuales', 'Truper', prov_lima, 4, 9.50, 40, 10, 'UND'),
            ('ALI-001', 'Alicate universal 8"', 'Herramientas manuales', 'Truper', prov_lima, 12, 21.00, 15, 5, 'UND'),
            ('CIN-001', 'Cinta métrica 5 m', 'Herramientas manuales', 'Stanley', prov_lima, 11, 22.00, 0, 5, 'UND'),
            ('TAL-001', 'Taladro percutor 650W', 'Eléctricos', 'Bosch', prov_norte, 190, 289.00, 6, 3, 'UND'),
            ('AMO-001', 'Amoladora angular 4 1/2" 850W', 'Eléctricos', 'Bosch', prov_norte, 165, 249.00, 4, 2, 'UND'),
            ('CAB-001', 'Cable THW 14 AWG (m)', 'Eléctricos', 'Sol', prov_norte, 1.2, 2.20, 300, 100, 'M'),
            ('PIN-001', 'Pintura látex blanco 1 gl', 'Pinturas', 'CPP', prov_norte, 38, 62.00, 12, 4, 'GLN'),
            ('PIN-002', 'Esmalte sintético negro 1/4 gl', 'Pinturas', 'CPP', prov_norte, 14, 24.50, 3, 4, 'GLN'),
            ('TUB-001', 'Tubo PVC 1/2" x 5 m', 'Gasfitería', 'Pavco', prov_const, 7, 14.00, 30, 10, 'UND'),
            ('CAN-001', 'Llave de paso 1/2"', 'Gasfitería', 'Celima', prov_const, 9, 16.90, 18, 6, 'UND'),
            ('CLA-001', 'Clavos 2" (kg)', 'Fijaciones', 'Truper', prov_const, 5, 8.50, 3, 5, 'KG'),
            ('TOR-001', 'Tornillo autorroscante 1" (caja x100)', 'Fijaciones', 'Truper', prov_lima, 6, 11.00, 22, 8, 'CAJA'),
            ('CEM-001', 'Cemento Portland tipo I 42.5 kg', 'Construcción', 'Sol', prov_const, 26, 31.50, 40, 15, 'BOLSA'),
        ]
        for cod, nom, cat, mar, prov, pc, pv, stock, smin, um in datos:
            p, creado = Producto.objects.get_or_create(codigo=cod, defaults=dict(
                nombre=nom, categoria=cats[cat], marca=marcas[mar], proveedor=prov,
                precio_compra=pc, precio_venta=pv, stock_actual=stock, stock_minimo=smin, unidad_medida=um))
            if creado and stock > 0:
                MovimientoInventario.objects.create(producto=p, tipo='INGRESO', cantidad=stock,
                                                    stock_resultante=stock, motivo='Stock inicial (demo)')

        clientes = [
            ('DNI', '12345678', 'Juan Pérez', '999111222'),
            ('DNI', '45678912', 'Rosa Huamán', '988222333'),
            ('RUC', '20601234567', 'Constructora Los Andes SAC', '044556677'),
            ('DNI', '70123456', 'Miguel Torres', '977333444'),
        ]
        for tipo, num, nom, tel in clientes:
            Cliente.objects.get_or_create(numero_documento=num,
                                          defaults={'tipo_documento': tipo, 'nombre': nom, 'telefono': tel})

        if opts['usuarios']:
            for username, rol, nombre in (('demo_admin', 'ADMIN', 'Luis'), ('demo_vendedor', 'VENDEDOR', 'Franco')):
                if not Usuario.objects.filter(username=username).exists():
                    Usuario.objects.create_user(username=username, password='Demo1234!', rol=rol,
                                                first_name=nombre, email=f'{username}@ferreteria.local')

        if opts['movimientos'] or not Venta.objects.exists():
            self._movimientos(prov_norte, prov_lima)
        self.stdout.write(self.style.SUCCESS('Datos de demo cargados.'))

    def _movimientos(self, prov_norte, prov_lima):
        admin = (Usuario.objects.filter(rol='ADMIN').first() or Usuario.objects.filter(is_superuser=True).first())
        vendedor = Usuario.objects.filter(rol='VENDEDOR', is_superuser=False).first() or admin
        if admin is None:
            self.stdout.write(self.style.WARNING('No hay usuarios: usa --usuarios para generar movimientos.'))
            return
        prod = {p.codigo: p for p in Producto.objects.all()}

        # Compras: una recibida, una parcial y una pendiente
        oc1 = compras_services.crear_orden(usuario=admin, proveedor=prov_norte, detalles=[
            {'producto': prod['PIN-001'], 'cantidad': 10}, {'producto': prod['TAL-001'], 'cantidad': 4}])
        compras_services.recibir_orden(orden=oc1, usuario=admin, observacion='Llegó completo')
        oc2 = compras_services.crear_orden(usuario=admin, proveedor=prov_lima, detalles=[
            {'producto': prod['CIN-001'], 'cantidad': 20}, {'producto': prod['MAR-001'], 'cantidad': 10}])
        linea = oc2.detalles.get(producto=prod['CIN-001'])
        compras_services.recibir_orden(orden=oc2, usuario=admin, items=[{'detalle': linea.pk, 'cantidad': 12}],
                                       observacion='Faltan 8 cintas')
        compras_services.crear_orden(usuario=admin, proveedor=prov_norte, observacion='Reposición mensual',
                                     fecha_entrega_estimada=timezone.localdate() + timedelta(days=5),
                                     detalles=[{'producto': prod['PIN-002'], 'cantidad': 12},
                                               {'producto': prod['CAB-001'], 'cantidad': 200}])

        # Ventas distribuidas en los últimos 7 días
        rnd = random.Random(42)
        clientes = list(Cliente.objects.all()) + [None, None]
        vendibles = ['MAR-001', 'DES-001', 'ALI-001', 'CAB-001', 'PIN-001', 'TUB-001', 'TOR-001', 'CEM-001', 'CAN-001']
        metodos = ['EFECTIVO', 'EFECTIVO', 'YAPE', 'PLIN', 'TARJETA']
        ahora = timezone.now()
        for dia in range(6, -1, -1):
            for _ in range(rnd.randint(2, 4)):
                codigos = rnd.sample(vendibles, rnd.randint(1, 3))
                detalles = [{'producto': prod[c], 'cantidad': rnd.randint(1, 4)} for c in codigos
                            if c in prod and prod[c].stock_actual > 6]
                if not detalles:
                    continue
                v = ventas_services.registrar_venta(vendedor=rnd.choice([admin, vendedor]), cliente=rnd.choice(clientes),
                                                    detalles=detalles, metodo_pago=rnd.choice(metodos),
                                                    origen=rnd.choice(['MOSTRADOR', 'MOSTRADOR', 'MOVIL']))
                Venta.objects.filter(pk=v.pk).update(fecha=ahora - timedelta(days=dia, hours=rnd.randint(0, 8)))
                for c in codigos:
                    prod[c].refresh_from_db()

        # Pedidos pendientes (web y WhatsApp) para la bandeja de aprobación
        if not Pedido.objects.filter(estado='PENDIENTE').exists():
            juan = Cliente.objects.get(numero_documento='12345678')
            ventas_services.crear_pedido(detalles=[{'producto': prod['CEM-001'], 'cantidad': 10},
                                                   {'producto': prod['TUB-001'], 'cantidad': 6}],
                                         origen='WEB', cliente=juan, creado_por=vendedor,
                                         observacion='Recoge el sábado')
            ventas_services.crear_pedido(detalles=[{'producto': prod['TAL-001'], 'cantidad': 1}],
                                         origen='WHATSAPP', contacto_nombre='Carla (WhatsApp)',
                                         contacto_telefono='51955444333', observacion='Mensaje: quiero 1 TAL-001')
        self.stdout.write('  + compras, ventas de 7 días y pedidos pendientes generados')

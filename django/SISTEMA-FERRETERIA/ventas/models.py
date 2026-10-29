from django.conf import settings
from django.core.validators import MinValueValidator
from django.db import models
from django.utils import timezone


class Cliente(models.Model):
    TIPOS_DOC = (
        ('DNI', 'DNI'),
        ('RUC', 'RUC'),
        ('CE', 'Carné de extranjería'),
        ('OTRO', 'Otro'),
    )
    tipo_documento = models.CharField(max_length=5, choices=TIPOS_DOC, default='DNI')
    numero_documento = models.CharField(max_length=20, unique=True, blank=True, null=True)
    nombre = models.CharField(max_length=150)
    telefono = models.CharField(max_length=20, blank=True)
    email = models.EmailField(blank=True)
    direccion = models.CharField(max_length=200, blank=True)
    activo = models.BooleanField(default=True)
    fecha_creacion = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['nombre']

    def __str__(self):
        return self.nombre


METODOS_PAGO = (
    ('EFECTIVO', 'Efectivo'),
    ('TARJETA', 'Tarjeta'),
    ('YAPE', 'Yape'),
    ('PLIN', 'Plin'),
    ('TRANSFERENCIA', 'Transferencia'),
)

ORIGENES = (
    ('MOSTRADOR', 'Mostrador (panel web)'),
    ('MOVIL', 'App móvil'),
    ('WEB', 'Pedido web'),
    ('WHATSAPP', 'WhatsApp'),
)


class Venta(models.Model):
    ESTADOS = (
        ('COMPLETADA', 'Completada'),
        ('ANULADA', 'Anulada'),
    )
    metodo_pago = models.CharField(max_length=15, choices=METODOS_PAGO, default='EFECTIVO')
    origen = models.CharField(max_length=10, choices=ORIGENES, default='MOSTRADOR')
    numero = models.CharField(max_length=20, unique=True, null=True, blank=True)  # comprobante interno B001-000001
    cliente = models.ForeignKey(Cliente, on_delete=models.PROTECT, related_name='ventas', null=True, blank=True)
    vendedor = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='ventas')
    fecha = models.DateTimeField(default=timezone.now)
    subtotal = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    igv = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    total = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    estado = models.CharField(max_length=12, choices=ESTADOS, default='COMPLETADA')
    observacion = models.TextField(blank=True)
    anulada_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT,
                                    related_name='ventas_anuladas', null=True, blank=True)
    fecha_anulacion = models.DateTimeField(null=True, blank=True)
    motivo_anulacion = models.CharField(max_length=250, blank=True)

    class Meta:
        ordering = ['-fecha']

    def __str__(self):
        return f"{self.numero or 'Venta'} - {self.total}"


class DetalleVenta(models.Model):
    venta = models.ForeignKey(Venta, on_delete=models.CASCADE, related_name='detalles')
    producto = models.ForeignKey('productos.Producto', on_delete=models.PROTECT, related_name='detalles_venta')
    cantidad = models.PositiveIntegerField(validators=[MinValueValidator(1)])
    precio_unitario = models.DecimalField(max_digits=10, decimal_places=2)
    subtotal = models.DecimalField(max_digits=12, decimal_places=2)

    def __str__(self):
        return f"{self.cantidad} x {self.producto.nombre}"


class CarritoItem(models.Model):
    """FER-45 - Carrito de compras guardado en el servidor, uno por usuario (web y móvil comparten)."""
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='carrito')
    producto = models.ForeignKey('productos.Producto', on_delete=models.CASCADE, related_name='en_carritos')
    cantidad = models.PositiveIntegerField(validators=[MinValueValidator(1)])
    agregado = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['agregado', 'id']
        constraints = [models.UniqueConstraint(fields=['usuario', 'producto'], name='carrito_usuario_producto_unico')]

    def __str__(self):
        return f'{self.usuario} - {self.cantidad} x {self.producto}'


class Pedido(models.Model):
    """
    FER-45 / FER-46 / RF32-RF33 - Pedido por confirmar.
    No toca el stock. Solo al APROBARLO se genera la venta formal (CU08) y se descuenta stock.
    Lo usan el panel web, la app móvil y el bot de WhatsApp.
    """
    ESTADOS = (
        ('PENDIENTE', 'Pendiente'),
        ('APROBADO', 'Aprobado'),
        ('RECHAZADO', 'Rechazado'),
        ('CANCELADO', 'Cancelado'),
    )

    numero = models.CharField(max_length=20, unique=True, null=True, blank=True)  # P-000001
    origen = models.CharField(max_length=10, choices=ORIGENES, default='WEB')
    estado = models.CharField(max_length=10, choices=ESTADOS, default='PENDIENTE')
    cliente = models.ForeignKey(Cliente, on_delete=models.PROTECT, related_name='pedidos', null=True, blank=True)
    contacto_nombre = models.CharField(max_length=150, blank=True)
    contacto_telefono = models.CharField(max_length=20, blank=True)
    observacion = models.TextField(blank=True)
    total_estimado = models.DecimalField(max_digits=12, decimal_places=2, default=0)  # con IGV, precios actuales
    creado_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
                                   related_name='pedidos_creados', null=True, blank=True)
    fecha = models.DateTimeField(default=timezone.now)
    revisado_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT,
                                     related_name='pedidos_revisados', null=True, blank=True)
    fecha_revision = models.DateTimeField(null=True, blank=True)
    motivo_rechazo = models.CharField(max_length=250, blank=True)
    venta = models.OneToOneField(Venta, on_delete=models.PROTECT, related_name='pedido', null=True, blank=True)

    class Meta:
        ordering = ['-fecha', '-id']

    def __str__(self):
        return f'{self.numero or "Pedido"} ({self.estado})'


class DetallePedido(models.Model):
    pedido = models.ForeignKey(Pedido, on_delete=models.CASCADE, related_name='detalles')
    producto = models.ForeignKey('productos.Producto', on_delete=models.PROTECT, related_name='detalles_pedido')
    cantidad = models.PositiveIntegerField(validators=[MinValueValidator(1)])
    precio_unitario = models.DecimalField(max_digits=10, decimal_places=2)  # referencial, sin IGV
    subtotal = models.DecimalField(max_digits=12, decimal_places=2)

    def __str__(self):
        return f'{self.cantidad} x {self.producto}'

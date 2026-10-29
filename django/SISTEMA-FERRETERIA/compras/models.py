from django.conf import settings
from django.core.validators import MinValueValidator
from django.db import models
from django.utils import timezone


class OrdenCompra(models.Model):
    """CU10 - Orden de compra a un proveedor (FER-40)."""
    ESTADOS = (
        ('PENDIENTE', 'Pendiente'),   # registrada, aún no llega mercadería
        ('PARCIAL', 'Parcial'),       # llegó una parte (CU11 FA-01)
        ('RECIBIDA', 'Recibida'),     # llegó todo
        ('ANULADA', 'Anulada'),       # cancelada antes de recibir
    )

    numero = models.CharField(max_length=20, unique=True, null=True, blank=True)  # OC-000001
    proveedor = models.ForeignKey('productos.Proveedor', on_delete=models.PROTECT, related_name='ordenes_compra')
    fecha = models.DateTimeField(default=timezone.now)
    fecha_entrega_estimada = models.DateField(null=True, blank=True)
    estado = models.CharField(max_length=10, choices=ESTADOS, default='PENDIENTE')
    observacion = models.TextField(blank=True)
    total = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    creado_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT,
                                   related_name='ordenes_compra')
    fecha_recepcion = models.DateTimeField(null=True, blank=True)  # cuando quedó RECIBIDA
    anulada_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT,
                                    related_name='ordenes_anuladas', null=True, blank=True)
    motivo_anulacion = models.CharField(max_length=250, blank=True)

    class Meta:
        ordering = ['-fecha', '-id']
        verbose_name = 'orden de compra'
        verbose_name_plural = 'órdenes de compra'

    def __str__(self):
        return f'{self.numero or "OC"} - {self.proveedor}'


class DetalleOrdenCompra(models.Model):
    orden = models.ForeignKey(OrdenCompra, on_delete=models.CASCADE, related_name='detalles')
    producto = models.ForeignKey('productos.Producto', on_delete=models.PROTECT, related_name='detalles_compra')
    cantidad = models.PositiveIntegerField(validators=[MinValueValidator(1)])
    cantidad_recibida = models.PositiveIntegerField(default=0)
    precio_compra = models.DecimalField(max_digits=10, decimal_places=2, validators=[MinValueValidator(0)])
    subtotal = models.DecimalField(max_digits=12, decimal_places=2, default=0)

    class Meta:
        verbose_name = 'detalle de orden de compra'

    @property
    def cantidad_pendiente(self):
        return self.cantidad - self.cantidad_recibida

    def __str__(self):
        return f'{self.cantidad} x {self.producto}'


class RecepcionCompra(models.Model):
    """CU11 - Cada llegada de mercadería (puede haber varias por orden si es parcial)."""
    orden = models.ForeignKey(OrdenCompra, on_delete=models.PROTECT, related_name='recepciones')
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='recepciones')
    fecha = models.DateTimeField(auto_now_add=True)
    observacion = models.CharField(max_length=250, blank=True)

    class Meta:
        ordering = ['-fecha', '-id']
        verbose_name = 'recepción de compra'
        verbose_name_plural = 'recepciones de compra'

    def __str__(self):
        return f'Recepción {self.pk} de {self.orden.numero}'


class DetalleRecepcion(models.Model):
    recepcion = models.ForeignKey(RecepcionCompra, on_delete=models.CASCADE, related_name='detalles')
    detalle_orden = models.ForeignKey(DetalleOrdenCompra, on_delete=models.PROTECT, related_name='recepciones')
    cantidad = models.PositiveIntegerField(validators=[MinValueValidator(1)])

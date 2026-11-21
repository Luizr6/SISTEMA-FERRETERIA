"""
Caja de cada vendedor (control del dinero, no solo de las ventas).

  1. Apertura: el vendedor abre su caja con el sencillo inicial.
  2. Durante el turno: cada venta queda en la caja abierta de quien la cobra; los gastos o
     entradas de dinero que no son ventas se registran como movimientos (egreso / ingreso).
  3. Cierre (arqueo): el vendedor cuenta el efectivo, el sistema calcula cuánto debería haber
     y guarda la diferencia (faltante o sobrante).

Yape, Plin, tarjeta y transferencia se muestran aparte: no están físicamente en el cajón.
"""
from django.conf import settings
from django.core.validators import MinValueValidator
from django.db import models
from django.db.models import Q
from django.utils import timezone


class Caja(models.Model):
    ESTADOS = (
        ('ABIERTA', 'Abierta'),
        ('CERRADA', 'Cerrada'),
    )
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='cajas')
    estado = models.CharField(max_length=10, choices=ESTADOS, default='ABIERTA')
    fecha_apertura = models.DateTimeField(default=timezone.now)
    monto_inicial = models.DecimalField(max_digits=10, decimal_places=2, default=0,
                                        validators=[MinValueValidator(0)])
    # Datos del cierre (arqueo). Se guardan al cerrar para que no cambien después.
    fecha_cierre = models.DateTimeField(null=True, blank=True)
    cerrada_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, null=True, blank=True,
                                    related_name='cajas_cerradas')
    monto_esperado = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)
    monto_contado = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)
    diferencia = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)
    observacion = models.CharField(max_length=250, blank=True)

    class Meta:
        ordering = ['-fecha_apertura']
        constraints = [
            # Un vendedor no puede tener dos cajas abiertas a la vez
            models.UniqueConstraint(fields=['usuario'], condition=Q(estado='ABIERTA'),
                                    name='una_caja_abierta_por_usuario'),
        ]

    def __str__(self):
        return f'Caja de {self.usuario} · {self.fecha_apertura:%d/%m/%Y %H:%M} ({self.get_estado_display()})'


class MovimientoCaja(models.Model):
    """Dinero que entra o sale del cajón sin ser una venta (pago a un cargador, más sencillo...)."""
    TIPOS = (
        ('INGRESO', 'Ingreso'),
        ('EGRESO', 'Egreso'),
    )
    caja = models.ForeignKey(Caja, on_delete=models.CASCADE, related_name='movimientos')
    tipo = models.CharField(max_length=10, choices=TIPOS)
    monto = models.DecimalField(max_digits=10, decimal_places=2, validators=[MinValueValidator(0.01)])
    concepto = models.CharField(max_length=150)
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='movimientos_caja')
    fecha = models.DateTimeField(default=timezone.now)

    class Meta:
        ordering = ['-fecha']

    def __str__(self):
        return f'{self.get_tipo_display()} S/ {self.monto} - {self.concepto}'

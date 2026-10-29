from django.conf import settings
from django.db import models


class Bitacora(models.Model):
    """RF04 / RNF03: registro de acciones críticas (quién, qué, cuándo)."""
    ACCIONES = (
        ('CREAR', 'Crear'),
        ('EDITAR', 'Editar'),
        ('ELIMINAR', 'Eliminar'),
        ('ANULAR', 'Anular'),
        ('RECIBIR', 'Recibir compra'),
        ('APROBAR', 'Aprobar pedido'),
        ('RECHAZAR', 'Rechazar pedido'),
        ('IMPORTAR', 'Importar CSV'),
        ('LOGIN', 'Inicio de sesión'),
        ('PASSWORD', 'Cambio de contraseña'),
    )

    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
                                null=True, blank=True, related_name='bitacora')
    accion = models.CharField(max_length=10, choices=ACCIONES)
    modelo = models.CharField(max_length=60)
    objeto_id = models.CharField(max_length=40, blank=True)
    descripcion = models.CharField(max_length=255, blank=True)
    ip = models.GenericIPAddressField(null=True, blank=True)
    fecha = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-fecha', '-id']
        verbose_name = 'registro de bitácora'
        verbose_name_plural = 'bitácora'

    def __str__(self):
        return f'{self.fecha:%Y-%m-%d %H:%M} {self.usuario} {self.accion} {self.modelo}#{self.objeto_id}'

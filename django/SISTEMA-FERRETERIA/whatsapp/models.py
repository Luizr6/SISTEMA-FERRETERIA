from django.db import models


class Conversacion(models.Model):
    """Un chat por número de WhatsApp (FER-54: panel de monitoreo)."""
    telefono = models.CharField(max_length=20, unique=True)  # formato internacional sin '+': 51999111222
    nombre = models.CharField(max_length=150, blank=True)
    cliente = models.ForeignKey('ventas.Cliente', on_delete=models.SET_NULL, null=True, blank=True,
                                related_name='conversaciones_whatsapp')
    ultimo_mensaje = models.DateTimeField(null=True, blank=True)
    no_leidos = models.PositiveIntegerField(default=0)
    bot_activo = models.BooleanField(default=True, help_text='Si es False, solo responden personas.')
    fecha_creacion = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-ultimo_mensaje', '-id']
        verbose_name = 'conversación de WhatsApp'
        verbose_name_plural = 'conversaciones de WhatsApp'

    def __str__(self):
        return f'{self.nombre or self.telefono}'


class Mensaje(models.Model):
    DIRECCIONES = (('ENTRANTE', 'Entrante'), ('SALIENTE', 'Saliente'))
    ESTADOS_ENVIO = (
        ('RECIBIDO', 'Recibido'),
        ('ENVIADO', 'Enviado a Meta'),
        ('SIMULADO', 'Simulado (sin credenciales)'),
        ('ERROR', 'Error al enviar'),
    )
    INTENCIONES = (
        ('SALUDO', 'Saludo / ayuda'),
        ('CONSULTA', 'Consulta de producto, precio o stock'),
        ('PEDIDO', 'Intención de compra'),
        ('ESTADO', 'Estado de pedido'),
        ('DESCONOCIDA', 'No reconocida'),
        ('MANUAL', 'Respuesta manual del vendedor'),
    )

    conversacion = models.ForeignKey(Conversacion, on_delete=models.CASCADE, related_name='mensajes')
    direccion = models.CharField(max_length=8, choices=DIRECCIONES)
    texto = models.TextField()
    wa_message_id = models.CharField(max_length=120, unique=True, null=True, blank=True)
    intencion = models.CharField(max_length=12, choices=INTENCIONES, blank=True)
    estado_envio = models.CharField(max_length=8, choices=ESTADOS_ENVIO, default='RECIBIDO')
    error = models.CharField(max_length=255, blank=True)
    pedido = models.ForeignKey('ventas.Pedido', on_delete=models.SET_NULL, null=True, blank=True,
                               related_name='mensajes_whatsapp')
    enviado_por = models.ForeignKey('cuentas.Usuario', on_delete=models.SET_NULL, null=True, blank=True,
                                    related_name='mensajes_whatsapp')
    fecha = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['fecha', 'id']

    def __str__(self):
        return f'{self.direccion} {self.conversacion}: {self.texto[:40]}'

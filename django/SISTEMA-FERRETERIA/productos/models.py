from django.conf import settings
from django.core.validators import MinValueValidator, RegexValidator
from django.db import models


class Categoria(models.Model):
    nombre = models.CharField(max_length=100, unique=True)
    descripcion = models.TextField(blank=True, null=True)
    activo = models.BooleanField(default=True)
    fecha_creacion = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['nombre']

    def __str__(self):
        return self.nombre


class Marca(models.Model):
    nombre = models.CharField(max_length=100, unique=True)
    activo = models.BooleanField(default=True)

    class Meta:
        ordering = ['nombre']

    def __str__(self):
        return self.nombre


class Proveedor(models.Model):
    razon_social = models.CharField(max_length=150, unique=True)
    ruc = models.CharField(
        max_length=11, unique=True, blank=True, null=True,
        validators=[RegexValidator(r'^\d{11}$', 'El RUC debe tener 11 dígitos numéricos.')],
    )
    contacto = models.CharField(max_length=100, blank=True)
    telefono = models.CharField(max_length=20, blank=True)
    email = models.EmailField(blank=True)
    direccion = models.CharField(max_length=200, blank=True)
    activo = models.BooleanField(default=True)
    fecha_creacion = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['razon_social']
        verbose_name_plural = 'proveedores'

    def __str__(self):
        return self.razon_social


class Producto(models.Model):
    UNIDADES = (
        ('UND', 'Unidad'),
        ('KG', 'Kilogramo'),
        ('M', 'Metro'),
        ('L', 'Litro'),
        ('CAJA', 'Caja'),
        ('BOLSA', 'Bolsa'),
        ('GLN', 'Galón'),
    )

    codigo = models.CharField(max_length=50, unique=True)
    nombre = models.CharField(max_length=150)
    descripcion = models.TextField(blank=True, null=True)
    categoria = models.ForeignKey(Categoria, on_delete=models.PROTECT, related_name='productos')
    marca = models.ForeignKey(Marca, on_delete=models.PROTECT, related_name='productos', blank=True, null=True)
    proveedor = models.ForeignKey(Proveedor, on_delete=models.PROTECT, related_name='productos', blank=True, null=True)
    unidad_medida = models.CharField(max_length=5, choices=UNIDADES, default='UND')
    imagen_url = models.URLField(max_length=500, blank=True, default='')
    precio_compra = models.DecimalField(max_digits=10, decimal_places=2, validators=[MinValueValidator(0)])
    precio_venta = models.DecimalField(max_digits=10, decimal_places=2, validators=[MinValueValidator(0)])
    stock_actual = models.PositiveIntegerField(default=0)
    stock_minimo = models.PositiveIntegerField(default=5)
    activo = models.BooleanField(default=True)
    fecha_creacion = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['nombre']

    def __str__(self):
        return f"{self.codigo} - {self.nombre}"

    @property
    def estado_stock(self):
        if self.stock_actual == 0:
            return 'AGOTADO'
        if self.stock_actual <= self.stock_minimo:
            return 'BAJO'
        return 'NORMAL'


class MovimientoInventario(models.Model):
    """Kardex: cada ingreso/salida/ajuste de stock queda registrado (RF10)."""
    TIPOS = (
        ('INGRESO', 'Ingreso'),
        ('SALIDA', 'Salida'),
        ('AJUSTE', 'Ajuste'),
    )

    producto = models.ForeignKey(Producto, on_delete=models.PROTECT, related_name='movimientos')
    tipo = models.CharField(max_length=10, choices=TIPOS)
    cantidad = models.IntegerField(help_text='Con signo: positivo ingresa, negativo sale.')
    stock_resultante = models.PositiveIntegerField()
    motivo = models.CharField(max_length=200, blank=True)
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
                                related_name='movimientos_inventario')
    fecha = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-fecha', '-id']

    def __str__(self):
        return f"{self.tipo} {self.cantidad} - {self.producto.codigo}"

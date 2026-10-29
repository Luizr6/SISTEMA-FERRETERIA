from decimal import Decimal

from django.db import transaction
from rest_framework import serializers

from auditoria.ms_cliente import enviar_evento
from .models import Categoria, Marca, MovimientoInventario, Producto, Proveedor


def _total_productos(obj):
    """Usa la anotación del queryset si existe (1 consulta) o cuenta al vuelo tras crear/editar."""
    valor = getattr(obj, 'total_productos', None)
    return valor if valor is not None else obj.productos.count()


class CategoriaSerializer(serializers.ModelSerializer):
    total_productos = serializers.SerializerMethodField()

    class Meta:
        model = Categoria
        fields = '__all__'

    def get_total_productos(self, obj) -> int:
        return _total_productos(obj)

    def validate_nombre(self, value):
        return value.strip()


class MarcaSerializer(serializers.ModelSerializer):
    class Meta:
        model = Marca
        fields = '__all__'

    def validate_nombre(self, value):
        return value.strip()


class ProveedorSerializer(serializers.ModelSerializer):
    total_productos = serializers.SerializerMethodField()

    class Meta:
        model = Proveedor
        fields = '__all__'

    def get_total_productos(self, obj) -> int:
        return _total_productos(obj)

    def validate_razon_social(self, value):
        return value.strip()

    def validate_ruc(self, value):
        # Evita que "" choque con el unique cuando hay varios proveedores sin RUC.
        return value or None


class ProductoSerializer(serializers.ModelSerializer):
    categoria_nombre = serializers.CharField(source='categoria.nombre', read_only=True)
    marca_nombre = serializers.CharField(source='marca.nombre', read_only=True, default=None)
    proveedor_nombre = serializers.CharField(source='proveedor.razon_social', read_only=True, default=None)
    estado_stock = serializers.CharField(read_only=True)
    margen = serializers.SerializerMethodField()

    class Meta:
        model = Producto
        fields = '__all__'

    def get_margen(self, obj) -> Decimal:
        return obj.precio_venta - obj.precio_compra

    def validate_codigo(self, value):
        return value.strip().upper()

    def validate_precio_compra(self, value):
        if value < 0:
            raise serializers.ValidationError('El precio de compra no puede ser negativo.')
        return value

    def validate_precio_venta(self, value):
        if value < 0:
            raise serializers.ValidationError('El precio de venta no puede ser negativo.')
        return value

    def validate_stock_actual(self, value):
        if value < 0:
            raise serializers.ValidationError('El stock no puede ser negativo.')
        return value

    def validate_stock_minimo(self, value):
        if value < 0:
            raise serializers.ValidationError('El stock mínimo no puede ser negativo.')
        return value

    def _usuario(self):
        request = self.context.get('request')
        return request.user if request and request.user.is_authenticated else None

    @transaction.atomic
    def create(self, validated_data):
        producto = super().create(validated_data)
        if producto.stock_actual > 0:
            MovimientoInventario.objects.create(
                producto=producto, tipo='INGRESO', cantidad=producto.stock_actual,
                stock_resultante=producto.stock_actual, motivo='Stock inicial',
                usuario=self._usuario(),
            )
        return producto

    @transaction.atomic
    def update(self, instance, validated_data):
        stock_anterior = instance.stock_actual
        precios_antes = (instance.precio_compra, instance.precio_venta)
        producto = super().update(instance, validated_data)
        if (producto.precio_compra, producto.precio_venta) != precios_antes:
            # Evento para el microservicio de auditoría (no bloquea si está caído)
            enviar_evento(
                'PRECIO_MODIFICADO',
                f'{producto.codigo}: venta S/ {precios_antes[1]} -> S/ {producto.precio_venta}, '
                f'costo S/ {precios_antes[0]} -> S/ {producto.precio_compra}',
                request=self.context.get('request'), modulo='productos', entidad='Producto', entidad_id=producto.pk,
                datos={'codigo': producto.codigo,
                       'precio_venta_antes': str(precios_antes[1]), 'precio_venta_despues': str(producto.precio_venta),
                       'precio_compra_antes': str(precios_antes[0]), 'precio_compra_despues': str(producto.precio_compra)},
            )
        diferencia = producto.stock_actual - stock_anterior
        if diferencia != 0:
            MovimientoInventario.objects.create(
                producto=producto, tipo='AJUSTE', cantidad=diferencia,
                stock_resultante=producto.stock_actual, motivo='Ajuste manual de stock',
                usuario=self._usuario(),
            )
        return producto


class MovimientoInventarioSerializer(serializers.ModelSerializer):
    producto_codigo = serializers.CharField(source='producto.codigo', read_only=True)
    producto_nombre = serializers.CharField(source='producto.nombre', read_only=True)
    usuario_nombre = serializers.CharField(source='usuario.username', read_only=True, default=None)

    class Meta:
        model = MovimientoInventario
        fields = '__all__'

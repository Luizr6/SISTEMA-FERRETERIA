from rest_framework import serializers

from productos.models import Producto
from . import services
from .models import DetalleOrdenCompra, DetalleRecepcion, OrdenCompra, RecepcionCompra


class DetalleOrdenCompraSerializer(serializers.ModelSerializer):
    producto_codigo = serializers.CharField(source='producto.codigo', read_only=True)
    producto_nombre = serializers.CharField(source='producto.nombre', read_only=True)
    unidad_medida = serializers.CharField(source='producto.unidad_medida', read_only=True)
    cantidad_pendiente = serializers.IntegerField(read_only=True)
    precio_compra = serializers.DecimalField(max_digits=10, decimal_places=2, min_value=0, required=False)

    class Meta:
        model = DetalleOrdenCompra
        fields = ['id', 'producto', 'producto_codigo', 'producto_nombre', 'unidad_medida',
                  'cantidad', 'cantidad_recibida', 'cantidad_pendiente', 'precio_compra', 'subtotal']
        read_only_fields = ['cantidad_recibida', 'subtotal']

    def validate_cantidad(self, value):
        if value < 1:
            raise serializers.ValidationError('La cantidad debe ser al menos 1.')
        return value


class OrdenCompraSerializer(serializers.ModelSerializer):
    """
    Crear:  POST /api/compras/
    {"proveedor": 1, "fecha_entrega_estimada": "2026-10-30", "observacion": "",
     "detalles": [{"producto": 3, "cantidad": 10, "precio_compra": "18.50"}]}
    precio_compra es opcional (si falta se usa el costo actual del producto).
    """
    detalles = DetalleOrdenCompraSerializer(many=True)
    proveedor_nombre = serializers.CharField(source='proveedor.razon_social', read_only=True)
    proveedor_ruc = serializers.CharField(source='proveedor.ruc', read_only=True, default=None)
    creado_por_nombre = serializers.CharField(source='creado_por.username', read_only=True)
    total_items = serializers.SerializerMethodField()
    avance_recepcion = serializers.SerializerMethodField()

    class Meta:
        model = OrdenCompra
        fields = ['id', 'numero', 'fecha', 'proveedor', 'proveedor_nombre', 'proveedor_ruc',
                  'fecha_entrega_estimada', 'estado', 'observacion', 'total', 'total_items',
                  'avance_recepcion', 'creado_por', 'creado_por_nombre', 'fecha_recepcion',
                  'motivo_anulacion', 'detalles']
        read_only_fields = ['numero', 'fecha', 'estado', 'total', 'creado_por',
                            'fecha_recepcion', 'motivo_anulacion']

    def get_total_items(self, obj) -> int:
        return sum(d.cantidad for d in obj.detalles.all())

    def get_avance_recepcion(self, obj) -> int:
        """Porcentaje de unidades recibidas (útil para una barra de progreso)."""
        pedidas = sum(d.cantidad for d in obj.detalles.all())
        recibidas = sum(d.cantidad_recibida for d in obj.detalles.all())
        return round(recibidas * 100 / pedidas) if pedidas else 0

    def create(self, validated_data):
        return services.crear_orden(
            usuario=self.context['request'].user,
            proveedor=validated_data['proveedor'],
            detalles=validated_data['detalles'],
            observacion=validated_data.get('observacion', ''),
            fecha_entrega_estimada=validated_data.get('fecha_entrega_estimada'),
        )

    def update(self, instance, validated_data):
        return services.actualizar_orden(orden=instance, datos=validated_data)


class ItemRecepcionSerializer(serializers.Serializer):
    detalle = serializers.IntegerField(help_text='id de la línea de la orden (detalles[].id)')
    cantidad = serializers.IntegerField(min_value=1)


class RecibirOrdenSerializer(serializers.Serializer):
    """
    POST /api/compras/<id>/recibir/
    {}  -> recibe todo lo pendiente
    {"items": [{"detalle": 5, "cantidad": 4}], "observacion": "Faltó 1 caja"} -> recepción parcial
    """
    items = ItemRecepcionSerializer(many=True, required=False)
    observacion = serializers.CharField(max_length=250, required=False, allow_blank=True)
    actualizar_costo = serializers.BooleanField(required=False, default=True)


class AnularOrdenSerializer(serializers.Serializer):
    motivo = serializers.CharField(max_length=250)


class DetalleRecepcionSerializer(serializers.ModelSerializer):
    producto_nombre = serializers.CharField(source='detalle_orden.producto.nombre', read_only=True)

    class Meta:
        model = DetalleRecepcion
        fields = ['id', 'detalle_orden', 'producto_nombre', 'cantidad']


class RecepcionCompraSerializer(serializers.ModelSerializer):
    usuario_nombre = serializers.CharField(source='usuario.username', read_only=True)
    detalles = DetalleRecepcionSerializer(many=True, read_only=True)

    class Meta:
        model = RecepcionCompra
        fields = ['id', 'orden', 'usuario', 'usuario_nombre', 'fecha', 'observacion', 'detalles']


class SugerenciaCompraSerializer(serializers.ModelSerializer):
    """Productos con stock bajo/agotado, para armar una orden rápida."""
    proveedor_nombre = serializers.CharField(source='proveedor.razon_social', read_only=True, default=None)
    cantidad_sugerida = serializers.SerializerMethodField()

    class Meta:
        model = Producto
        fields = ['id', 'codigo', 'nombre', 'stock_actual', 'stock_minimo', 'precio_compra',
                  'proveedor', 'proveedor_nombre', 'cantidad_sugerida']

    def get_cantidad_sugerida(self, obj) -> int:
        # Reponer hasta el doble del stock mínimo
        return max(obj.stock_minimo * 2 - obj.stock_actual, 1)

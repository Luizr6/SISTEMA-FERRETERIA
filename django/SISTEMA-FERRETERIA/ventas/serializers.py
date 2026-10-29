from rest_framework import serializers

from productos.models import Producto
from . import services
from .models import METODOS_PAGO, Cliente, DetallePedido, DetalleVenta, Pedido, Venta


class ClienteSerializer(serializers.ModelSerializer):
    total_compras = serializers.SerializerMethodField()

    class Meta:
        model = Cliente
        fields = '__all__'

    def get_total_compras(self, obj) -> int:
        valor = getattr(obj, 'total_compras', None)
        return valor if valor is not None else obj.ventas.filter(estado='COMPLETADA').count()

    def validate_numero_documento(self, value):
        return (value or '').strip() or None  # "" no debe chocar con el unique

    def validate(self, attrs):
        tipo = attrs.get('tipo_documento', getattr(self.instance, 'tipo_documento', None))
        numero = attrs.get('numero_documento', getattr(self.instance, 'numero_documento', None))
        if numero:
            if tipo == 'DNI' and not (numero.isdigit() and len(numero) == 8):
                raise serializers.ValidationError({'numero_documento': 'El DNI debe tener 8 dígitos.'})
            if tipo == 'RUC' and not (numero.isdigit() and len(numero) == 11):
                raise serializers.ValidationError({'numero_documento': 'El RUC debe tener 11 dígitos.'})
        return attrs


# ---------------------------------------------------------------------------
# Ventas
# ---------------------------------------------------------------------------
class DetalleVentaSerializer(serializers.ModelSerializer):
    producto_codigo = serializers.CharField(source='producto.codigo', read_only=True)
    producto_nombre = serializers.CharField(source='producto.nombre', read_only=True)
    unidad_medida = serializers.CharField(source='producto.unidad_medida', read_only=True)

    class Meta:
        model = DetalleVenta
        fields = ['id', 'producto', 'producto_codigo', 'producto_nombre', 'unidad_medida',
                  'cantidad', 'precio_unitario', 'subtotal']
        read_only_fields = ['precio_unitario', 'subtotal']

    def validate_cantidad(self, value):
        if value < 1:
            raise serializers.ValidationError('La cantidad debe ser al menos 1.')
        return value


class VentaSerializer(serializers.ModelSerializer):
    """
    POST /api/ventas/
    {"cliente": 1 | null, "metodo_pago": "EFECTIVO", "origen": "MOSTRADOR" | "MOVIL",
     "observacion": "", "detalles": [{"producto": 3, "cantidad": 2}]}
    """
    detalles = DetalleVentaSerializer(many=True)
    cliente_nombre = serializers.CharField(source='cliente.nombre', read_only=True, default=None)
    cliente_documento = serializers.CharField(source='cliente.numero_documento', read_only=True, default=None)
    vendedor_nombre = serializers.CharField(source='vendedor.username', read_only=True)
    anulada_por_nombre = serializers.CharField(source='anulada_por.username', read_only=True, default=None)
    metodo_pago_display = serializers.CharField(source='get_metodo_pago_display', read_only=True)
    pedido_numero = serializers.SerializerMethodField()
    origen = serializers.ChoiceField(choices=[('MOSTRADOR', 'Mostrador'), ('MOVIL', 'App móvil')],
                                     required=False, default='MOSTRADOR')

    class Meta:
        model = Venta
        fields = ['id', 'numero', 'fecha', 'cliente', 'cliente_nombre', 'cliente_documento',
                  'vendedor', 'vendedor_nombre', 'metodo_pago', 'metodo_pago_display', 'origen',
                  'subtotal', 'igv', 'total', 'estado', 'observacion', 'pedido_numero',
                  'anulada_por_nombre', 'fecha_anulacion', 'motivo_anulacion', 'detalles']
        read_only_fields = ['numero', 'fecha', 'vendedor', 'subtotal', 'igv', 'total', 'estado',
                            'fecha_anulacion', 'motivo_anulacion']

    def get_pedido_numero(self, obj) -> str | None:
        try:
            return obj.pedido.numero
        except Pedido.DoesNotExist:
            return None

    def to_representation(self, instance):
        data = super().to_representation(instance)
        data['origen'] = instance.origen  # al leer se muestran también WEB / WHATSAPP
        return data

    def validate_cliente(self, value):
        if value is not None and not value.activo:
            raise serializers.ValidationError('El cliente está inactivo.')
        return value

    def validate_detalles(self, value):
        if not value:
            raise serializers.ValidationError('La venta debe tener al menos un producto.')
        return value

    def create(self, validated_data):
        return services.registrar_venta(
            vendedor=self.context['request'].user,
            cliente=validated_data.get('cliente'),
            detalles=validated_data['detalles'],
            observacion=validated_data.get('observacion', ''),
            metodo_pago=validated_data.get('metodo_pago', 'EFECTIVO'),
            origen=validated_data.get('origen', 'MOSTRADOR'),
        )


class AnularVentaSerializer(serializers.Serializer):
    motivo = serializers.CharField(max_length=250)


# ---------------------------------------------------------------------------
# Pedidos por confirmar
# ---------------------------------------------------------------------------
class DetallePedidoSerializer(serializers.ModelSerializer):
    producto_codigo = serializers.CharField(source='producto.codigo', read_only=True)
    producto_nombre = serializers.CharField(source='producto.nombre', read_only=True)
    stock_disponible = serializers.IntegerField(source='producto.stock_actual', read_only=True)
    stock_suficiente = serializers.SerializerMethodField()

    class Meta:
        model = DetallePedido
        fields = ['id', 'producto', 'producto_codigo', 'producto_nombre', 'cantidad',
                  'precio_unitario', 'subtotal', 'stock_disponible', 'stock_suficiente']
        read_only_fields = ['precio_unitario', 'subtotal']

    def get_stock_suficiente(self, obj) -> bool:
        return obj.producto.activo and obj.cantidad <= obj.producto.stock_actual

    def validate_cantidad(self, value):
        if value < 1:
            raise serializers.ValidationError('La cantidad debe ser al menos 1.')
        return value


class PedidoSerializer(serializers.ModelSerializer):
    """
    POST /api/pedidos/
    {"cliente": 1 | null, "contacto_nombre": "", "contacto_telefono": "", "origen": "WEB" | "MOVIL",
     "observacion": "", "detalles": [{"producto": 3, "cantidad": 2}]}
    """
    detalles = DetallePedidoSerializer(many=True)
    cliente_nombre = serializers.CharField(source='cliente.nombre', read_only=True, default=None)
    creado_por_nombre = serializers.CharField(source='creado_por.username', read_only=True, default=None)
    revisado_por_nombre = serializers.CharField(source='revisado_por.username', read_only=True, default=None)
    venta_numero = serializers.CharField(source='venta.numero', read_only=True, default=None)
    puede_aprobarse = serializers.SerializerMethodField()
    origen = serializers.ChoiceField(choices=[('WEB', 'Web'), ('MOVIL', 'App móvil')], required=False, default='WEB')

    class Meta:
        model = Pedido
        fields = ['id', 'numero', 'fecha', 'origen', 'estado', 'cliente', 'cliente_nombre',
                  'contacto_nombre', 'contacto_telefono', 'observacion', 'total_estimado',
                  'creado_por', 'creado_por_nombre', 'revisado_por', 'revisado_por_nombre',
                  'fecha_revision', 'motivo_rechazo', 'venta', 'venta_numero', 'puede_aprobarse', 'detalles']
        read_only_fields = ['numero', 'fecha', 'estado', 'total_estimado', 'creado_por', 'revisado_por',
                            'fecha_revision', 'motivo_rechazo', 'venta']

    def get_puede_aprobarse(self, obj) -> bool:
        return obj.estado == 'PENDIENTE' and all(
            d.producto.activo and d.cantidad <= d.producto.stock_actual for d in obj.detalles.all())

    def to_representation(self, instance):
        data = super().to_representation(instance)
        data['origen'] = instance.origen
        return data

    def validate(self, attrs):
        if not attrs.get('cliente') and not attrs.get('contacto_nombre'):
            raise serializers.ValidationError({'cliente': 'Indica un cliente registrado o un nombre de contacto.'})
        return attrs

    def create(self, validated_data):
        return services.crear_pedido(
            detalles=validated_data['detalles'], origen=validated_data.get('origen', 'WEB'),
            cliente=validated_data.get('cliente'), creado_por=self.context['request'].user,
            contacto_nombre=validated_data.get('contacto_nombre', ''),
            contacto_telefono=validated_data.get('contacto_telefono', ''),
            observacion=validated_data.get('observacion', ''),
        )


class AprobarPedidoSerializer(serializers.Serializer):
    metodo_pago = serializers.ChoiceField(choices=METODOS_PAGO, required=False, default='EFECTIVO')
    cliente = serializers.PrimaryKeyRelatedField(queryset=Cliente.objects.filter(activo=True),
                                                 required=False, allow_null=True)


class RechazarPedidoSerializer(serializers.Serializer):
    motivo = serializers.CharField(max_length=250)


# ---------------------------------------------------------------------------
# Carrito
# ---------------------------------------------------------------------------
class AgregarCarritoSerializer(serializers.Serializer):
    producto = serializers.PrimaryKeyRelatedField(queryset=Producto.objects.all())
    cantidad = serializers.IntegerField(min_value=1, default=1)
    reemplazar = serializers.BooleanField(required=False, default=False,
                                          help_text='true = fija la cantidad; false = suma a la existente')


class CantidadCarritoSerializer(serializers.Serializer):
    cantidad = serializers.IntegerField(min_value=1)


class ConfirmarCarritoSerializer(serializers.Serializer):
    cliente = serializers.PrimaryKeyRelatedField(queryset=Cliente.objects.filter(activo=True),
                                                 required=False, allow_null=True)
    metodo_pago = serializers.ChoiceField(choices=METODOS_PAGO, required=False, default='EFECTIVO')
    observacion = serializers.CharField(required=False, allow_blank=True, default='')
    origen = serializers.ChoiceField(choices=[('MOSTRADOR', 'Mostrador'), ('MOVIL', 'App móvil')],
                                     required=False, default='MOSTRADOR')


class CarritoAPedidoSerializer(serializers.Serializer):
    cliente = serializers.PrimaryKeyRelatedField(queryset=Cliente.objects.filter(activo=True),
                                                 required=False, allow_null=True)
    contacto_nombre = serializers.CharField(required=False, allow_blank=True, default='')
    contacto_telefono = serializers.CharField(required=False, allow_blank=True, default='')
    observacion = serializers.CharField(required=False, allow_blank=True, default='')
    origen = serializers.ChoiceField(choices=[('WEB', 'Web'), ('MOVIL', 'App móvil')], required=False, default='WEB')

from rest_framework import serializers

from . import services
from .models import Caja, MovimientoCaja


class MovimientoCajaSerializer(serializers.ModelSerializer):
    usuario_nombre = serializers.CharField(source='usuario.username', read_only=True)
    tipo_display = serializers.CharField(source='get_tipo_display', read_only=True)

    class Meta:
        model = MovimientoCaja
        fields = ['id', 'tipo', 'tipo_display', 'monto', 'concepto', 'usuario', 'usuario_nombre', 'fecha']
        read_only_fields = ['usuario', 'fecha']


class CajaSerializer(serializers.ModelSerializer):
    usuario_nombre = serializers.CharField(source='usuario.username', read_only=True)
    cerrada_por_nombre = serializers.CharField(source='cerrada_por.username', read_only=True, default=None)
    estado_display = serializers.CharField(source='get_estado_display', read_only=True)
    resumen = serializers.SerializerMethodField()

    class Meta:
        model = Caja
        fields = ['id', 'usuario', 'usuario_nombre', 'estado', 'estado_display', 'fecha_apertura', 'monto_inicial',
                  'fecha_cierre', 'cerrada_por_nombre', 'monto_esperado', 'monto_contado', 'diferencia',
                  'observacion', 'resumen']
        read_only_fields = fields

    def get_resumen(self, obj) -> dict:
        return services.resumen(obj)


class CajaDetalleSerializer(CajaSerializer):
    movimientos = MovimientoCajaSerializer(many=True, read_only=True)

    class Meta(CajaSerializer.Meta):
        fields = CajaSerializer.Meta.fields + ['movimientos']
        read_only_fields = fields

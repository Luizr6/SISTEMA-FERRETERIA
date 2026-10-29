from django.contrib import admin

from .models import CarritoItem, Cliente, DetallePedido, DetalleVenta, Pedido, Venta


class DetalleInline(admin.TabularInline):
    model = DetalleVenta
    extra = 0
    readonly_fields = ('producto', 'cantidad', 'precio_unitario', 'subtotal')
    can_delete = False


@admin.register(Venta)
class VentaAdmin(admin.ModelAdmin):
    list_display = ('numero', 'fecha', 'cliente', 'vendedor', 'metodo_pago', 'origen', 'total', 'estado')
    list_filter = ('estado', 'metodo_pago', 'origen')
    search_fields = ('numero', 'cliente__nombre')
    inlines = [DetalleInline]


class DetallePedidoInline(admin.TabularInline):
    model = DetallePedido
    extra = 0


@admin.register(Pedido)
class PedidoAdmin(admin.ModelAdmin):
    list_display = ('numero', 'fecha', 'origen', 'estado', 'contacto_nombre', 'total_estimado', 'venta')
    list_filter = ('estado', 'origen')
    search_fields = ('numero', 'contacto_nombre', 'contacto_telefono')
    inlines = [DetallePedidoInline]


@admin.register(Cliente)
class ClienteAdmin(admin.ModelAdmin):
    list_display = ('nombre', 'tipo_documento', 'numero_documento', 'telefono', 'activo')
    search_fields = ('nombre', 'numero_documento')


admin.site.register(CarritoItem)

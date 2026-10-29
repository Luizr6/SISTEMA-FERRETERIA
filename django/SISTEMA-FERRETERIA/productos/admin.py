from django.contrib import admin

from .models import Categoria, Marca, MovimientoInventario, Producto, Proveedor


@admin.register(Producto)
class ProductoAdmin(admin.ModelAdmin):
    list_display = ('codigo', 'nombre', 'categoria', 'marca', 'precio_venta', 'stock_actual', 'stock_minimo', 'activo')
    list_filter = ('categoria', 'marca', 'activo')
    search_fields = ('codigo', 'nombre')


@admin.register(MovimientoInventario)
class MovimientoAdmin(admin.ModelAdmin):
    list_display = ('fecha', 'producto', 'tipo', 'cantidad', 'stock_resultante', 'usuario')
    list_filter = ('tipo',)


admin.site.register(Categoria)
admin.site.register(Marca)
admin.site.register(Proveedor)

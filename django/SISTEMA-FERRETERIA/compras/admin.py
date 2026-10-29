from django.contrib import admin

from .models import DetalleOrdenCompra, OrdenCompra, RecepcionCompra


class DetalleOrdenInline(admin.TabularInline):
    model = DetalleOrdenCompra
    extra = 0
    readonly_fields = ('cantidad_recibida', 'subtotal')


@admin.register(OrdenCompra)
class OrdenCompraAdmin(admin.ModelAdmin):
    list_display = ('numero', 'fecha', 'proveedor', 'estado', 'total', 'creado_por')
    list_filter = ('estado', 'proveedor')
    search_fields = ('numero', 'proveedor__razon_social')
    inlines = [DetalleOrdenInline]
    readonly_fields = ('numero', 'total', 'estado', 'fecha_recepcion')


@admin.register(RecepcionCompra)
class RecepcionCompraAdmin(admin.ModelAdmin):
    list_display = ('id', 'orden', 'usuario', 'fecha')

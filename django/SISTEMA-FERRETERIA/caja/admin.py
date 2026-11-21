from django.contrib import admin

from .models import Caja, MovimientoCaja


class MovimientoInline(admin.TabularInline):
    model = MovimientoCaja
    extra = 0


@admin.register(Caja)
class CajaAdmin(admin.ModelAdmin):
    list_display = ('id', 'usuario', 'estado', 'fecha_apertura', 'monto_inicial', 'fecha_cierre', 'diferencia')
    list_filter = ('estado', 'usuario')
    inlines = [MovimientoInline]

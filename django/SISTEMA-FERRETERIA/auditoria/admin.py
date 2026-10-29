from django.contrib import admin

from .models import Bitacora


@admin.register(Bitacora)
class BitacoraAdmin(admin.ModelAdmin):
    list_display = ('fecha', 'usuario', 'accion', 'modelo', 'objeto_id', 'descripcion', 'ip')
    list_filter = ('accion', 'modelo')
    search_fields = ('descripcion', 'objeto_id')

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

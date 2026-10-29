from django.contrib import admin

from .models import Conversacion, Mensaje


class MensajeInline(admin.TabularInline):
    model = Mensaje
    extra = 0
    readonly_fields = ('direccion', 'texto', 'intencion', 'estado_envio', 'pedido', 'fecha')
    can_delete = False


@admin.register(Conversacion)
class ConversacionAdmin(admin.ModelAdmin):
    list_display = ('telefono', 'nombre', 'cliente', 'ultimo_mensaje', 'no_leidos', 'bot_activo')
    search_fields = ('telefono', 'nombre')
    inlines = [MensajeInline]

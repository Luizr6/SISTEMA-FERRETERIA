from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import Usuario


@admin.register(Usuario)
class UsuarioAdmin(UserAdmin):
    list_display = ('username', 'first_name', 'last_name', 'email', 'rol', 'is_active', 'is_superuser')
    list_filter = ('rol', 'is_active', 'is_superuser')
    fieldsets = UserAdmin.fieldsets + (('Rol en la ferretería', {'fields': ('rol',)}),)
    add_fieldsets = UserAdmin.add_fieldsets + (('Rol en la ferretería', {'fields': ('rol',)}),)

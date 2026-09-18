from rest_framework import serializers
from .models import Producto, Categoria, Marca

class ProductoSerializer(serializers.ModelSerializer):
    class Meta:
        model = Producto
        fields = '__all__'
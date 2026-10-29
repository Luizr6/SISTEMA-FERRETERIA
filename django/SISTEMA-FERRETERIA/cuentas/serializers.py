from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import Usuario


class LoginSerializer(TokenObtainPairSerializer):
    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token['username'] = user.username
        token['rol'] = 'ADMIN' if user.is_superuser else user.rol
        return token

    def validate(self, attrs):
        data = super().validate(attrs)
        data['user_id'] = self.user.id
        data['username'] = self.user.username
        data['rol'] = 'ADMIN' if self.user.is_superuser else self.user.rol
        return data


class UsuarioSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=6, required=False)

    class Meta:
        model = Usuario
        fields = ['id', 'username', 'first_name', 'last_name', 'email', 'rol', 'is_active', 'password']

    def validate(self, attrs):
        if self.instance is None and not attrs.get('password'):
            raise serializers.ValidationError({'password': 'La contraseña es obligatoria.'})
        return attrs

    def create(self, validated_data):
        password = validated_data.pop('password')
        return Usuario.objects.create_user(password=password, **validated_data)  # hash seguro

    def update(self, instance, validated_data):
        password = validated_data.pop('password', None)
        instance = super().update(instance, validated_data)
        if password:
            instance.set_password(password)
            instance.save(update_fields=['password'])
        return instance

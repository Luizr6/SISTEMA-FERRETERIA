from django.contrib.auth.models import AbstractUser
from django.db import models

class Usuario(AbstractUser):
    ROLES = (
        ('ADMIN', 'ADMIN'),
        ('VENDEDOR', 'VENDEDOR'),
    )
    rol = models.CharField(max_length=10, choices=ROLES, default='VENDEDOR')
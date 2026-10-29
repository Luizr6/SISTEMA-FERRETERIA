from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenRefreshView

from .views import (
    LoginView, MeView, PasswordChangeView, PasswordResetConfirmView, PasswordResetView, UsuarioViewSet,
)

router = DefaultRouter()
router.register(r'usuarios', UsuarioViewSet, basename='usuario')

urlpatterns = [
    path('auth/login/', LoginView.as_view(), name='login'),
    path('auth/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
    path('auth/me/', MeView.as_view(), name='me'),
    path('auth/password/reset/', PasswordResetView.as_view(), name='password_reset'),
    path('auth/password/reset/confirm/', PasswordResetConfirmView.as_view(), name='password_reset_confirm'),
    path('auth/password/change/', PasswordChangeView.as_view(), name='password_change'),
    path('', include(router.urls)),
]

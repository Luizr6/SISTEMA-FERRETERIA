"""
Configuración del backend SISTEMA-FERRETERIA.

Todo lo sensible o que cambia entre máquinas se lee de variables de entorno o de un
archivo `.env` en la raíz del proyecto (ver `.env.example`). Sin `.env` el proyecto
arranca igual en modo desarrollo con SQLite.
"""
import os
import sys
from datetime import timedelta
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


# ---------------------------------------------------------------------------
# Lectura mínima de .env (sin dependencias externas)
# ---------------------------------------------------------------------------
def _cargar_env(ruta):
    if not ruta.exists():
        return
    for linea in ruta.read_text(encoding='utf-8').splitlines():
        linea = linea.strip()
        if not linea or linea.startswith('#') or '=' not in linea:
            continue
        clave, valor = linea.split('=', 1)
        os.environ.setdefault(clave.strip(), valor.strip().strip('"').strip("'"))


_cargar_env(BASE_DIR / '.env')


def env(clave, defecto=''):
    return os.environ.get(clave, defecto)


def env_bool(clave, defecto=False):
    return env(clave, str(defecto)).lower() in ('1', 'true', 'si', 'sí', 'yes')


def env_lista(clave, defecto=''):
    return [x.strip() for x in env(clave, defecto).split(',') if x.strip()]


# ---------------------------------------------------------------------------
# Seguridad básica
# ---------------------------------------------------------------------------
# En desarrollo se usa una clave fija; en producción DEBE definirse DJANGO_SECRET_KEY.
SECRET_KEY = env('DJANGO_SECRET_KEY', 'dev-solo-local-cambiar-en-produccion-ferreteria-2026')
DEBUG = env_bool('DJANGO_DEBUG', True)
# '*' en desarrollo para que el emulador Android (10.0.2.2) y la red local funcionen.
ALLOWED_HOSTS = env_lista('DJANGO_ALLOWED_HOSTS', '*')


# ---------------------------------------------------------------------------
# Aplicaciones
# ---------------------------------------------------------------------------
INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    # Terceros
    'rest_framework',
    'rest_framework_simplejwt',
    'rest_framework_simplejwt.token_blacklist',   # anula los refresh viejos al renovar la sesión
    'corsheaders',
    'drf_spectacular',
    # Apps del proyecto
    'cuentas',      # Usuarios, roles, login JWT, recuperación de contraseña (Sprint 2)
    'auditoria',    # Bitácora de acciones críticas (RF04)
    'productos',    # Catálogo, proveedores, kardex (Sprints 3 y 5)
    'compras',      # Órdenes de compra y recepción (Sprint 5)
    'ventas',       # Clientes, ventas, carrito, pedidos, PDF, reportes (Sprints 4 y 6)
    'whatsapp',     # Webhook y bot de WhatsApp Cloud API (Sprint 7)
    'asistente',    # Asistente IA y alertas del Administrador (app móvil)
    'caja',         # Caja por vendedor: apertura, movimientos y cierre con arqueo
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'corsheaders.middleware.CorsMiddleware',  # debe ir ANTES de CommonMiddleware
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'ferreteria.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'ferreteria.wsgi.application'


# ---------------------------------------------------------------------------
# Base de datos: SQLite en desarrollo, PostgreSQL si se define POSTGRES_DB
# ---------------------------------------------------------------------------
if env('POSTGRES_DB'):
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',  # requiere: pip install "psycopg[binary]"
            'NAME': env('POSTGRES_DB'),
            'USER': env('POSTGRES_USER', 'postgres'),
            'PASSWORD': env('POSTGRES_PASSWORD'),
            'HOST': env('POSTGRES_HOST', 'localhost'),
            'PORT': env('POSTGRES_PORT', '5432'),
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }


AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator'},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'},
    {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'},
]

LANGUAGE_CODE = 'es-pe'
TIME_ZONE = 'America/Lima'
USE_I18N = True
USE_TZ = True

STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
AUTH_USER_MODEL = 'cuentas.Usuario'


# ---------------------------------------------------------------------------
# Correo (RF03 - recuperación de contraseña)
# Sin EMAIL_HOST el correo se imprime en la consola del servidor (ideal para la demo).
# ---------------------------------------------------------------------------
if env('EMAIL_HOST'):
    EMAIL_BACKEND = 'django.core.mail.backends.smtp.EmailBackend'
    EMAIL_HOST = env('EMAIL_HOST')
    EMAIL_PORT = int(env('EMAIL_PORT', '587'))
    EMAIL_HOST_USER = env('EMAIL_HOST_USER')
    EMAIL_HOST_PASSWORD = env('EMAIL_HOST_PASSWORD')
    EMAIL_USE_TLS = env_bool('EMAIL_USE_TLS', True)
else:
    EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'
DEFAULT_FROM_EMAIL = env('DEFAULT_FROM_EMAIL', 'Ferretería <no-responder@ferreteria.local>')
# URL del panel React a la que apunta el enlace de recuperación
FRONTEND_URL = env('FRONTEND_URL', 'http://localhost:5173')


# ---------------------------------------------------------------------------
# Django REST Framework + JWT
# ---------------------------------------------------------------------------
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
        'rest_framework.authentication.SessionAuthentication',  # para el navegador DRF / admin
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'DEFAULT_SCHEMA_CLASS': 'drf_spectacular.openapi.AutoSchema',
    'EXCEPTION_HANDLER': 'ferreteria.exceptions.manejador_errores',
}

# Sesión de 30 días que se renueva con el uso: cada vez que la web o la app renuevan el access,
# reciben un refresh nuevo (válido otros 30 días) y el anterior queda anulado (lista negra).
# Quien no entra en 30 días tiene que volver a iniciar sesión.
SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=int(env('JWT_ACCESS_MINUTOS', '60'))),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=int(env('JWT_REFRESH_DIAS', '30'))),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
    'UPDATE_LAST_LOGIN': True,
    'AUTH_HEADER_TYPES': ('Bearer',),
}

SPECTACULAR_SETTINGS = {
    'TITLE': 'API Sistema Ferretería',
    'DESCRIPTION': 'Backend central (Django REST Framework) para el panel web React, '
                   'la app Android y el bot de WhatsApp.',
    'VERSION': '0.9.0',
    'SERVE_INCLUDE_SCHEMA': False,
    'COMPONENT_SPLIT_REQUEST': True,
    'ENUM_NAME_OVERRIDES': {
        'OrigenVentaEnum': 'ventas.models.ORIGENES',
        'MetodoPagoEnum': 'ventas.models.METODOS_PAGO',
    },
}


# ---------------------------------------------------------------------------
# CORS (RF28)
# ---------------------------------------------------------------------------
CORS_ALLOW_ALL_ORIGINS = DEBUG  # en desarrollo no se bloquea nada
CORS_ALLOWED_ORIGINS = env_lista(
    'CORS_ALLOWED_ORIGINS',
    'http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000',
)
CORS_EXPOSE_HEADERS = ['Content-Disposition', 'X-Emisor']  # nombre del PDF y quién lo generó (Spring o Django)


# ---------------------------------------------------------------------------
# Datos del negocio (salen en el comprobante PDF)
# ---------------------------------------------------------------------------
FERRETERIA = {
    'NOMBRE': env('FERRETERIA_NOMBRE', 'FERRETERÍA C&R'),
    'RUC': env('FERRETERIA_RUC', '20000000001'),
    'DIRECCION': env('FERRETERIA_DIRECCION', 'Av. Principal 123'),
    'TELEFONO': env('FERRETERIA_TELEFONO', '999 999 999'),
    'SERIE_BOLETA': env('FERRETERIA_SERIE', 'B001'),
    'SERIE_FACTURA': env('FERRETERIA_SERIE_FACTURA', 'F001'),   # clientes con RUC
    # SUNAT: boletas por encima de este monto deben identificar al comprador (DNI)
    'BOLETA_LIMITE_SIN_DNI': env('FERRETERIA_BOLETA_LIMITE_SIN_DNI', '700'),
    'HORARIO': env('FERRETERIA_HORARIO', 'Lunes a sábado de 8:00 a. m. a 7:00 p. m.'),
    # Horario para el bot (pausa cuando piden asesor): 0 = lunes ... 6 = domingo
    'DIAS_ATENCION': env('FERRETERIA_DIAS_ATENCION', '0-5'),
    'HORA_APERTURA': env('FERRETERIA_HORA_APERTURA', '08:00'),
    'HORA_CIERRE': env('FERRETERIA_HORA_CIERRE', '19:00'),
}
IGV_TASA = env('IGV_TASA', '0.18')


# ---------------------------------------------------------------------------
# WhatsApp Cloud API (Meta) - Sprint 7
# Sin WHATSAPP_TOKEN el bot funciona en modo SIMULADO (responde y guarda, pero no envía).
# ---------------------------------------------------------------------------
WHATSAPP = {
    'TOKEN': env('WHATSAPP_TOKEN'),
    'PHONE_NUMBER_ID': env('WHATSAPP_PHONE_NUMBER_ID'),
    'VERIFY_TOKEN': env('WHATSAPP_VERIFY_TOKEN', 'ferreteria-verify-2026'),
    'APP_SECRET': env('WHATSAPP_APP_SECRET'),
    'API_VERSION': env('WHATSAPP_API_VERSION', 'v21.0'),
}


# ---------------------------------------------------------------------------
# Inteligencia artificial (RF30) - Groq, API compatible con OpenAI
# La usan el bot de WhatsApp (entender al cliente) y el asistente del Administrador.
# Sin GROQ_API_KEY todo funciona igual con el bot por reglas.
# La IA nunca inventa precios ni stock: solo redacta con datos de la base de datos.
# ---------------------------------------------------------------------------
# Caja: si es obligatoria, nadie puede cobrar sin haber abierto su caja.
# En las pruebas automáticas se desactiva por defecto (las de caja la activan).
CAJA = {
    'OBLIGATORIA': env_bool('CAJA_OBLIGATORIA', True) and 'test' not in sys.argv,
}

IA = {
    'API_KEY': env('GROQ_API_KEY'),
    'MODELO': env('GROQ_MODEL') or 'openai/gpt-oss-120b',
    # Si el modelo principal llega a su límite gratuito por minuto, se usa este (tiene su propio cupo)
    'MODELO_RESPALDO': env('GROQ_MODEL_RESPALDO', 'openai/gpt-oss-20b'),
    'URL': env('GROQ_URL', 'https://api.groq.com/openai/v1/chat/completions'),
    'TIMEOUT': int(env('IA_TIMEOUT_SEGUNDOS') or 20),
    # Mensajes previos de la conversación que se envían como contexto ("dame 20" -> ¿de qué?)
    'HISTORIAL': int(env('IA_HISTORIAL_MENSAJES') or 4),
}


# ---------------------------------------------------------------------------
# Microservicio de reportes y auditoría (Spring Boot) - opcional
# Sin MS_AUDITORIA_URL no se envía nada y el sistema funciona igual.
# ---------------------------------------------------------------------------
MS_AUDITORIA = {
    'URL': env('MS_AUDITORIA_URL'),                      # ej. http://localhost:8081/api/auditoria/eventos
    'API_KEY': env('MS_API_KEY', 'clave-servicio-dev-2026'),
    'TIMEOUT': 3,
    'SINCRONO': env_bool('MS_AUDITORIA_SINCRONO', False),  # True solo en pruebas
}

# Facturación y reportes en Spring Boot: el comprobante de cada venta lo emite Spring
# (XML UBL, hash, QR, PDF A5 y ticket). Si Spring no responde, Django entrega su PDF interno.
# Sin MS_REPORTES_URL se deduce de MS_AUDITORIA_URL (misma máquina, mismo puerto).
_ms_base = env('MS_REPORTES_URL') or (MS_AUDITORIA['URL'] or '').split('/auditoria/')[0]
MS_REPORTES = {
    'URL': _ms_base.rstrip('/'),                          # ej. http://localhost:8081/api
    'TIMEOUT': int(env('MS_REPORTES_TIMEOUT') or 8),
}


LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'handlers': {'console': {'class': 'logging.StreamHandler'}},
    'loggers': {
        'ferreteria': {'handlers': ['console'], 'level': 'INFO'},
    },
}

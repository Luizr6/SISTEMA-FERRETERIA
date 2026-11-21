# SISTEMA-FERRETERIA · FERRETERÍA C&R

Proyecto Integrador TECSUP 2026. Sistema de ventas, inventario, caja y atención por WhatsApp para una ferretería.

## Componentes

| Carpeta | Tecnología | Qué hace |
|---|---|---|
| `django/SISTEMA-FERRETERIA` | Django REST Framework + PostgreSQL | Núcleo del sistema y API REST (`/api/`): ventas, stock, pedidos, caja, clientes, usuarios (JWT), bot de WhatsApp con IA (Groq) |
| `Spring/ferreteria-reportes-ms` | Spring Boot 3 | Facturación electrónica simulada (XML UBL 2.1, hash, QR, PDF A5 y ticket 80 mm), reportes en PDF/Excel, cierre del día automático y auditoría |
| `ferreteria-admin` | React + Vite + Tailwind | Panel web para Administrador y Vendedor |
| `FerreteriaCR_app` | Kotlin (Android) | App para vender, ver pedidos y atender WhatsApp desde el celular |

```
App Android ─┐
             ├──> Django (8000) ──> PostgreSQL <── Spring Boot (8081)
Panel web ───┘         │                                ▲
                       └── WhatsApp Cloud API / Groq    └── la web le pide reportes y comprobantes
```

## Cómo levantarlo en local

1. **PostgreSQL** con una base `ferreteria`.
2. **Django**
   ```
   cd django/SISTEMA-FERRETERIA
   python -m venv venv && venv\Scripts\activate
   pip install -r requirements.txt
   copy .env.example .env      (y completar los valores)
   python manage.py migrate
   python manage.py createsuperuser
   python manage.py runserver 0.0.0.0:8000
   ```
3. **Spring Boot**: abrir `Spring/ferreteria-reportes-ms` en Spring Tools / IntelliJ y ejecutar
   `FerreteriaMsApplication` (puerto 8081). Usa la misma base y la misma `DJANGO_SECRET_KEY` que Django.
4. **Web**
   ```
   cd ferreteria-admin
   npm install
   copy .env.example .env
   npm run dev
   ```
5. **App**: abrir `FerreteriaCR_app` en Android Studio, ejecutar y en el login poner la dirección del servidor.

Cada carpeta tiene su propio README con el detalle.

## Importante

- Los archivos `.env` tienen las claves (WhatsApp, Groq, base de datos) y **no se suben** a GitHub.
  Cada uno copia `.env.example` y pone sus valores.
- Los comprobantes son **simulados**: siguen el formato de SUNAT pero no se firman ni se envían.

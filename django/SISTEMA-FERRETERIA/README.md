# SISTEMA-FERRETERIA — Backend (Django REST Framework)

API central (única fuente de verdad) para el panel web React, la app Android y el bot de WhatsApp.

## Puesta en marcha (Windows)
```bash
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python manage.py migrate
python manage.py seed_demo --usuarios     # datos de ejemplo + demo_admin / demo_vendedor (clave: Demo1234!)
python manage.py runserver 0.0.0.0:8000
```
Linux/Mac: `source venv/bin/activate`. Requiere **Python 3.12 o superior** (Django 6.1).

- API: http://127.0.0.1:8000/api/
- Swagger: http://127.0.0.1:8000/api/docs/ · ReDoc: http://127.0.0.1:8000/api/redoc/
- Admin Django: http://127.0.0.1:8000/admin/
- Emulador Android: usar `http://10.0.2.2:8000/api/`
- Pruebas automáticas: `python manage.py test` (35 pruebas)
- Configuración opcional: copiar `.env.example` como `.env`

## Autenticación (JWT)
`POST /api/auth/login/` {username, password} → {access, refresh, user_id, username, rol}
Enviar en cada petición: `Authorization: Bearer <access>`. Renovar en `POST /api/auth/refresh/` {refresh}.

Errores siempre en formato `{codigo, mensaje, detalle}` (RF27).

## Endpoints (todos bajo /api/)
| Módulo | URL | Permisos |
|---|---|---|
| Sesión | `auth/login/` `auth/refresh/` `auth/me/` | Público / autenticado |
| Contraseña (RF03) | `auth/password/reset/` `auth/password/reset/confirm/` `auth/password/change/` | Público / autenticado |
| Usuarios | `usuarios/` (`?rol=`) | Admin |
| Bitácora (RF04) | `bitacora/` (`?accion= &modelo= &usuario= &fecha_desde=`) — paginada | Admin |
| Categorías / Marcas | `categorias/` `marcas/` | Leer: todos · Escribir: Admin |
| Proveedores | `proveedores/` · `proveedores/<id>/productos/` | Leer: todos · Escribir: Admin |
| Productos | `productos/` (`?search= &categoria= &marca= &proveedor= &activo= &stock=agotado\|bajo\|critico\|normal`) | Leer: todos · Escribir: Admin |
| Carga CSV (RF11) | `productos/importar-csv/` (multipart `archivo`) · `productos/plantilla-csv/` | Admin |
| Kardex (RF10) | `movimientos/?producto=&tipo=` · `productos/<id>/kardex/` | Lectura |
| **Compras (Sprint 5)** | `compras/` (`?proveedor= &estado= &fecha_desde= &fecha_hasta=`) | Admin |
| | `compras/<id>/recibir/` {items?} · `compras/<id>/anular/` · `compras/<id>/recepciones/` · `compras/sugerencias/` | Admin |
| Clientes | `clientes/` | Autenticados (eliminar: Admin) |
| **Ventas (Sprint 6)** | `ventas/` (`?fecha_desde= &vendedor= &cliente= &estado= &metodo_pago= &origen= &mias=true &search=`) | Crear/leer: todos |
| | `ventas/resumen/` · `ventas/<id>/comprobante/` (PDF) · `ventas/<id>/anular/` | Anular: Admin |
| **Carrito** | `carrito/` · `carrito/agregar/` · `carrito/items/<id>/` · `carrito/vaciar/` · `carrito/confirmar/` · `carrito/enviar-pedido/` | Autenticados |
| **Pedidos** | `pedidos/` (`?estado=PENDIENTE &origen=`) · `pedidos/resumen/` · `pedidos/<id>/aprobar/` · `rechazar/` · `cancelar/` | Autenticados |
| Dashboard / reportes | `dashboard/` · `reportes/ventas/?agrupar=dia\|semana\|mes` · `reportes/margen/` | Todos / margen: Admin |
| **WhatsApp (Sprint 7)** | `whatsapp/webhook/` (Meta) · `whatsapp/simular/` · `whatsapp/estado/` · `whatsapp/conversaciones/` | Webhook público / resto autenticado |

## Reglas de negocio clave
- `precio_venta` del catálogo es **sin IGV**; la venta suma IGV (18 %, configurable con `IGV_TASA`).
- Venta, recepción de compra, anulación y aprobación de pedido son **transacciones atómicas**: o se hace todo o nada.
- Un **pedido** (web, móvil o WhatsApp) **no toca stock** hasta que un usuario lo aprueba; al aprobarlo se crea la venta.
- Una **orden de compra** solo se edita o anula si está PENDIENTE; la recepción puede ser parcial (estado PARCIAL).
- Todo cambio de stock queda en el kardex y toda acción crítica en la bitácora.

## Estructura
```
ferreteria/   configuración (settings lee .env), urls, manejador de errores
cuentas/      usuario con rol, login JWT, permisos, recuperación de contraseña
auditoria/    bitácora + AuditoriaMixin
productos/    categorías, marcas, proveedores, productos, kardex, carga CSV, seed_demo
compras/      órdenes de compra, recepciones, sugerencias de abastecimiento
ventas/       clientes, ventas, carrito, pedidos, comprobante PDF, dashboard, reportes
whatsapp/     webhook Cloud API, bot de consultas por reglas, conversaciones
```

## Microservicio de reportes y auditoría (Spring Boot, opcional)
El proyecto `ferreteria-reportes-ms` lee esta misma base (PostgreSQL) para reportes en PDF y guarda una bitácora independiente.
Para que Django le envíe eventos (login fallido, cambio de precio, aprobación de pedidos…), agrega al `.env`:
```
MS_AUDITORIA_URL=http://localhost:8081/api/auditoria/eventos
MS_API_KEY=clave-servicio-dev-2026
```
El envío va en segundo plano (`auditoria/ms_cliente.py`): si el microservicio está apagado, Django funciona igual.
Requiere usar PostgreSQL (`POSTGRES_DB=...` en `.env` y `pip install "psycopg[binary]"`).

# ferreteria-reportes-ms: microservicio Spring Boot

Microservicio complementario del SISTEMA-FERRETERIA. **Django sigue siendo el núcleo y la única fuente de verdad.**
Este servicio solo:

1. **Reportes gerenciales:** lee (sin modificar) las tablas de Django en PostgreSQL y genera reportes en JSON y PDF.
2. **Auditoría independiente:** guarda eventos en su **propia tabla** `ms_evento_auditoria` (login fallido,
   cambio de precio, aprobación de pedidos, etc.) y detecta varios intentos fallidos seguidos.

```
                       JWT (el mismo de Django)
 React / Postman  ───────────────────────────────►  Spring Boot :8081  ──── lectura ────┐
                                                        ▲   │                            ▼
 Django :8000 ── eventos (X-API-Key) ───────────────────┘   └── escribe ──► ms_evento_auditoria
       │                                                                     (tabla propia)
       └──────────────── lectura/escritura ──────────► PostgreSQL "ferreteria"
```

## Requisitos
- **Java 17 o superior** y **Maven 3.9+**. IntelliJ IDEA ya trae Maven incluido.
- **PostgreSQL** con la base `ferreteria`, la misma que usa Django.

## 1. Pasar Django a PostgreSQL (una sola vez)
En el `.env` del backend Django:
```
POSTGRES_DB=ferreteria
POSTGRES_USER=postgres
POSTGRES_PASSWORD=tu_clave
POSTGRES_HOST=localhost
DJANGO_SECRET_KEY=una-clave-larga-de-al-menos-32-caracteres
MS_AUDITORIA_URL=http://localhost:8081/api/auditoria/eventos
MS_API_KEY=clave-servicio-dev-2026
```
```bash
pip install "psycopg[binary]"
createdb -U postgres ferreteria            # o desde pgAdmin
python manage.py migrate
python manage.py seed_demo --usuarios
python manage.py runserver
```
Si no tienes PostgreSQL instalado, puedes levantarlo con Docker: `docker compose up -d` en esta carpeta.

## 2. Levantar el microservicio
Las variables deben coincidir con las de Django. La más importante es **`DJANGO_SECRET_KEY`**, porque sin ella no se pueden validar los tokens.

**Windows (PowerShell)**
```powershell
$env:DB_PASSWORD="tu_clave"
$env:DJANGO_SECRET_KEY="la-misma-que-en-django"
mvn spring-boot:run
```
**Linux / Mac**
```bash
DB_PASSWORD=tu_clave DJANGO_SECRET_KEY=la-misma-que-en-django mvn spring-boot:run
```
Pruebas: `mvn test`. Se genera el jar con `mvn package`, y se ejecuta con `java -jar target/ferreteria-reportes-ms-0.1.0.jar`.

| Variable | Por defecto | Para qué |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | localhost, 5432, ferreteria, postgres, postgres | Conexión a la base de Django |
| `DJANGO_SECRET_KEY` | la clave de desarrollo del backend | Validar el JWT (HS256) |
| `MS_API_KEY` | `clave-servicio-dev-2026` | Django la envía en `X-API-Key` al registrar eventos |
| `PORT` | 8081 | Puerto del servicio |

## 3. Endpoints
Todos usan el **token de Django**: `Authorization: Bearer <access>`, el mismo que devuelve `POST /api/auth/login/` en Django.

| Método y ruta | Quién | Qué devuelve |
|---|---|---|
| `GET /api/salud` | Público | Estado del servicio y de la base |
| `GET /api/reportes/ventas-dia?fecha=2026-10-27` | ADMIN | Totales, IGV, ticket, por pago, vendedor, canal, hora y top 10 |
| `GET /api/reportes/ventas-dia/pdf?fecha=…` | ADMIN | El mismo reporte en PDF |
| `GET /api/reportes/stock-critico` | ADMIN | Productos por reponer con cantidad y costo sugeridos |
| `GET /api/reportes/stock-critico/pdf` | ADMIN | PDF |
| `POST /api/auditoria/eventos` | Django (`X-API-Key`) o usuario con JWT | Registra un evento |
| `GET /api/auditoria/eventos?tipo=&nivel=&usuario=&modulo=&desde=&hasta=&page=0&size=20` | ADMIN | Eventos paginados |
| `GET /api/auditoria/eventos/{id}` | ADMIN | Un evento |
| `GET /api/auditoria/resumen?dias=7` | ADMIN | Conteo por tipo y nivel, y últimos eventos críticos |
| `GET /api/auditoria/tipos` | ADMIN | Tipos de evento sugeridos |

- Documentación interactiva: http://localhost:8081/swagger-ui.html
- Los errores salen con el mismo formato que Django: `{codigo, mensaje, detalle}`.
- El JSON usa snake_case, igual que Django.

### Ejemplo de evento
```json
POST /api/auditoria/eventos
X-API-Key: clave-servicio-dev-2026

{"tipo": "PRECIO_MODIFICADO", "descripcion": "MAR-001: venta S/ 32.00 -> S/ 35.00",
 "usuario": "demo_admin", "usuario_id": 1, "modulo": "productos",
 "entidad": "Producto", "entidad_id": "7",
 "datos": {"precio_venta_antes": "32.00", "precio_venta_despues": "35.00"}}
```
Si se reciben 5 `LOGIN_FALLIDO` del mismo usuario en 10 minutos, el evento se guarda como **CRITICO**. Ambos valores son configurables.

## 4. Qué envía Django automáticamente
Con `MS_AUDITORIA_URL` configurado, el backend envía eventos en segundo plano, con un timeout de 3 s. Si el microservicio está caído, Django sigue funcionando igual.

| Evento | Cuándo |
|---|---|
| `LOGIN_FALLIDO` | Contraseña incorrecta. Se envía el usuario intentado, **nunca la contraseña** |
| `LOGIN_EXITOSO` | Inicio de sesión |
| `PRECIO_MODIFICADO` | Al editar un producto y cambiar su costo o precio de venta, con los valores anterior y nuevo |
| `PEDIDO_APROBADO`, `PEDIDO_RECHAZADO` | Bandeja de pedidos |
| `VENTA_REGISTRADA`, `VENTA_ANULADA`, `COMPRA_RECIBIDA`, `IMPORTACION_CSV`, `REGISTRO_*` | Todo lo que también va a la bitácora de Django |

Código del lado Django: `auditoria/ms_cliente.py`.

## 5. Estructura
```
src/main/java/pe/tecsup/ferreteria/ms/
├─ FerreteriaMsApplication.java
├─ config/        FerreteriaProperties (ferreteria.*), WebConfig (CORS + interceptor)
├─ seguridad/     JwtDjangoService (valida el JWT de Django), AutenticacionInterceptor, UsuarioToken
├─ dominio/
│  ├─ django/     Venta, DetalleVenta, Producto, Categoria, Proveedor, Cliente, Usuario  (@Immutable: solo lectura)
│  └─ auditoria/  EventoAuditoria, NivelEvento                                       (tabla propia)
├─ repositorio/   VentaRepository, ProductoRepository (sin save/delete), EventoAuditoriaRepository
├─ servicio/      ReporteService, PdfService (OpenPDF), AuditoriaService
└─ web/           ReporteController, AuditoriaController, SaludController, ManejadorErrores, dto/
src/main/resources/
├─ application.properties
└─ schema.sql     crea ms_evento_auditoria si no existe
```

## 6. Decisiones de diseño
- **Solo lectura sobre Django:** `ddl-auto=none`, entidades `@Immutable` y repositorios sin métodos de escritura. Así Spring no puede alterar el stock ni las ventas.
- **No se mapea la contraseña** de `cuentas_usuario`.
- **Un solo login:** se valida el mismo JWT de SimpleJWT (HS256 con el `SECRET_KEY` de Django). Los reportes y la lectura de la bitácora exigen rol ADMIN.
- **Fechas:** Django guarda en UTC, y los reportes cortan el día en hora de Lima (`ferreteria.zona-horaria`).

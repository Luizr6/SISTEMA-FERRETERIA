# ferreteria-reportes-ms: servicio de facturación, reportes y auditoría (Spring Boot)

El SISTEMA-FERRETERIA reparte el trabajo entre dos backends:

| Django (:8000) | Spring Boot (:8081) |
|---|---|
| Núcleo transaccional: ventas, stock, pedidos, caja, usuarios | **Facturación**: emite el comprobante electrónico de cada venta |
| WhatsApp e IA | **Reportes gerenciales** en JSON, PDF y Excel |
| Login (emite el JWT) | **Procesos programados**: facturación automática y cierre del día |
| | **Auditoría** independiente |

Spring **lee** las tablas de Django sin modificarlas y **escribe solo en sus tablas** (prefijo `ms_`):

1. **Facturación (`ms_comprobante`):** por cada venta arma el XML en formato **UBL 2.1** (estándar SUNAT),
   calcula el **código hash** (SHA-256), genera el **código QR** y la representación impresa en **A5** o
   **ticket de 80 mm** para la impresora térmica. Boleta (03) o factura (01) si el cliente tiene RUC.
   Cuando una venta se anula en Django, el comprobante queda dado de baja. *Simulado:* no se firma con
   certificado digital ni se envía a SUNAT.
2. **Reportes:** ventas por rango (por día, vendedor, categoría, forma de pago y canal), arqueos de caja con
   faltantes por vendedor, kardex por producto y stock crítico. Exporta a PDF (OpenPDF) y Excel (Apache POI).
3. **Procesos programados (`@Scheduled`):** cada minuto emite los comprobantes pendientes; a las 23:00 genera el
   **cierre del día** (`ms_cierre_diario`) con alertas: cajas sin cerrar, faltantes, productos agotados.
4. **Auditoría (`ms_evento_auditoria`):** login fallido, cambio de precio, aprobación de pedidos, etc.,
   y detecta varios intentos fallidos seguidos.

La serie y el número del comprobante los pone Django al registrar la venta. Así una venta nunca se detiene
aunque Spring esté apagado: al volver, la tarea programada emite los pendientes. Y si Spring no responde,
Django entrega su PDF interno de respaldo.

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
| `FERRETERIA_NOMBRE` `FERRETERIA_RUC` `FERRETERIA_DIRECCION` `FERRETERIA_TELEFONO` | los de Django | Datos impresos en los comprobantes |
| `TAREAS_ACTIVAS` | true | Apaga la facturación automática y el cierre programado |

## 3. Endpoints
Todos usan el **token de Django**: `Authorization: Bearer <access>`, el mismo que devuelve `POST /api/auth/login/` en Django.

| Método y ruta | Quién | Qué devuelve |
|---|---|---|
| `GET /api/salud` | Público | Estado del servicio y de la base |
| `GET /api/comprobantes/venta/{id}` | ADMIN o VENDEDOR | Datos del comprobante (lo emite si no existe): número, hash, texto del QR, total en letras |
| `GET /api/comprobantes/venta/{id}/pdf?formato=A5\|TICKET` | ADMIN o VENDEDOR | Representación impresa (A5 o ticket de 80 mm) |
| `GET /api/comprobantes/venta/{id}/xml` | ADMIN o VENDEDOR | XML UBL 2.1 |
| `GET /api/comprobantes?estado=&tipo=&desde=&hasta=` | ADMIN | Registro de comprobantes emitidos |
| `POST /api/comprobantes/procesar` | ADMIN | Emitir pendientes y dar de baja anulados ahora |
| `GET /api/reportes/ventas?desde=&hasta=` | ADMIN | Ventas del rango por día, vendedor, categoría, pago, canal y top 20 |
| `GET /api/reportes/ventas/pdf` · `/ventas/excel` | ADMIN | Lo mismo en PDF o Excel (el Excel trae además cada venta) |
| `GET /api/reportes/cajas?desde=&hasta=` | ADMIN | Arqueos: esperado, contado, faltante/sobrante, por vendedor |
| `GET /api/reportes/cajas/pdf` · `/cajas/excel` | ADMIN | PDF o Excel |
| `GET /api/reportes/kardex?codigo=&desde=&hasta=` · `/kardex/excel` | ADMIN | Kardex de un producto |
| `GET /api/cierres` | ADMIN | Últimos 60 cierres del día |
| `POST /api/cierres/ejecutar?fecha=` | ADMIN | Generar el cierre ahora (para la demostración) |
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
│  ├─ django/       Venta, DetalleVenta, Producto, Categoria, Proveedor, Cliente, Usuario,
│  │                Caja, MovimientoCaja, MovimientoInventario          (@Immutable: solo lectura)
│  ├─ facturacion/  Comprobante                                          (tabla propia ms_comprobante)
│  ├─ cierre/       CierreDiario                                         (tabla propia ms_cierre_diario)
│  └─ auditoria/    EventoAuditoria, NivelEvento                         (tabla propia ms_evento_auditoria)
├─ repositorio/     lectura de Django (sin save/delete) y repositorios JPA de las tablas propias
├─ servicio/
│  ├─ facturacion/  ComprobanteService (XML UBL, hash), ComprobantePdfService (A5 y ticket), QrService, NumeroALetras
│  ├─ ReporteService, PdfService (OpenPDF), ExcelService (Apache POI)
│  ├─ CierreService, TareasProgramadas (@Scheduled)
│  └─ AuditoriaService
└─ web/             ComprobanteController, ReporteController, CierreController, AuditoriaController,
                    SaludController, ManejadorErrores, dto/
src/main/resources/
├─ application.properties
└─ schema.sql       crea las tablas ms_* si no existen
```

## 6. Decisiones de diseño
- **Cada backend con su responsabilidad:** Django registra (transacciones), Spring factura, reporta y audita.
  Se comunican por REST y comparten el mismo login (JWT).
- **Solo lectura sobre Django:** `ddl-auto=none`, entidades `@Immutable` y repositorios sin métodos de escritura. Así Spring no puede alterar el stock ni las ventas.
- **No se mapea la contraseña** de `cuentas_usuario`.
- **Un solo login:** se valida el mismo JWT de SimpleJWT (HS256 con el `SECRET_KEY` de Django). Los reportes, los cierres y la bitácora exigen rol ADMIN; el comprobante de una venta lo puede pedir también el Vendedor.
- **Fechas:** Django guarda en UTC, y los reportes cortan el día en hora de Lima (`ferreteria.zona-horaria`).

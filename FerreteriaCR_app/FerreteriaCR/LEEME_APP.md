# App móvil Ferretería C&R (v2.0)

App Android en Kotlin para **vendedores y administradores**. Usa la misma API REST de Django que el panel web:
no tiene base de datos propia ni habla con la IA o con Meta directamente.

## Cómo abrirla y correrla

1. En Android Studio: **File > Open** y elige la carpeta `FerreteriaCR`.
2. Espera a que termine "Gradle sync" (la primera vez descarga librerías, necesita internet).
3. Levanta Django (`python manage.py runserver`).
4. Elige un emulador y presiona **Run ▶**.

### Dirección del servidor (botón "Servidor" en el login)

| Dónde corre la app | Dirección |
| --- | --- |
| Emulador de Android Studio | `http://10.0.2.2:8000/` (viene por defecto) |
| Celular en la misma Wi-Fi que la PC | `http://IP-de-la-PC:8000/` (Django con `runserver 0.0.0.0:8000`) |
| Celular con datos móviles | La URL de ngrok: `https://product-quarry-corporal.ngrok-free.dev/` |

## Ingreso

- **Primera vez:** usuario y contraseña de Django. Luego se crea un **PIN de 4 dígitos**.
- **Después:** al abrir la app o al volver después de **30 minutos** fuera, se pide solo el PIN.
  Si vuelve antes de 30 minutos (por ejemplo, desde WhatsApp), entra directo.
- 5 intentos fallidos de PIN cierran la sesión.
- La vista de **Vendedor** o **Administrador** se abre según la cuenta.
- Si el administrador desactiva al usuario en la web, el celular pierde el acceso.

## Pestañas

| Pestaña | Vendedor | Administrador |
| --- | --- | --- |
| **Inicio** | Mis ventas de hoy, pedidos por confirmar, mensajes sin leer, cuadre del día por forma de pago | Ventas de la tienda vs ayer, pedidos, agotados, **asistente IA** y **alertas** |
| **Vender** | Buscador, categorías, +/−, cobro (cliente, forma de pago, vuelto) y boleta PDF | Igual |
| **Pedidos** | Lista (por confirmar / aprobados / todos, filtro por origen) → detalle → aprobar y cobrar o rechazar | Igual |
| **WhatsApp** | Lista de chats (no leídos, bot en pausa) → chat: responder y pausar o activar el bot | Igual |
| **Más** | Mi día, consultar precio, historial de ventas, clientes, cambiar PIN, cerrar sesión | Además: ventas por vendedor y anular ventas |

**Notificaciones (cada 15 minutos):** pedidos por confirmar y mensajes de WhatsApp sin leer; el administrador
recibe además las alertas de stock agotado o mínimo, compras atrasadas y ventas bajas.

## Pendiente (requiere cambios en el backend)

- **Caja** (abrir, movimientos y cierre con arqueo). Por ahora la app muestra el "cuadre del día"
  calculado con las ventas.
- **Pausa automática del bot** cuando el cliente pide un asesor (con horario de atención).
- **Sesión renovable de 30 días** (`ROTATE_REFRESH_TOKENS`). Hoy la sesión dura 7 días; la app ya está lista
  para el cambio.

## Estructura

```
data/        Repositorio (único acceso a datos), modelos, Retrofit + renovación de token, sesión y PIN
ui/          login, pin, main (pestañas), inicio, asistente, vender, pedidos, whatsapp, mas, ventas, clientes
notificaciones/AlertasWorker.kt   avisos cada 15 minutos (WorkManager)
```

# Panel web: Ferretería (React + Vite + Tailwind)

Cliente web del SISTEMA-FERRETERIA. Consume la API REST de Django con JWT. No tiene lógica de negocio propia:
IGV, stock y totales los calcula siempre el backend.

## 1. Cómo correrlo
Requisitos: Node.js 18 o superior (recomendado 20 o 22), con el backend corriendo en el puerto 8000.

```bash
npm install                 # no hay dependencias nuevas: si ya tenías node_modules, puedes saltarlo
copy .env.example .env      # Linux/Mac: cp .env.example .env
npm run dev                 # abre http://localhost:5173
```
Usuarios de demo (`python manage.py seed_demo --usuarios` en el backend): `demo_admin` y `demo_vendedor`, clave `Demo1234!`.

## 2. Pantallas
| Pantalla | Qué hace | Sprint / RF |
|---|---|---|
| Ingreso y recuperar contraseña | Login JWT y enlace por correo (`/reset-password`) | S2 · RF01, RF03 |
| Inicio | 4 métricas (hoy, mes, pedidos, stock crítico), ventas de 7 días, más vendidos y productos por reponer | S4 · RF22 |
| Vender | Punto de venta con carrito en el servidor, método de pago, cliente, cobrar o guardar como pedido | S6 · FER-45, CU08 |
| Pedidos | Bandeja de pedidos web, móvil y WhatsApp: aprobar (genera boleta) o rechazar | S6 · FER-46, RF33 |
| WhatsApp | Conversaciones del bot, respuesta manual, pausa del bot y simulador para la demo | S7 · FER-54 |
| Historial de ventas | Filtros por fecha, vendedor, pago y origen, resumen, PDF y anulación | S6 · FER-47, FER-49 |
| Productos | Catálogo con stock en "wincha", kardex por producto e importación CSV | S3 · RF06–RF11 |
| Compras (admin) | Órdenes de compra, recepción total o parcial y "lo que falta reponer" | S5 · FER-40, FER-41 |
| Proveedores y clientes | Fichas y productos de cada proveedor | S5 · RF12, RF16 |
| Reportes (admin) | Ventas diarias, semanales o mensuales y margen por producto | RF23, RF24 |
| Usuarios y bitácora (admin) | Usuarios con rol y registro de acciones | RF02, RF04 |

El vendedor no ve Compras, Reportes, Usuarios ni Bitácora: el menú las oculta, las rutas lo redirigen y el backend igual lo valida.

## 3. Diseño y modo oscuro
Estilo SaaS sobrio: grises neutros, tarjetas con borde fino, acento **azul corporativo** (`#2563EB`) y **verde esmeralda**
para estados correctos; ámbar y rojo solo para alertas. Tipografía *Geist*.

- Todos los colores son variables CSS definidas en `src/index.css` (`:root` para claro, `.dark` para oscuro) y
  expuestas a Tailwind en `tailwind.config.js` (`bg-superficie`, `text-gris-900`, `bg-acento-600`, etc.).
  Por eso cualquier componente nuevo que use estas clases funciona en ambos modos sin escribir `dark:` en cada elemento.
- `darkMode: 'class'`: el botón **Modo oscuro / Modo claro** (barra lateral, cabecera móvil y login) pone la clase `dark`
  en `<html>`, la recuerda en el navegador y la primera vez respeta la preferencia del sistema operativo.
- Los gráficos (Recharts) toman sus colores de `useColoresGrafico()` en `src/context/TemaContext.jsx`.

## 4. Estructura
```
src/
├─ services/    api.js (Axios + JWT + refresh), authService, catalogo.js, comercial.js
├─ context/     AuthContext (sesión y rol), AvisosContext (contadores del menú), TemaContext (modo oscuro)
├─ hooks/       useCarga (carga datos con aviso de error y búsqueda con retraso)
├─ components/
│  ├─ layout/   Layout, Sidebar, CambiarClave
│  ├─ ui/       Modal, Toast, Wincha (indicador de stock), Badge/Estado, Filtros, Spinner/Vacio/Aviso, PageHeader, StatCard (métrica)
│  ├─ productos/ ProductoForm, Kardex, ImportarCsv
│  ├─ compras/  OrdenForm, DetalleOrden
│  └─ ventas/   ClienteForm
├─ pages/       Login, RecuperarClave, Dashboard, Vender, Pedidos, WhatsApp, Ventas, Productos,
│               Compras, Proveedores, Clientes, Reportes, Usuarios, Bitacora
└─ utils/format.js  moneda S/, fechas, métodos de pago y mensajes de error de la API
```

## 5. Producción
`npm run build` genera `dist/`. Agrega el dominio a `CORS_ALLOWED_ORIGINS` en el `.env` del backend.

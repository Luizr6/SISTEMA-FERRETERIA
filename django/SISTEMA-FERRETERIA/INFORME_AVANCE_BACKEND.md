# Informe de avance del backend: Segundo Avance

**Fecha:** 23/10/2026 · **Versión de la API:** 0.9.0 · **Pruebas automáticas:** 28 de 28 aprobadas

## Estado de partida
El backend ya tenía funcionando el login JWT, los roles, los productos, el kardex, las ventas con IGV, la anulación de ventas y el dashboard, con 6 pruebas que pasaban.
**Faltaba:** todo el módulo de compras, el carrito, los pedidos, el comprobante PDF y varios RF del documento de requerimientos (RF03, RF04, RF11, RF15, RF23 y RF24).

## Sprint 5: Proveedores, Compras y Abastecimiento ✅ cerrado
| Tarea | Qué se hizo | RF |
|---|---|---|
| FER-39 CRUD de proveedores | Ya existía. Se agregaron el conteo de productos, la ruta `proveedores/<id>/productos/`, el filtro `?activo=` y el registro en la bitácora | RF12 |
| FER-40 Órdenes de compra | Nueva app `compras`: crear, editar y anular órdenes (solo si están PENDIENTES). Numeración OC-000001 y total automático. Historial filtrado por proveedor, estado y fechas | RF13, RF15, CU10 |
| FER-41 Stock automático | `compras/<id>/recibir/` acepta recepción total o parcial. Sube el stock, registra INGRESO en el kardex, actualiza el costo y cambia el estado a PARCIAL o RECIBIDA, todo en una transacción atómica | RF14, CU11 |
| Extra | `compras/sugerencias/` lista los productos bajo el stock mínimo con una cantidad sugerida para reponer | RF08 |

## Sprint 6: Ventas avanzadas y órdenes web ✅ backend terminado
| Tarea | Qué se hizo | RF |
|---|---|---|
| FER-45 Carrito y pedidos web | Carrito guardado en el servidor por usuario (web y móvil comparten el mismo). Se puede confirmar como venta o enviar como pedido. Los pedidos (`pedidos/`) **no tocan el stock** | RF17, RF32 |
| FER-46 Aprobación de pedidos | `aprobar` genera la venta formal y descuenta el stock. `rechazar` y `cancelar` cierran el pedido sin venta. `pedidos/resumen/` da el contador para la notificación | RF33, CU17 |
| FER-47 Historial y filtros | Filtros por vendedor, cliente, fechas, método de pago, origen, `?mias=true` y búsqueda. `ventas/resumen/` da los totales por vendedor y por método de pago | RF21, CU13 |
| FER-48 Venta móvil | Endpoints listos para Android: `ventas/` con `origen=MOVIL` y el carrito. **Falta la parte de Alessandro en Kotlin** | RF17 |
| FER-49 Comprobante PDF | `ventas/<id>/comprobante/` genera una boleta interna en A5 con IGV, el total en letras y una marca si está anulada | RF20 |
| Extra | Las ventas ahora guardan el método de pago (Efectivo, Yape, Plin, Tarjeta, Transferencia) y el origen (Mostrador, Móvil, Web, WhatsApp) | — |

## RF del documento que estaban pendientes
| RF | Qué se hizo |
|---|---|
| RF03 Recuperar contraseña | Recuperación por correo con un token de un solo uso y cambio de contraseña con sesión iniciada |
| RF04 / RNF03 Bitácora | Nueva app `auditoria` que registra quién, qué, cuándo y desde qué IP. Endpoint `bitacora/` solo para Admin |
| RF11 Carga CSV | Importa productos fila por fila con reporte de errores. Acepta separador `,` o `;` y hay una plantilla descargable |
| RF23 Reporte por periodo | `reportes/ventas/?agrupar=dia|semana|mes` |
| RF24 Margen por producto | `reportes/margen/` con margen, porcentaje, utilidad estimada y alertas de margen bajo |
| RF25 Swagger | Todas las rutas aparecen en `/api/docs/`. Antes faltaban el dashboard y otras |
| RNF08 Portabilidad | Configuración con `.env` (`.env.example`) y PostgreSQL opcional |

## Sprint 7: WhatsApp 🟡 base adelantada
| Tarea | Estado |
|---|---|
| FER-51 Webhook | ✅ `whatsapp/webhook/` con verificación de Meta, validación de firma y protección contra mensajes duplicados |
| FER-52 Cloud API | ✅ Cliente para la API oficial de Meta. Sin credenciales funciona en **modo SIMULADO** |
| FER-53 Consultas por chat | ✅ Versión por reglas: consulta precio y stock reales, registra pedidos como PENDIENTES y consulta el estado de un pedido. Queda aislada para reemplazarla por IA (RF30) |
| FER-54 Panel de conversaciones | 🟡 API lista (`whatsapp/conversaciones/`, `responder`, `simular`). **Falta la pantalla en React** |

## Otros cambios
- Se eliminó la app `usuarios`, que estaba vacía y no estaba instalada.
- La clave secreta de Django ya no está fija en el código: se lee del `.env`.
- Se amplió `seed_demo` con 14 productos, 3 proveedores, 4 clientes, 3 órdenes de compra, 20 ventas y 2 pedidos pendientes.
- La base de datos `db.sqlite3` existente se migró sin perder datos.

## Pendientes para el equipo
1. **Franco:** pantallas de Compras, Carrito, Pedidos, Historial con PDF y Conversaciones de WhatsApp.
2. **Alessandro:** FER-48, que consume `ventas/` o `carrito/` con `origen=MOVIL`.
3. **Sprint 7 real:** crear la app en Meta for Developers y exponer el webhook con HTTPS (ngrok o despliegue).
4. **Sprint 8:** PostgreSQL, despliegue y pruebas de interfaz.

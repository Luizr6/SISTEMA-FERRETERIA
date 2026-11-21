import axios from 'axios'
import api, { renovarSesion, storage } from './api'

const d = (r) => r.data

// Servicio de facturación y reportes en Spring Boot (puerto 8081). Usa el mismo token de Django.
export const REPORTES_URL = import.meta.env.VITE_REPORTES_URL || 'http://localhost:8081/api'

const spring = axios.create({ baseURL: REPORTES_URL, timeout: 20000 })
spring.interceptors.request.use((config) => {
  if (storage.access) config.headers.Authorization = `Bearer ${storage.access}`
  return config
})
// Si el token venció se renueva una vez con Django. Un 401 de Spring nunca cierra la sesión.
spring.interceptors.response.use((r) => r, async (error) => {
  const original = error.config
  if (error.response?.status === 401 && original && !original._retry && storage.refresh) {
    original._retry = true
    try {
      original.headers.Authorization = `Bearer ${await renovarSesion()}`
      return spring(original)
    } catch {
      // se informa el error original
    }
  }
  return Promise.reject(error)
})

/** Descarga un archivo (PDF o Excel) que exige el token. */
async function descargar(ruta, params, nombre) {
  const { data } = await spring.get(ruta, { params, responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export const ventasService = {
  listar: (params = {}) => api.get('/ventas/', { params }).then(d),
  obtener: (id) => api.get(`/ventas/${id}/`).then(d),
  // { cliente, metodo_pago, origen, observacion, detalles: [{producto, cantidad}] }
  crear: (data) => api.post('/ventas/', data).then(d),
  anular: (id, motivo) => api.post(`/ventas/${id}/anular/`, { motivo }).then(d),
  resumen: (params = {}) => api.get('/ventas/resumen/', { params }).then(d),
  /**
   * Abre el comprobante en otra pestaña. Lo emite el servicio de facturación (Spring Boot) con QR y hash;
   * si Spring no responde, se usa el PDF interno de Django. formato: 'A5' (hoja) o 'TICKET' (80 mm).
   */
  async abrirComprobante(id, numero, formato = 'A5') {
    const ventana = window.open('', '_blank')
    let data
    try {
      ({ data } = await spring.get(`/comprobantes/venta/${id}/pdf`, { params: { formato }, responseType: 'blob' }))
    } catch {
      ({ data } = await api.get(`/ventas/${id}/comprobante/`, { params: { formato }, responseType: 'blob' }))
    }
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    if (ventana) ventana.location.href = url
    else {
      const a = document.createElement('a')
      a.href = url
      a.download = `${formato === 'TICKET' ? 'ticket' : 'comprobante'}_${numero || id}.pdf`
      a.click()
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },
}

export const carritoService = {
  ver: () => api.get('/carrito/').then(d),
  agregar: (producto, cantidad = 1, reemplazar = false) =>
    api.post('/carrito/agregar/', { producto, cantidad, reemplazar }).then(d),
  cambiar: (itemId, cantidad) => api.patch(`/carrito/items/${itemId}/`, { cantidad }).then(d),
  quitar: (itemId) => api.delete(`/carrito/items/${itemId}/`).then(d),
  vaciar: () => api.post('/carrito/vaciar/').then(d),
  confirmar: (data) => api.post('/carrito/confirmar/', data).then(d),
  enviarPedido: (data) => api.post('/carrito/enviar-pedido/', data).then(d),
}

export const pedidosService = {
  listar: (params = {}) => api.get('/pedidos/', { params }).then(d),
  resumen: () => api.get('/pedidos/resumen/').then(d),
  aprobar: (id, data) => api.post(`/pedidos/${id}/aprobar/`, data).then(d),
  rechazar: (id, motivo) => api.post(`/pedidos/${id}/rechazar/`, { motivo }).then(d),
  cancelar: (id, motivo) => api.post(`/pedidos/${id}/cancelar/`, { motivo }).then(d),
}

export const clientesService = {
  listar: (params = {}) => api.get('/clientes/', { params }).then(d),
  crear: (data) => api.post('/clientes/', data).then(d),
  actualizar: (id, data) => api.put(`/clientes/${id}/`, data).then(d),
  eliminar: (id) => api.delete(`/clientes/${id}/`),
}

export const dashboardService = {
  obtener: () => api.get('/dashboard/').then(d),
}

export const reportesService = {
  ventas: (params = {}) => api.get('/reportes/ventas/', { params }).then(d),
  margen: (params = {}) => api.get('/reportes/margen/', { params }).then(d),
}

export const whatsappService = {
  estado: () => api.get('/whatsapp/estado/').then(d),
  conversaciones: (params = {}) => api.get('/whatsapp/conversaciones/', { params }).then(d),
  mensajes: (id) => api.get(`/whatsapp/conversaciones/${id}/mensajes/`).then(d),
  responder: (id, texto) => api.post(`/whatsapp/conversaciones/${id}/responder/`, { texto }).then(d),
  cambiarBot: (id, bot_activo) => api.patch(`/whatsapp/conversaciones/${id}/`, { bot_activo }).then(d),
  simular: (data) => api.post('/whatsapp/simular/', data).then(d),
  eliminarConversacion: (id) => api.delete(`/whatsapp/conversaciones/${id}/`),
  eliminarMensaje: (id, mensajeId) => api.delete(`/whatsapp/conversaciones/${id}/mensajes/${mensajeId}/`),
}

export const usuariosService = {
  listar: (params = {}) => api.get('/usuarios/', { params }).then(d),
  crear: (data) => api.post('/usuarios/', data).then(d),
  actualizar: (id, data) => api.patch(`/usuarios/${id}/`, data).then(d),
  eliminar: (id) => api.delete(`/usuarios/${id}/`),
  bitacora: (params = {}) => api.get('/bitacora/', { params }).then(d),
}

export const reportesPdfService = {
  ventasDia: () => spring.get(`/reportes/ventas-dia/pdf`, { responseType: 'blob' }),
  stockCritico: () => spring.get(`/reportes/stock-critico/pdf`, { responseType: 'blob' }),
}

// --- Spring Boot: reportes gerenciales, comprobantes y cierres (solo Administrador) ---
export const gerencialService = {
  salud: () => axios.get(`${REPORTES_URL}/salud`, { timeout: 4000 }).then(d),
  ventas: (params) => spring.get(`/reportes/ventas`, { params }).then(d),
  cajas: (params) => spring.get(`/reportes/cajas`, { params }).then(d),
  kardex: (params) => spring.get(`/reportes/kardex`, { params }).then(d),
  // tipo: 'ventas' | 'cajas' | 'kardex' ; formato: 'pdf' | 'excel'
  exportar: (tipo, formato, params, nombre) => descargar(`/reportes/${tipo}/${formato}`, params, nombre),
}

export const facturacionService = {
  deVenta: (ventaId) => spring.get(`/comprobantes/venta/${ventaId}`).then(d),
  registro: (params) => spring.get(`/comprobantes`, { params }).then(d),
  procesar: () => spring.post(`/comprobantes/procesar`).then(d),
  xml: (ventaId, numero) => descargar(`/comprobantes/venta/${ventaId}/xml`, {}, `${numero || ventaId}.xml`),
}

export const cierresService = {
  listar: () => spring.get(`/cierres`).then(d),
  ejecutar: (fecha) => spring.post(`/cierres/ejecutar`, null, { params: fecha ? { fecha } : {} }).then(d),
}
// Caja de cada vendedor: apertura, movimientos (ingreso/egreso) y cierre con arqueo
export const cajaService = {
  actual: () => api.get('/cajas/actual/').then((r) => r.data.caja),
  abrir: (monto_inicial) => api.post('/cajas/abrir/', { monto_inicial }).then(d),
  movimiento: (id, data) => api.post(`/cajas/${id}/movimientos/`, data).then(d),
  cerrar: (id, data) => api.post(`/cajas/${id}/cerrar/`, data).then(d),
  listar: (params = {}) => api.get('/cajas/', { params }).then(d),
  obtener: (id) => api.get(`/cajas/${id}/`).then(d),
}

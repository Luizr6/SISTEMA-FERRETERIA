import api from './api'

const d = (r) => r.data

export const ventasService = {
  listar: (params = {}) => api.get('/ventas/', { params }).then(d),
  obtener: (id) => api.get(`/ventas/${id}/`).then(d),
  // { cliente, metodo_pago, origen, observacion, detalles: [{producto, cantidad}] }
  crear: (data) => api.post('/ventas/', data).then(d),
  anular: (id, motivo) => api.post(`/ventas/${id}/anular/`, { motivo }).then(d),
  resumen: (params = {}) => api.get('/ventas/resumen/', { params }).then(d),
  // Abre el PDF en otra pestaña (la API exige el token, por eso se descarga como blob)
  async abrirComprobante(id, numero) {
    const ventana = window.open('', '_blank')
    const { data } = await api.get(`/ventas/${id}/comprobante/`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    if (ventana) ventana.location.href = url
    else {
      const a = document.createElement('a')
      a.href = url
      a.download = `comprobante_${numero || id}.pdf`
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
}

export const usuariosService = {
  listar: (params = {}) => api.get('/usuarios/', { params }).then(d),
  crear: (data) => api.post('/usuarios/', data).then(d),
  actualizar: (id, data) => api.patch(`/usuarios/${id}/`, data).then(d),
  eliminar: (id) => api.delete(`/usuarios/${id}/`),
  bitacora: (params = {}) => api.get('/bitacora/', { params }).then(d),
}

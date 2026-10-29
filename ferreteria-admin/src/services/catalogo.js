import api from './api'

const d = (r) => r.data

// La API devuelve arrays simples (sin paginación), salvo la bitácora.
export const productosService = {
  listar: (params = {}) => api.get('/productos/', { params }).then(d),
  crear: (data) => api.post('/productos/', data).then(d),
  actualizar: (id, data) => api.put(`/productos/${id}/`, data).then(d),
  eliminar: (id) => api.delete(`/productos/${id}/`),
  kardex: (id) => api.get(`/productos/${id}/kardex/`).then(d),
  importarCsv: (archivo, actualizar) => {
    const fd = new FormData()
    fd.append('archivo', archivo)
    fd.append('actualizar', actualizar ? 'true' : 'false')
    return api.post('/productos/importar-csv/', fd).then(d)
  },
  plantillaCsv: () => api.get('/productos/plantilla-csv/', { responseType: 'blob' }).then(d),
}

export const categoriasService = {
  listar: () => api.get('/categorias/').then(d),
  crear: (data) => api.post('/categorias/', data).then(d),
}

export const marcasService = {
  listar: () => api.get('/marcas/').then(d),
  crear: (data) => api.post('/marcas/', data).then(d),
}

export const proveedoresService = {
  listar: (params = {}) => api.get('/proveedores/', { params }).then(d),
  crear: (data) => api.post('/proveedores/', data).then(d),
  actualizar: (id, data) => api.put(`/proveedores/${id}/`, data).then(d),
  eliminar: (id) => api.delete(`/proveedores/${id}/`),
  productos: (id) => api.get(`/proveedores/${id}/productos/`).then(d),
}

export const comprasService = {
  listar: (params = {}) => api.get('/compras/', { params }).then(d),
  obtener: (id) => api.get(`/compras/${id}/`).then(d),
  // { proveedor, fecha_entrega_estimada, observacion, detalles: [{producto, cantidad, precio_compra}] }
  crear: (data) => api.post('/compras/', data).then(d),
  actualizar: (id, data) => api.patch(`/compras/${id}/`, data).then(d),
  // items: [{detalle, cantidad}] o vacío para recibir todo
  recibir: (id, data) => api.post(`/compras/${id}/recibir/`, data).then(d),
  anular: (id, motivo) => api.post(`/compras/${id}/anular/`, { motivo }).then(d),
  recepciones: (id) => api.get(`/compras/${id}/recepciones/`).then(d),
  sugerencias: (params = {}) => api.get('/compras/sugerencias/', { params }).then(d),
}

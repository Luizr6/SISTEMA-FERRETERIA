const moneda = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

export const money = (n) => moneda.format(Number(n || 0))

export const numero = (n) => new Intl.NumberFormat('es-PE').format(Number(n || 0))

export const fechaHora = (iso) =>
  iso ? new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' }) : '—'

export const hora = (iso) =>
  iso ? new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }) : ''

export const fecha = (iso) => {
  if (!iso) return '—'
  const s = String(iso)
  if (s.length === 10) { // 'YYYY-MM-DD' sin hora: evitar desfase por zona horaria
    const [y, m, d] = s.split('-')
    return `${d}/${m}/${y}`
  }
  return new Date(s).toLocaleDateString('es-PE')
}

export const fechaCorta = (iso) => {
  if (!iso) return '—'
  const [, m, d] = String(iso).slice(0, 10).split('-')
  return `${d}/${m}`
}

export const diaSemana = (iso) => {
  const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('es-PE', { weekday: 'short' }).replace('.', '')
}

export const hace = (iso) => {
  if (!iso) return ''
  const seg = (Date.now() - new Date(iso).getTime()) / 1000
  if (seg < 60) return 'ahora'
  if (seg < 3600) return `hace ${Math.floor(seg / 60)} min`
  if (seg < 86400) return `hace ${Math.floor(seg / 3600)} h`
  return fechaHora(iso)
}

export const hoyISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export const METODOS_PAGO = [
  ['EFECTIVO', 'Efectivo'], ['YAPE', 'Yape'], ['PLIN', 'Plin'], ['TARJETA', 'Tarjeta'], ['TRANSFERENCIA', 'Transferencia'],
]
export const ORIGENES = { MOSTRADOR: 'Mostrador', MOVIL: 'App móvil', WEB: 'Web', WHATSAPP: 'WhatsApp' }
export const UNIDADES = [
  ['UND', 'Unidad'], ['KG', 'Kilogramo'], ['M', 'Metro'], ['L', 'Litro'],
  ['CAJA', 'Caja'], ['BOLSA', 'Bolsa'], ['GLN', 'Galón'],
]

const CAMPOS = {
  detalles: 'Productos', items: 'Recepción', cliente: 'Cliente', proveedor: 'Proveedor',
  numero_documento: 'Documento', codigo: 'Código', precio_venta: 'Precio de venta',
  precio_compra: 'Precio de compra', estado: 'Estado', carrito: 'Carrito', new_password: 'Nueva contraseña',
  old_password: 'Contraseña actual', token: 'Enlace', username: 'Usuario', password: 'Contraseña', ruc: 'RUC',
  razon_social: 'Razón social', archivo: 'Archivo', motivo: 'Motivo', texto: 'Mensaje',
}

/** Convierte el error de Django ({codigo, mensaje, detalle}) en texto legible. */
export function errorMessage(err) {
  const data = err?.response?.data
  if (!data) return err?.message === 'Network Error'
    ? 'No hay conexión con el servidor. Revisa que Django esté corriendo.'
    : err?.message || 'No se pudo conectar con el servidor.'
  if (data instanceof Blob) return 'No se pudo generar el archivo.'
  const det = data.detalle
  if (det && typeof det === 'object') {
    const partes = Object.entries(det).map(([campo, msgs]) => {
      const txt = Array.isArray(msgs)
        ? msgs.map((m) => (typeof m === 'object' ? Object.values(m).flat().join(' ') : m)).join(' ')
        : typeof msgs === 'object' ? Object.values(msgs).flat().join(' ') : String(msgs)
      return ['detail', 'non_field_errors'].includes(campo) ? txt : `${CAMPOS[campo] || campo}: ${txt}`
    })
    if (partes.length) return partes.join(' · ')
  }
  return data.mensaje || data.detail || 'Ocurrió un error.'
}

/** Quita filtros vacíos antes de mandarlos como query string */
export const limpiar = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v != null))

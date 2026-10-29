import { useEffect, useMemo, useState } from 'react'
import { Loader2, Plus, Sparkles, Trash2 } from 'lucide-react'
import { comprasService, productosService } from '../../services/catalogo'
import { errorMessage, money } from '../../utils/format'
import { Aviso } from '../ui/Spinner'
import { Campo } from '../ui/Filtros'

/** CU10 - Nueva orden de compra. Con `sugerir` precarga los productos con stock bajo. */
export default function OrdenForm({ proveedores, sugerir = false, onSaved, onCancel }) {
  const [proveedor, setProveedor] = useState('')
  const [entrega, setEntrega] = useState('')
  const [observacion, setObservacion] = useState('')
  const [lineas, setLineas] = useState([]) // [{producto, codigo, nombre, cantidad, precio_compra}]
  const [productos, setProductos] = useState([])
  const [agregar, setAgregar] = useState('')
  const [error, setError] = useState('')
  const [nota, setNota] = useState('') // informativo, no es un error
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    productosService.listar({ activo: true }).then(setProductos).catch((e) => setError(errorMessage(e)))
  }, [])

  const cargarSugerencias = async (prov) => {
    try {
      const s = await comprasService.sugerencias(prov ? { proveedor: prov } : {})
      if (!prov && s.length) {
        // proveedor con más productos por reponer
        const conteo = {}
        s.forEach((x) => { if (x.proveedor) conteo[x.proveedor] = (conteo[x.proveedor] || 0) + 1 })
        const top = Object.entries(conteo).sort((a, b) => b[1] - a[1])[0]?.[0]
        if (top) { setProveedor(top); return cargarSugerencias(top) }
      }
      if (!s.length) return setNota('Este proveedor no tiene productos por debajo del stock mínimo.')
      setError(''); setNota('')
      setLineas((ls) => {
        const ya = new Set(ls.map((l) => l.producto))
        return [...ls, ...s.filter((x) => !ya.has(x.id)).map((x) => ({
          producto: x.id, codigo: x.codigo, nombre: x.nombre, cantidad: x.cantidad_sugerida, precio_compra: x.precio_compra,
        }))]
      })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  useEffect(() => { if (sugerir) cargarSugerencias('') }, [sugerir]) // eslint-disable-line react-hooks/exhaustive-deps

  const delProveedor = useMemo(
    () => productos.filter((p) => !proveedor || String(p.proveedor) === String(proveedor) || !p.proveedor),
    [productos, proveedor])

  const agregarLinea = (id) => {
    const p = productos.find((x) => String(x.id) === String(id))
    if (!p || lineas.some((l) => l.producto === p.id)) return setAgregar('')
    setLineas([...lineas, { producto: p.id, codigo: p.codigo, nombre: p.nombre, cantidad: Math.max(p.stock_minimo, 1), precio_compra: p.precio_compra }])
    setAgregar('')
  }
  const cambiar = (i, k, v) => setLineas(lineas.map((l, j) => (j === i ? { ...l, [k]: v } : l)))
  const total = lineas.reduce((s, l) => s + Number(l.cantidad || 0) * Number(l.precio_compra || 0), 0)

  const guardar = async (e) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      const o = await comprasService.crear({
        proveedor: Number(proveedor), observacion, fecha_entrega_estimada: entrega || null,
        detalles: lineas.map((l) => ({ producto: l.producto, cantidad: Number(l.cantidad), precio_compra: l.precio_compra })),
      })
      onSaved(o)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={guardar} className="space-y-4">
      {error && <Aviso>{error}</Aviso>}
      {nota && <Aviso tono="aviso">{nota}</Aviso>}
      <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
        <Campo label="Proveedor *">
          <select className="input" required value={proveedor} onChange={(e) => setProveedor(e.target.value)}>
            <option value="">Elegir proveedor…</option>
            {proveedores.filter((p) => p.activo).map((p) => <option key={p.id} value={p.id}>{p.razon_social}</option>)}
          </select>
        </Campo>
        <Campo label="Entrega estimada"><input className="input" type="date" value={entrega} onChange={(e) => setEntrega(e.target.value)} /></Campo>
      </div>

      <div className="flex flex-wrap gap-2">
        <select className="input min-w-[220px] flex-1" aria-label="Agregar producto" value={agregar}
          onChange={(e) => agregarLinea(e.target.value)}>
          <option value="">Agregar producto…</option>
          {delProveedor.map((p) => (
            <option key={p.id} value={p.id}>{p.codigo}, {p.nombre} (stock {p.stock_actual})</option>
          ))}
        </select>
        <button type="button" className="btn-secondary" disabled={!proveedor} onClick={() => cargarSugerencias(proveedor)}
          title="Agrega los productos de este proveedor que están por debajo del mínimo">
          <Sparkles className="h-4 w-4" /> Lo que falta reponer
        </button>
      </div>

      <div className="overflow-x-auto rounded-[12px] border border-gris-200">
        <table className="tabla w-full min-w-[560px]">
          <thead><tr>
            <th className="th">Producto</th><th className="th w-28 text-right">Cantidad</th>
            <th className="th w-32 text-right">Costo unit. (S/)</th><th className="th text-right">Subtotal</th><th className="th w-10" />
          </tr></thead>
          <tbody>
            {lineas.length === 0 && (
              <tr><td colSpan={5} className="py-8 text-center text-sm text-gris-600">Agrega los productos que vas a pedir.</td></tr>
            )}
            {lineas.map((l, i) => (
              <tr key={l.producto}>
                <td className="td"><span className="etiqueta mr-2">{l.codigo}</span>{l.nombre}</td>
                <td className="td"><input className="input py-1.5 text-right" type="number" min="1" required value={l.cantidad}
                  aria-label={`Cantidad de ${l.nombre}`} onChange={(e) => cambiar(i, 'cantidad', e.target.value)} /></td>
                <td className="td"><input className="input py-1.5 text-right" type="number" min="0" step="0.01" required value={l.precio_compra}
                  aria-label={`Costo de ${l.nombre}`} onChange={(e) => cambiar(i, 'precio_compra', e.target.value)} /></td>
                <td className="td cifra text-right font-semibold">{money(Number(l.cantidad) * Number(l.precio_compra))}</td>
                <td className="td"><button type="button" className="icon-btn hover:text-peligro-600" aria-label="Quitar"
                  onClick={() => setLineas(lineas.filter((_, j) => j !== i))}><Trash2 className="h-4 w-4" /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Campo label="Observación"><input className="input" value={observacion} onChange={(e) => setObservacion(e.target.value)} /></Campo>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-gris-100 pt-4">
        <p className="text-sm text-gris-600">Total de la orden <b className="cifra ml-1 text-xl text-gris-900">{money(total)}</b></p>
        <div className="flex gap-2">
          <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
          <button className="btn-primary" disabled={saving || !lineas.length}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Registrar orden
          </button>
        </div>
      </div>
    </form>
  )
}

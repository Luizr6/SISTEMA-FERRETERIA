import { useState } from 'react'
import { Loader2, Plus } from 'lucide-react'
import { categoriasService, marcasService, productosService } from '../../services/catalogo'
import { errorMessage, money, UNIDADES } from '../../utils/format'
import { Aviso } from '../ui/Spinner'
import { Campo } from '../ui/Filtros'

const vacio = {
  codigo: '', nombre: '', descripcion: '', categoria: '', marca: '', proveedor: '',
  unidad_medida: 'UND', imagen_url: '', precio_compra: '', precio_venta: '',
  stock_actual: 0, stock_minimo: 5, activo: true,
}

export default function ProductoForm({
  producto, categorias, marcas, proveedores, onCategoriaCreada, onMarcaCreada, onSaved, onCancel,
}) {
  const editando = !!producto.id
  const [form, setForm] = useState(() =>
    Object.fromEntries(Object.keys(vacio).map((k) => [k, producto[k] ?? vacio[k]])))
  const [nuevo, setNuevo] = useState(null) // {tipo: 'categoria'|'marca', nombre}
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const crearRapido = async () => {
    const nombre = nuevo?.nombre.trim()
    if (!nombre) return setNuevo(null)
    try {
      if (nuevo.tipo === 'categoria') {
        const c = await categoriasService.crear({ nombre })
        onCategoriaCreada(c); setForm((f) => ({ ...f, categoria: c.id }))
      } else {
        const m = await marcasService.crear({ nombre })
        onMarcaCreada(m); setForm((f) => ({ ...f, marca: m.id }))
      }
      setNuevo(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const margen = Number(form.precio_venta || 0) - Number(form.precio_compra || 0)
  const margenPct = Number(form.precio_venta) ? (margen / Number(form.precio_venta)) * 100 : 0

  const submit = async (e) => {
    e.preventDefault()
    setError(''); setSaving(true)
    const payload = {
      ...form,
      categoria: Number(form.categoria),
      marca: form.marca ? Number(form.marca) : null,
      proveedor: form.proveedor ? Number(form.proveedor) : null,
      stock_actual: Number(form.stock_actual),
      stock_minimo: Number(form.stock_minimo),
    }
    try {
      if (editando) await productosService.actualizar(producto.id, payload)
      else await productosService.crear(payload)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const selectConAlta = (tipo, label, lista, campo, opcionVacia, required) => (
    <Campo label={label}>
      {nuevo?.tipo === tipo ? (
        <div className="flex gap-1">
          <input className="input" autoFocus placeholder={`Nueva ${label.toLowerCase()}`} value={nuevo.nombre}
            onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); crearRapido() } }} />
          <button type="button" className="btn-dark px-3" onClick={crearRapido}>Crear</button>
        </div>
      ) : (
        <div className="flex gap-1">
          <select className="input" required={required} value={form[campo]} onChange={set(campo)}>
            <option value="">{opcionVacia}</option>
            {lista.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
          <button type="button" title={`Nueva ${label.toLowerCase()}`} aria-label={`Nueva ${label.toLowerCase()}`}
            onClick={() => setNuevo({ tipo, nombre: '' })} className="btn-secondary px-2.5"><Plus className="h-4 w-4" /></button>
        </div>
      )}
    </Campo>
  )

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <Aviso>{error}</Aviso>}

      <div className="grid gap-4 sm:grid-cols-3">
        <Campo label="Código *"><input className="input uppercase" required value={form.codigo} onChange={set('codigo')} placeholder="MAR-001" /></Campo>
        <Campo label="Nombre *" className="sm:col-span-2">
          <input className="input" required value={form.nombre} onChange={set('nombre')} placeholder="Martillo de uña 16 oz" />
        </Campo>
      </div>

      <Campo label="Descripción"><textarea className="input" rows={2} value={form.descripcion || ''} onChange={set('descripcion')} /></Campo>

      <div className="grid gap-4 sm:grid-cols-3">
        {selectConAlta('categoria', 'Categoría *', categorias, 'categoria', 'Elegir…', true)}
        {selectConAlta('marca', 'Marca', marcas, 'marca', 'Sin marca', false)}
        <Campo label="Proveedor">
          <select className="input" value={form.proveedor || ''} onChange={set('proveedor')}>
            <option value="">Sin proveedor</option>
            {proveedores.map((p) => <option key={p.id} value={p.id}>{p.razon_social}</option>)}
          </select>
        </Campo>
      </div>

      <fieldset className="rounded-[14px] bg-gris-50 p-4">
        <legend className="sr-only">Precios y stock</legend>
        <div className="grid gap-4 sm:grid-cols-4">
          <Campo label="Costo (S/) *"><input className="input" required type="number" min="0" step="0.01" value={form.precio_compra} onChange={set('precio_compra')} /></Campo>
          <Campo label="Venta sin IGV (S/) *"><input className="input" required type="number" min="0" step="0.01" value={form.precio_venta} onChange={set('precio_venta')} /></Campo>
          <Campo label="Stock actual" hint={editando ? 'Si lo cambias queda como ajuste en el kardex.' : undefined}>
            <input className="input" type="number" min="0" value={form.stock_actual} onChange={set('stock_actual')} />
          </Campo>
          <Campo label="Stock mínimo"><input className="input" type="number" min="0" value={form.stock_minimo} onChange={set('stock_minimo')} /></Campo>
        </div>
        {form.precio_venta !== '' && (
          <p className={`mt-3 text-sm ${margen < 0 ? 'text-peligro-600' : 'text-gris-600'}`}>
            Margen por unidad: <b className="cifra">{money(margen)}</b> ({margenPct.toFixed(1)} %).
            Precio al público con IGV: <b className="cifra">{money(Number(form.precio_venta) * 1.18)}</b>.
          </p>
        )}
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-3">
        <Campo label="Unidad de medida">
          <select className="input" value={form.unidad_medida} onChange={set('unidad_medida')}>
            {UNIDADES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Campo>
        <Campo label="Imagen (URL)" className="sm:col-span-2">
          <input className="input" type="url" value={form.imagen_url} onChange={set('imagen_url')} placeholder="https://…" />
        </Campo>
      </div>

      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" className="h-4 w-4 accent-acento-600" checked={form.activo} onChange={set('activo')} />
        Disponible para la venta
      </label>

      <div className="flex justify-end gap-2 border-t border-gris-100 pt-4">
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
        <button className="btn-primary" disabled={saving}>
          {saving && <Loader2 className="h-4 w-4 animate-spin" />} {editando ? 'Guardar cambios' : 'Crear producto'}
        </button>
      </div>
    </form>
  )
}

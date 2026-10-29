import { useState } from 'react'
import { Loader2, Mail, MapPin, Package, Pencil, Phone, Plus, Truck } from 'lucide-react'
import { proveedoresService } from '../services/catalogo'
import { errorMessage, money } from '../utils/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Aviso, Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import Wincha from '../components/ui/Wincha'
import { Buscador, Campo } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

const vacio = { razon_social: '', ruc: '', contacto: '', telefono: '', email: '', direccion: '', activo: true }

function ProveedorForm({ proveedor, onSaved, onCancel }) {
  const [f, setF] = useState(() => Object.fromEntries(Object.keys(vacio).map((k) => [k, proveedor[k] ?? vacio[k]])))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      onSaved(proveedor.id ? await proveedoresService.actualizar(proveedor.id, f) : await proveedoresService.crear(f))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3.5">
      {error && <Aviso>{error}</Aviso>}
      <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
        <Campo label="Razón social *"><input className="input" required autoFocus value={f.razon_social} onChange={set('razon_social')} /></Campo>
        <Campo label="RUC"><input className="input" inputMode="numeric" maxLength={11} placeholder="11 dígitos" value={f.ruc || ''} onChange={set('ruc')} /></Campo>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Persona de contacto"><input className="input" value={f.contacto} onChange={set('contacto')} /></Campo>
        <Campo label="Teléfono"><input className="input" value={f.telefono} onChange={set('telefono')} /></Campo>
      </div>
      <Campo label="Correo"><input className="input" type="email" value={f.email} onChange={set('email')} /></Campo>
      <Campo label="Dirección"><input className="input" value={f.direccion} onChange={set('direccion')} /></Campo>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="h-4 w-4 accent-acento-600" checked={f.activo} onChange={set('activo')} /> Proveedor activo
      </label>
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
        <button className="btn-primary" disabled={saving}>{saving && <Loader2 className="h-4 w-4 animate-spin" />}
          {proveedor.id ? 'Guardar cambios' : 'Registrar proveedor'}</button>
      </div>
    </form>
  )
}

function ProductosDelProveedor({ proveedor }) {
  const { data } = useCarga(() => proveedoresService.productos(proveedor.id), [proveedor.id])
  if (data === null) return <Spinner />
  if (!data.length) return <Vacio icon={Package} titulo="Sin productos asociados" texto="Asigna este proveedor desde el formulario de cada producto." />
  return (
    <ul className="divide-y divide-gris-100">
      {data.map((p) => (
        <li key={p.id} className="flex items-center justify-between gap-4 py-3">
          <div className="min-w-0">
            <span className="etiqueta">{p.codigo}</span>
            <p className="mt-1 truncate text-sm font-semibold">{p.nombre}</p>
            <p className="text-xs text-gris-500">Costo {money(p.precio_compra)}</p>
          </div>
          <Wincha stock={p.stock_actual} minimo={p.stock_minimo} unidad={p.unidad_medida} compacta />
        </li>
      ))}
    </ul>
  )
}

export default function Proveedores() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [editando, setEditando] = useState(null)
  const [viendo, setViendo] = useState(null)
  const { data, recargar } = useCarga(() => proveedoresService.listar({ search: search || undefined }), [search], { retraso: 250 })

  return (
    <>
      <PageHeader title="Proveedores" subtitle="A quién le compras y qué productos te surte cada uno.">
        {isAdmin && <button className="btn-primary" onClick={() => setEditando({})}><Plus className="h-4 w-4" /> Nuevo proveedor</button>}
      </PageHeader>
      <Buscador className="mb-4 max-w-md" value={search} onChange={setSearch} placeholder="Razón social, RUC o contacto" />

      {data === null ? <Spinner /> : data.length === 0 ? (
        <div className="panel"><Vacio icon={Truck} titulo="No hay proveedores" texto="Registra a tus proveedores para armar órdenes de compra." /></div>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {data.map((p) => (
            <li key={p.id} className={`panel flex flex-col p-4 ${p.activo ? '' : 'opacity-60'}`}>
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h2 className="text-[17px] font-semibold leading-snug">{p.razon_social}</h2>
                  <p className="cifra text-sm text-gris-600">{p.ruc ? `RUC ${p.ruc}` : 'Sin RUC'}</p>
                </div>
                {!p.activo && <Badge>Inactivo</Badge>}
              </div>
              <div className="mb-4 mt-3 space-y-1 text-sm text-gris-600">
                {p.contacto && <p className="font-medium text-gris-900">{p.contacto}</p>}
                {p.telefono && <p className="flex items-center gap-2"><Phone className="h-3.5 w-3.5" /> {p.telefono}</p>}
                {p.email && <p className="flex items-center gap-2 truncate"><Mail className="h-3.5 w-3.5" /> {p.email}</p>}
                {p.direccion && <p className="flex items-center gap-2"><MapPin className="h-3.5 w-3.5 shrink-0" /> <span className="truncate">{p.direccion}</span></p>}
              </div>
              <div className="mt-auto flex items-center justify-between gap-2 border-t border-gris-100 pt-3">
                <button className="btn-ghost -ml-2.5" onClick={() => setViendo(p)}>
                  <Package className="h-4 w-4" /> {p.total_productos} {p.total_productos === 1 ? 'producto' : 'productos'}
                </button>
                {isAdmin && <button className="icon-btn" aria-label="Editar" title="Editar" onClick={() => setEditando(p)}><Pencil className="h-4 w-4" /></button>}
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={!!editando} onClose={() => setEditando(null)} title={editando?.id ? 'Editar proveedor' : 'Nuevo proveedor'}>
        {editando && <ProveedorForm proveedor={editando} onCancel={() => setEditando(null)}
          onSaved={(p) => { setEditando(null); toast(`${p.razon_social} guardado.`); recargar() }} />}
      </Modal>
      <Modal open={!!viendo} onClose={() => setViendo(null)} title={viendo?.razon_social} subtitle="Productos que surte este proveedor">
        {viendo && <ProductosDelProveedor proveedor={viendo} />}
      </Modal>
    </>
  )
}

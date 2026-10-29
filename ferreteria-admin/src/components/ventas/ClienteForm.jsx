import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { clientesService } from '../../services/comercial'
import { errorMessage } from '../../utils/format'
import { Aviso } from '../ui/Spinner'
import { Campo } from '../ui/Filtros'

const vacio = { tipo_documento: 'DNI', numero_documento: '', nombre: '', telefono: '', email: '', direccion: '', activo: true }

export default function ClienteForm({ cliente = {}, onSaved, onCancel }) {
  const [f, setF] = useState(() => Object.fromEntries(Object.keys(vacio).map((k) => [k, cliente[k] ?? vacio[k]])))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    e.stopPropagation()
    setSaving(true); setError('')
    try {
      const r = cliente.id ? await clientesService.actualizar(cliente.id, f) : await clientesService.crear(f)
      onSaved(r)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3.5">
      {error && <Aviso>{error}</Aviso>}
      <div className="grid gap-3 sm:grid-cols-[130px_1fr]">
        <Campo label="Documento">
          <select className="input" value={f.tipo_documento} onChange={set('tipo_documento')}>
            <option value="DNI">DNI</option><option value="RUC">RUC</option>
            <option value="CE">Carné ext.</option><option value="OTRO">Otro</option>
          </select>
        </Campo>
        <Campo label="Número">
          <input className="input" inputMode="numeric" value={f.numero_documento || ''} onChange={set('numero_documento')}
            placeholder={f.tipo_documento === 'RUC' ? '11 dígitos' : f.tipo_documento === 'DNI' ? '8 dígitos' : ''} />
        </Campo>
      </div>
      <Campo label={f.tipo_documento === 'RUC' ? 'Razón social *' : 'Nombre completo *'}>
        <input className="input" required autoFocus value={f.nombre} onChange={set('nombre')} />
      </Campo>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Teléfono / WhatsApp"><input className="input" value={f.telefono} onChange={set('telefono')} /></Campo>
        <Campo label="Correo"><input className="input" type="email" value={f.email} onChange={set('email')} /></Campo>
      </div>
      <Campo label="Dirección"><input className="input" value={f.direccion} onChange={set('direccion')} /></Campo>
      {cliente.id && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="h-4 w-4 accent-acento-600" checked={f.activo} onChange={set('activo')} /> Cliente activo
        </label>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
        <button className="btn-primary" disabled={saving}>
          {saving && <Loader2 className="h-4 w-4 animate-spin" />} {cliente.id ? 'Guardar cambios' : 'Registrar cliente'}
        </button>
      </div>
    </form>
  )
}

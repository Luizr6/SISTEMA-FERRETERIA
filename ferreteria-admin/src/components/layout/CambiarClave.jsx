import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import Modal from '../ui/Modal'
import { Aviso } from '../ui/Spinner'
import { Campo } from '../ui/Filtros'
import { useToast } from '../ui/Toast'
import { passwordService } from '../../services/authService'
import { errorMessage } from '../../utils/format'

export default function CambiarClave({ open, onClose }) {
  const toast = useToast()
  const [f, setF] = useState({ actual: '', nueva: '', repetir: '' })
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  const cerrar = () => { setF({ actual: '', nueva: '', repetir: '' }); setError(''); onClose() }

  const submit = async (e) => {
    e.preventDefault()
    if (f.nueva !== f.repetir) return setError('Las contraseñas nuevas no coinciden.')
    setGuardando(true); setError('')
    try {
      await passwordService.cambiar(f.actual, f.nueva)
      toast('Contraseña actualizada.')
      cerrar()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={open} onClose={cerrar} size="sm" title="Cambiar contraseña">
      <form onSubmit={submit} className="space-y-3.5">
        {error && <Aviso>{error}</Aviso>}
        <Campo label="Contraseña actual">
          <input className="input" type="password" required autoComplete="current-password"
            value={f.actual} onChange={(e) => setF({ ...f, actual: e.target.value })} />
        </Campo>
        <Campo label="Nueva contraseña" hint="Mínimo 8 caracteres, que no sea solo números.">
          <input className="input" type="password" required minLength={8} autoComplete="new-password"
            value={f.nueva} onChange={(e) => setF({ ...f, nueva: e.target.value })} />
        </Campo>
        <Campo label="Repite la nueva contraseña">
          <input className="input" type="password" required autoComplete="new-password"
            value={f.repetir} onChange={(e) => setF({ ...f, repetir: e.target.value })} />
        </Campo>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" className="btn-secondary" onClick={cerrar}>Cancelar</button>
          <button className="btn-primary" disabled={guardando}>
            {guardando && <Loader2 className="h-4 w-4 animate-spin" />} Cambiar contraseña
          </button>
        </div>
      </form>
    </Modal>
  )
}

import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { MarcoAcceso } from './Login'
import { Aviso } from '../components/ui/Spinner'
import { Campo } from '../components/ui/Filtros'
import { passwordService } from '../services/authService'
import { errorMessage } from '../utils/format'

/** Destino del enlace del correo: /reset-password?uid=...&token=... (RF03) */
export default function RecuperarClave() {
  const [params] = useSearchParams()
  const [f, setF] = useState({ uid: params.get('uid') || '', token: params.get('token') || '', nueva: '', repetir: '' })
  const [error, setError] = useState('')
  const [listo, setListo] = useState(false)
  const [cargando, setCargando] = useState(false)
  const desdeEnlace = Boolean(params.get('uid') && params.get('token'))

  const submit = async (e) => {
    e.preventDefault()
    if (f.nueva !== f.repetir) return setError('Las contraseñas no coinciden.')
    setError(''); setCargando(true)
    try {
      await passwordService.confirmar(f.uid.trim(), f.token.trim(), f.nueva)
      setListo(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <MarcoAcceso>
        <div>
          {listo ? (
            <>
              <h1 className="text-xl font-semibold">Contraseña actualizada</h1>
              <p className="mt-2 text-gris-600">Ya puedes ingresar con tu nueva contraseña.</p>
              <Link to="/login" className="btn-primary mt-6 w-full py-2.5">Ir al ingreso</Link>
            </>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div>
                <h1 className="text-xl font-semibold">Nueva contraseña</h1>
                <p className="mt-1 text-gris-600">Elige una contraseña de al menos 8 caracteres.</p>
              </div>
              {error && <Aviso>{error}</Aviso>}
              {!desdeEnlace && (
                <div className="grid grid-cols-2 gap-3">
                  <Campo label="uid"><input className="input" required value={f.uid} onChange={(e) => setF({ ...f, uid: e.target.value })} /></Campo>
                  <Campo label="token"><input className="input" required value={f.token} onChange={(e) => setF({ ...f, token: e.target.value })} /></Campo>
                </div>
              )}
              <Campo label="Nueva contraseña">
                <input className="input" type="password" required minLength={8} autoComplete="new-password"
                  value={f.nueva} onChange={(e) => setF({ ...f, nueva: e.target.value })} />
              </Campo>
              <Campo label="Repite la contraseña">
                <input className="input" type="password" required autoComplete="new-password"
                  value={f.repetir} onChange={(e) => setF({ ...f, repetir: e.target.value })} />
              </Campo>
              <button className="btn-primary w-full py-2.5" disabled={cargando}>
                {cargando && <Loader2 className="h-4 w-4 animate-spin" />} Guardar contraseña
              </button>
              <Link to="/login" className="btn-ghost w-full">Volver al ingreso</Link>
            </form>
          )}
        </div>
    </MarcoAcceso>
  )
}

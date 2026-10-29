import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { BotonTema } from '../context/TemaContext'
import { Aviso } from '../components/ui/Spinner'
import { Campo } from '../components/ui/Filtros'
import { passwordService } from '../services/authService'
import { errorMessage } from '../utils/format'

/** Marco común de las pantallas de acceso: logo, tarjeta centrada y botón de tema. */
export function MarcoAcceso({ children }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-fondo px-4 py-10">
      <div aria-hidden className="pointer-events-none absolute inset-0 opacity-60 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]"
        style={{ backgroundImage: 'linear-gradient(rgb(var(--gris-200)) 1px, transparent 1px), linear-gradient(90deg, rgb(var(--gris-200)) 1px, transparent 1px)', backgroundSize: '40px 40px' }} />
      <BotonTema className="absolute right-4 top-4" />
      <div className="relative w-full max-w-[400px]">
        <div className="mb-6 flex items-center justify-center gap-2.5">
          <img src="/favicon.svg" alt="" className="h-9 w-9 rounded-lg" />
          <div className="leading-tight">
            <p className="text-[15px] font-semibold text-gris-900">Ferretería</p>
            <p className="text-xs text-gris-500">Sistema de gestión comercial</p>
          </div>
        </div>
        <div className="panel p-6 shadow-md sm:p-7">{children}</div>
        <p className="mt-6 text-center text-xs text-gris-500">Proyecto integrador de TECSUP</p>
      </div>
    </div>
  )
}

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ username: '', password: '' })
  const [show, setShow] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [modo, setModo] = useState('login') // login | recuperar | enviado
  const [email, setEmail] = useState('')

  if (user) return <Navigate to="/" replace />

  const submit = async (e) => {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      await login(form.username.trim(), form.password)
      navigate(location.state?.from?.pathname || '/', { replace: true })
    } catch (err) {
      setError(err.response?.status === 401
        ? 'Usuario o contraseña incorrectos.'
        : 'No hay conexión con el servidor. Revisa que Django esté corriendo en el puerto 8000.')
    } finally {
      setLoading(false)
    }
  }

  const recuperar = async (e) => {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      await passwordService.solicitar(email.trim())
      setModo('enviado')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <MarcoAcceso>
      {modo === 'login' && (
        <form onSubmit={submit}>
          <h1 className="text-xl font-semibold">Iniciar sesión</h1>
          <p className="mb-6 mt-1 text-sm text-gris-500">Ingresa con tu usuario del sistema.</p>
          {error && <div className="mb-4"><Aviso>{error}</Aviso></div>}

          <Campo label="Usuario" className="mb-4">
            <input className="input" autoFocus required autoComplete="username"
              value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </Campo>

          <div className="mb-1.5 flex items-center justify-between">
            <label htmlFor="clave" className="label mb-0">Contraseña</label>
            <button type="button" onClick={() => { setModo('recuperar'); setError('') }}
              className="text-[13px] font-medium text-acento-600 hover:underline">¿Olvidaste tu contraseña?</button>
          </div>
          <div className="relative mb-6">
            <input id="clave" className="input pr-10" required type={show ? 'text' : 'password'} autoComplete="current-password"
              value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            <button type="button" onClick={() => setShow(!show)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              className="icon-btn absolute right-1.5 top-1/2 h-7 w-7 -translate-y-1/2">
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>

          <button className="btn-primary w-full py-2.5" disabled={loading}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" />} Ingresar
          </button>
        </form>
      )}

      {modo === 'recuperar' && (
        <form onSubmit={recuperar}>
          <h1 className="text-xl font-semibold">Recuperar contraseña</h1>
          <p className="mb-6 mt-1 text-sm text-gris-500">Te enviaremos un enlace al correo registrado en tu usuario.</p>
          {error && <div className="mb-4"><Aviso>{error}</Aviso></div>}
          <Campo label="Correo" className="mb-6">
            <input className="input" type="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </Campo>
          <button className="btn-primary w-full py-2.5" disabled={loading}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" />} Enviar enlace
          </button>
          <button type="button" className="btn-ghost mt-2 w-full" onClick={() => setModo('login')}>Volver</button>
        </form>
      )}

      {modo === 'enviado' && (
        <div>
          <h1 className="text-xl font-semibold">Revisa tu correo</h1>
          <p className="mt-2 text-sm text-gris-600">
            Si <b className="text-gris-900">{email}</b> pertenece a un usuario, ya tiene un enlace para cambiar su contraseña.
          </p>
          <p className="mt-3 rounded-lg bg-gris-50 p-3 text-[13px] text-gris-600">
            En desarrollo el correo no se envía: aparece en la consola donde corre <code>runserver</code>.
          </p>
          <Link to="/reset-password" className="btn-secondary mt-5 w-full">Ya tengo el enlace</Link>
          <button className="btn-ghost mt-2 w-full" onClick={() => setModo('login')}>Volver</button>
        </div>
      )}
    </MarcoAcceso>
  )
}

import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Loader2, Lock, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { BotonTema } from '../context/TemaContext'
import { Aviso } from '../components/ui/Spinner'
import { Campo } from '../components/ui/Filtros'
import { passwordService } from '../services/authService'
import { errorMessage } from '../utils/format'
import { MARCA } from '../config/marca'

/* ------------------------------------------------------------------
   Pantallas de acceso: foto con el nombre del negocio a la izquierda y el formulario a la derecha.
   Nombre, frase, logo y foto se cambian en src/config/marca.js (sin foto se ve un fondo azul marino).
   ------------------------------------------------------------------ */

function Banner() {
  return (
    <aside className="sticky top-0 hidden h-screen overflow-hidden bg-[#0f1e3d] lg:block lg:w-1/2">
      <img src={MARCA.fotoLogin} alt="" className="absolute inset-0 h-full w-full object-cover object-[70%_50%]"
        onError={(e) => { e.currentTarget.style.display = 'none' }} />
      {/* degradado para que el texto se lea sobre cualquier foto */}
      <div aria-hidden className="absolute inset-0 bg-gradient-to-t from-[#0f1e3d] via-[#0f1e3d]/55 to-[#0f1e3d]/20" />

      <div className="absolute inset-x-0 bottom-0 p-12 xl:p-16">
        <span className="mb-5 block h-1 w-14 rounded-full bg-amber-400" />
        <h2 className="text-[46px] font-bold leading-[1.02] tracking-tight text-white xl:text-[58px]">{MARCA.nombre}</h2>
        <p className="mt-4 max-w-[440px] text-lg leading-snug text-white/80 xl:text-xl">{MARCA.eslogan}</p>
      </div>
    </aside>
  )
}

/** Marco común de las pantallas de acceso (login y restablecer contraseña). */
export function MarcoAcceso({ children }) {
  return (
    <div className="flex min-h-screen bg-fondo">
      <Banner />
      <main className="relative flex flex-1 items-center justify-center px-5 py-12 sm:px-10">
        <BotonTema className="absolute right-4 top-4" />
        <div className="w-full max-w-[420px] rounded-2xl border border-gris-200 bg-superficie p-7 shadow-xl sm:p-10">
          <div className="mb-8 text-center">
            <img src={MARCA.logo} alt="" className="mx-auto h-14 w-14 rounded-2xl shadow-lg shadow-amber-500/20" />
            <p className="mt-4 text-2xl font-bold tracking-tight text-gris-900">{MARCA.nombre}</p>
            <p className="mt-1 text-sm text-gris-500">Accede a tu cuenta</p>
          </div>
          {children}
        </div>
      </main>
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
          {error && <div className="mb-4"><Aviso>{error}</Aviso></div>}

          <label htmlFor="usuario" className="label">Usuario</label>
          <div className="relative mb-4">
            <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gris-400" />
            <input id="usuario" className="input py-2.5 pl-9" autoFocus required autoComplete="username" placeholder="Tu usuario"
              value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </div>

          <label htmlFor="clave" className="label">Contraseña</label>
          <div className="relative">
            <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gris-400" />
            <input id="clave" className="input py-2.5 pl-9 pr-10" required type={show ? 'text' : 'password'} autoComplete="current-password"
              placeholder="••••••••" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            <button type="button" onClick={() => setShow(!show)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              className="icon-btn absolute right-1.5 top-1/2 h-7 w-7 -translate-y-1/2">
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          <div className="mb-6 mt-2 text-right">
            <button type="button" onClick={() => { setModo('recuperar'); setError('') }}
              className="text-[13px] font-medium text-acento-600 hover:underline">¿Olvidaste tu contraseña?</button>
          </div>

          <button className="btn-primary w-full py-3 text-[15px] font-semibold" disabled={loading}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" />} Iniciar sesión
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

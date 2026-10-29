import { useState } from 'react'
import { Loader2, Pencil, Plus, Trash2, UserCog } from 'lucide-react'
import { usuariosService } from '../services/comercial'
import { errorMessage } from '../utils/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Aviso, Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import { Campo } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

function UsuarioForm({ usuario, onSaved, onCancel }) {
  const [f, setF] = useState({
    username: usuario.username || '', first_name: usuario.first_name || '', last_name: usuario.last_name || '',
    email: usuario.email || '', rol: usuario.rol || 'VENDEDOR', is_active: usuario.is_active ?? true, password: '',
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true); setError('')
    const datos = { ...f }
    if (!datos.password) delete datos.password
    try {
      onSaved(usuario.id ? await usuariosService.actualizar(usuario.id, datos) : await usuariosService.crear(datos))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3.5">
      {error && <Aviso>{error}</Aviso>}
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Nombre"><input className="input" autoFocus value={f.first_name} onChange={set('first_name')} /></Campo>
        <Campo label="Apellido"><input className="input" value={f.last_name} onChange={set('last_name')} /></Campo>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Usuario *"><input className="input" required value={f.username} onChange={set('username')} /></Campo>
        <Campo label="Correo" hint="Lo necesita para recuperar su contraseña."><input className="input" type="email" value={f.email} onChange={set('email')} /></Campo>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Rol">
          <select className="input" value={f.rol} onChange={set('rol')}>
            <option value="VENDEDOR">Vendedor</option><option value="ADMIN">Administrador</option>
          </select>
        </Campo>
        <Campo label={usuario.id ? 'Nueva contraseña' : 'Contraseña *'} hint={usuario.id ? 'Déjala vacía para no cambiarla.' : 'Mínimo 6 caracteres.'}>
          <input className="input" type="password" autoComplete="new-password" required={!usuario.id} minLength={6}
            value={f.password} onChange={set('password')} />
        </Campo>
      </div>
      <p className="rounded-[10px] bg-gris-50 p-3 text-sm text-gris-600">
        {f.rol === 'ADMIN'
          ? 'El administrador gestiona catálogo, compras, usuarios, anula ventas y ve reportes.'
          : 'El vendedor registra ventas, aprueba pedidos, atiende WhatsApp y consulta el inventario.'}
      </p>
      {usuario.id && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="h-4 w-4 accent-acento-600" checked={f.is_active} onChange={set('is_active')} /> Puede ingresar al sistema
        </label>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
        <button className="btn-primary" disabled={saving}>{saving && <Loader2 className="h-4 w-4 animate-spin" />}
          {usuario.id ? 'Guardar cambios' : 'Crear usuario'}</button>
      </div>
    </form>
  )
}

export default function Usuarios() {
  const { user } = useAuth()
  const toast = useToast()
  const [editando, setEditando] = useState(null)
  const [aEliminar, setAEliminar] = useState(null)
  const { data, recargar } = useCarga(() => usuariosService.listar(), [])

  const eliminar = async () => {
    try {
      await usuariosService.eliminar(aEliminar.id)
      toast(`Usuario ${aEliminar.username} eliminado.`)
      recargar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
    setAEliminar(null)
  }

  return (
    <>
      <PageHeader title="Usuarios" subtitle="Quién entra al sistema y qué puede hacer.">
        <button className="btn-primary" onClick={() => setEditando({})}><Plus className="h-4 w-4" /> Nuevo usuario</button>
      </PageHeader>
      <div className="panel overflow-hidden">
        {data === null ? <Spinner /> : data.length === 0 ? <Vacio icon={UserCog} titulo="Sin usuarios" /> : (
          <div className="overflow-x-auto">
            <table className="tabla w-full min-w-[620px]">
              <thead><tr><th className="th">Persona</th><th className="th">Usuario</th><th className="th">Rol</th><th className="th">Estado</th><th className="th" /></tr></thead>
              <tbody>
                {data.map((u) => (
                  <tr key={u.id}>
                    <td className="td">
                      <p className="font-semibold">{[u.first_name, u.last_name].filter(Boolean).join(' ') || '—'}</p>
                      <p className="text-xs text-gris-500">{u.email || 'sin correo'}</p>
                    </td>
                    <td className="td cifra">{u.username}{u.id === user.id && <span className="ml-2 text-xs text-gris-500">(tú)</span>}</td>
                    <td className="td"><Badge tone={u.rol === 'ADMIN' ? 'pino' : 'papel'}>{u.rol === 'ADMIN' ? 'Administrador' : 'Vendedor'}</Badge></td>
                    <td className="td">{u.is_active ? <Badge tone="exito" punto>Activo</Badge> : <Badge tone="peligro" punto>Bloqueado</Badge>}</td>
                    <td className="td">
                      <div className="flex justify-end gap-0.5">
                        <button className="icon-btn" aria-label="Editar" title="Editar" onClick={() => setEditando(u)}><Pencil className="h-4 w-4" /></button>
                        {u.id !== user.id && <button className="icon-btn hover:bg-peligro-50 hover:text-peligro-600" aria-label="Eliminar" title="Eliminar"
                          onClick={() => setAEliminar(u)}><Trash2 className="h-4 w-4" /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <Modal open={!!editando} onClose={() => setEditando(null)} title={editando?.id ? `Editar a ${editando.username}` : 'Nuevo usuario'}>
        {editando && <UsuarioForm usuario={editando} onCancel={() => setEditando(null)}
          onSaved={(u) => { setEditando(null); toast(`Usuario ${u.username} guardado.`); recargar() }} />}
      </Modal>
      <Modal open={!!aEliminar} onClose={() => setAEliminar(null)} size="sm" title="Eliminar usuario"
        footer={<><button className="btn-secondary" onClick={() => setAEliminar(null)}>Cancelar</button>
          <button className="btn-danger" onClick={eliminar}>Eliminar</button></>}>
        <p className="text-sm text-gris-600">Si <b className="text-gris-900">{aEliminar?.username}</b> ya registró ventas no se podrá eliminar: edítalo y quítale el acceso.</p>
      </Modal>
    </>
  )
}

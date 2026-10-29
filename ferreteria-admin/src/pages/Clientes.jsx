import { useState } from 'react'
import { Pencil, Plus, Trash2, Users } from 'lucide-react'
import { clientesService } from '../services/comercial'
import { errorMessage } from '../utils/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import { Buscador } from '../components/ui/Filtros'
import ClienteForm from '../components/ventas/ClienteForm'
import useCarga from '../hooks/useCarga'

export default function Clientes() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [editando, setEditando] = useState(null)
  const [aEliminar, setAEliminar] = useState(null)
  const { data, recargar } = useCarga(() => clientesService.listar({ search: search || undefined }), [search], { retraso: 250 })

  const eliminar = async () => {
    try {
      await clientesService.eliminar(aEliminar.id)
      toast(`${aEliminar.nombre} eliminado.`)
      recargar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
    setAEliminar(null)
  }

  return (
    <>
      <PageHeader title="Clientes" subtitle="Personas y empresas que compran en la ferretería.">
        <button className="btn-primary" onClick={() => setEditando({})}><Plus className="h-4 w-4" /> Nuevo cliente</button>
      </PageHeader>
      <Buscador className="mb-4 max-w-md" value={search} onChange={setSearch} placeholder="Nombre, DNI/RUC o teléfono" />

      <div className="panel overflow-hidden">
        {data === null ? <Spinner /> : data.length === 0 ? (
          <Vacio icon={Users} titulo="No hay clientes con ese dato" texto="Registra al cliente para emitir la boleta a su nombre." />
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla w-full min-w-[680px]">
              <thead><tr>
                <th className="th">Cliente</th><th className="th">Documento</th><th className="th">Contacto</th>
                <th className="th text-right">Compras</th><th className="th"><span className="sr-only">Acciones</span></th>
              </tr></thead>
              <tbody>
                {data.map((c) => (
                  <tr key={c.id} className={c.activo ? '' : 'opacity-55'}>
                    <td className="td">
                      <div className="flex items-center gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gris-100 font-display font-semibold uppercase text-acento-600">
                          {c.nombre[0]}
                        </span>
                        <div>
                          <p className="font-semibold">{c.nombre}</p>
                          {!c.activo && <Badge>Inactivo</Badge>}
                        </div>
                      </div>
                    </td>
                    <td className="td cifra text-gris-600">{c.numero_documento ? `${c.tipo_documento} ${c.numero_documento}` : '—'}</td>
                    <td className="td text-sm text-gris-600">{[c.telefono, c.email].filter(Boolean).join(', ') || '—'}</td>
                    <td className="td cifra text-right font-semibold">{c.total_compras}</td>
                    <td className="td">
                      <div className="flex justify-end gap-0.5">
                        <button className="icon-btn" aria-label="Editar" title="Editar" onClick={() => setEditando(c)}><Pencil className="h-4 w-4" /></button>
                        {isAdmin && <button className="icon-btn hover:bg-peligro-50 hover:text-peligro-600" aria-label="Eliminar" title="Eliminar"
                          onClick={() => setAEliminar(c)}><Trash2 className="h-4 w-4" /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={!!editando} onClose={() => setEditando(null)} title={editando?.id ? 'Editar cliente' : 'Nuevo cliente'}>
        {editando && <ClienteForm cliente={editando} onCancel={() => setEditando(null)}
          onSaved={(c) => { setEditando(null); toast(`${c.nombre} guardado.`); recargar() }} />}
      </Modal>
      <Modal open={!!aEliminar} onClose={() => setAEliminar(null)} size="sm" title="Eliminar cliente"
        footer={<><button className="btn-secondary" onClick={() => setAEliminar(null)}>Cancelar</button>
          <button className="btn-danger" onClick={eliminar}>Eliminar</button></>}>
        <p className="text-sm text-gris-600">¿Eliminar a <b className="text-gris-900">{aEliminar?.nombre}</b>? Si ya tiene compras no se podrá: desactívalo en su lugar.</p>
      </Modal>
    </>
  )
}

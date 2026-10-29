import { useState } from 'react'
import { ScrollText } from 'lucide-react'
import { usuariosService } from '../services/comercial'
import { fechaHora, limpiar } from '../utils/format'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Badge from '../components/ui/Badge'
import { Buscador } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

const ACCIONES = {
  CREAR: ['musgo', 'Creó'], EDITAR: ['cobalto', 'Editó'], ELIMINAR: ['oxido', 'Eliminó'], ANULAR: ['oxido', 'Anuló'],
  RECIBIR: ['pino', 'Recibió compra'], APROBAR: ['musgo', 'Aprobó pedido'], RECHAZAR: ['senal', 'Rechazó pedido'],
  IMPORTAR: ['cobalto', 'Importó CSV'], LOGIN: ['papel', 'Ingresó'], PASSWORD: ['senal', 'Cambió contraseña'],
}

/** RF04 - quién hizo qué y cuándo (paginada desde el backend) */
export default function Bitacora() {
  const [f, setF] = useState({ search: '', accion: '', fecha_desde: '', fecha_hasta: '', page: 1 })
  const { data } = useCarga(() => usuariosService.bitacora(limpiar(f)), [JSON.stringify(f)], { retraso: 200 })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value, page: 1 })

  return (
    <>
      <PageHeader title="Bitácora" subtitle="Registro de las acciones importantes: quién las hizo, cuándo y desde qué equipo." />
      <div className="mb-4 grid gap-2.5 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr]">
        <Buscador value={f.search} onChange={(v) => setF({ ...f, search: v, page: 1 })} placeholder="Buscar en la descripción" />
        <select className="input" aria-label="Acción" value={f.accion} onChange={set('accion')}>
          <option value="">Todas las acciones</option>
          {Object.entries(ACCIONES).map(([k, [, l]]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <input type="date" className="input" aria-label="Desde" value={f.fecha_desde} onChange={set('fecha_desde')} />
        <input type="date" className="input" aria-label="Hasta" value={f.fecha_hasta} onChange={set('fecha_hasta')} />
      </div>
      <div className="panel overflow-hidden">
        {!data ? <Spinner /> : data.results.length === 0 ? (
          <Vacio icon={ScrollText} titulo="Sin registros" texto="Prueba con otras fechas o acciones." />
        ) : (
          <>
            <ul className="divide-y divide-gris-100">
              {data.results.map((r) => {
                const [tono, label] = ACCIONES[r.accion] || ['papel', r.accion]
                return (
                  <li key={r.id} className="grid gap-1 px-5 py-3 sm:grid-cols-[150px_150px_1fr] sm:items-center sm:gap-4">
                    <span className="text-sm text-gris-600">{fechaHora(r.fecha)}</span>
                    <span className="flex items-center gap-2">
                      <Badge tone={tono}>{label}</Badge>
                    </span>
                    <span className="min-w-0 text-sm">
                      <b>{r.usuario_nombre || 'Sistema'}</b>
                      <span className="text-gris-600"> en {r.modelo}{r.objeto_id ? ` #${r.objeto_id}` : ''}: </span>
                      {r.descripcion}
                      {r.ip && <span className="ml-1 text-xs text-gris-500">({r.ip})</span>}
                    </span>
                  </li>
                )
              })}
            </ul>
            <div className="flex items-center justify-between border-t border-gris-100 px-5 py-3 text-sm">
              <span className="text-gris-600">{data.count} registros</span>
              <div className="flex gap-2">
                <button className="btn-secondary py-1.5" disabled={!data.previous} onClick={() => setF({ ...f, page: f.page - 1 })}>Anteriores</button>
                <button className="btn-secondary py-1.5" disabled={!data.next} onClick={() => setF({ ...f, page: f.page + 1 })}>Siguientes</button>
              </div>
            </div>
          </>
        )}
      </div>
    </>
  )
}

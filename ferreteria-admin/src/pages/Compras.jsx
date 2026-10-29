import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Plus, Warehouse } from 'lucide-react'
import { comprasService, proveedoresService } from '../services/catalogo'
import { fecha, limpiar, money } from '../utils/format'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import { Estado } from '../components/ui/Badge'
import { Buscador, Pestanas } from '../components/ui/Filtros'
import OrdenForm from '../components/compras/OrdenForm'
import DetalleOrden from '../components/compras/DetalleOrden'
import useCarga from '../hooks/useCarga'

export default function Compras() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [filtros, setFiltros] = useState({ estado: 'PENDIENTE,PARCIAL', proveedor: '', fecha_desde: '', fecha_hasta: '', search: '' })
  const [nueva, setNueva] = useState(params.get('sugerir') ? 'sugerir' : null)
  const [detalle, setDetalle] = useState(null)

  const { data: proveedores } = useCarga(() => proveedoresService.listar(), [], { inicial: [] })
  const { data: ordenes, recargar } = useCarga(() => comprasService.listar(limpiar(filtros)), [JSON.stringify(filtros)], { retraso: 200 })

  useEffect(() => { if (params.get('sugerir')) setParams({}, { replace: true }) }, []) // eslint-disable-line

  const f = (k) => (e) => setFiltros({ ...filtros, [k]: e.target.value })

  return (
    <>
      <PageHeader title="Compras" subtitle="Órdenes a proveedores. Al registrar lo que llegó, el stock sube solo y queda en el kardex.">
        <button className="btn-primary" onClick={() => setNueva('nueva')}><Plus className="h-4 w-4" /> Nueva orden</button>
      </PageHeader>

      <div className="mb-4 flex flex-wrap items-center gap-2.5">
        <Pestanas valor={filtros.estado} onChange={(estado) => setFiltros({ ...filtros, estado })} opciones={[
          ['PENDIENTE,PARCIAL', 'Por recibir'], ['RECIBIDA', 'Recibidas'], ['ANULADA', 'Anuladas'], ['', 'Todas'],
        ]} />
        <select className="input w-auto" aria-label="Proveedor" value={filtros.proveedor} onChange={f('proveedor')}>
          <option value="">Todos los proveedores</option>
          {proveedores.map((p) => <option key={p.id} value={p.id}>{p.razon_social}</option>)}
        </select>
        <input type="date" className="input w-auto" aria-label="Desde" title="Desde" value={filtros.fecha_desde} onChange={f('fecha_desde')} />
        <input type="date" className="input w-auto" aria-label="Hasta" title="Hasta" value={filtros.fecha_hasta} onChange={f('fecha_hasta')} />
        <Buscador className="min-w-[180px] flex-1" value={filtros.search} onChange={(v) => setFiltros({ ...filtros, search: v })} placeholder="N° de orden" />
      </div>

      <div className="panel overflow-hidden">
        {ordenes === null ? <Spinner /> : ordenes.length === 0 ? (
          <Vacio icon={Warehouse} titulo="No hay órdenes aquí" texto="Crea una orden cuando necesites reponer mercadería.">
            <button className="btn-primary" onClick={() => setNueva('sugerir')}>Armar con lo que falta reponer</button>
          </Vacio>
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla w-full min-w-[780px]">
              <thead><tr>
                <th className="th">Orden</th><th className="th">Proveedor</th><th className="th">Entrega</th>
                <th className="th w-48">Recepción</th><th className="th text-right">Total</th>
              </tr></thead>
              <tbody>
                {ordenes.map((o) => (
                  <tr key={o.id} className="cursor-pointer" onClick={() => setDetalle(o)}>
                    <td className="td">
                      <p className="cifra font-semibold text-gris-900">{o.numero}</p>
                      <p className="text-xs text-gris-500">{fecha(o.fecha)}</p>
                    </td>
                    <td className="td">
                      <p className="font-semibold">{o.proveedor_nombre}</p>
                      <p className="text-xs text-gris-500">{o.total_items} unidades en {o.detalles.length} productos</p>
                    </td>
                    <td className="td text-gris-600">{o.fecha_entrega_estimada ? fecha(o.fecha_entrega_estimada) : '—'}</td>
                    <td className="td">
                      <div className="flex items-center gap-2">
                        <div className="h-2 flex-1 overflow-hidden rounded-full bg-gris-100">
                          <div className={`h-full rounded-full ${o.estado === 'ANULADA' ? 'bg-gris-200' : 'bg-exito-500'}`}
                            style={{ width: `${o.avance_recepcion}%` }} />
                        </div>
                        <Estado valor={o.estado} />
                      </div>
                    </td>
                    <td className="td cifra text-right text-[15px] font-semibold">{money(o.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={!!nueva} onClose={() => setNueva(null)} size="lg" title="Nueva orden de compra"
        subtitle="Registrar la orden no mueve el stock; eso pasa cuando llega la mercadería.">
        {nueva && (
          <OrdenForm proveedores={proveedores} sugerir={nueva === 'sugerir'} onCancel={() => setNueva(null)}
            onSaved={(o) => { setNueva(null); toast(`Orden ${o.numero} registrada por ${money(o.total)}.`); recargar(); setDetalle(o) }} />
        )}
      </Modal>

      <Modal open={!!detalle} onClose={() => setDetalle(null)} size="lg"
        title={detalle ? `${detalle.numero}, ${detalle.proveedor_nombre}` : ''}>
        {detalle && (
          <DetalleOrden key={`${detalle.id}-${detalle.estado}-${detalle.avance_recepcion}`} orden={detalle}
            onCambio={(o) => {
              setDetalle(o); recargar()
              toast(o.estado === 'ANULADA' ? `Orden ${o.numero} anulada.`
                : o.estado === 'RECIBIDA' ? `Orden ${o.numero} recibida completa. Stock actualizado.`
                  : `Recepción registrada. Falta ${100 - o.avance_recepcion} % de la orden.`)
            }} />
        )}
      </Modal>
    </>
  )
}

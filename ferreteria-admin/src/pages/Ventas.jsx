import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Ban, FileText, ReceiptText, ShoppingCart } from 'lucide-react'
import { usuariosService, ventasService } from '../services/comercial'
import { errorMessage, fechaHora, limpiar, METODOS_PAGO, money, ORIGENES } from '../utils/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge, { Estado } from '../components/ui/Badge'
import { Buscador, Campo } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

const filtrosVacios = { search: '', fecha_desde: '', fecha_hasta: '', estado: '', metodo_pago: '', origen: '', vendedor: '', mias: '' }

export default function Ventas() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [filtros, setFiltros] = useState(filtrosVacios)
  const [detalle, setDetalle] = useState(null)
  const [anulando, setAnulando] = useState(null)
  const [motivo, setMotivo] = useState('')

  const clave = JSON.stringify(filtros)
  const { data: ventas, recargar } = useCarga(() => ventasService.listar(limpiar(filtros)), [clave], { retraso: 250 })
  const { data: resumen, recargar: recargarResumen } = useCarga(() => ventasService.resumen(limpiar(filtros)), [clave], { retraso: 250 })
  const { data: usuarios } = useCarga(() => (isAdmin ? usuariosService.listar() : Promise.resolve([])), [isAdmin], { inicial: [] })

  const f = (k) => (e) => setFiltros({ ...filtros, [k]: e.target.value })
  const hayFiltros = clave !== JSON.stringify(filtrosVacios)

  const pdf = (v) => ventasService.abrirComprobante(v.id, v.numero).catch((e) => toast(errorMessage(e), 'error'))

  const anular = async (e) => {
    e.preventDefault()
    try {
      await ventasService.anular(anulando.id, motivo)
      toast(`Venta ${anulando.numero} anulada. El stock volvió al almacén.`)
      setAnulando(null); setMotivo(''); setDetalle(null)
      recargar(); recargarResumen()
    } catch (err) {
      toast(errorMessage(err), 'error')
    }
  }

  return (
    <>
      <PageHeader title="Historial de ventas" subtitle="Filtra por fecha, vendedor o forma de pago y abre cualquier comprobante.">
        <Link to="/vender" className="btn-primary"><ShoppingCart className="h-4 w-4" /> Nueva venta</Link>
      </PageHeader>

      {/* Resumen del filtro */}
      {resumen && (
        <section className="mb-5 grid gap-px overflow-hidden rounded-panel border border-gris-200/70 bg-gris-200/70 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['Vendido', money(resumen.total_vendido)],
            ['Ventas', resumen.ventas_completadas],
            ['Ticket promedio', money(resumen.ticket_promedio)],
            ['Anuladas', resumen.ventas_anuladas],
          ].map(([l, v]) => (
            <div key={l} className="bg-superficie px-5 py-4">
              <p className="text-sm text-gris-600">{l}</p>
              <p className="cifra mt-0.5 text-[26px] font-semibold leading-tight text-gris-900">{v}</p>
            </div>
          ))}
        </section>
      )}

      <div className="mb-4 grid gap-2.5 sm:grid-cols-2 lg:grid-cols-6">
        <Buscador className="sm:col-span-2" value={filtros.search} onChange={(v) => setFiltros({ ...filtros, search: v })}
          placeholder="N° de boleta o cliente" />
        <input type="date" className="input" aria-label="Desde" title="Desde" value={filtros.fecha_desde} onChange={f('fecha_desde')} />
        <input type="date" className="input" aria-label="Hasta" title="Hasta" value={filtros.fecha_hasta} onChange={f('fecha_hasta')} />
        <select className="input" aria-label="Forma de pago" value={filtros.metodo_pago} onChange={f('metodo_pago')}>
          <option value="">Cualquier pago</option>
          {METODOS_PAGO.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        {isAdmin ? (
          <select className="input" aria-label="Vendedor" value={filtros.vendedor} onChange={f('vendedor')}>
            <option value="">Todos los vendedores</option>
            {usuarios.map((u) => <option key={u.id} value={u.id}>{u.first_name || u.username}</option>)}
          </select>
        ) : (
          <select className="input" aria-label="Vendedor" value={filtros.mias} onChange={f('mias')}>
            <option value="">Todas las ventas</option>
            <option value="true">Solo mis ventas</option>
          </select>
        )}
        <select className="input" aria-label="Origen" value={filtros.origen} onChange={f('origen')}>
          <option value="">Cualquier origen</option>
          {Object.entries(ORIGENES).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <select className="input" aria-label="Estado" value={filtros.estado} onChange={f('estado')}>
          <option value="">Cualquier estado</option>
          <option value="COMPLETADA">Completadas</option>
          <option value="ANULADA">Anuladas</option>
        </select>
        {hayFiltros && <button className="btn-ghost justify-start" onClick={() => setFiltros(filtrosVacios)}>Limpiar filtros</button>}
      </div>

      <div className="panel overflow-hidden">
        {ventas === null ? <Spinner /> : ventas.length === 0 ? (
          <Vacio icon={ReceiptText} titulo="No hay ventas con esos filtros" texto="Cambia las fechas o limpia los filtros." />
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla w-full min-w-[820px]">
              <thead><tr>
                <th className="th">Boleta</th><th className="th">Fecha</th><th className="th">Cliente</th>
                <th className="th">Vendedor</th><th className="th">Pago</th><th className="th text-right">Total</th>
                <th className="th"><span className="sr-only">Acciones</span></th>
              </tr></thead>
              <tbody>
                {ventas.map((v) => (
                  <tr key={v.id} className="cursor-pointer" onClick={() => setDetalle(v)}>
                    <td className="td">
                      <p className="cifra font-semibold text-gris-900">{v.numero}</p>
                      {v.estado === 'ANULADA' && <Estado valor="ANULADA" />}
                    </td>
                    <td className="td text-gris-600">{fechaHora(v.fecha)}</td>
                    <td className="td">{v.cliente_nombre || <span className="text-gris-500">Clientes varios</span>}</td>
                    <td className="td text-gris-600">{v.vendedor_nombre}</td>
                    <td className="td">
                      <span className="text-sm">{v.metodo_pago_display}</span>
                      {v.origen !== 'MOSTRADOR' && <p className="text-xs text-gris-500">{ORIGENES[v.origen]}</p>}
                    </td>
                    <td className={`td cifra text-right text-[15px] font-semibold ${v.estado === 'ANULADA' ? 'text-gris-500 line-through' : ''}`}>
                      {money(v.total)}
                    </td>
                    <td className="td" onClick={(e) => e.stopPropagation()}>
                      <div className="flex justify-end gap-0.5">
                        <button title="Comprobante PDF" aria-label="Comprobante PDF" className="icon-btn" onClick={() => pdf(v)}>
                          <FileText className="h-4 w-4" /></button>
                        {isAdmin && v.estado === 'COMPLETADA' && (
                          <button title="Anular" aria-label="Anular" className="icon-btn hover:bg-peligro-50 hover:text-peligro-600"
                            onClick={() => setAnulando(v)}><Ban className="h-4 w-4" /></button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isAdmin && resumen?.por_vendedor?.length > 1 && (
        <section className="panel mt-5 p-5">
          <h2 className="text-[15px] font-semibold">Por vendedor</h2>
          <ul className="mt-3 space-y-2.5">
            {resumen.por_vendedor.map((r) => (
              <li key={r.vendedor_id} className="grid grid-cols-[120px_1fr_auto] items-center gap-3 text-sm">
                <span className="truncate font-semibold">{r.vendedor}</span>
                <span className="h-2 rounded-full bg-gris-100">
                  <span className="block h-full rounded-full bg-gris-1000"
                    style={{ width: `${(Number(r.total) / Number(resumen.por_vendedor[0].total)) * 100}%` }} />
                </span>
                <span className="cifra font-semibold">{money(r.total)} <span className="font-normal text-gris-500">({r.cantidad})</span></span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <Modal open={!!detalle} onClose={() => setDetalle(null)} size="lg" title={`Boleta ${detalle?.numero || ''}`}
        subtitle={detalle && `${fechaHora(detalle.fecha)}, atendió ${detalle.vendedor_nombre}`}
        footer={detalle && <>
          {isAdmin && detalle.estado === 'COMPLETADA' && (
            <button className="btn-ghost mr-auto text-peligro-600 hover:bg-peligro-50 hover:text-peligro-700" onClick={() => setAnulando(detalle)}>
              <Ban className="h-4 w-4" /> Anular venta</button>
          )}
          <button className="btn-dark" onClick={() => pdf(detalle)}><FileText className="h-4 w-4" /> Ver PDF</button>
        </>}>
        {detalle && (
          <div>
            <div className="mb-4 flex flex-wrap gap-2">
              <Estado valor={detalle.estado} />
              <Badge tone="acento">{detalle.metodo_pago_display}</Badge>
              <Badge>{ORIGENES[detalle.origen]}</Badge>
              {detalle.pedido_numero && <Badge tone="aviso">Del pedido {detalle.pedido_numero}</Badge>}
            </div>
            <p className="mb-3 text-sm"><span className="text-gris-600">Cliente:</span> {detalle.cliente_nombre || 'Clientes varios'}
              {detalle.cliente_documento && <span className="text-gris-500"> ({detalle.cliente_documento})</span>}</p>
            {detalle.observacion && <p className="mb-3 text-sm text-gris-600">{detalle.observacion}</p>}
            {detalle.estado === 'ANULADA' && (
              <p className="mb-3 rounded-[10px] bg-peligro-50 p-2.5 text-sm text-peligro-700">
                Anulada el {fechaHora(detalle.fecha_anulacion)} por {detalle.anulada_por_nombre}: {detalle.motivo_anulacion}
              </p>
            )}
            <table className="tabla w-full">
              <thead><tr>
                <th className="th">Producto</th><th className="th text-right">Cant.</th>
                <th className="th text-right">P. unit.</th><th className="th text-right">Importe</th>
              </tr></thead>
              <tbody>
                {detalle.detalles.map((d) => (
                  <tr key={d.id}>
                    <td className="td"><span className="etiqueta mr-2">{d.producto_codigo}</span>{d.producto_nombre}</td>
                    <td className="td cifra text-right">{d.cantidad}</td>
                    <td className="td cifra text-right">{money(d.precio_unitario)}</td>
                    <td className="td cifra text-right font-semibold">{money(d.subtotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <dl className="ml-auto mt-4 w-full max-w-[260px] space-y-1 text-sm">
              <div className="flex justify-between"><dt className="text-gris-600">Op. gravada</dt><dd className="cifra">{money(detalle.subtotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-gris-600">IGV 18 %</dt><dd className="cifra">{money(detalle.igv)}</dd></div>
              <div className="flex justify-between border-t border-gris-200 pt-2 text-base font-semibold text-gris-900">
                <dt>Total</dt><dd className="cifra">{money(detalle.total)}</dd></div>
            </dl>
          </div>
        )}
      </Modal>

      <Modal open={!!anulando} onClose={() => setAnulando(null)} size="sm" title={`Anular ${anulando?.numero || ''}`}>
        <form onSubmit={anular}>
          <p className="mb-3 text-sm text-gris-600">
            Los productos vuelven al stock y la boleta queda marcada como anulada. No se puede deshacer.
          </p>
          <Campo label="Motivo *">
            <input className="input" required maxLength={250} autoFocus value={motivo} onChange={(e) => setMotivo(e.target.value)}
              placeholder="Ej.: el cliente devolvió el producto" />
          </Campo>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setAnulando(null)}>Cancelar</button>
            <button className="btn-danger">Anular venta</button>
          </div>
        </form>
      </Modal>
    </>
  )
}

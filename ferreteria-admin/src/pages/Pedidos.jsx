import { useEffect, useState } from 'react'
import { Check, ClipboardCheck, FileText, Globe, Loader2, MessageCircle, Phone, Smartphone, X } from 'lucide-react'
import { pedidosService, ventasService } from '../services/comercial'
import { errorMessage, fechaHora, hace, METODOS_PAGO, money } from '../utils/format'
import { useToast } from '../components/ui/Toast'
import { useAvisos } from '../context/AvisosContext'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Aviso, Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import { Estado } from '../components/ui/Badge'
import { Buscador, Campo, Pestanas } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

const ORIGEN = {
  WEB: { icon: Globe, label: 'Web', color: 'bg-acento-50 text-acento-600' },
  MOVIL: { icon: Smartphone, label: 'App móvil', color: 'bg-gris-100 text-acento-600' },
  WHATSAPP: { icon: MessageCircle, label: 'WhatsApp', color: 'bg-exito-50 text-exito-600' },
  MOSTRADOR: { icon: Globe, label: 'Mostrador', color: 'bg-gris-100 text-gris-600' },
}

function DetallePedido({ pedido, onCambio }) {
  const toast = useToast()
  const [metodo, setMetodo] = useState('EFECTIVO')
  const [rechazando, setRechazando] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const o = ORIGEN[pedido.origen]
  const OIcon = o.icon

  useEffect(() => { setError(''); setRechazando(false); setMotivo('') }, [pedido.id])

  const aprobar = async () => {
    setOcupado(true); setError('')
    try {
      const p = await pedidosService.aprobar(pedido.id, { metodo_pago: metodo })
      toast(`Pedido ${p.numero} aprobado. Se generó la boleta ${p.venta_numero}.`)
      onCambio(p)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setOcupado(false)
    }
  }

  const rechazar = async (e) => {
    e.preventDefault()
    setOcupado(true)
    try {
      const p = await pedidosService.rechazar(pedido.id, motivo)
      toast(`Pedido ${p.numero} rechazado.`)
      setRechazando(false)
      onCambio(p)
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="panel overflow-hidden">
      <div className="border-b border-gris-100 px-5 py-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="cifra text-2xl font-semibold">{pedido.numero}</h2>
          <Estado valor={pedido.estado} />
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gris-600">
          <span className={`chip ${o.color}`}><OIcon className="h-3.5 w-3.5" /> {o.label}</span>
          <span>{fechaHora(pedido.fecha)}</span>
        </div>
        <div className="mt-3 rounded-[12px] bg-gris-50 p-3 text-sm">
          <p className="font-semibold">{pedido.cliente_nombre || pedido.contacto_nombre}</p>
          {pedido.contacto_telefono && (
            <p className="mt-0.5 flex items-center gap-1.5 text-gris-600"><Phone className="h-3.5 w-3.5" /> {pedido.contacto_telefono}</p>
          )}
          {pedido.observacion && <p className="mt-1.5 text-gris-600">{pedido.observacion}</p>}
        </div>
      </div>

      <ul className="divide-y divide-gris-100 px-5">
        {pedido.detalles.map((d) => (
          <li key={d.id} className="flex items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <span className="etiqueta">{d.producto_codigo}</span>
              <p className="mt-1 truncate text-sm font-semibold">{d.cantidad} × {d.producto_nombre}</p>
              {pedido.estado === 'PENDIENTE' && (
                <p className={`text-xs font-semibold ${d.stock_suficiente ? 'text-exito-600' : 'text-peligro-600'}`}>
                  {d.stock_suficiente ? `Hay ${d.stock_disponible} en stock` : `Solo hay ${d.stock_disponible} en stock`}
                </p>
              )}
            </div>
            <p className="cifra shrink-0 font-semibold">{money(d.subtotal)}</p>
          </li>
        ))}
      </ul>

      <div className="border-t border-gris-100 bg-gris-50 px-5 py-4">
        <div className="flex items-baseline justify-between">
          <span className="text-sm text-gris-600">Total estimado con IGV</span>
          <span className="cifra text-2xl font-semibold text-gris-900">{money(pedido.total_estimado)}</span>
        </div>

        {pedido.estado === 'PENDIENTE' && (
          <div className="mt-4 space-y-3">
            {error && <Aviso>{error}</Aviso>}
            {!pedido.puede_aprobarse && !error && (
              <Aviso tono="aviso">No hay stock suficiente para todo el pedido. Repón o rechaza el pedido.</Aviso>
            )}
            <Campo label="Forma de pago al aprobar">
              <select className="input" value={metodo} onChange={(e) => setMetodo(e.target.value)}>
                {METODOS_PAGO.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </Campo>
            <div className="flex gap-2">
              <button className="btn-primary flex-1 py-2.5" disabled={ocupado || !pedido.puede_aprobarse} onClick={aprobar}>
                {ocupado ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} Aprobar y generar venta
              </button>
              <button className="btn-secondary" disabled={ocupado} onClick={() => setRechazando(true)}>
                <X className="h-4 w-4" /> Rechazar</button>
            </div>
            <p className="text-xs text-gris-500">Al aprobar se crea la boleta y se descuenta el stock.</p>
          </div>
        )}

        {pedido.estado === 'APROBADO' && (
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="text-gris-600">Boleta <b className="text-gris-900">{pedido.venta_numero}</b>, aprobó {pedido.revisado_por_nombre}</span>
            <button className="btn-dark" onClick={() => ventasService.abrirComprobante(pedido.venta, pedido.venta_numero)
              .catch((e) => toast(errorMessage(e), 'error'))}><FileText className="h-4 w-4" /> Ver PDF</button>
          </div>
        )}
        {['RECHAZADO', 'CANCELADO'].includes(pedido.estado) && (
          <p className="mt-3 text-sm text-gris-600">
            {pedido.estado === 'RECHAZADO' ? 'Rechazado' : 'Cancelado'} por {pedido.revisado_por_nombre}: {pedido.motivo_rechazo}
          </p>
        )}
      </div>

      <Modal open={rechazando} onClose={() => setRechazando(false)} size="sm" title={`Rechazar ${pedido.numero}`}>
        <form onSubmit={rechazar}>
          <Campo label="Motivo *" hint={pedido.origen === 'WHATSAPP' ? 'El cliente lo verá si consulta el estado por WhatsApp.' : undefined}>
            <input className="input" required autoFocus value={motivo} onChange={(e) => setMotivo(e.target.value)}
              placeholder="Ej.: sin stock hasta el lunes" />
          </Campo>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setRechazando(false)}>Cancelar</button>
            <button className="btn-danger" disabled={ocupado}>Rechazar pedido</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

export default function Pedidos() {
  const avisos = useAvisos()
  const [estado, setEstado] = useState('PENDIENTE')
  const [origen, setOrigen] = useState('')
  const [search, setSearch] = useState('')
  const [sel, setSel] = useState(null)

  const { data: resumen, recargar: recargarResumen } = useCarga(() => pedidosService.resumen(), [])
  const { data: pedidos, setData: setPedidos, recargar } = useCarga(
    () => pedidosService.listar({ estado: estado || undefined, origen: origen || undefined, search: search || undefined }),
    [estado, origen, search], { retraso: 200 })

  useEffect(() => {
    if (pedidos && !pedidos.find((p) => p.id === sel?.id)) setSel(pedidos[0] || null)
  }, [pedidos]) // eslint-disable-line react-hooks/exhaustive-deps

  const onCambio = (p) => {
    setSel(p)
    setPedidos((l) => l.map((x) => (x.id === p.id ? p : x)))
    recargarResumen(); avisos.refrescar()
  }

  return (
    <>
      <PageHeader title="Pedidos" subtitle="Pedidos de la web, la app y WhatsApp. No tocan el stock hasta que los apruebas." />

      <div className="mb-4 flex flex-wrap items-center gap-2.5">
        <Pestanas valor={estado} onChange={setEstado} opciones={[
          ['PENDIENTE', 'Por aprobar', resumen?.pendientes], ['APROBADO', 'Aprobados'],
          ['RECHAZADO', 'Rechazados'], ['', 'Todos'],
        ]} />
        <select className="input w-auto" aria-label="Origen" value={origen} onChange={(e) => setOrigen(e.target.value)}>
          <option value="">Todos los canales</option>
          <option value="WEB">Web</option><option value="MOVIL">App móvil</option><option value="WHATSAPP">WhatsApp</option>
        </select>
        <Buscador className="min-w-[200px] flex-1" value={search} onChange={setSearch} placeholder="Número, nombre o teléfono" />
        <button className="btn-ghost" onClick={() => { recargar(); recargarResumen() }}>Actualizar</button>
      </div>

      {pedidos === null ? <Spinner /> : pedidos.length === 0 ? (
        <div className="panel">
          <Vacio icon={ClipboardCheck} titulo={estado === 'PENDIENTE' ? 'No hay pedidos por aprobar' : 'No hay pedidos aquí'}
            texto="Los pedidos guardados desde Vender, la app móvil o WhatsApp aparecerán en esta bandeja." />
        </div>
      ) : (
        <div className="grid gap-5 lg:grid-cols-[360px_1fr]">
          <ul className="space-y-2">
            {pedidos.map((p) => {
              const o = ORIGEN[p.origen]
              const OIcon = o.icon
              const activo = sel?.id === p.id
              return (
                <li key={p.id}>
                  <button onClick={() => setSel(p)} aria-current={activo}
                    className={`flex w-full items-start gap-3 rounded-[12px] border bg-superficie p-3.5 text-left transition ${
                      activo ? 'border-acento-600 ring-1 ring-acento-600' : 'border-gris-200/80 hover:border-gris-300'}`}>
                    <span className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${o.color}`}>
                      <OIcon className="h-4 w-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="truncate font-semibold">{p.cliente_nombre || p.contacto_nombre}</span>
                        <span className="shrink-0 text-xs text-gris-500">{hace(p.fecha)}</span>
                      </span>
                      <span className="mt-0.5 flex items-center justify-between gap-2 text-sm">
                        <span className="text-gris-600">{p.numero}, {p.detalles.length} {p.detalles.length === 1 ? 'producto' : 'productos'}</span>
                        <span className="cifra font-semibold text-gris-900">{money(p.total_estimado)}</span>
                      </span>
                      {p.estado === 'PENDIENTE' && !p.puede_aprobarse && (
                        <span className="mt-1 block text-xs font-semibold text-peligro-600">Falta stock</span>
                      )}
                      {estado === '' && <span className="mt-1.5 block"><Estado valor={p.estado} /></span>}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
          {sel && <div className="lg:sticky lg:top-6 lg:self-start"><DetallePedido pedido={sel} onCambio={onCambio} /></div>}
        </div>
      )}
    </>
  )
}

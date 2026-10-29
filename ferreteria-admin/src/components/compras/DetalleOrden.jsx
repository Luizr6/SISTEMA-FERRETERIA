import { useState } from 'react'
import { Ban, Loader2, PackageCheck } from 'lucide-react'
import { comprasService } from '../../services/catalogo'
import { errorMessage, fecha, fechaHora, money } from '../../utils/format'
import { Estado } from '../ui/Badge'
import { Aviso } from '../ui/Spinner'
import { Campo } from '../ui/Filtros'
import useCarga from '../../hooks/useCarga'

/** CU11 - recepción total o parcial. Al confirmar, el backend sube el stock y registra el kardex. */
export default function DetalleOrden({ orden, onCambio }) {
  const abierta = ['PENDIENTE', 'PARCIAL'].includes(orden.estado)
  const [modo, setModo] = useState(null) // null | 'recibir' | 'anular'
  const [cant, setCant] = useState(() => Object.fromEntries(orden.detalles.map((d) => [d.id, d.cantidad_pendiente])))
  const [obs, setObs] = useState('')
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const { data: recepciones } = useCarga(() => comprasService.recepciones(orden.id), [orden.id, orden.estado])

  const ejecutar = async (fn) => {
    setOcupado(true); setError('')
    try {
      onCambio(await fn())
      setModo(null)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setOcupado(false)
    }
  }

  const recibir = () => ejecutar(() => comprasService.recibir(orden.id, {
    observacion: obs,
    items: Object.entries(cant).filter(([, c]) => Number(c) > 0).map(([detalle, c]) => ({ detalle: Number(detalle), cantidad: Number(c) })),
  }))

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
        <Estado valor={orden.estado} />
        <span className="text-gris-600">Registrada {fechaHora(orden.fecha)} por {orden.creado_por_nombre}</span>
        {orden.fecha_entrega_estimada && <span className="text-gris-600">Entrega estimada {fecha(orden.fecha_entrega_estimada)}</span>}
      </div>
      {orden.observacion && <p className="mb-3 text-sm text-gris-600">{orden.observacion}</p>}
      {orden.estado === 'ANULADA' && <div className="mb-3"><Aviso>Anulada: {orden.motivo_anulacion}</Aviso></div>}

      <div className="overflow-x-auto rounded-[12px] border border-gris-200">
        <table className="tabla w-full min-w-[620px]">
          <thead><tr>
            <th className="th">Producto</th><th className="th text-right">Pedido</th><th className="th text-right">Recibido</th>
            {modo === 'recibir' ? <th className="th w-32 text-right">Llega ahora</th> : <th className="th text-right">Costo</th>}
            <th className="th text-right">Subtotal</th>
          </tr></thead>
          <tbody>
            {orden.detalles.map((d) => (
              <tr key={d.id}>
                <td className="td"><span className="etiqueta mr-2">{d.producto_codigo}</span>{d.producto_nombre}</td>
                <td className="td cifra text-right">{d.cantidad}</td>
                <td className={`td cifra text-right font-semibold ${d.cantidad_pendiente === 0 ? 'text-exito-600' : ''}`}>{d.cantidad_recibida}</td>
                {modo === 'recibir' ? (
                  <td className="td">
                    <input className="input py-1.5 text-right" type="number" min="0" max={d.cantidad_pendiente}
                      disabled={d.cantidad_pendiente === 0} value={cant[d.id]} aria-label={`Recibir ${d.producto_nombre}`}
                      onChange={(e) => setCant({ ...cant, [d.id]: e.target.value })} />
                  </td>
                ) : <td className="td cifra text-right">{money(d.precio_compra)}</td>}
                <td className="td cifra text-right">{money(d.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-right text-sm text-gris-600">Total <b className="cifra ml-1 text-xl text-gris-900">{money(orden.total)}</b></p>

      {error && <div className="mt-3"><Aviso>{error}</Aviso></div>}

      {abierta && modo === null && (
        <div className="mt-4 flex flex-wrap justify-end gap-2 border-t border-gris-100 pt-4">
          {orden.estado === 'PENDIENTE' && (
            <button className="btn-ghost mr-auto text-peligro-600 hover:bg-peligro-50" onClick={() => setModo('anular')}>
              <Ban className="h-4 w-4" /> Anular orden</button>
          )}
          <button className="btn-secondary" onClick={() => setModo('recibir')}>Recepción parcial</button>
          <button className="btn-primary" disabled={ocupado} onClick={() => ejecutar(() => comprasService.recibir(orden.id, {}))}>
            {ocupado ? <Loader2 className="h-4 w-4 animate-spin" /> : <PackageCheck className="h-4 w-4" />} Llegó todo
          </button>
        </div>
      )}

      {modo === 'recibir' && (
        <div className="mt-4 space-y-3 border-t border-gris-100 pt-4">
          <p className="text-sm text-gris-600">Escribe cuánto llegó de cada producto. Lo que falte quedará pendiente.</p>
          <Campo label="Observación"><input className="input" value={obs} onChange={(e) => setObs(e.target.value)} placeholder="Ej.: faltaron 2 cajas" /></Campo>
          <div className="flex justify-end gap-2">
            <button className="btn-secondary" onClick={() => setModo(null)}>Cancelar</button>
            <button className="btn-primary" disabled={ocupado} onClick={recibir}>
              {ocupado && <Loader2 className="h-4 w-4 animate-spin" />} Registrar recepción
            </button>
          </div>
        </div>
      )}

      {modo === 'anular' && (
        <form className="mt-4 space-y-3 border-t border-gris-100 pt-4"
          onSubmit={(e) => { e.preventDefault(); ejecutar(() => comprasService.anular(orden.id, obs)) }}>
          <Campo label="Motivo de anulación *"><input className="input" required autoFocus value={obs} onChange={(e) => setObs(e.target.value)} /></Campo>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setModo(null)}>Cancelar</button>
            <button className="btn-danger" disabled={ocupado}>Anular orden</button>
          </div>
        </form>
      )}

      {recepciones?.length > 0 && (
        <div className="mt-5">
          <h4 className="font-display text-base font-semibold text-gris-900">Recepciones</h4>
          <ul className="mt-2 space-y-2">
            {recepciones.map((r) => (
              <li key={r.id} className="rounded-[10px] bg-gris-50 p-3 text-sm">
                <p className="font-semibold">{fechaHora(r.fecha)}, recibió {r.usuario_nombre}</p>
                <p className="text-gris-600">{r.detalles.map((d) => `${d.cantidad} ${d.producto_nombre}`).join(', ')}</p>
                {r.observacion && <p className="text-gris-500">{r.observacion}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

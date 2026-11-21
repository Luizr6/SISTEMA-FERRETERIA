import { useEffect, useMemo, useState } from 'react'
import { History, Loader2, Lock, MinusCircle, PlusCircle, RefreshCw, Wallet } from 'lucide-react'
import { cajaService } from '../services/comercial'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import { errorMessage, fechaHora, hora, hoyISO, METODOS_PAGO, money } from '../utils/format'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import { Campo, Pestanas } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

const etiquetaMetodo = (m) => METODOS_PAGO.find(([v]) => v === m)?.[1] || m

const haceDias = (n) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toLocaleDateString('en-CA')
}

/** "−S/ 5.90 faltante" / "+S/ 2.00 sobrante" / "Cuadrada" */
function Diferencia({ valor }) {
  if (valor == null) return <span className="text-gris-500">—</span>
  const n = Number(valor)
  if (n === 0) return <Badge tone="exito" punto>Cuadrada</Badge>
  return n < 0
    ? <Badge tone="peligro" punto>Faltante {money(Math.abs(n))}</Badge>
    : <Badge tone="aviso" punto>Sobrante {money(n)}</Badge>
}

function Cifra({ etiqueta, valor, detalle, destacada = false }) {
  return (
    <div className={`panel p-4 ${destacada ? 'border-acento-500 ring-1 ring-acento-500/30' : ''}`}>
      <p className="text-[13px] font-medium text-gris-600">{etiqueta}</p>
      <p className={`cifra mt-1.5 text-[24px] font-semibold leading-tight ${destacada ? 'text-acento-700' : 'text-gris-900'}`}>{valor}</p>
      {detalle && <p className="mt-0.5 text-[13px] text-gris-500">{detalle}</p>}
    </div>
  )
}

/** Resumen de una caja: efectivo esperado, otros medios y movimientos. */
function ResumenCaja({ caja }) {
  const r = caja.resumen
  const otros = r.por_metodo.filter((m) => m.metodo_pago !== 'EFECTIVO')
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Cifra etiqueta="Monto inicial" valor={money(r.monto_inicial)} detalle={`Abierta ${fechaHora(caja.fecha_apertura)}`} />
        <Cifra etiqueta="Ventas en efectivo" valor={money(r.ventas_efectivo)}
          detalle={`${r.cantidad_ventas} venta(s)${r.ventas_anuladas ? ` · ${r.ventas_anuladas} anulada(s)` : ''}`} />
        <Cifra etiqueta="Ingresos − egresos" valor={money(Number(r.ingresos) - Number(r.egresos))}
          detalle={`+${money(r.ingresos)} / −${money(r.egresos)}`} />
        <Cifra etiqueta={caja.estado === 'ABIERTA' ? 'Debería haber en caja' : 'Efectivo esperado'}
          valor={money(r.efectivo_esperado)} detalle="Solo efectivo" destacada />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <section className="panel">
          <div className="flex items-center justify-between border-b border-gris-200 px-4 py-3">
            <h2 className="text-[15px] font-semibold">Movimientos de caja</h2>
            <span className="text-xs text-gris-500">Dinero que entró o salió sin ser venta</span>
          </div>
          {caja.movimientos?.length ? (
            <div className="overflow-x-auto">
              <table className="tabla w-full">
                <thead><tr>
                  <th className="th">Hora</th><th className="th">Tipo</th><th className="th">Concepto</th>
                  <th className="th">Usuario</th><th className="th text-right">Monto</th>
                </tr></thead>
                <tbody>
                  {caja.movimientos.map((m) => (
                    <tr key={m.id}>
                      <td className="td cifra text-gris-600">{hora(m.fecha)}</td>
                      <td className="td"><Badge tone={m.tipo === 'INGRESO' ? 'exito' : 'peligro'}>{m.tipo_display}</Badge></td>
                      <td className="td">{m.concepto}</td>
                      <td className="td text-gris-600">{m.usuario_nombre}</td>
                      <td className={`td cifra text-right font-semibold ${m.tipo === 'EGRESO' ? 'text-peligro-600' : 'text-exito-700'}`}>
                        {m.tipo === 'EGRESO' ? '−' : '+'}{money(m.monto)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : <p className="px-4 py-6 text-center text-sm text-gris-500">Sin movimientos. Registra aquí los pagos o entradas de dinero que no son ventas.</p>}
        </section>

        <section className="panel p-4">
          <h2 className="text-[15px] font-semibold">Cobrado por otros medios</h2>
          <p className="mt-0.5 text-xs text-gris-500">No está en el cajón: va a la cuenta del negocio.</p>
          <dl className="mt-3 space-y-2 text-sm">
            {otros.length ? otros.map((m) => (
              <div key={m.metodo_pago} className="flex justify-between">
                <dt className="text-gris-600">{etiquetaMetodo(m.metodo_pago)} <span className="text-gris-500">({m.cantidad})</span></dt>
                <dd className="cifra font-semibold">{money(m.total)}</dd>
              </div>
            )) : <p className="text-gris-500">Aún no hay cobros por Yape, Plin, tarjeta o transferencia.</p>}
            <div className="flex justify-between border-t border-gris-100 pt-2">
              <dt className="font-semibold text-gris-900">Total vendido</dt>
              <dd className="cifra font-semibold text-gris-900">{money(r.total_vendido)}</dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  )
}

/** Modal de arqueo: cuenta el efectivo, ve la diferencia y cierra. */
function CerrarCaja({ caja, onClose, onCerrada }) {
  const toast = useToast()
  const [contado, setContado] = useState('')
  const [observacion, setObservacion] = useState('')
  const [enviando, setEnviando] = useState(false)
  const esperado = Number(caja.resumen.efectivo_esperado)
  const diferencia = contado === '' ? null : Math.round((Number(contado) - esperado) * 100) / 100

  const cerrar = async () => {
    setEnviando(true)
    try {
      const c = await cajaService.cerrar(caja.id, { monto_contado: Number(contado), observacion })
      toast('Caja cerrada.')
      onCerrada(c)
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal open onClose={() => !enviando && onClose()} size="sm" title="Cerrar caja (arqueo)"
      subtitle={`Caja de ${caja.usuario_nombre} · abierta ${fechaHora(caja.fecha_apertura)}`}
      footer={<>
        <button className="btn-secondary" disabled={enviando} onClick={onClose}>Cancelar</button>
        <button className="btn-danger" disabled={enviando || contado === '' || Number(contado) < 0} onClick={cerrar}>
          {enviando && <Loader2 className="h-4 w-4 animate-spin" />} Cerrar caja</button>
      </>}>
      <div className="space-y-4">
        <div className="rounded-xl bg-gris-50 p-3 text-sm">
          <div className="flex justify-between"><span className="text-gris-600">Monto inicial</span><span className="cifra">{money(caja.resumen.monto_inicial)}</span></div>
          <div className="flex justify-between"><span className="text-gris-600">+ Ventas en efectivo</span><span className="cifra">{money(caja.resumen.ventas_efectivo)}</span></div>
          <div className="flex justify-between"><span className="text-gris-600">+ Ingresos</span><span className="cifra">{money(caja.resumen.ingresos)}</span></div>
          <div className="flex justify-between"><span className="text-gris-600">− Egresos</span><span className="cifra">{money(caja.resumen.egresos)}</span></div>
          <div className="mt-1 flex justify-between border-t border-gris-200 pt-1 font-semibold">
            <span>Debería haber</span><span className="cifra">{money(esperado)}</span></div>
        </div>
        <Campo label="Efectivo contado (S/)" hint="Cuenta los billetes y monedas del cajón.">
          <input className="input cifra text-lg" type="number" min="0" step="0.10" autoFocus value={contado}
            onChange={(e) => setContado(e.target.value)} />
        </Campo>
        {diferencia != null && (
          <div className="flex items-center justify-between">
            <span className="text-sm text-gris-600">Diferencia</span>
            <Diferencia valor={diferencia} />
          </div>
        )}
        <Campo label="Observación (opcional)">
          <input className="input" maxLength={250} placeholder="Ej.: di mal un vuelto" value={observacion}
            onChange={(e) => setObservacion(e.target.value)} />
        </Campo>
      </div>
    </Modal>
  )
}

function Movimiento({ caja, tipo, onClose, onGuardado }) {
  const toast = useToast()
  const [monto, setMonto] = useState('')
  const [concepto, setConcepto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const egreso = tipo === 'EGRESO'

  const guardar = async () => {
    setEnviando(true)
    try {
      await cajaService.movimiento(caja.id, { tipo, monto: Number(monto), concepto })
      toast(egreso ? 'Egreso registrado.' : 'Ingreso registrado.')
      onGuardado()
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal open onClose={() => !enviando && onClose()} size="sm"
      title={egreso ? 'Registrar egreso' : 'Registrar ingreso'}
      subtitle={egreso ? 'Dinero que sale del cajón (pago a un cargador, compra de útiles…)' : 'Dinero que entra sin ser venta (más sencillo, devolución…)'}
      footer={<>
        <button className="btn-secondary" disabled={enviando} onClick={onClose}>Cancelar</button>
        <button className={egreso ? 'btn-danger' : 'btn-primary'} disabled={enviando || !monto || Number(monto) <= 0 || concepto.trim().length < 3}
          onClick={guardar}>{enviando && <Loader2 className="h-4 w-4 animate-spin" />} Registrar</button>
      </>}>
      <div className="space-y-4">
        <Campo label="Monto (S/)">
          <input className="input cifra text-lg" type="number" min="0.10" step="0.10" autoFocus value={monto}
            onChange={(e) => setMonto(e.target.value)} />
        </Campo>
        <Campo label="Concepto">
          <input className="input" maxLength={150} placeholder={egreso ? 'Ej.: pago a cargador' : 'Ej.: sencillo que dejó el dueño'}
            value={concepto} onChange={(e) => setConcepto(e.target.value)} />
        </Campo>
      </div>
    </Modal>
  )
}

/** Tabla de cajas (historial). */
function TablaCajas({ cajas, mostrarVendedor, onAbrir }) {
  if (!cajas.length) return <Vacio icon={History} titulo="Sin cajas en estas fechas" texto="Cambia el rango de fechas." />
  return (
    <div className="overflow-x-auto">
      <table className="tabla w-full min-w-[860px]">
        <thead><tr>
          {mostrarVendedor && <th className="th">Vendedor</th>}
          <th className="th">Apertura</th><th className="th">Cierre</th>
          <th className="th text-right">Inicial</th><th className="th text-right">Efectivo esperado</th>
          <th className="th text-right">Contado</th><th className="th">Diferencia</th>
          <th className="th text-right">Otros medios</th>
        </tr></thead>
        <tbody>
          {cajas.map((c) => (
            <tr key={c.id} className="cursor-pointer" onClick={() => onAbrir(c)}>
              {mostrarVendedor && <td className="td font-semibold">{c.usuario_nombre}</td>}
              <td className="td cifra text-gris-600">{fechaHora(c.fecha_apertura)}</td>
              <td className="td cifra text-gris-600">
                {c.estado === 'ABIERTA' ? <Badge tone="acento" punto>Abierta</Badge> : fechaHora(c.fecha_cierre)}
              </td>
              <td className="td cifra text-right">{money(c.monto_inicial)}</td>
              <td className="td cifra text-right font-semibold">{money(c.resumen.efectivo_esperado)}</td>
              <td className="td cifra text-right">{c.monto_contado != null ? money(c.monto_contado) : '—'}</td>
              <td className="td"><Diferencia valor={c.diferencia} /></td>
              <td className="td cifra text-right text-gris-600">{money(c.resumen.ventas_otros_medios)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Caja: cada vendedor abre su caja con el sencillo, registra ingresos o egresos y al final la cierra
 * contando el efectivo. El Administrador ve las cajas de todos y los faltantes o sobrantes.
 */
export default function Caja() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [vista, setVista] = useState('mia')
  const [caja, setCaja] = useState(undefined) // undefined = cargando, null = cerrada
  const [montoInicial, setMontoInicial] = useState('')
  const [abriendo, setAbriendo] = useState(false)
  const [movimiento, setMovimiento] = useState(null) // 'INGRESO' | 'EGRESO'
  const [cerrando, setCerrando] = useState(null)
  const [detalle, setDetalle] = useState(null)
  const [desde, setDesde] = useState(haceDias(7))
  const [hasta, setHasta] = useState(hoyISO())

  const cargarActual = async () => {
    try {
      setCaja(await cajaService.actual())
    } catch (e) {
      toast(errorMessage(e), 'error')
      setCaja(null)
    }
  }

  useEffect(() => {
    cargarActual()
    const t = setInterval(cargarActual, 30_000) // las ventas del vendedor actualizan el resumen
    return () => clearInterval(t)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const historial = useCarga(() => cajaService.listar({ fecha_desde: desde, fecha_hasta: hasta }), [desde, hasta, caja?.estado],
    { inicial: [] })
  const otrasCajas = useMemo(() => (historial.data || []).filter((c) => c.id !== caja?.id), [historial.data, caja])
  const faltantes = (historial.data || []).filter((c) => Number(c.diferencia) < 0).length

  const abrir = async () => {
    setAbriendo(true)
    try {
      setCaja(await cajaService.abrir(Number(montoInicial || 0)))
      setMontoInicial('')
      toast('Caja abierta. Ya puedes cobrar.')
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setAbriendo(false)
    }
  }

  const verDetalle = async (c) => {
    try {
      setDetalle(await cajaService.obtener(c.id))
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <>
      <PageHeader title="Caja" subtitle="Abre tu caja con el sencillo, registra los gastos y ciérrala contando el efectivo.">
        {isAdmin && (
          <Pestanas valor={vista} onChange={setVista}
            opciones={[['mia', 'Mi caja'], ['todas', 'Cajas de los vendedores', faltantes]]} />
        )}
      </PageHeader>

      {vista === 'mia' && (
        caja === undefined ? <Spinner /> : caja === null ? (
          <div className="panel mx-auto max-w-md p-6 text-center">
            <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-acento-50 text-acento-600">
              <Wallet className="h-7 w-7" /></span>
            <h2 className="mt-3 text-lg font-semibold">Tu caja está cerrada</h2>
            <p className="mt-1 text-sm text-gris-600">Para cobrar, abre tu caja con el efectivo que tienes en el cajón para dar vuelto.</p>
            <div className="mt-5 text-left">
              <Campo label="Monto inicial (S/)">
                <input className="input cifra text-lg" type="number" min="0" step="0.10" placeholder="0.00" value={montoInicial}
                  onChange={(e) => setMontoInicial(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && abrir()} />
              </Campo>
            </div>
            <button className="btn-primary mt-4 w-full py-3 text-base" disabled={abriendo} onClick={abrir}>
              {abriendo && <Loader2 className="h-4 w-4 animate-spin" />} Abrir caja</button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="flex items-center gap-2 text-sm text-gris-600">
                <span className="h-2.5 w-2.5 rounded-full bg-exito-500" />
                Caja abierta desde las <span className="cifra font-semibold text-gris-900">{hora(caja.fecha_apertura)}</span>
              </p>
              <div className="flex flex-wrap gap-2">
                <button className="btn-ghost" onClick={cargarActual} title="Actualizar"><RefreshCw className="h-4 w-4" /></button>
                <button className="btn-secondary" onClick={() => setMovimiento('INGRESO')}><PlusCircle className="h-4 w-4" /> Ingreso</button>
                <button className="btn-secondary" onClick={() => setMovimiento('EGRESO')}><MinusCircle className="h-4 w-4" /> Egreso</button>
                <button className="btn-danger" onClick={() => setCerrando(caja)}><Lock className="h-4 w-4" /> Cerrar caja</button>
              </div>
            </div>
            <ResumenCaja caja={caja} />
          </div>
        )
      )}

      {(vista === 'todas' || !isAdmin) && (
        <section className={`panel ${vista === 'mia' ? 'mt-6' : ''}`}>
          <div className="flex flex-wrap items-end justify-between gap-3 border-b border-gris-200 px-4 py-3">
            <div>
              <h2 className="text-[15px] font-semibold">{isAdmin ? 'Cajas de los vendedores' : 'Mis cajas anteriores'}</h2>
              <p className="text-xs text-gris-500">Toca una fila para ver el detalle y los movimientos.</p>
            </div>
            <div className="flex gap-2">
              <input className="input w-auto" type="date" value={desde} max={hasta} onChange={(e) => setDesde(e.target.value)} aria-label="Desde" />
              <input className="input w-auto" type="date" value={hasta} min={desde} onChange={(e) => setHasta(e.target.value)} aria-label="Hasta" />
            </div>
          </div>
          {historial.cargando && !historial.data?.length ? <Spinner /> : (
            <TablaCajas cajas={isAdmin ? historial.data : otrasCajas} mostrarVendedor={isAdmin} onAbrir={verDetalle} />
          )}
        </section>
      )}

      {movimiento && caja && (
        <Movimiento caja={caja} tipo={movimiento} onClose={() => setMovimiento(null)}
          onGuardado={() => { setMovimiento(null); cargarActual() }} />
      )}

      {cerrando && (
        <CerrarCaja caja={cerrando} onClose={() => setCerrando(null)}
          onCerrada={(c) => {
            setCerrando(null)
            setDetalle(c) // muestra el resultado del arqueo
            if (caja && c.id === caja.id) setCaja(null)
            historial.recargar()
          }} />
      )}

      <Modal open={!!detalle} onClose={() => setDetalle(null)} size="xl"
        title={detalle ? `Caja de ${detalle.usuario_nombre}` : ''}
        subtitle={detalle ? `${fechaHora(detalle.fecha_apertura)} → ${detalle.fecha_cierre ? fechaHora(detalle.fecha_cierre) : 'abierta'}` : ''}
        footer={detalle && <>
          {isAdmin && detalle.estado === 'ABIERTA' && (
            <button className="btn-danger" onClick={() => { setCerrando(detalle); setDetalle(null) }}><Lock className="h-4 w-4" /> Cerrar esta caja</button>
          )}
          <button className="btn-secondary" onClick={() => setDetalle(null)}>Cerrar</button>
        </>}>
        {detalle && (
          <div className="space-y-4">
            {detalle.estado === 'CERRADA' && (
              <div className="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-xl bg-gris-50 p-3 text-sm">
                <span>Contado: <b className="cifra">{money(detalle.monto_contado)}</b></span>
                <span>Esperado: <b className="cifra">{money(detalle.monto_esperado)}</b></span>
                <Diferencia valor={detalle.diferencia} />
                {detalle.cerrada_por_nombre && detalle.cerrada_por_nombre !== detalle.usuario_nombre && (
                  <span className="text-gris-500">Cerrada por {detalle.cerrada_por_nombre}</span>
                )}
                {detalle.observacion && <span className="text-gris-600">“{detalle.observacion}”</span>}
              </div>
            )}
            <ResumenCaja caja={detalle} />
          </div>
        )}
      </Modal>
    </>
  )
}

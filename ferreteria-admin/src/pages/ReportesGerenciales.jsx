/**
 * Reportes que genera el servicio de Spring Boot (puerto 8081): ventas por rango, arqueos de caja,
 * kardex, registro de comprobantes y cierres del día. La web le pide los datos directo a Spring
 * con el mismo token de Django.
 */
import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CalendarCheck, FileCode2, FileSpreadsheet, FileText, Printer, RefreshCw, Server } from 'lucide-react'
import { cierresService, facturacionService, gerencialService, ventasService } from '../services/comercial'
import { productosService } from '../services/catalogo'
import { errorMessage, fecha, fechaCorta, fechaHora, hoyISO, limpiar, money, numero, ORIGENES } from '../utils/format'
import Spinner, { Vacio, Aviso } from '../components/ui/Spinner'
import Badge from '../components/ui/Badge'
import Metrica from '../components/ui/StatCard'
import { Pestanas } from '../components/ui/Filtros'
import { useToast } from '../components/ui/Toast'
import useCarga from '../hooks/useCarga'
import { useColoresGrafico } from '../context/TemaContext'

const PAGO = { EFECTIVO: 'Efectivo', YAPE: 'Yape', PLIN: 'Plin', TARJETA: 'Tarjeta', TRANSFERENCIA: 'Transferencia' }
const haceDias = (n) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Aviso que solo aparece si el servicio de reportes (Spring Boot) no responde. */
export function EstadoSpring() {
  const [estado, setEstado] = useState('ok')
  useEffect(() => {
    gerencialService.salud().then((r) => setEstado(r.estado === 'ok' ? 'ok' : 'degradado')).catch(() => setEstado('apagado'))
  }, [])
  if (estado === 'ok') return null
  return <Badge tone="peligro" punto>Reportes avanzados no disponibles</Badge>
}

function Rango({ valor, onChange }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <input type="date" className="input w-auto" aria-label="Desde" value={valor.desde}
        onChange={(e) => onChange({ ...valor, desde: e.target.value })} />
      <span className="text-sm text-gris-500">al</span>
      <input type="date" className="input w-auto" aria-label="Hasta" value={valor.hasta}
        onChange={(e) => onChange({ ...valor, hasta: e.target.value })} />
    </div>
  )
}

function useExportar() {
  const toast = useToast()
  return async (tipo, formato, params, nombre) => {
    try {
      await gerencialService.exportar(tipo, formato, limpiar(params), nombre)
    } catch (e) {
      toast(e.response ? 'No se pudo generar el archivo. Revisa los filtros.' : 'El servicio de Spring Boot no responde (puerto 8081).', 'error')
    }
  }
}

function Exportar({ tipo, params, nombre }) {
  const exportar = useExportar()
  return (
    <div className="flex gap-2">
      <button className="btn-secondary" onClick={() => exportar(tipo, 'pdf', params, `${nombre}.pdf`)}>
        <FileText className="h-4 w-4" /> PDF</button>
      <button className="btn-secondary" onClick={() => exportar(tipo, 'excel', params, `${nombre}.xlsx`)}>
        <FileSpreadsheet className="h-4 w-4" /> Excel</button>
    </div>
  )
}

function TablaFilas({ titulo, filas, etiqueta = (k) => k, columnaCantidad = 'Ventas' }) {
  const max = Math.max(1, ...filas.map((f) => Number(f.total)))
  return (
    <div className="panel overflow-hidden">
      <h3 className="px-4 pb-2 pt-3 text-[14px] font-semibold">{titulo}</h3>
      {filas.length === 0 ? <p className="px-4 pb-4 text-sm text-gris-500">Sin datos.</p> : (
        <table className="tabla w-full">
          <tbody>
            {filas.map((f) => (
              <tr key={f.clave}>
                <td className="td">
                  <p className="font-medium">{etiqueta(f.clave)}</p>
                  <div className="mt-1 h-1.5 rounded-full bg-gris-100">
                    <div className="h-full rounded-full bg-acento-500" style={{ width: `${(Number(f.total) / max) * 100}%` }} />
                  </div>
                </td>
                <td className="td cifra w-20 text-right text-gris-600" title={columnaCantidad}>{numero(f.cantidad)}</td>
                <td className="td cifra w-28 text-right font-semibold">{money(f.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------- Ventas por rango
export function VentasGerencial() {
  const c = useColoresGrafico()
  const [r, setR] = useState({ desde: haceDias(29), hasta: hoyISO() })
  const { data, cargando } = useCarga(() => gerencialService.ventas(limpiar(r)), [r.desde, r.hasta])
  const serie = (data?.por_dia || []).map((f) => ({ x: fechaCorta(f.clave), total: Number(f.total) }))

  return (
    <section className="space-y-4">
      <div className="panel flex flex-wrap items-end justify-between gap-3 p-4">
        <div>
          <h2 className="text-[15px] font-semibold">Ventas por rango</h2>
          <p className="text-sm text-gris-600">Calculado por el servicio de Spring Boot sobre las ventas de Django.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Rango valor={r} onChange={setR} />
          <Exportar tipo="ventas" params={r} nombre={`ventas_${r.desde}_${r.hasta}`} />
        </div>
      </div>

      {cargando && !data ? <Spinner /> : !data ? <Aviso>No se pudo cargar. Revisa que Spring Boot esté encendido.</Aviso> : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Metrica etiqueta="Total vendido" valor={money(data.total)} detalle={`IGV ${money(data.igv)}`} />
            <Metrica etiqueta="Ventas" valor={numero(data.ventas_completadas)} detalle={`${data.ventas_anuladas} anuladas`} tono="exito" />
            <Metrica etiqueta="Ticket promedio" valor={money(data.ticket_promedio)} />
            <Metrica etiqueta="Anulado" valor={money(data.monto_anulado)} tono={data.ventas_anuladas ? 'peligro' : 'acento'} />
          </div>

          <div className="panel p-4">
            <h3 className="text-[14px] font-semibold">Vendido por día</h3>
            <div className="mt-3 h-60">
              {data.ventas_completadas === 0 ? <Vacio titulo="Sin ventas en ese rango" /> : (
                <ResponsiveContainer>
                  <BarChart data={serie} margin={{ left: -4, right: 8, top: 8 }}>
                    <CartesianGrid stroke={c.rejilla} vertical={false} />
                    <XAxis dataKey="x" tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} minTickGap={12} />
                    <YAxis tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} width={56}
                      tickFormatter={(v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v)} />
                    <Tooltip formatter={(v) => [money(v), 'Vendido']} cursor={{ fill: c.rejilla }}
                      contentStyle={{ background: c.tooltipFondo, border: `1px solid ${c.tooltipBorde}`, borderRadius: 8, fontSize: 13, color: c.texto }}
                      itemStyle={{ color: c.texto }} labelStyle={{ color: c.eje }} />
                    <Bar dataKey="total" fill={c.linea} radius={[4, 4, 0, 0]} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <TablaFilas titulo="Por vendedor" filas={data.por_vendedor} />
            <TablaFilas titulo="Por categoría (sin IGV · unidades)" filas={data.por_categoria} columnaCantidad="Unidades" />
            <TablaFilas titulo="Por forma de pago" filas={data.por_metodo_pago} etiqueta={(k) => PAGO[k] || k} />
            <TablaFilas titulo="Por canal" filas={data.por_origen} etiqueta={(k) => ORIGENES[k] || k} />
          </div>

          <div className="panel overflow-hidden">
            <h3 className="px-4 pb-2 pt-3 text-[14px] font-semibold">Productos más vendidos</h3>
            {data.top_productos.length === 0 ? <p className="px-4 pb-4 text-sm text-gris-500">Sin datos.</p> : (
              <div className="overflow-x-auto">
                <table className="tabla w-full min-w-[520px]">
                  <thead><tr><th className="th">Producto</th><th className="th text-right">Unidades</th><th className="th text-right">Importe (sin IGV)</th></tr></thead>
                  <tbody>
                    {data.top_productos.map((p) => (
                      <tr key={p.codigo}>
                        <td className="td"><span className="etiqueta">{p.codigo}</span><p className="mt-1 font-medium">{p.nombre}</p></td>
                        <td className="td cifra text-right">{numero(p.unidades)}</td>
                        <td className="td cifra text-right font-semibold">{money(p.importe)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------- Arqueos de caja
function Dif({ valor }) {
  if (valor == null) return <span className="text-gris-500">—</span>
  const n = Number(valor)
  if (n < 0) return <Badge tone="peligro">Falta {money(-n)}</Badge>
  if (n > 0) return <Badge tone="aviso">Sobra {money(n)}</Badge>
  return <Badge tone="exito">Cuadrada</Badge>
}

export function CajasGerencial() {
  const [r, setR] = useState({ desde: haceDias(6), hasta: hoyISO() })
  const { data, cargando } = useCarga(() => gerencialService.cajas(limpiar(r)), [r.desde, r.hasta])

  return (
    <section className="space-y-4">
      <div className="panel flex flex-wrap items-end justify-between gap-3 p-4">
        <div>
          <h2 className="text-[15px] font-semibold">Arqueos de caja</h2>
          <p className="text-sm text-gris-600">Cuánto debía haber, cuánto se contó y quién tuvo faltantes.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Rango valor={r} onChange={setR} />
          <Exportar tipo="cajas" params={r} nombre={`cajas_${r.desde}_${r.hasta}`} />
        </div>
      </div>
      {cargando && !data ? <Spinner /> : !data ? <Aviso>No se pudo cargar. Revisa que Spring Boot esté encendido.</Aviso> : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Metrica etiqueta="Cajas" valor={numero(data.cajas)} detalle={data.abiertas ? `${data.abiertas} abiertas` : 'Todas cerradas'} />
            <Metrica etiqueta="Con faltante" valor={numero(data.con_faltante)} tono={data.con_faltante ? 'peligro' : 'exito'} />
            <Metrica etiqueta="Total faltante" valor={money(data.total_faltante)} tono={Number(data.total_faltante) ? 'peligro' : 'exito'} />
            <Metrica etiqueta="Total sobrante" valor={money(data.total_sobrante)} tono="aviso" />
          </div>
          <div className="panel overflow-hidden">
            <h3 className="px-4 pb-2 pt-3 text-[14px] font-semibold">Por vendedor</h3>
            {data.por_vendedor.length === 0 ? <p className="px-4 pb-4 text-sm text-gris-500">No hubo cajas en ese rango.</p> : (
              <div className="overflow-x-auto">
                <table className="tabla w-full min-w-[560px]">
                  <thead><tr><th className="th">Vendedor</th><th className="th text-right">Cajas</th><th className="th text-right">Con faltante</th>
                    <th className="th text-right">Faltante</th><th className="th text-right">Sobrante</th><th className="th text-right">Vendido</th></tr></thead>
                  <tbody>
                    {data.por_vendedor.map((v) => (
                      <tr key={v.vendedor}>
                        <td className="td font-medium">{v.vendedor}</td>
                        <td className="td cifra text-right">{v.cajas}</td>
                        <td className="td cifra text-right">{v.con_faltante}</td>
                        <td className={`td cifra text-right ${Number(v.faltante) ? 'font-semibold text-peligro-600' : ''}`}>{money(v.faltante)}</td>
                        <td className="td cifra text-right">{money(v.sobrante)}</td>
                        <td className="td cifra text-right">{money(v.vendido)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          <div className="panel overflow-hidden">
            <h3 className="px-4 pb-2 pt-3 text-[14px] font-semibold">Detalle</h3>
            {data.detalle.length === 0 ? <p className="px-4 pb-4 text-sm text-gris-500">Sin cajas.</p> : (
              <div className="overflow-x-auto">
                <table className="tabla w-full min-w-[760px]">
                  <thead><tr><th className="th">Vendedor</th><th className="th">Apertura</th><th className="th text-right">Inicial</th>
                    <th className="th text-right">Efectivo</th><th className="th text-right">Otros medios</th>
                    <th className="th text-right">Esperado</th><th className="th text-right">Contado</th><th className="th">Resultado</th></tr></thead>
                  <tbody>
                    {data.detalle.map((c) => (
                      <tr key={c.id}>
                        <td className="td font-medium">{c.vendedor}</td>
                        <td className="td text-sm text-gris-600">{fechaHora(c.fecha_apertura)}</td>
                        <td className="td cifra text-right">{money(c.monto_inicial)}</td>
                        <td className="td cifra text-right">{money(c.ventas_efectivo)}</td>
                        <td className="td cifra text-right text-gris-600">{money(c.ventas_otros_medios)}</td>
                        <td className="td cifra text-right font-semibold">{money(c.esperado)}</td>
                        <td className="td cifra text-right">{c.contado == null ? '—' : money(c.contado)}</td>
                        <td className="td">{c.estado === 'ABIERTA' ? <Badge tone="acento" punto>Abierta</Badge> : <Dif valor={c.diferencia} />}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------- Kardex
export function KardexGerencial() {
  const toast = useToast()
  const exportar = useExportar()
  const [f, setF] = useState({ codigo: '', desde: haceDias(29), hasta: hoyISO() })
  const [texto, setTexto] = useState('')
  const [opciones, setOpciones] = useState([])
  const [data, setData] = useState(null)
  const [cargando, setCargando] = useState(false)

  // Sugerencias mientras escribe: por nombre o código (catálogo de Django)
  useEffect(() => {
    if (texto.trim().length < 2 || texto === f.etiqueta) { setOpciones([]); return undefined }
    const t = setTimeout(() => {
      productosService.listar({ search: texto.trim() })
        .then((r) => setOpciones((r.results || r).slice(0, 8))).catch(() => setOpciones([]))
    }, 300)
    return () => clearTimeout(t)
  }, [texto, f.etiqueta])

  const consultar = async (filtros) => {
    if (!filtros.codigo) return
    setCargando(true)
    try {
      setData(await gerencialService.kardex(limpiar({ codigo: filtros.codigo, desde: filtros.desde, hasta: filtros.hasta })))
    } catch (err) {
      setData(null)
      toast(errorMessage(err), 'error')
    } finally {
      setCargando(false)
    }
  }

  const elegir = (p) => {
    const etiqueta = `${p.codigo} · ${p.nombre}`
    const nuevo = { ...f, codigo: p.codigo, etiqueta }
    setF(nuevo)
    setTexto(etiqueta)
    setOpciones([])
    consultar(nuevo)
  }

  const buscar = (e) => {
    e?.preventDefault()
    // Si escribió un código exacto sin elegir de la lista, se usa tal cual
    const codigo = f.codigo && texto === f.etiqueta ? f.codigo : texto.trim().split(' ')[0]
    consultar({ ...f, codigo })
  }

  return (
    <section className="space-y-4">
      <form onSubmit={buscar} className="panel flex flex-wrap items-end justify-between gap-3 p-4">
        <div>
          <h2 className="text-[15px] font-semibold">Kardex por producto</h2>
          <p className="text-sm text-gris-600">Entradas, salidas y saldo de un producto. Escribe su nombre o código y elígelo.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative w-72 max-w-full">
            <input className="input" placeholder="Producto (ej. alambre, cemento)" aria-label="Producto" value={texto}
              onChange={(e) => setTexto(e.target.value)} />
            {opciones.length > 0 && (
              <ul className="absolute z-10 mt-1 w-full overflow-hidden rounded-lg border border-gris-200 bg-white shadow-lg">
                {opciones.map((p) => (
                  <li key={p.id}>
                    <button type="button" className="w-full px-3 py-2 text-left text-sm hover:bg-gris-50" onClick={() => elegir(p)}>
                      <span className="etiqueta">{p.codigo}</span> <span className="ml-1">{p.nombre}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <Rango valor={f} onChange={(v) => { const n = { ...f, ...v }; setF(n); if (data) consultar(n) }} />
          <button className="btn-primary" type="submit" disabled={cargando || !texto.trim()}>Ver kardex</button>
          {data && (
            <button type="button" className="btn-secondary" onClick={() => exportar('kardex', 'excel', { codigo: data.codigo, desde: f.desde, hasta: f.hasta }, `kardex_${data.codigo}.xlsx`)}>
              <FileSpreadsheet className="h-4 w-4" /> Excel</button>
          )}
        </div>
      </form>
      {cargando ? <Spinner /> : data && (
        <div className="panel overflow-hidden">
          <div className="flex flex-wrap items-end justify-between gap-2 px-4 pb-2 pt-3">
            <div>
              <span className="etiqueta">{data.codigo}</span>
              <h3 className="mt-1 text-[15px] font-semibold">{data.producto}</h3>
              <p className="text-sm text-gris-600">{data.categoria} · stock actual {data.stock_actual} {data.unidad_medida?.toLowerCase()}</p>
            </div>
            <p className="text-sm text-gris-600">Inicial <b className="cifra">{data.stock_inicial}</b> · Entradas <b className="cifra text-exito-600">+{data.entradas}</b>
              {' '}· Salidas <b className="cifra text-peligro-600">−{data.salidas}</b> · Final <b className="cifra">{data.stock_final}</b></p>
          </div>
          {data.movimientos.length === 0 ? <p className="px-4 pb-4 text-sm text-gris-500">Sin movimientos en ese rango.</p> : (
            <div className="overflow-x-auto">
              <table className="tabla w-full min-w-[680px]">
                <thead><tr><th className="th">Fecha</th><th className="th">Tipo</th><th className="th">Motivo</th><th className="th">Usuario</th>
                  <th className="th text-right">Entrada</th><th className="th text-right">Salida</th><th className="th text-right">Saldo</th></tr></thead>
                <tbody>
                  {data.movimientos.map((m, i) => (
                    <tr key={i}>
                      <td className="td text-sm text-gris-600">{fechaHora(m.fecha)}</td>
                      <td className="td"><Badge tone={m.tipo === 'INGRESO' ? 'exito' : m.tipo === 'SALIDA' ? 'gris' : 'aviso'}>{m.tipo}</Badge></td>
                      <td className="td text-sm">{m.motivo || '—'}</td>
                      <td className="td text-sm text-gris-600">{m.usuario || '—'}</td>
                      <td className="td cifra text-right text-exito-600">{m.entrada || ''}</td>
                      <td className="td cifra text-right text-peligro-600">{m.salida || ''}</td>
                      <td className="td cifra text-right font-semibold">{m.saldo}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------- Comprobantes
export function ComprobantesGerencial() {
  const toast = useToast()
  const [f, setF] = useState({ desde: haceDias(6), hasta: hoyISO(), tipo: '', estado: '' })
  const [pagina, setPagina] = useState(0)
  const { data, cargando, recargar } = useCarga(() => facturacionService.registro(limpiar({ ...f, page: pagina, size: 15 })),
    [f.desde, f.hasta, f.tipo, f.estado, pagina])

  const procesar = async () => {
    try {
      const r = await facturacionService.procesar()
      toast(`Facturación: ${r.emitidos} emitido(s), ${r.anulados} dado(s) de baja.`)
      recargar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <section className="space-y-4">
      <div className="panel flex flex-wrap items-end justify-between gap-3 p-4">
        <div>
          <h2 className="text-[15px] font-semibold">Comprobantes emitidos</h2>
          <p className="text-sm text-gris-600">Spring emite cada minuto el comprobante de las ventas nuevas (XML UBL 2.1, hash y QR).</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Pestanas valor={f.tipo} onChange={(tipo) => { setPagina(0); setF({ ...f, tipo }) }}
            opciones={[['', 'Todos'], ['BOLETA', 'Boletas'], ['FACTURA', 'Facturas']]} />
          <select className="input w-auto" aria-label="Estado" value={f.estado} onChange={(e) => { setPagina(0); setF({ ...f, estado: e.target.value }) }}>
            <option value="">Emitidos y anulados</option><option value="EMITIDO">Emitidos</option><option value="ANULADO">Anulados</option>
          </select>
          <Rango valor={f} onChange={(v) => { setPagina(0); setF({ ...f, ...v }) }} />
          <button className="btn-secondary" onClick={procesar}><RefreshCw className="h-4 w-4" /> Procesar ahora</button>
        </div>
      </div>
      {cargando && !data ? <Spinner /> : !data ? <Aviso>No se pudo cargar. Revisa que Spring Boot esté encendido.</Aviso> : (
        <div className="panel overflow-hidden">
          {data.resultados.length === 0 ? <Vacio titulo="Sin comprobantes en ese rango" /> : (
            <div className="overflow-x-auto">
              <table className="tabla w-full min-w-[820px]">
                <thead><tr><th className="th">Comprobante</th><th className="th">Fecha</th><th className="th">Cliente</th>
                  <th className="th text-right">Total</th><th className="th">Hash</th><th className="th">Estado</th><th className="th text-right">Archivos</th></tr></thead>
                <tbody>
                  {data.resultados.map((c) => (
                    <tr key={c.id}>
                      <td className="td"><span className="etiqueta">{c.tipo === 'FACTURA' ? 'Factura' : 'Boleta'}</span>
                        <p className="mt-1 font-semibold">{c.numero}</p></td>
                      <td className="td text-sm text-gris-600">{fechaHora(c.fecha_emision)}</td>
                      <td className="td text-sm">{c.cliente_nombre}<p className="text-xs text-gris-500">{c.cliente_documento}</p></td>
                      <td className="td cifra text-right font-semibold">{money(c.total)}</td>
                      <td className="td"><code className="text-[11px] text-gris-500" title={c.hash}>{c.hash.slice(0, 14)}…</code></td>
                      <td className="td">{c.estado === 'ANULADO' ? <Badge tone="peligro">Anulado</Badge> : <Badge tone="exito">Emitido</Badge>}</td>
                      <td className="td">
                        <div className="flex justify-end gap-1">
                          <button className="icon-btn" title="PDF" aria-label="PDF" onClick={() => ventasService.abrirComprobante(c.venta_id, c.numero)}>
                            <FileText className="h-4 w-4" /></button>
                          <button className="icon-btn" title="Ticket 80 mm" aria-label="Ticket 80 mm" onClick={() => ventasService.abrirComprobante(c.venta_id, c.numero, 'TICKET')}>
                            <Printer className="h-4 w-4" /></button>
                          <button className="icon-btn" title="XML UBL 2.1" aria-label="XML" onClick={() => facturacionService.xml(c.venta_id, c.numero)
                            .catch((e) => toast(errorMessage(e), 'error'))}>
                            <FileCode2 className="h-4 w-4" /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data.total_paginas > 1 && (
            <div className="flex items-center justify-between border-t border-gris-100 px-4 py-3 text-sm">
              <span className="text-gris-600">{numero(data.total)} comprobantes · página {data.pagina + 1} de {data.total_paginas}</span>
              <div className="flex gap-2">
                <button className="btn-secondary" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
                <button className="btn-secondary" disabled={pagina + 1 >= data.total_paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

// ---------------------------------------------------------------------------- Cierres del día
export function CierresGerencial() {
  const toast = useToast()
  const { data, cargando, recargar } = useCarga(() => cierresService.listar(), [])
  const [ejecutando, setEjecutando] = useState(false)
  const [dia, setDia] = useState(hoyISO())

  const ejecutar = async () => {
    setEjecutando(true)
    try {
      const c = await cierresService.ejecutar(dia)
      toast(`Cierre del ${fecha(c.fecha)} generado${c.alertas.length ? ` con ${c.alertas.length} alerta(s)` : ''}.`)
      recargar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setEjecutando(false)
    }
  }

  return (
    <section className="space-y-4">
      <div className="panel flex flex-wrap items-end justify-between gap-3 p-4">
        <div>
          <h2 className="text-[15px] font-semibold">Cierres del día</h2>
          <p className="text-sm text-gris-600">Se genera solo todos los días a las 11:00 p. m. También puedes generarlo o rehacerlo para cualquier día.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input type="date" className="input w-auto" aria-label="Día del cierre" value={dia} max={hoyISO()}
            onChange={(e) => setDia(e.target.value)} />
          <button className="btn-primary" onClick={ejecutar} disabled={ejecutando || !dia}>
            <CalendarCheck className="h-4 w-4" /> {ejecutando ? 'Generando…' : dia === hoyISO() ? 'Generar cierre de hoy' : 'Generar cierre de ese día'}</button>
        </div>
      </div>
      {cargando && !data ? <Spinner /> : !data ? <Aviso>No se pudo cargar. Revisa que Spring Boot esté encendido.</Aviso>
        : data.length === 0 ? <Vacio icon={Server} titulo="Todavía no hay cierres" texto="Se crean a las 11:00 p. m. o con el botón." /> : (
          <div className="space-y-3">
            {data.map((c) => (
              <article key={c.id} className="panel p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <h3 className="text-[15px] font-semibold">{fecha(c.fecha)}</h3>
                    <Badge tone={c.origen === 'PROGRAMADO' ? 'acento' : 'gris'}>{c.origen === 'PROGRAMADO' ? 'Automático 23:00' : 'Manual'}</Badge>
                  </div>
                  <span className="text-xs text-gris-500">Generado {fechaHora(c.generado_en)}</span>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4">
                  <p><span className="text-gris-500">Vendido</span><br /><b className="cifra">{money(c.total_vendido)}</b></p>
                  <p><span className="text-gris-500">Ventas</span><br /><b className="cifra">{c.cantidad_ventas}</b>
                    {c.ventas_anuladas > 0 && <span className="text-gris-500"> · {c.ventas_anuladas} anuladas</span>}</p>
                  <p><span className="text-gris-500">Efectivo / otros</span><br /><b className="cifra">{money(c.total_efectivo)}</b>
                    <span className="text-gris-500"> / {money(c.total_otros_medios)}</span></p>
                  <p><span className="text-gris-500">Comprobantes</span><br /><b className="cifra">{c.comprobantes_emitidos}</b></p>
                  <p><span className="text-gris-500">Cajas cerradas</span><br /><b className="cifra">{c.cajas_cerradas}</b>
                    {c.cajas_abiertas > 0 && <span className="text-aviso-600"> · {c.cajas_abiertas} abiertas</span>}</p>
                  <p><span className="text-gris-500">Faltantes</span><br /><b className={`cifra ${Number(c.faltantes) ? 'text-peligro-600' : ''}`}>{money(c.faltantes)}</b></p>
                  <p><span className="text-gris-500">Sobrantes</span><br /><b className="cifra">{money(c.sobrantes)}</b></p>
                  <p><span className="text-gris-500">Stock crítico</span><br /><b className="cifra">{c.productos_criticos}</b></p>
                </div>
                {c.alertas.length > 0 && (
                  <ul className="mt-3 space-y-1 rounded-lg bg-aviso-50 p-3 text-sm text-aviso-700">
                    {c.alertas.map((a) => <li key={a}>• {a}</li>)}
                  </ul>
                )}
              </article>
            ))}
          </div>
        )}
    </section>
  )
}

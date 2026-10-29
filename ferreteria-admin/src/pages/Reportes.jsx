import { useState } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { reportesService } from '../services/comercial'
import { fecha, fechaCorta, limpiar, money, numero } from '../utils/format'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Badge from '../components/ui/Badge'
import { Pestanas } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'
import { useColoresGrafico } from '../context/TemaContext'

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic']
const etiqueta = (p, agrupar) => {
  const s = String(p).slice(0, 10)
  if (agrupar === 'mes') return `${MESES[Number(s.slice(5, 7)) - 1]} ${s.slice(2, 4)}`
  if (agrupar === 'semana') return `sem. ${fechaCorta(s)}`
  return fechaCorta(s)
}

function VentasPeriodo() {
  const c = useColoresGrafico()
  const [p, setP] = useState({ agrupar: 'dia', fecha_desde: '', fecha_hasta: '' })
  const { data } = useCarga(() => reportesService.ventas(limpiar(p)), [JSON.stringify(p)])
  const serie = (data?.datos || []).map((d) => ({ x: etiqueta(d.periodo, data.agrupar), total: Number(d.total), cantidad: d.cantidad }))

  return (
    <section className="panel p-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-[15px] font-semibold">Ventas por periodo</h2>
          {data && <p className="text-sm text-gris-600">Del {fecha(data.fecha_desde)} al {fecha(data.fecha_hasta)}:
            <b className="cifra ml-1 text-gris-900">{money(data.total_periodo)}</b></p>}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Pestanas valor={p.agrupar} onChange={(agrupar) => setP({ ...p, agrupar })}
            opciones={[['dia', 'Diario'], ['semana', 'Semanal'], ['mes', 'Mensual']]} />
          <input type="date" className="input w-auto" aria-label="Desde" value={p.fecha_desde} onChange={(e) => setP({ ...p, fecha_desde: e.target.value })} />
          <input type="date" className="input w-auto" aria-label="Hasta" value={p.fecha_hasta} onChange={(e) => setP({ ...p, fecha_hasta: e.target.value })} />
        </div>
      </div>
      <div className="mt-5 h-72">
        {!data ? <Spinner /> : serie.length === 0 ? <Vacio titulo="Sin ventas en ese rango" /> : (
          <ResponsiveContainer>
            <AreaChart data={serie} margin={{ left: -4, right: 8, top: 8 }}>
              <defs>
                <linearGradient id="relleno" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={c.linea} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={c.linea} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={c.rejilla} vertical={false} />
              <XAxis dataKey="x" tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} minTickGap={12} />
              <YAxis tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} width={56}
                tickFormatter={(v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v)} />
              <Tooltip formatter={(v, n) => (n === 'total' ? [money(v), 'Vendido'] : [v, n])}
                contentStyle={{ background: c.tooltipFondo, border: `1px solid ${c.tooltipBorde}`, borderRadius: 8, fontSize: 13, color: c.texto }} itemStyle={{ color: c.texto }} labelStyle={{ color: c.eje }} />
              <Area type="linear" dataKey="total" stroke={c.linea} strokeWidth={2} isAnimationActive={false} fill="url(#relleno)" />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  )
}

function Margen() {
  const [ordenar, setOrdenar] = useState('utilidad')
  const { data } = useCarga(() => reportesService.margen({ ordenar }), [ordenar])

  return (
    <section className="panel mt-6 overflow-hidden">
      <div className="flex flex-wrap items-end justify-between gap-3 px-5 pb-3 pt-4">
        <div>
          <h2 className="text-[15px] font-semibold">Margen por producto</h2>
          {data && <p className="text-sm text-gris-600">Utilidad estimada de lo vendido:
            <b className="cifra ml-1 text-gris-900">{money(data.utilidad_total_estimada)}</b></p>}
        </div>
        <Pestanas valor={ordenar} onChange={setOrdenar}
          opciones={[['utilidad', 'Más utilidad'], ['margen', 'Mayor margen %'], ['unidades', 'Más vendidos']]} />
      </div>
      {!data ? <Spinner /> : (
        <div className="overflow-x-auto">
          <table className="tabla w-full min-w-[760px]">
            <thead><tr>
              <th className="th">Producto</th><th className="th text-right">Costo</th><th className="th text-right">Venta</th>
              <th className="th w-48">Margen</th><th className="th text-right">Vendidos</th><th className="th text-right">Utilidad</th>
            </tr></thead>
            <tbody>
              {data.productos.map((p) => (
                <tr key={p.id}>
                  <td className="td">
                    <span className="etiqueta">{p.codigo}</span>
                    <p className="mt-1 font-semibold">{p.nombre}</p>
                  </td>
                  <td className="td cifra text-right text-gris-600">{money(p.precio_compra)}</td>
                  <td className="td cifra text-right">{money(p.precio_venta)}</td>
                  <td className="td">
                    <div className="flex items-center gap-2">
                      <div className="h-2 flex-1 rounded-full bg-gris-100">
                        <div className={`h-full rounded-full ${p.margen_porcentaje < 10 ? 'bg-peligro-500' : 'bg-exito-500'}`}
                          style={{ width: `${Math.max(0, Math.min(100, p.margen_porcentaje * 2))}%` }} />
                      </div>
                      <span className="cifra w-12 text-right text-sm font-semibold">{p.margen_porcentaje}%</span>
                    </div>
                    {p.alerta && <Badge tone="peligro">{p.alerta === 'MARGEN_NEGATIVO' ? 'Se vende a pérdida' : 'Margen bajo'}</Badge>}
                  </td>
                  <td className="td cifra text-right">{numero(p.unidades_vendidas)}</td>
                  <td className="td cifra text-right font-semibold">{money(p.utilidad_estimada)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default function Reportes() {
  return (
    <>
      <PageHeader title="Reportes" subtitle="Cómo van las ventas y cuánto deja cada producto. Montos sin IGV en el margen." />
      <VentasPeriodo />
      <Margen />
    </>
  )
}

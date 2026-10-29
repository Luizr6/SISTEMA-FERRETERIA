import { Link } from 'react-router-dom'
import { AlertTriangle, CalendarDays, ClipboardCheck, PackageSearch, Plus, TrendingUp } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { dashboardService } from '../services/comercial'
import { diaSemana, fechaCorta, hoyISO, money, numero } from '../utils/format'
import Metrica from '../components/ui/StatCard'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Wincha from '../components/ui/Wincha'
import { useAuth } from '../context/AuthContext'
import { useColoresGrafico } from '../context/TemaContext'
import useCarga from '../hooks/useCarga'

const hoyLargo = () => new Date().toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' })

export default function Dashboard() {
  const { user, isAdmin } = useAuth()
  const c = useColoresGrafico()
  const { data } = useCarga(() => dashboardService.obtener(), [])

  if (!data) return <Spinner />

  const hoy = hoyISO()
  const serie = data.ventas_ultimos_7_dias.map((d) => {
    const esHoy = String(d.dia).slice(0, 10) === hoy
    return { dia: esHoy ? 'Hoy' : diaSemana(d.dia), etiqueta: fechaCorta(d.dia), total: Number(d.total), cantidad: d.cantidad, esHoy }
  })
  const totalSemana = serie.reduce((s, d) => s + d.total, 0)
  const ticket = data.ventas_mes.cantidad ? Number(data.ventas_mes.total) / data.ventas_mes.cantidad : 0
  const maxTop = Math.max(...data.productos_mas_vendidos.map((p) => Number(p.unidades)), 1)

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold">Inicio</h1>
          <p className="mt-1 text-sm text-gris-500">
            Hola, {user.nombre || user.username}. Resumen del {hoyLargo()}.
          </p>
        </div>
        <div className="flex gap-2">
          {isAdmin && <Link to="/compras" className="btn-secondary">Registrar compra</Link>}
          <Link to="/vender" className="btn-primary"><Plus className="h-4 w-4" /> Nueva venta</Link>
        </div>
      </div>

      {/* Métricas esenciales */}
      <section className="grid grid-cols-2 gap-3 xl:grid-cols-4" aria-label="Métricas">
        <Metrica icon={TrendingUp} tono="acento" etiqueta="Ventas de hoy" valor={money(data.ventas_hoy.total)}
          detalle={`${data.ventas_hoy.cantidad} ${data.ventas_hoy.cantidad === 1 ? 'venta' : 'ventas'}`} to="/ventas" />
        <Metrica icon={CalendarDays} tono="exito" etiqueta="Ventas del mes" valor={money(data.ventas_mes.total)}
          detalle={`Ticket promedio ${money(ticket)}`} to="/ventas" />
        <Metrica icon={ClipboardCheck} tono="aviso" etiqueta="Pedidos por aprobar" valor={data.pedidos_pendientes}
          detalle="Web, app y WhatsApp" to="/pedidos" />
        <Metrica icon={AlertTriangle} tono="peligro" etiqueta="Stock crítico" valor={data.stock_critico.length}
          detalle={isAdmin ? `${data.compras_por_recibir} compras por recibir` : 'Productos por reponer'} to="/productos?stock=critico" />
      </section>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <section className="panel p-5 lg:col-span-2">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <div>
              <h2 className="text-[15px] font-semibold">Ventas de los últimos 7 días</h2>
              <p className="text-[13px] text-gris-500">Montos con IGV</p>
            </div>
            <p className="cifra text-lg font-semibold">{money(totalSemana)}</p>
          </div>
          <div className="mt-4 h-60">
            <ResponsiveContainer>
              <BarChart data={serie} margin={{ left: -8, right: 4, top: 4 }}>
                <CartesianGrid stroke={c.rejilla} vertical={false} />
                <XAxis dataKey="dia" tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: c.eje }} axisLine={false} tickLine={false} width={48}
                  tickFormatter={(v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v)} />
                <Tooltip cursor={{ fill: c.rejilla }}
                  contentStyle={{ background: c.tooltipFondo, border: `1px solid ${c.tooltipBorde}`, borderRadius: 8, fontSize: 13, color: c.texto }} itemStyle={{ color: c.texto }} labelStyle={{ color: c.eje }}
                  labelFormatter={(_, p) => p?.[0]?.payload.etiqueta}
                  formatter={(v, _, p) => [`${money(v)} (${p.payload.cantidad} ventas)`, 'Total']} />
                <Bar dataKey="total" radius={[4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false}>
                  {serie.map((d) => <Cell key={d.etiqueta} fill={d.esHoy ? c.barra : c.barraSuave} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="panel p-5">
          <h2 className="text-[15px] font-semibold">Más vendidos</h2>
          <p className="text-[13px] text-gris-500">Unidades vendidas</p>
          {data.productos_mas_vendidos.length === 0 ? (
            <Vacio titulo="Aún no hay ventas" />
          ) : (
            <ol className="mt-4 space-y-3">
              {data.productos_mas_vendidos.map((p) => (
                <li key={p.producto_id}>
                  <div className="flex items-baseline justify-between gap-3 text-[13px]">
                    <span className="truncate text-gris-800">{p.nombre}</span>
                    <span className="cifra shrink-0 font-medium text-gris-900">{numero(p.unidades)}</span>
                  </div>
                  <div className="mt-1.5 h-1.5 rounded-full bg-gris-100">
                    <div className="h-full rounded-full bg-acento-600" style={{ width: `${(Number(p.unidades) / maxTop) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      <section className="panel mt-4 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gris-200 px-5 py-3.5">
          <div>
            <h2 className="text-[15px] font-semibold">Productos por reponer</h2>
            <p className="text-[13px] text-gris-500">Stock igual o menor al mínimo configurado</p>
          </div>
          {isAdmin && data.stock_critico.length > 0 && (
            <Link to="/compras?sugerir=1" className="btn-secondary">Generar orden de compra</Link>
          )}
        </div>
        {data.stock_critico.length === 0 ? (
          <Vacio icon={PackageSearch} titulo="Inventario en orden" texto="Ningún producto está por debajo de su stock mínimo." />
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla w-full min-w-[560px]">
              <thead><tr>
                <th className="th">Código</th><th className="th">Producto</th>
                <th className="th text-right">Mínimo</th><th className="th w-48">Stock</th>
              </tr></thead>
              <tbody>
                {data.stock_critico.map((p) => (
                  <tr key={p.id}>
                    <td className="td"><span className="etiqueta">{p.codigo}</span></td>
                    <td className="td font-medium">{p.nombre}</td>
                    <td className="td cifra text-right text-gris-500">{p.stock_minimo}</td>
                    <td className="td"><Wincha stock={p.stock_actual} minimo={p.stock_minimo} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

import { ArrowDownLeft, ArrowUpRight, SlidersHorizontal } from 'lucide-react'
import { productosService } from '../../services/catalogo'
import { fechaHora } from '../../utils/format'
import Spinner, { Vacio } from '../ui/Spinner'
import Wincha from '../ui/Wincha'
import useCarga from '../../hooks/useCarga'

const TIPOS = {
  INGRESO: { icon: ArrowDownLeft, color: 'bg-exito-50 text-exito-600', label: 'Ingreso' },
  SALIDA: { icon: ArrowUpRight, color: 'bg-peligro-50 text-peligro-600', label: 'Salida' },
  AJUSTE: { icon: SlidersHorizontal, color: 'bg-acento-50 text-acento-600', label: 'Ajuste' },
}

export default function Kardex({ producto }) {
  const { data } = useCarga(() => productosService.kardex(producto.id), [producto.id])

  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-4 rounded-[12px] bg-gris-50 p-3.5">
        <div>
          <span className="etiqueta">{producto.codigo}</span>
          <p className="mt-1 text-sm text-gris-600">Stock actual</p>
        </div>
        <Wincha stock={producto.stock_actual} minimo={producto.stock_minimo} unidad={producto.unidad_medida} />
      </div>
      {data === null ? <Spinner /> : data.length === 0 ? (
        <Vacio titulo="Sin movimientos" texto="Este producto todavía no tiene ingresos ni salidas registradas." />
      ) : (
        <ul className="divide-y divide-gris-100">
          {data.map((m) => {
            const t = TIPOS[m.tipo]
            const Icon = t.icon
            return (
              <li key={m.id} className="flex items-center gap-3 py-2.5">
                <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${t.color}`}>
                  <Icon className="h-4 w-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold">{m.motivo || t.label}</p>
                  <p className="text-xs text-gris-500">{fechaHora(m.fecha)}{m.usuario_nombre ? `, por ${m.usuario_nombre}` : ''}</p>
                </div>
                <div className="text-right">
                  <p className={`cifra font-semibold ${m.cantidad > 0 ? 'text-exito-600' : 'text-peligro-600'}`}>
                    {m.cantidad > 0 ? '+' : ''}{m.cantidad}
                  </p>
                  <p className="text-xs text-gris-500">queda {m.stock_resultante}</p>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}

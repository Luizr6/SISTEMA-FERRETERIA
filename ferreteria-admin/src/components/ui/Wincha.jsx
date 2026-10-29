/**
 * Indicador de stock: cantidad y barra de nivel. La marca vertical es el stock mínimo.
 * Verde = normal, ámbar = por debajo del mínimo, rojo = agotado.
 */
export default function Wincha({ stock, minimo, unidad, compacta = false }) {
  const tope = Math.max(minimo * 3, stock, 1)
  const pct = Math.min(100, (stock / tope) * 100)
  const pctMin = Math.min(100, (minimo / tope) * 100)
  const estado = stock === 0 ? 'agotado' : stock <= minimo ? 'bajo' : 'ok'
  const barra = { agotado: 'bg-peligro-500', bajo: 'bg-aviso-500', ok: 'bg-exito-500' }[estado]
  const texto = { agotado: 'text-peligro-600', bajo: 'text-aviso-700', ok: 'text-gris-900' }[estado]

  return (
    <div className={compacta ? 'w-24 shrink-0 sm:w-28' : 'w-full min-w-[120px] max-w-[170px]'}
      title={`Stock ${stock}${unidad ? ' ' + unidad.toLowerCase() : ''}, mínimo ${minimo}`}>
      <div className="flex items-baseline justify-between gap-2">
        <span className={`cifra text-sm font-semibold ${texto}`}>
          {stock}<span className="ml-1 text-xs font-normal text-gris-500">{unidad?.toLowerCase()}</span>
        </span>
        {!compacta && (
          <span className="text-[11.5px] text-gris-500">
            {estado === 'agotado' ? 'Agotado' : estado === 'bajo' ? 'Reponer' : `mín. ${minimo}`}
          </span>
        )}
      </div>
      <div className="relative mt-1.5 h-1.5 rounded-full bg-gris-100">
        <div className={`absolute inset-y-0 left-0 rounded-full ${barra}`} style={{ width: `${pct}%` }} />
        <div className="absolute -inset-y-[3px] w-px bg-gris-400" style={{ left: `${pctMin}%` }} />
      </div>
    </div>
  )
}

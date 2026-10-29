import { Link } from 'react-router-dom'

/**
 * Tarjeta de métrica: etiqueta, valor y una línea de contexto.
 * Si recibe `to`, toda la tarjeta lleva al módulo donde se trabaja ese dato.
 */
export default function Metrica({ to, icon: Icon, etiqueta, valor, detalle, tono = 'acento' }) {
  const tonos = {
    acento: 'text-acento-600 bg-acento-50',
    exito: 'text-exito-600 bg-exito-50',
    aviso: 'text-aviso-600 bg-aviso-50',
    peligro: 'text-peligro-600 bg-peligro-50',
  }
  const contenido = (
    <>
      <div className="flex items-center justify-between">
        <span className="truncate text-[13px] font-medium text-gris-600">{etiqueta}</span>
        {Icon && (
          <span className={`hidden h-8 w-8 shrink-0 items-center sm:flex justify-center rounded-lg ${tonos[tono]}`}>
            <Icon className="h-4 w-4" />
          </span>
        )}
      </div>
      <p className="cifra mt-2 text-lg font-semibold sm:text-[26px] leading-tight tracking-tight text-gris-900">{valor}</p>
      {detalle && <p className="mt-0.5 text-[13px] text-gris-500">{detalle}</p>}
    </>
  )
  const clase = 'panel block p-4 transition'
  return to
    ? <Link to={to} className={`${clase} hover:border-gris-300 hover:shadow-md`}>{contenido}</Link>
    : <div className={clase}>{contenido}</div>
}

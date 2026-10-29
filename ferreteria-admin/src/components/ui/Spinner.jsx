import { Loader2 } from 'lucide-react'

export default function Spinner({ label = 'Cargando…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-gris-500">
      <Loader2 className="h-4 w-4 animate-spin text-acento-600" /> {label}
    </div>
  )
}

export function Vacio({ icon: Icon, titulo, texto, children }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      {Icon && (
        <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl border border-gris-200 bg-gris-50 text-gris-500">
          <Icon className="h-5 w-5" />
        </div>
      )}
      <p className="text-[15px] font-semibold text-gris-900">{titulo}</p>
      {texto && <p className="mt-1 max-w-sm text-sm text-gris-500">{texto}</p>}
      {children && <div className="mt-4">{children}</div>}
    </div>
  )
}

export function Aviso({ tono = 'peligro', children }) {
  const t = {
    peligro: 'border-peligro-100 bg-peligro-50 text-peligro-700',
    aviso: 'border-aviso-200 bg-aviso-50 text-aviso-700',
    exito: 'border-exito-100 bg-exito-50 text-exito-700',
    acento: 'border-acento-100 bg-acento-50 text-acento-700',
  }
  const alias = { oxido: 'peligro', senal: 'aviso', musgo: 'exito' }
  return <div className={`rounded-lg border px-3 py-2 text-sm ${t[alias[tono] || tono] || t.peligro}`}>{children}</div>
}

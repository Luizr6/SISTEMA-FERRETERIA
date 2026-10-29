import { Search } from 'lucide-react'

export function Buscador({ value, onChange, placeholder = 'Buscar…', className = '' }) {
  return (
    <div className={`relative ${className}`}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gris-500" />
      <input className="input pl-9" type="search" placeholder={placeholder} aria-label={placeholder}
        value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}

/** Pestañas tipo "segmented control" con contador opcional */
export function Pestanas({ opciones, valor, onChange }) {
  return (
    <div role="tablist" className="inline-flex flex-wrap gap-1 rounded-[12px] bg-gris-100 p-1">
      {opciones.map(([v, label, n]) => {
        const activa = valor === v
        return (
          <button key={v} role="tab" aria-selected={activa} onClick={() => onChange(v)}
            className={`flex items-center gap-1.5 rounded-[9px] px-3 py-1.5 text-sm font-semibold transition ${
              activa ? 'bg-superficie text-gris-900 shadow-sm' : 'text-gris-600 hover:text-gris-900'}`}>
            {label}
            {n != null && n > 0 && (
              <span className={`cifra rounded-full px-1.5 text-[11px] leading-[18px] ${
                activa ? 'bg-acento-600 text-white' : 'bg-gris-200 text-gris-600'}`}>{n}</span>
            )}
          </button>
        )
      })}
    </div>
  )
}

export function Campo({ label, children, className = '', hint }) {
  return (
    <label className={`block ${className}`}>
      <span className="label">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-gris-500">{hint}</span>}
    </label>
  )
}

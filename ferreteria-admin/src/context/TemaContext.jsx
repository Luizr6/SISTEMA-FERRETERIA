import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Moon, Sun } from 'lucide-react'

const TemaContext = createContext({ oscuro: false, alternar: () => {} })

const leerInicial = () => {
  try {
    const t = localStorage.getItem('tema')
    if (t) return t === 'oscuro'
  } catch { /* almacenamiento bloqueado */ }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

/** Modo claro / oscuro. Se guarda en el navegador y respeta la preferencia del sistema la primera vez. */
export function TemaProvider({ children }) {
  const [oscuro, setOscuro] = useState(leerInicial)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', oscuro)
  }, [oscuro])

  const alternar = useCallback(() => {
    const raiz = document.documentElement
    raiz.classList.add('tema-transicion') // transición suave solo durante el cambio
    setOscuro((o) => {
      try { localStorage.setItem('tema', o ? 'claro' : 'oscuro') } catch { /* sin almacenamiento */ }
      return !o
    })
    setTimeout(() => raiz.classList.remove('tema-transicion'), 350)
  }, [])

  return <TemaContext.Provider value={{ oscuro, alternar }}>{children}</TemaContext.Provider>
}

export const useTema = () => useContext(TemaContext)

/** Botón para alternar el tema */
export function BotonTema({ className = '', conTexto = false }) {
  const { oscuro, alternar } = useTema()
  const Icono = oscuro ? Sun : Moon
  const texto = oscuro ? 'Modo claro' : 'Modo oscuro'
  return (
    <button type="button" onClick={alternar} title={texto} aria-label={texto}
      className={conTexto ? `btn-ghost w-full justify-start ${className}` : `icon-btn ${className}`}>
      <Icono className="h-4 w-4" />
      {conTexto && <span>{texto}</span>}
    </button>
  )
}

/** Colores para gráficos (Recharts no lee variables CSS en sus props) */
export function useColoresGrafico() {
  const { oscuro } = useTema()
  return oscuro
    ? { barra: '#3b82f6', barraSuave: '#334155', destaque: '#34d399', rejilla: '#1e2431', eje: '#8c96a8', linea: '#60a5fa', tooltipFondo: '#111522', tooltipBorde: '#2a3140', texto: '#f3f5f8' }
    : { barra: '#2563eb', barraSuave: '#cbd5e1', destaque: '#059669', rejilla: '#eef0f3', eje: '#6b7280', linea: '#2563eb', tooltipFondo: '#ffffff', tooltipBorde: '#e5e7eb', texto: '#111827' }
}

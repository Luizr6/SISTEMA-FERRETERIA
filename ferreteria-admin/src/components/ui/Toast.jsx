import { createContext, useCallback, useContext, useState } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'

const ToastContext = createContext(() => {})

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const push = useCallback((message, type = 'success') => {
    const id = Math.random().toString(36).slice(2)
    setToasts((t) => [...t, { id, message, type }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), type === 'error' ? 7000 : 4500)
  }, [])

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div aria-live="polite"
        className="pointer-events-none fixed bottom-4 left-1/2 z-[100] flex w-[min(92vw,400px)] -translate-x-1/2 flex-col gap-2 sm:left-auto sm:right-5 sm:translate-x-0">
        {toasts.map((t) => (
          <div key={t.id} role={t.type === 'error' ? 'alert' : 'status'}
            className="anim-entra pointer-events-auto flex items-start gap-2.5 rounded-lg border border-gris-200 bg-superficie p-3 text-sm text-gris-800 shadow-flota">
            {t.type === 'error'
              ? <XCircle className="mt-px h-[18px] w-[18px] shrink-0 text-peligro-600" />
              : <CheckCircle2 className="mt-px h-[18px] w-[18px] shrink-0 text-exito-600" />}
            <p className="leading-snug">{t.message}</p>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)

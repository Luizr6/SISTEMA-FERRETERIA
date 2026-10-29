import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'

export default function Modal({ open, onClose, title, subtitle, children, size = 'md', footer }) {
  const caja = useRef(null)
  const cerrar = useRef(onClose)
  cerrar.current = onClose

  // Solo depende de `open`: así el foco no salta mientras se escribe
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && cerrar.current()
    window.addEventListener('keydown', onKey)
    const previo = document.activeElement
    const foco = caja.current?.querySelector('[autofocus], input, select, textarea, button:not([data-cerrar])')
    foco?.focus()
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      previo?.focus?.()
    }
  }, [open])

  if (!open) return null
  const width = { sm: 'sm:max-w-md', md: 'sm:max-w-xl', lg: 'sm:max-w-3xl', xl: 'sm:max-w-5xl' }[size]

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-[2px] sm:items-center sm:p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={caja} role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : undefined}
        className={`anim-entra flex max-h-[94vh] w-full ${width} flex-col overflow-hidden rounded-t-2xl border border-gris-200 bg-superficie shadow-flota sm:rounded-xl`}>
        <div className="flex items-start justify-between gap-4 border-b border-gris-200 px-5 pb-3.5 pt-4">
          <div className="min-w-0">
            <h3 className="text-base font-semibold leading-tight">{title}</h3>
            {subtitle && <p className="mt-0.5 text-sm text-gris-600">{subtitle}</p>}
          </div>
          <button data-cerrar onClick={onClose} className="icon-btn -mr-1 shrink-0" aria-label="Cerrar">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-gris-100 bg-gris-50 px-5 py-3">{footer}</div>}
      </div>
    </div>
  )
}

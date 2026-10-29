import { useCallback, useEffect, useRef, useState } from 'react'
import { useToast } from '../components/ui/Toast'
import { errorMessage } from '../utils/format'

/**
 * Carga datos de la API y expone {data, cargando, recargar}.
 * `retraso` sirve para búsquedas mientras se escribe.
 */
export default function useCarga(fn, deps = [], { retraso = 0, inicial = null } = {}) {
  const toast = useToast()
  const [data, setData] = useState(inicial)
  const [cargando, setCargando] = useState(true)
  const ultima = useRef(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const recargar = useCallback(async () => {
    const id = ++ultima.current
    setCargando(true)
    try {
      const r = await fn()
      if (id === ultima.current) setData(r)
    } catch (e) {
      if (id === ultima.current) toast(errorMessage(e), 'error')
    } finally {
      if (id === ultima.current) setCargando(false)
    }
  }, deps)

  useEffect(() => {
    if (!retraso) { recargar(); return undefined }
    const t = setTimeout(recargar, retraso)
    return () => clearTimeout(t)
  }, [recargar, retraso])

  return { data, setData, cargando, recargar }
}

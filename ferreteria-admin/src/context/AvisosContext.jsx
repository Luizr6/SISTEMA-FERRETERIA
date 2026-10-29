import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { pedidosService, whatsappService } from '../services/comercial'

const AvisosContext = createContext({ pedidos: 0, mensajes: 0, refrescar: () => {} })

/** Contadores para la barra lateral (pedidos por aprobar y mensajes sin leer). Se actualiza cada 30 s. */
export function AvisosProvider({ children }) {
  const [avisos, setAvisos] = useState({ pedidos: 0, mensajes: 0 })

  const refrescar = useCallback(async () => {
    const [p, w] = await Promise.allSettled([pedidosService.resumen(), whatsappService.estado()])
    setAvisos({
      pedidos: p.status === 'fulfilled' ? p.value.pendientes : 0,
      mensajes: w.status === 'fulfilled' ? w.value.mensajes_sin_leer : 0,
    })
  }, [])

  useEffect(() => {
    refrescar()
    const t = setInterval(refrescar, 30_000)
    return () => clearInterval(t)
  }, [refrescar])

  return <AvisosContext.Provider value={{ ...avisos, refrescar }}>{children}</AvisosContext.Provider>
}

export const useAvisos = () => useContext(AvisosContext)

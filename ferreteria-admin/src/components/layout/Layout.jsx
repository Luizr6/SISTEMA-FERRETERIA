import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Menu } from 'lucide-react'
import Sidebar from './Sidebar'
import CambiarClave from './CambiarClave'
import { AvisosProvider } from '../../context/AvisosContext'
import { BotonTema } from '../../context/TemaContext'

export default function Layout() {
  const [open, setOpen] = useState(false)
  const [clave, setClave] = useState(false)
  const { pathname } = useLocation()

  return (
    <AvisosProvider>
      <div className="min-h-screen">
        <Sidebar open={open} onClose={() => setOpen(false)} onCambiarClave={() => setClave(true)} />
        <div className="lg:pl-[248px]">
          <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-gris-200 bg-superficie/90 px-4 backdrop-blur lg:hidden">
            <button onClick={() => setOpen(true)} className="icon-btn" aria-label="Abrir menú"><Menu className="h-5 w-5" /></button>
            <img src="/favicon.svg" alt="" className="h-6 w-6 rounded-md" />
            <span className="flex-1 text-sm font-semibold">Ferretería</span>
            <BotonTema />
          </header>
          <main key={pathname} className="anim-entra mx-auto max-w-[1320px] px-4 py-6 sm:px-6 lg:px-8 lg:py-7">
            <Outlet />
          </main>
        </div>
        <CambiarClave open={clave} onClose={() => setClave(false)} />
      </div>
    </AvisosProvider>
  )
}

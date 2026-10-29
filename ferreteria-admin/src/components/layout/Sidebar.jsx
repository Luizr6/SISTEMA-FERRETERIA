import { NavLink } from 'react-router-dom'
import {
  BarChart3, ClipboardCheck, KeyRound, LayoutDashboard, LogOut, MessageCircle, Package, ReceiptText,
  ScrollText, ShoppingCart, Truck, UserCog, Users, Warehouse, X,
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useAvisos } from '../../context/AvisosContext'
import { BotonTema } from '../../context/TemaContext'

// `admin: true` = solo visible para el Administrador (el backend también lo valida)
const grupos = [
  {
    links: [{ to: '/', label: 'Inicio', icon: LayoutDashboard, end: true }],
  },
  {
    titulo: 'Operaciones',
    links: [
      { to: '/vender', label: 'Vender', icon: ShoppingCart },
      { to: '/pedidos', label: 'Pedidos', icon: ClipboardCheck, aviso: 'pedidos' },
      { to: '/whatsapp', label: 'WhatsApp', icon: MessageCircle, aviso: 'mensajes' },
      { to: '/ventas', label: 'Historial de ventas', icon: ReceiptText },
    ],
  },
  {
    titulo: 'Inventario',
    links: [
      { to: '/productos', label: 'Productos', icon: Package },
      { to: '/compras', label: 'Compras', icon: Warehouse, admin: true },
      { to: '/proveedores', label: 'Proveedores', icon: Truck },
    ],
  },
  {
    titulo: 'Gestión',
    links: [
      { to: '/clientes', label: 'Clientes', icon: Users },
      { to: '/usuarios', label: 'Usuarios', icon: UserCog, admin: true },
    ],
  },
  {
    titulo: 'Gerencia',
    links: [
      { to: '/reportes', label: 'Reportes', icon: BarChart3, admin: true },
      { to: '/bitacora', label: 'Bitácora', icon: ScrollText, admin: true },
    ],
  },
]

export default function Sidebar({ open, onClose, onCambiarClave }) {
  const { user, isAdmin, logout } = useAuth()
  const avisos = useAvisos()
  const nombre = user?.nombre || user?.username

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-black/40 lg:hidden" onClick={onClose} />}
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-[248px] flex-col border-r border-gris-200 bg-lateral transition-transform duration-300 lg:translate-x-0 ${
        open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-14 items-center justify-between border-b border-gris-200 px-4">
          <div className="flex items-center gap-2.5">
            <img src="/favicon.svg" alt="" className="h-7 w-7 rounded-md" />
            <div className="leading-tight">
              <p className="text-sm font-semibold text-gris-900">Ferretería</p>
              <p className="text-[11.5px] text-gris-500">Sistema de gestión</p>
            </div>
          </div>
          <button className="icon-btn lg:hidden" onClick={onClose} aria-label="Cerrar menú"><X className="h-4 w-4" /></button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-3" aria-label="Principal">
          {grupos.map((g, i) => {
            const links = g.links.filter((l) => !l.admin || isAdmin)
            if (!links.length) return null
            return (
              <div key={g.titulo || i} className={i ? 'mt-5' : ''}>
                {g.titulo && <p className="mb-1 px-2.5 text-[11.5px] font-medium text-gris-500">{g.titulo}</p>}
                <ul className="space-y-0.5">
                  {links.map(({ to, label, icon: Icon, end, aviso }) => {
                    const n = aviso ? avisos[aviso] : 0
                    return (
                      <li key={to}>
                        <NavLink to={to} end={end} onClick={onClose}
                          className={({ isActive }) =>
                            `flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[13.5px] font-medium transition ${
                              isActive
                                ? 'bg-acento-50 text-acento-700'
                                : 'text-gris-600 hover:bg-gris-100 hover:text-gris-900'}`}>
                          <Icon className="h-4 w-4 shrink-0" />
                          <span className="flex-1 truncate">{label}</span>
                          {n > 0 && (
                            <span className="cifra min-w-[20px] rounded-full bg-acento-600 px-1.5 text-center text-[11px] font-semibold leading-[18px] text-white">
                              {n}
                            </span>
                          )}
                        </NavLink>
                      </li>
                    )
                  })}
                </ul>
              </div>
            )
          })}
        </nav>

        <div className="border-t border-gris-200 p-3">
          <BotonTema conTexto className="mb-1 text-[13px]" />
          <div className="flex items-center gap-2.5 rounded-md px-1.5 py-1.5">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gris-100 text-[13px] font-semibold uppercase text-gris-700">
              {nombre?.[0]}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] font-medium text-gris-900">{nombre}</p>
              <p className="text-[11.5px] text-gris-500">{user?.rol === 'ADMIN' ? 'Administrador' : 'Vendedor'}</p>
            </div>
            <button onClick={onCambiarClave} title="Cambiar contraseña" aria-label="Cambiar contraseña" className="icon-btn h-7 w-7">
              <KeyRound className="h-3.5 w-3.5" /></button>
            <button onClick={logout} title="Cerrar sesión" aria-label="Cerrar sesión" className="icon-btn h-7 w-7">
              <LogOut className="h-3.5 w-3.5" /></button>
          </div>
        </div>
      </aside>
    </>
  )
}

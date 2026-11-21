import { NavLink } from 'react-router-dom'
import {
  BarChart3, ClipboardCheck, KeyRound, LayoutDashboard, LogOut, MessageCircle, Package, ReceiptText,
  ScrollText, ShoppingCart, Truck, UserCog, Users, Wallet, Warehouse, X,
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useAvisos } from '../../context/AvisosContext'
import { BotonTema } from '../../context/TemaContext'
import { MARCA } from '../../config/marca'

// `admin: true` = solo visible para el Administrador (el backend también lo valida)
const grupos = [
  {
    links: [{ to: '/', label: 'Inicio', icon: LayoutDashboard, end: true }],
  },
  {
    titulo: 'Operaciones',
    links: [
      { to: '/vender', label: 'Vender', icon: ShoppingCart },
      { to: '/caja', label: 'Caja', icon: Wallet },
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
      {open && <div className="fixed inset-0 z-30 bg-black/50 lg:hidden" onClick={onClose} />}
      {/* Menú lateral siempre oscuro (azul marino en modo claro): textos en blanco con opacidad */}
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-[248px] flex-col border-r border-white/5 bg-lateral text-white transition-transform duration-300 lg:translate-x-0 ${
        open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-16 items-center justify-between border-b border-white/10 px-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <img src={MARCA.logo} alt="" className="h-9 w-9 shrink-0 rounded-[10px] shadow-md shadow-black/30" />
            <div className="min-w-0 leading-tight">
              <p className="truncate text-[14.5px] font-bold tracking-tight text-white">{MARCA.nombre}</p>
              <p className="text-[11.5px] text-white/50">{MARCA.subtitulo}</p>
            </div>
          </div>
          <button className="icon-btn text-white/70 hover:bg-white/10 hover:text-white lg:hidden" onClick={onClose} aria-label="Cerrar menú">
            <X className="h-4 w-4" /></button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label="Principal">
          {grupos.map((g, i) => {
            const links = g.links.filter((l) => !l.admin || isAdmin)
            if (!links.length) return null
            return (
              <div key={g.titulo || i} className={i ? 'mt-5' : ''}>
                {g.titulo && <p className="mb-1.5 px-2.5 text-[11px] font-semibold uppercase tracking-[.08em] text-white/40">{g.titulo}</p>}
                <ul className="space-y-0.5">
                  {links.map(({ to, label, icon: Icon, end, aviso }) => {
                    const n = aviso ? avisos[aviso] : 0
                    return (
                      <li key={to}>
                        <NavLink to={to} end={end} onClick={onClose}
                          className={({ isActive }) =>
                            `group flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium transition ${
                              isActive
                                ? 'bg-white/[.12] text-white shadow-[inset_0_0_0_1px_rgba(255,255,255,.06)]'
                                : 'text-white/65 hover:bg-white/[.06] hover:text-white'}`}>
                          {({ isActive }) => (
                            <>
                              <Icon className={`h-[17px] w-[17px] shrink-0 ${isActive ? 'text-amber-400' : 'text-white/45 group-hover:text-white/80'}`} />
                              <span className="flex-1 truncate">{label}</span>
                              {n > 0 && (
                                <span className="cifra min-w-[20px] rounded-full bg-amber-400 px-1.5 text-center text-[11px] font-bold leading-[18px] text-[#0f1e3d]">
                                  {n}
                                </span>
                              )}
                            </>
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

        <div className="border-t border-white/10 p-3">
          <BotonTema conTexto className="mb-1 text-[13px] text-white/65 hover:bg-white/[.06] hover:text-white" />
          <div className="flex items-center gap-2.5 rounded-lg bg-white/[.05] px-2 py-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-400 text-[13px] font-bold uppercase text-[#0f1e3d]">
              {nombre?.[0]}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] font-medium text-white">{nombre}</p>
              <p className="text-[11.5px] text-white/50">{user?.rol === 'ADMIN' ? 'Administrador' : 'Vendedor'}</p>
            </div>
            <button onClick={onCambiarClave} title="Cambiar contraseña" aria-label="Cambiar contraseña"
              className="icon-btn h-7 w-7 text-white/60 hover:bg-white/10 hover:text-white">
              <KeyRound className="h-3.5 w-3.5" /></button>
            <button onClick={logout} title="Cerrar sesión" aria-label="Cerrar sesión"
              className="icon-btn h-7 w-7 text-white/60 hover:bg-white/10 hover:text-white">
              <LogOut className="h-3.5 w-3.5" /></button>
          </div>
        </div>
      </aside>
    </>
  )
}

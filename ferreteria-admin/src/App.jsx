import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/layout/Layout'
import Login from './pages/Login'
import RecuperarClave from './pages/RecuperarClave'
import Dashboard from './pages/Dashboard'
import Productos from './pages/Productos'
import Vender from './pages/Vender'
import Ventas from './pages/Ventas'
import Pedidos from './pages/Pedidos'
import Compras from './pages/Compras'
import Proveedores from './pages/Proveedores'
import Clientes from './pages/Clientes'
import WhatsApp from './pages/WhatsApp'
import Reportes from './pages/Reportes'
import Usuarios from './pages/Usuarios'
import Bitacora from './pages/Bitacora'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/reset-password" element={<RecuperarClave />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/vender" element={<Vender />} />
          <Route path="/pedidos" element={<Pedidos />} />
          <Route path="/whatsapp" element={<WhatsApp />} />
          <Route path="/ventas" element={<Ventas />} />
          <Route path="/productos" element={<Productos />} />
          <Route path="/proveedores" element={<Proveedores />} />
          <Route path="/clientes" element={<Clientes />} />
          <Route element={<ProtectedRoute soloAdmin />}>
            <Route path="/compras" element={<Compras />} />
            <Route path="/reportes" element={<Reportes />} />
            <Route path="/usuarios" element={<Usuarios />} />
            <Route path="/bitacora" element={<Bitacora />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

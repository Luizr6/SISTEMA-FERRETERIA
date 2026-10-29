import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/** Exige sesión. Con `soloAdmin` además exige rol Administrador. */
export default function ProtectedRoute({ soloAdmin = false }) {
  const { user, isAdmin } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  if (soloAdmin && !isAdmin) return <Navigate to="/" replace />
  return <Outlet />
}

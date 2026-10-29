import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authService from '../services/authService'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => authService.getStoredUser())

  const login = useCallback(async (username, password) => {
    const u = await authService.login(username, password)
    setUser(u)
    return u
  }, [])

  const logout = useCallback(() => {
    authService.logout()
    setUser(null)
  }, [])

  // El interceptor de Axios avisa cuando la sesión expiró del todo
  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('auth:logout', onLogout)
    return () => window.removeEventListener('auth:logout', onLogout)
  }, [])

  const value = useMemo(
    () => ({ user, isAdmin: user?.rol === 'ADMIN', login, logout }),
    [user, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

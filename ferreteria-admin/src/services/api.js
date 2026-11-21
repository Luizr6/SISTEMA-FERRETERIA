import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000/api'

export const storage = {
  get access() { return localStorage.getItem('access') },
  get refresh() { return localStorage.getItem('refresh') },
  setTokens({ access, refresh }) {
    if (access) localStorage.setItem('access', access)
    if (refresh) localStorage.setItem('refresh', refresh)
  },
  clear() {
    localStorage.removeItem('access')
    localStorage.removeItem('refresh')
    localStorage.removeItem('user')
  },
}

const api = axios.create({ baseURL: BASE_URL, timeout: 15000 })

// 1) Request: adjunta automáticamente  Authorization: Bearer <token>
api.interceptors.request.use((config) => {
  const token = storage.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 2) Response: si el access token venció (401) intenta renovarlo con el refresh token
let refreshing = null

/**
 * Renueva el access con el refresh guardado. Con la sesión de 30 días Django devuelve también un
 * refresh nuevo y anula el anterior, así que todas las renovaciones pasan por aquí (una a la vez).
 */
export async function renovarSesion() {
  refreshing ??= axios
    .post(`${BASE_URL}/auth/refresh/`, { refresh: storage.refresh })
    .then(({ data }) => {
      storage.setTokens({ access: data.access, refresh: data.refresh })
      return data.access
    })
    .finally(() => { refreshing = null })
  return refreshing
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const status = error.response?.status
    const esAuth = original?.url?.includes('/auth/login/') || original?.url?.includes('/auth/refresh/')

    if (status === 401 && !original._retry && !esAuth && storage.refresh) {
      original._retry = true
      try {
        const access = await renovarSesion()
        original.headers.Authorization = `Bearer ${access}`
        return api(original)
      } catch {
        // refresh inválido o vencido -> cerrar sesión
      }
    }

    if (status === 401 && !esAuth) {
      storage.clear()
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(error)
  },
)

export default api

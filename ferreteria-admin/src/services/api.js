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

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const status = error.response?.status
    const esAuth = original?.url?.includes('/auth/login/') || original?.url?.includes('/auth/refresh/')

    if (status === 401 && !original._retry && !esAuth && storage.refresh) {
      original._retry = true
      try {
        refreshing ??= axios
          .post(`${BASE_URL}/auth/refresh/`, { refresh: storage.refresh })
          .finally(() => { refreshing = null })
        const { data } = await refreshing
        storage.setTokens({ access: data.access, refresh: data.refresh })
        original.headers.Authorization = `Bearer ${data.access}`
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

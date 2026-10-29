import api, { storage } from './api'

export async function login(username, password) {
  const { data } = await api.post('/auth/login/', { username, password })
  storage.setTokens(data)
  const user = { id: data.user_id, username: data.username, rol: data.rol }
  localStorage.setItem('user', JSON.stringify(user))
  // nombre real para el saludo (no bloquea el login si falla)
  try {
    const { data: me } = await api.get('/auth/me/')
    user.nombre = me.first_name || me.username
    localStorage.setItem('user', JSON.stringify(user))
  } catch { /* opcional */ }
  return user
}

export function logout() {
  storage.clear()
}

export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('user'))
  } catch {
    return null
  }
}

// RF03 - recuperación y cambio de contraseña
export const passwordService = {
  solicitar: (email) => api.post('/auth/password/reset/', { email }).then((r) => r.data),
  confirmar: (uid, token, new_password) =>
    api.post('/auth/password/reset/confirm/', { uid, token, new_password }).then((r) => r.data),
  cambiar: (old_password, new_password) =>
    api.post('/auth/password/change/', { old_password, new_password }).then((r) => r.data),
}

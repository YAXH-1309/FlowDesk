import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // sends HttpOnly refresh token cookie
})

// Attach JWT from in-memory store
api.interceptors.request.use((config) => {
  const jwt = useAuthStore.getState().jwt
  if (jwt) {
    config.headers.Authorization = `Bearer ${jwt}`
  }
  return config
})

// Auto-refresh on 401
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
      error.config._retry = true
      try {
        const { data } = await axios.post('/api/v1/auth/refresh', {}, { withCredentials: true })
        useAuthStore.getState().setAuth(data.token, useAuthStore.getState().user)
        error.config.headers.Authorization = `Bearer ${data.token}`
        return api(error.config)
      } catch {
        useAuthStore.getState().clearAuth()
      }
    }
    return Promise.reject(error)
  },
)

export default api

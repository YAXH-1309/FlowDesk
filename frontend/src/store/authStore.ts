import { create } from 'zustand'

interface AuthState {
  jwt: string | null
  user: { id: string; email: string; roles: string[] } | null
  setAuth: (jwt: string, user: AuthState['user']) => void
  clearAuth: () => void
}

// JWT stored in memory only (not localStorage) for security
export const useAuthStore = create<AuthState>((set) => ({
  jwt: null,
  user: null,
  setAuth: (jwt, user) => set({ jwt, user }),
  clearAuth: () => set({ jwt: null, user: null }),
}))

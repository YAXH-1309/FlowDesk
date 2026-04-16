import { create } from 'zustand'

interface UiState {
  sidebarOpen: boolean
  activeModule: string
  notifications: { id: string; message: string; type: 'info' | 'error' | 'success' }[]
  toggleSidebar: () => void
  setActiveModule: (module: string) => void
  addNotification: (message: string, type?: 'info' | 'error' | 'success') => void
  removeNotification: (id: string) => void
}

export const useUiStore = create<UiState>((set) => ({
  sidebarOpen: true,
  activeModule: 'tasks',
  notifications: [],
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  setActiveModule: (module) => set({ activeModule: module }),
  addNotification: (message, type = 'info') =>
    set((s) => ({
      notifications: [
        ...s.notifications,
        { id: crypto.randomUUID(), message, type },
      ],
    })),
  removeNotification: (id) =>
    set((s) => ({ notifications: s.notifications.filter((n) => n.id !== id) })),
}))

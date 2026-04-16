import React, { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'

// Route-based code splitting — each chunk < 200KB gzipped
const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const TasksPage = lazy(() => import('./pages/TasksPage'))
const HrPage = lazy(() => import('./pages/HrPage'))
const InventoryPage = lazy(() => import('./pages/InventoryPage'))
const AccountingPage = lazy(() => import('./pages/AccountingPage'))
const SalesPage = lazy(() => import('./pages/SalesPage'))
const ReportingPage = lazy(() => import('./pages/ReportingPage'))

function RequireAuth({ children }: { children: React.ReactNode }) {
  const jwt = useAuthStore((s) => s.jwt)
  return jwt ? <>{children}</> : <Navigate to="/login" replace />
}

function LoadingFallback() {
  return (
    <div role="status" aria-label="Loading" style={{ padding: '2rem', textAlign: 'center' }}>
      Loading...
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<RequireAuth><DashboardPage /></RequireAuth>} />
          <Route path="/tasks/*" element={<RequireAuth><TasksPage /></RequireAuth>} />
          <Route path="/hr/*" element={<RequireAuth><HrPage /></RequireAuth>} />
          <Route path="/inventory/*" element={<RequireAuth><InventoryPage /></RequireAuth>} />
          <Route path="/accounting/*" element={<RequireAuth><AccountingPage /></RequireAuth>} />
          <Route path="/sales/*" element={<RequireAuth><SalesPage /></RequireAuth>} />
          <Route path="/reporting/*" element={<RequireAuth><ReportingPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

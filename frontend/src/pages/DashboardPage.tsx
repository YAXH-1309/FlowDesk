import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '../api/client'
import { useAuthStore } from '../store/authStore'

function useAdminDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'admin'],
    queryFn: () => api.get('/reporting/dashboards/admin').then((r) => r.data),
    refetchInterval: 30_000, // refresh every 30 seconds without full page reload
    enabled: true,
  })
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.roles?.includes('ADMIN')
  const { data, isLoading } = useAdminDashboard()

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Flowdesk Dashboard</h1>
      <nav aria-label="Module navigation">
        <ul style={{ display: 'flex', gap: '1rem', listStyle: 'none', padding: 0 }}>
          <li><Link to="/tasks">Tasks</Link></li>
          <li><Link to="/hr">HR</Link></li>
          <li><Link to="/inventory">Inventory</Link></li>
          <li><Link to="/accounting">Accounting</Link></li>
          <li><Link to="/sales">Sales</Link></li>
          <li><Link to="/reporting">Reporting</Link></li>
        </ul>
      </nav>

      {isAdmin && (
        <section aria-label="Analytics dashboard">
          <h2>Analytics</h2>
          {isLoading ? (
            <p aria-busy="true">Loading metrics...</p>
          ) : (
            <dl>
              <dt>Active Users</dt><dd>{data?.activeUsers ?? '—'}</dd>
              <dt>Open Opportunities</dt><dd>{data?.openOpportunities ?? '—'}</dd>
              <dt>Inventory Alerts</dt><dd>{data?.inventoryAlerts ?? '—'}</dd>
            </dl>
          )}
        </section>
      )}
    </main>
  )
}

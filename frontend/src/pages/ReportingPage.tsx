import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client'

export default function ReportingPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [module, setModule] = useState('tasks')

  const { data: dashboard, isLoading: dashLoading } = useQuery({
    queryKey: ['dashboard', module],
    queryFn: () => api.get(`/reporting/dashboards/${module}`).then((r) => r.data),
    refetchInterval: 30_000,
  })

  const { data: searchResults, refetch: runSearch } = useQuery({
    queryKey: ['search', searchQuery],
    queryFn: () => api.get(`/reporting/search?q=${encodeURIComponent(searchQuery)}`).then((r) => r.data),
    enabled: false,
  })

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Reporting & Analytics</h1>

      <section aria-label="Module dashboard">
        <h2>Dashboard</h2>
        <label htmlFor="module-select">Module</label>
        <select id="module-select" value={module} onChange={(e) => setModule(e.target.value)}>
          {['tasks', 'hr', 'inventory', 'accounting', 'sales'].map((m) => (
            <option key={m} value={m}>{m}</option>
          ))}
        </select>
        {dashLoading ? <p aria-busy="true">Loading...</p> : (
          <pre style={{ background: '#f5f5f5', padding: '1rem', borderRadius: 4 }}>
            {JSON.stringify(dashboard, null, 2)}
          </pre>
        )}
      </section>

      <section aria-label="Full-text search" style={{ marginTop: '2rem' }}>
        <h2>Search</h2>
        <form onSubmit={(e) => { e.preventDefault(); runSearch() }}>
          <label htmlFor="search-input">Search query</label>
          <input id="search-input" type="search" value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ marginRight: '0.5rem' }} />
          <button type="submit">Search</button>
        </form>
        {searchResults && (
          <ul aria-label="Search results">
            {(searchResults as any[]).map((r: any) => (
              <li key={r.id}>{r.entityType}: {r.content}</li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

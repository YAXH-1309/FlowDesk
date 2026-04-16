import React from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client'

type Customer = { id: string; companyName: string; contactEmail: string; creditLimit: number }
type Opportunity = { id: string; stage: string; value: number }

export default function SalesPage() {
  const { data: customers = [] } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: () => api.get('/sales/customers').then((r) => r.data),
  })

  const { data: opportunities = [] } = useQuery<Opportunity[]>({
    queryKey: ['opportunities'],
    queryFn: () => api.get('/sales/opportunities').then((r) => r.data),
  })

  const stages = ['PROSPECT', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'CLOSED_WON', 'CLOSED_LOST']

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Sales & CRM</h1>

      <section aria-label="Customers">
        <h2>Customers</h2>
        <table>
          <caption>Customer List</caption>
          <thead>
            <tr>
              <th scope="col">Company</th>
              <th scope="col">Email</th>
              <th scope="col">Credit Limit</th>
            </tr>
          </thead>
          <tbody>
            {customers.map((c) => (
              <tr key={c.id}>
                <td>{c.companyName}</td>
                <td>{c.contactEmail}</td>
                <td>${c.creditLimit}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section aria-label="Pipeline" style={{ marginTop: '2rem' }}>
        <h2>Opportunity Pipeline</h2>
        <div style={{ display: 'flex', gap: '0.5rem', overflowX: 'auto' }}>
          {stages.map((stage) => (
            <div key={stage} style={{ minWidth: 150, background: '#f5f5f5', padding: '0.5rem', borderRadius: 4 }}>
              <h3 style={{ fontSize: '0.85rem' }}>{stage}</h3>
              {opportunities.filter((o) => o.stage === stage).map((o) => (
                <div key={o.id} style={{ background: '#fff', padding: '0.25rem', marginBottom: '0.25rem', borderRadius: 2 }}>
                  ${o.value?.toLocaleString() ?? 0}
                </div>
              ))}
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}

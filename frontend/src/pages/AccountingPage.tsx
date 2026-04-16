import React from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client'

type TrialBalanceLine = { id: string; code: string; name: string; type: string; balance: number }

export default function AccountingPage() {
  const { data: trialBalance = [], isLoading } = useQuery<TrialBalanceLine[]>({
    queryKey: ['trial-balance'],
    queryFn: () => api.get('/accounting/reports/trial-balance').then((r) => r.data),
  })

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Accounting</h1>
      <h2>Trial Balance</h2>
      {isLoading ? <p aria-busy="true">Loading...</p> : (
        <table>
          <caption>Trial Balance</caption>
          <thead>
            <tr>
              <th scope="col">Code</th>
              <th scope="col">Account</th>
              <th scope="col">Type</th>
              <th scope="col">Balance</th>
            </tr>
          </thead>
          <tbody>
            {trialBalance.map((line) => (
              <tr key={line.id}>
                <td>{line.code}</td>
                <td>{line.name}</td>
                <td>{line.type}</td>
                <td style={{ textAlign: 'right' }}>{line.balance.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  )
}

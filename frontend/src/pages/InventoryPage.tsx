import React from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client'

type Sku = { id: string; productName: string; reorderThreshold: number; unitCost: number }

export default function InventoryPage() {
  const { data: skus = [], isLoading } = useQuery<Sku[]>({
    queryKey: ['skus'],
    queryFn: () => api.get('/inventory/skus').then((r) => r.data),
  })

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Inventory</h1>
      {isLoading ? <p aria-busy="true">Loading SKUs...</p> : (
        <table>
          <caption>SKU List</caption>
          <thead>
            <tr>
              <th scope="col">Product</th>
              <th scope="col">Reorder Threshold</th>
              <th scope="col">Unit Cost</th>
            </tr>
          </thead>
          <tbody>
            {skus.map((s) => (
              <tr key={s.id}>
                <td>{s.productName}</td>
                <td>{s.reorderThreshold}</td>
                <td>${s.unitCost}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  )
}

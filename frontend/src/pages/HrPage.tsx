import React from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/client'

type Employee = { id: string; fullName: string; department: string; jobTitle: string; employmentStatus: string }

export default function HrPage() {
  const { data: employees = [], isLoading } = useQuery<Employee[]>({
    queryKey: ['employees'],
    queryFn: () => api.get('/hr/employees').then((r) => r.data),
  })

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Human Resources</h1>
      {isLoading ? <p aria-busy="true">Loading employees...</p> : (
        <table>
          <caption>Employee List</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Department</th>
              <th scope="col">Title</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {employees.map((e) => (
              <tr key={e.id}>
                <td>{e.fullName}</td>
                <td>{e.department}</td>
                <td>{e.jobTitle}</td>
                <td>{e.employmentStatus}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  )
}

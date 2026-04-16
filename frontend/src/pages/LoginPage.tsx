import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import api from '../api/client'
import { useAuthStore } from '../store/authStore'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)

  const login = useMutation({
    mutationFn: () => api.post('/auth/login', { email, password }),
    onSuccess: (res) => {
      setAuth(res.data.token, { id: '', email, roles: [] })
      navigate('/')
    },
  })

  return (
    <main style={{ maxWidth: 400, margin: '4rem auto', padding: '2rem' }}>
      <h1>Sign in to Flowdesk</h1>
      {login.isError && (
        <p role="alert" style={{ color: 'red' }}>Invalid credentials</p>
      )}
      <form onSubmit={(e) => { e.preventDefault(); login.mutate() }}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
          required autoComplete="email" style={{ display: 'block', width: '100%', marginBottom: '1rem' }} />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          required autoComplete="current-password" style={{ display: 'block', width: '100%', marginBottom: '1rem' }} />
        <button type="submit" disabled={login.isPending} style={{ width: '100%' }}>
          {login.isPending ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      <p><Link to="/register">Create an account</Link></p>
    </main>
  )
}

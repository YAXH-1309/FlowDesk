import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import api from '../api/client'
import { useAuthStore } from '../store/authStore'

export default function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)

  const register = useMutation({
    mutationFn: () => api.post('/auth/register', { email, password }),
    onSuccess: (res) => {
      setAuth(res.data.token, { id: '', email, roles: ['MEMBER'] })
      navigate('/')
    },
  })

  return (
    <main style={{ maxWidth: 400, margin: '4rem auto', padding: '2rem' }}>
      <h1>Create your account</h1>
      {register.isError && (
        <p role="alert" style={{ color: 'red' }}>
          {(register.error as any)?.response?.data?.message || 'Registration failed'}
        </p>
      )}
      <form onSubmit={(e) => { e.preventDefault(); register.mutate() }}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
          required autoComplete="email" style={{ display: 'block', width: '100%', marginBottom: '1rem' }} />
        <label htmlFor="password">Password (min 8 characters)</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          required minLength={8} autoComplete="new-password"
          style={{ display: 'block', width: '100%', marginBottom: '1rem' }} />
        <button type="submit" disabled={register.isPending} style={{ width: '100%' }}>
          {register.isPending ? 'Creating account...' : 'Create account'}
        </button>
      </form>
      <p><Link to="/login">Already have an account?</Link></p>
    </main>
  )
}

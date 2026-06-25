import React, { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../store'

interface LoginLocationState {
  from?: {
    pathname?: string
    search?: string
    hash?: string
  }
}

const Login: React.FC = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const login = useAuthStore((state) => state.login)
  const status = useAuthStore((state) => state.status)
  const location = useLocation()
  const navigate = useNavigate()
  const locationState = location.state as LoginLocationState | null
  const from = locationState?.from
  const redirectTo = `${from?.pathname ?? '/'}${from?.search ?? ''}${from?.hash ?? ''}`

  if (status === 'authenticated') {
    return <Navigate to={redirectTo} replace />
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      await login({ email, password })
      navigate(redirectTo, { replace: true })
    } catch {
      setError('Invalid email or password')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
      <div className="w-full max-w-md p-8 rounded-xl border" style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}>
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>CommentAdmin</h1>
          <p className="mt-2" style={{ color: 'var(--text)' }}>Sign in to the admin panel</p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--text-h)' }}>
              Email
            </label>
            <input
              type="email"
              className="w-full px-4 py-2 rounded-lg border outline-none transition"
              style={{
                backgroundColor: 'var(--code-bg)',
                borderColor: 'var(--border)',
                color: 'var(--text-h)',
              }}
              placeholder="admin@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--text-h)' }}>
              Password
            </label>
            <input
              type="password"
              className="w-full px-4 py-2 rounded-lg border outline-none transition"
              style={{
                backgroundColor: 'var(--code-bg)',
                borderColor: 'var(--border)',
                color: 'var(--text-h)',
              }}
              placeholder="Password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </div>

          {error ? (
            <p className="text-sm text-left text-red-500" role="alert">
              {error}
            </p>
          ) : null}

          <button
            type="submit"
            className="w-full py-2 px-4 rounded-lg font-medium transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            style={{ backgroundColor: 'var(--accent)', color: 'white' }}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}

export default Login

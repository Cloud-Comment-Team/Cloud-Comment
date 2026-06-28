import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, LogIn, Mail } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { useAuthStore } from '../../store'

interface LoginFormState {
  email: string
  password: string
}

const initialFormState: LoginFormState = {
  email: '',
  password: '',
}

function validateLoginForm(form: LoginFormState): string | null {
  if (!form.email.trim()) {
    return 'Введите email'
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    return 'Введите корректный email'
  }

  if (!form.password) {
    return 'Введите пароль'
  }

  if (form.password.length < 8) {
    return 'Пароль должен быть не короче 8 символов'
  }

  return null
}

const Login = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((state) => state.login)
  const status = useAuthStore((state) => state.status)
  const [form, setForm] = useState<LoginFormState>(initialFormState)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const redirectTo =
    typeof location.state === 'object' &&
    location.state !== null &&
    'from' in location.state &&
    typeof location.state.from === 'object' &&
    location.state.from !== null &&
    'pathname' in location.state.from &&
    typeof location.state.from.pathname === 'string'
      ? location.state.from.pathname
      : '/'

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const validationError = validateLoginForm(form)
    if (validationError) {
      setError(validationError)
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      await login({
        email: form.email.trim(),
        password: form.password,
      })
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err, 'Не удалось войти. Проверьте email и пароль.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen px-4 py-10 flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
      <section className="w-full max-w-md">
        <div className="mb-8 text-left">
          <div className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
            <LogIn className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1
            className="mb-2 font-semibold"
            style={{ color: 'var(--text-h)', fontSize: '1.875rem', lineHeight: '2.25rem', letterSpacing: 0, margin: '0 0 0.5rem' }}
          >
            Вход в админку
          </h1>
          <p className="text-base" style={{ color: 'var(--text)' }}>
            Используйте email и пароль администратора Cloud Comment.
          </p>
        </div>

        <form className="space-y-5 text-left" onSubmit={handleSubmit} noValidate>
          {error && (
            <div className="rounded-lg border px-4 py-3 text-sm" style={{ backgroundColor: '#fff1f0', borderColor: '#ffccc7', color: '#a8071a' }} role="alert">
              {error}
            </div>
          )}

          <label className="block">
            <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
              Email
            </span>
            <span className="relative block">
              <Mail className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2" style={{ color: 'var(--text)' }} aria-hidden="true" />
              <input
                autoComplete="email"
                className="w-full rounded-lg border py-3 pl-10 pr-4 outline-none transition focus:ring-2"
                name="email"
                onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                placeholder="admin@example.com"
                style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
                type="email"
                value={form.email}
              />
            </span>
          </label>

          <label className="block">
            <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
              Пароль
            </span>
            <span className="relative block">
              <Lock className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2" style={{ color: 'var(--text)' }} aria-hidden="true" />
              <input
                autoComplete="current-password"
                className="w-full rounded-lg border py-3 pl-10 pr-12 outline-none transition focus:ring-2"
                name="password"
                onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                placeholder="Минимум 8 символов"
                style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
                type={showPassword ? 'text' : 'password'}
                value={form.password}
              />
              <button
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
                className="absolute right-3 top-1/2 -translate-y-1/2 rounded p-1"
                onClick={() => setShowPassword((current) => !current)}
                style={{ color: 'var(--text)' }}
                type="button"
              >
                {showPassword ? <EyeOff className="h-5 w-5" aria-hidden="true" /> : <Eye className="h-5 w-5" aria-hidden="true" />}
              </button>
            </span>
          </label>

          <button
            className="w-full rounded-lg px-4 py-3 font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isSubmitting}
            style={{ backgroundColor: 'var(--accent)' }}
            type="submit"
          >
            {isSubmitting ? 'Входим...' : 'Войти'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm" style={{ color: 'var(--text)' }}>
          Нет аккаунта?{' '}
          <Link className="font-semibold hover:underline" style={{ color: 'var(--accent)' }} to="/register">
            Зарегистрироваться
          </Link>
        </p>
      </section>
    </main>
  )
}

export default Login

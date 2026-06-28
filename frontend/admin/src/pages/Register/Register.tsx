import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, Mail, UserPlus } from 'lucide-react'

import { getApiErrorMessage, register } from '../../api/auth'
import { useAuthStore } from '../../store'

interface RegisterFormState {
  email: string
  password: string
  confirmPassword: string
}

const initialFormState: RegisterFormState = {
  email: '',
  password: '',
  confirmPassword: '',
}

function validateRegisterForm(form: RegisterFormState): string | null {
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

  if (form.password.length > 72) {
    return 'Пароль должен быть не длиннее 72 символов'
  }

  if (form.password !== form.confirmPassword) {
    return 'Пароли не совпадают'
  }

  return null
}

const Register = () => {
  const navigate = useNavigate()
  const status = useAuthStore((state) => state.status)
  const [form, setForm] = useState<RegisterFormState>(initialFormState)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const validationError = validateRegisterForm(form)
    if (validationError) {
      setError(validationError)
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      await register({
        email: form.email.trim(),
        password: form.password,
      })
      navigate('/login', { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err, 'Не удалось зарегистрироваться. Попробуйте еще раз.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen px-4 py-10 flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
      <section className="w-full max-w-md">
        <div className="mb-8 text-left">
          <div className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
            <UserPlus className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1
            className="mb-2 font-semibold"
            style={{ color: 'var(--text-h)', fontSize: '1.875rem', lineHeight: '2.25rem', letterSpacing: 0, margin: '0 0 0.5rem' }}
          >
            Регистрация
          </h1>
          <p className="text-base" style={{ color: 'var(--text)' }}>
            Создайте аккаунт для доступа к панели Cloud Comment.
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
                autoComplete="new-password"
                className="w-full rounded-lg border py-3 pl-10 pr-12 outline-none transition focus:ring-2"
                name="password"
                onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                placeholder="8-72 символа"
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

          <label className="block">
            <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
              Повторите пароль
            </span>
            <span className="relative block">
              <Lock className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2" style={{ color: 'var(--text)' }} aria-hidden="true" />
              <input
                autoComplete="new-password"
                className="w-full rounded-lg border py-3 pl-10 pr-4 outline-none transition focus:ring-2"
                name="confirmPassword"
                onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                placeholder="Повторите пароль"
                style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
                type={showPassword ? 'text' : 'password'}
                value={form.confirmPassword}
              />
            </span>
          </label>

          <button
            className="w-full rounded-lg px-4 py-3 font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isSubmitting}
            style={{ backgroundColor: 'var(--accent)' }}
            type="submit"
          >
            {isSubmitting ? 'Создаем аккаунт...' : 'Зарегистрироваться'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm" style={{ color: 'var(--text)' }}>
          Уже есть аккаунт?{' '}
          <Link className="font-semibold hover:underline" style={{ color: 'var(--accent)' }} to="/login">
            Войти
          </Link>
        </p>
      </section>
    </main>
  )
}

export default Register

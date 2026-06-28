import { useState } from 'react'
import type { SubmitErrorHandler, SubmitHandler } from 'react-hook-form'
import { useForm, useWatch } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, LogIn, Mail } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { getFieldFeedback, validateEmail, validatePassword } from '../../auth/formValidation'
import { AuthFormShell, AuthSubmitButton } from '../../components/auth/AuthFormShell'
import Input from '../../components/common/Input/Input'
import { useAuthStore } from '../../store'

interface LoginFormState {
  email: string
  password: string
}

interface LoginLocationState {
  from?: {
    pathname?: string
    search?: string
    hash?: string
  }
}

const initialFormState: LoginFormState = {
  email: '',
  password: '',
}

function getRedirectTo(state: unknown): string {
  const locationState = state as LoginLocationState | null
  const from = locationState?.from
  const pathname = typeof from?.pathname === 'string' && from.pathname.startsWith('/') ? from.pathname : '/'
  const search = typeof from?.search === 'string' ? from.search : ''
  const hash = typeof from?.hash === 'string' ? from.hash : ''

  return `${pathname}${search}${hash}`
}

const Login = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((state) => state.login)
  const status = useAuthStore((state) => state.status)
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    control,
    handleSubmit,
    setFocus,
    formState: { dirtyFields, errors, isSubmitting },
  } = useForm<LoginFormState>({
    defaultValues: initialFormState,
    mode: 'onChange',
    reValidateMode: 'onChange',
    shouldFocusError: true,
  })

  const email = useWatch({ control, name: 'email' }) ?? ''
  const password = useWatch({ control, name: 'password' }) ?? ''
  const redirectTo = getRedirectTo(location.state)
  const clearServerError = () => setServerError((current) => (current ? null : current))
  const emailField = register('email', { validate: validateEmail, onChange: clearServerError })
  const passwordField = register('password', { validate: validatePassword, onChange: clearServerError })
  const emailFeedback = getFieldFeedback(
    dirtyFields.email,
    email,
    errors.email?.message,
    'Введите email администратора',
    'Email выглядит корректно',
  )
  const passwordFeedback = getFieldFeedback(
    dirtyFields.password,
    password,
    errors.password?.message,
    'Пароль должен быть от 8 до 72 символов',
    'Пароль подходит по длине',
  )

  if (status === 'authenticated') {
    return <Navigate to={redirectTo} replace />
  }

  const handleValidSubmit: SubmitHandler<LoginFormState> = async ({ email: formEmail, password: formPassword }) => {
    setServerError(null)

    try {
      await login({
        email: formEmail.trim(),
        password: formPassword,
      })
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setServerError(getApiErrorMessage(err, 'Не удалось войти. Проверьте email и пароль.'))
    }
  }

  const handleInvalidSubmit: SubmitErrorHandler<LoginFormState> = (fieldErrors) => {
    setServerError(null)

    if (fieldErrors.email) {
      setFocus('email')
      return
    }

    if (fieldErrors.password) {
      setFocus('password')
    }
  }

  return (
    <AuthFormShell
      description="Используйте email и пароль администратора Cloud Comment."
      footer={
        <>
          Нет аккаунта?{' '}
          <Link className="font-semibold hover:underline" style={{ color: 'var(--accent)' }} to="/register">
            Зарегистрироваться
          </Link>
        </>
      }
      icon={<LogIn className="h-6 w-6" aria-hidden="true" />}
      serverError={serverError}
      title="Вход в админку"
    >
      <form className="space-y-5 text-left" onSubmit={handleSubmit(handleValidSubmit, handleInvalidSubmit)} noValidate>
        <Input
          autoComplete="email"
          error={emailFeedback.error}
          hint={emailFeedback.hint}
          icon={<Mail className="h-5 w-5" aria-hidden="true" />}
          label="Email"
          placeholder="admin@example.com"
          success={emailFeedback.success}
          type="email"
          {...emailField}
        />

        <Input
          autoComplete="current-password"
          error={passwordFeedback.error}
          hint={passwordFeedback.hint}
          icon={<Lock className="h-5 w-5" aria-hidden="true" />}
          label="Пароль"
          placeholder="Минимум 8 символов"
          rightAction={
            <button
              aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              className="absolute right-3 top-1/2 -translate-y-1/2 rounded p-1"
              onClick={() => setShowPassword((current) => !current)}
              style={{ color: 'var(--text)' }}
              type="button"
            >
              {showPassword ? <EyeOff className="h-5 w-5" aria-hidden="true" /> : <Eye className="h-5 w-5" aria-hidden="true" />}
            </button>
          }
          success={passwordFeedback.success}
          type={showPassword ? 'text' : 'password'}
          {...passwordField}
        />

        <AuthSubmitButton isSubmitting={isSubmitting} submittingLabel="Входим...">
          Войти
        </AuthSubmitButton>
      </form>
    </AuthFormShell>
  )
}

export default Login

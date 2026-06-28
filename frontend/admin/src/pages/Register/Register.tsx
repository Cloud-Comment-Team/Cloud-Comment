import { useEffect, useState } from 'react'
import type { SubmitErrorHandler, SubmitHandler } from 'react-hook-form'
import { useForm, useWatch } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, Mail, UserPlus } from 'lucide-react'

import { getApiErrorMessage, register as registerUser } from '../../api/auth'
import {
  getFieldFeedback,
  validateEmail,
  validatePassword,
  validatePasswordConfirmation,
} from '../../auth/formValidation'
import { AuthFormShell, AuthSubmitButton } from '../../components/auth/AuthFormShell'
import Input from '../../components/common/Input/Input'
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

const Register = () => {
  const navigate = useNavigate()
  const status = useAuthStore((state) => state.status)
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    control,
    handleSubmit,
    setFocus,
    trigger,
    formState: { dirtyFields, errors, isSubmitting },
  } = useForm<RegisterFormState>({
    defaultValues: initialFormState,
    mode: 'onChange',
    reValidateMode: 'onChange',
    shouldFocusError: true,
  })

  const email = useWatch({ control, name: 'email' }) ?? ''
  const password = useWatch({ control, name: 'password' }) ?? ''
  const confirmPassword = useWatch({ control, name: 'confirmPassword' }) ?? ''
  const clearServerError = () => setServerError((current) => (current ? null : current))
  const emailField = register('email', { validate: validateEmail, onChange: clearServerError })
  const passwordField = register('password', { validate: validatePassword, onChange: clearServerError })
  const confirmPasswordField = register('confirmPassword', {
    validate: (value) => validatePasswordConfirmation(value, password),
    onChange: clearServerError,
  })
  const emailFeedback = getFieldFeedback(
    dirtyFields.email,
    email,
    errors.email?.message,
    'Введите email для аккаунта',
    'Email выглядит корректно',
  )
  const passwordFeedback = getFieldFeedback(
    dirtyFields.password,
    password,
    errors.password?.message,
    'Пароль должен быть от 8 до 72 символов',
    'Пароль подходит по длине',
  )
  const confirmPasswordFeedback = getFieldFeedback(
    dirtyFields.confirmPassword,
    confirmPassword,
    errors.confirmPassword?.message,
    'Повторите пароль',
    'Пароли совпадают',
  )

  useEffect(() => {
    if (dirtyFields.confirmPassword) {
      void trigger('confirmPassword')
    }
  }, [dirtyFields.confirmPassword, password, trigger])

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const handleValidSubmit: SubmitHandler<RegisterFormState> = async ({ email: formEmail, password: formPassword }) => {
    setServerError(null)

    try {
      await registerUser({
        email: formEmail.trim(),
        password: formPassword,
      })
      navigate('/login', { replace: true })
    } catch (err) {
      setServerError(getApiErrorMessage(err, 'Не удалось зарегистрироваться. Попробуйте еще раз.'))
    }
  }

  const handleInvalidSubmit: SubmitErrorHandler<RegisterFormState> = (fieldErrors) => {
    setServerError(null)

    if (fieldErrors.email) {
      setFocus('email')
      return
    }

    if (fieldErrors.password) {
      setFocus('password')
      return
    }

    if (fieldErrors.confirmPassword) {
      setFocus('confirmPassword')
    }
  }

  return (
    <AuthFormShell
      description="Создайте аккаунт для доступа к панели Cloud Comment."
      footer={
        <>
          Уже есть аккаунт?{' '}
          <Link className="font-semibold hover:underline" style={{ color: 'var(--accent)' }} to="/login">
            Войти
          </Link>
        </>
      }
      icon={<UserPlus className="h-6 w-6" aria-hidden="true" />}
      serverError={serverError}
      title="Регистрация"
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
          autoComplete="new-password"
          error={passwordFeedback.error}
          hint={passwordFeedback.hint}
          icon={<Lock className="h-5 w-5" aria-hidden="true" />}
          label="Пароль"
          placeholder="8-72 символа"
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

        <Input
          autoComplete="new-password"
          error={confirmPasswordFeedback.error}
          hint={confirmPasswordFeedback.hint}
          icon={<Lock className="h-5 w-5" aria-hidden="true" />}
          label="Повторите пароль"
          placeholder="Повторите пароль"
          success={confirmPasswordFeedback.success}
          type={showPassword ? 'text' : 'password'}
          {...confirmPasswordField}
        />

        <AuthSubmitButton isSubmitting={isSubmitting} submittingLabel="Создаем аккаунт...">
          Зарегистрироваться
        </AuthSubmitButton>
      </form>
    </AuthFormShell>
  )
}

export default Register

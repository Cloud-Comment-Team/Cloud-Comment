import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AlertCircle, CheckCircle2, Loader2, LogIn, UserPlus } from 'lucide-react'

import { confirmAccountDeletion } from '../../api/account'
import { getApiErrorMessage } from '../../api/auth'
import { removeStoredAuthToken } from '../../auth/tokenStorage'
import { AuthFormShell } from '../../components/auth/AuthFormShell'
import { useAuthStore } from '../../store'

type ConfirmationState = 'loading' | 'success' | 'missing-token' | 'error'

function getConfirmationError(error: unknown): string {
  const message = getApiErrorMessage(error, 'Ссылка подтверждения недействительна или уже устарела.')
  const normalized = message.toLowerCase()

  if (normalized.includes('expired')) {
    return 'Срок действия ссылки истек. Войдите в аккаунт и запросите новое письмо для удаления.'
  }

  if (normalized.includes('already been used')) {
    return 'Эта ссылка уже была использована. Если аккаунт был удален, войти в него больше не получится.'
  }

  return message
}

const AccountDeletionConfirm = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''
  const submittedTokenRef = useRef<string | null>(null)
  const [state, setState] = useState<ConfirmationState>(token ? 'loading' : 'missing-token')
  const [error, setError] = useState<string | null>(null)
  const displayState = token ? state : 'missing-token'

  useEffect(() => {
    if (!token) {
      return
    }

    if (submittedTokenRef.current === token) {
      return
    }

    submittedTokenRef.current = token
    setState('loading')
    setError(null)

    async function confirm() {
      try {
        await confirmAccountDeletion(token)
        removeStoredAuthToken()
        useAuthStore.setState({ token: null, user: null, status: 'unauthenticated' })
        setState('success')
      } catch (confirmationError) {
        setError(getConfirmationError(confirmationError))
        setState('error')
      }
    }

    void confirm()
  }, [token])

  return (
    <AuthFormShell
      description="Проверяем одноразовую ссылку из письма и завершаем удаление аккаунта CloudComment."
      footer={
        <span>
          Нужен новый аккаунт?{' '}
          <Link className="font-semibold hover:underline" style={{ color: 'var(--accent)' }} to="/register">
            Зарегистрироваться
          </Link>
        </span>
      }
      title="Подтверждение удаления"
    >
      <div className="space-y-5 text-left">
        {displayState === 'loading' && (
          <div
            className="flex items-start gap-3 rounded-lg border px-4 py-4"
            style={{ backgroundColor: 'var(--surface-muted)', borderColor: 'var(--border)' }}
          >
            <Loader2 className="mt-0.5 h-5 w-5 shrink-0 animate-spin" style={{ color: 'var(--accent)' }} aria-hidden="true" />
            <div>
              <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
                Подтверждаем удаление
              </p>
              <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                Это займет несколько секунд. После подтверждения активные сессии аккаунта будут завершены.
              </p>
            </div>
          </div>
        )}

        {displayState === 'success' && (
          <>
            <div
              className="flex items-start gap-3 rounded-lg border px-4 py-4"
              style={{ backgroundColor: 'var(--success-bg)', borderColor: 'var(--success-border)' }}
            >
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--success)' }} aria-hidden="true" />
              <div>
                <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
                  Аккаунт удален
                </p>
                <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                  Мы завершили текущие сессии и применили политику удаления аккаунта. Теперь можно закрыть эту вкладку.
                </p>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Link className="cc-button-secondary" to="/login">
                <LogIn className="h-4 w-4" aria-hidden="true" />
                Войти
              </Link>
              <Link className="cc-button-primary" to="/register">
                <UserPlus className="h-4 w-4" aria-hidden="true" />
                Новый аккаунт
              </Link>
            </div>
          </>
        )}

        {displayState === 'missing-token' && (
          <div
            className="flex items-start gap-3 rounded-lg border px-4 py-4"
            style={{ backgroundColor: 'var(--danger-bg)', borderColor: 'var(--danger)' }}
          >
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--danger)' }} aria-hidden="true" />
            <div>
              <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
                В ссылке нет токена
              </p>
              <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                Откройте ссылку из последнего письма или запросите удаление аккаунта повторно в настройках.
              </p>
            </div>
          </div>
        )}

        {displayState === 'error' && (
          <>
            <div
              className="flex items-start gap-3 rounded-lg border px-4 py-4"
              style={{ backgroundColor: 'var(--danger-bg)', borderColor: 'var(--danger)' }}
            >
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--danger)' }} aria-hidden="true" />
              <div>
                <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
                  Не удалось подтвердить удаление
                </p>
                <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                  {error}
                </p>
              </div>
            </div>

            <Link className="cc-button-primary w-full" to="/login">
              <LogIn className="h-4 w-4" aria-hidden="true" />
              Вернуться ко входу
            </Link>
          </>
        )}
      </div>
    </AuthFormShell>
  )
}

export default AccountDeletionConfirm

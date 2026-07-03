import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AlertCircle, CheckCircle2, KeyRound, Loader2, LogIn, UserPlus } from 'lucide-react'

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
  const [manualToken, setManualToken] = useState(token)
  const displayState = state

  const submitToken = useCallback(async (confirmationToken: string) => {
    setState('loading')
    setError(null)

    try {
      await confirmAccountDeletion(confirmationToken)
      removeStoredAuthToken()
      useAuthStore.setState({ token: null, user: null, status: 'unauthenticated' })
      setState('success')
    } catch (confirmationError) {
      setError(getConfirmationError(confirmationError))
      setState('error')
    }
  }, [])

  function handleManualSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const confirmationToken = manualToken.trim()
    if (!confirmationToken) {
      setError('Вставьте код подтверждения из письма.')
      setState('missing-token')
      return
    }

    submittedTokenRef.current = confirmationToken
    setManualToken(confirmationToken)
    void submitToken(confirmationToken)
  }

  useEffect(() => {
    if (!token) {
      return
    }

    if (submittedTokenRef.current === token) {
      return
    }

    submittedTokenRef.current = token
    setManualToken(token)
    void submitToken(token)
  }, [submitToken, token])

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
          <>
            <div
              className="flex items-start gap-3 rounded-lg border px-4 py-4"
              style={{ backgroundColor: 'var(--surface-muted)', borderColor: 'var(--border)' }}
            >
              <KeyRound className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              <div>
                <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
                  Введите код из письма
                </p>
                <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                  Если вы не хотите переходить по ссылке из email или она открылась без токена,
                  вставьте одноразовый код подтверждения вручную.
                </p>
              </div>
            </div>

            {error && (
              <p
                className="rounded-lg border px-4 py-3 text-sm"
                style={{ backgroundColor: 'var(--danger-bg)', borderColor: 'var(--danger)', color: 'var(--danger)' }}
              >
                {error}
              </p>
            )}

            <form className="space-y-3" onSubmit={handleManualSubmit}>
              <label className="block text-sm font-semibold" htmlFor="deletion-token" style={{ color: 'var(--text-h)' }}>
                Код подтверждения
              </label>
              <textarea
                className="cc-field min-h-24 resize-y"
                id="deletion-token"
                name="deletion-token"
                onChange={(event) => setManualToken(event.target.value)}
                placeholder="Вставьте одноразовый код из письма"
                rows={3}
                value={manualToken}
              />
              <button className="cc-button-primary w-full" type="submit">
                Подтвердить удаление
              </button>
            </form>
          </>
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

            <form className="space-y-3" onSubmit={handleManualSubmit}>
              <label className="block text-sm font-semibold" htmlFor="deletion-token-retry" style={{ color: 'var(--text-h)' }}>
                Ввести другой код
              </label>
              <textarea
                className="cc-field min-h-24 resize-y"
                id="deletion-token-retry"
                name="deletion-token-retry"
                onChange={(event) => setManualToken(event.target.value)}
                placeholder="Вставьте новый одноразовый код из письма"
                rows={3}
                value={manualToken}
              />
              <button className="cc-button-primary w-full" type="submit">
                Проверить код
              </button>
            </form>

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

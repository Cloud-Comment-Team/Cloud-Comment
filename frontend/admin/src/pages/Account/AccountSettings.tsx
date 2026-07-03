import { useEffect, useState } from 'react'
import { ExternalLink, Shield, Trash2 } from 'lucide-react'

import {
  createAccountDeletionRequest,
  getCurrentAccountDeletionRequest,
  type AccountDeletionRequest,
} from '../../api/account'
import { getApiErrorMessage } from '../../api/auth'
import { getConsentRequirements, type ConsentRequirements } from '../../api/privacy'
import { AsyncState } from '../../components/common/AsyncState'
import { formatDateTime } from '../../utils/formatDate'

const AccountSettings = () => {
  const [requirements, setRequirements] = useState<ConsentRequirements | null>(null)
  const [deletionRequest, setDeletionRequest] = useState<AccountDeletionRequest | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)

      try {
        const [consentRequirements, currentDeletionRequest] = await Promise.all([
          getConsentRequirements(),
          getCurrentAccountDeletionRequest(),
        ])

        if (!cancelled) {
          setRequirements(consentRequirements)
          setDeletionRequest(currentDeletionRequest)
        }
      } catch (err) {
        if (!cancelled) {
          setError(getApiErrorMessage(err, 'Не удалось загрузить настройки аккаунта.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [])

  async function handleDeletionRequest() {
    setSubmitting(true)
    setError(null)
    setNotice(null)

    try {
      const request = await createAccountDeletionRequest()
      setDeletionRequest(request)
      setNotice('Запрос на удаление создан. Проверьте email и подтвердите удаление по ссылке из письма.')
    } catch (err) {
      setError(getApiErrorMessage(err, 'Не удалось создать запрос на удаление.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1 text-left">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          Аккаунт и конфиденциальность
        </h1>
        <p className="text-sm" style={{ color: 'var(--text)' }}>
          Управление персональными данными, экспортом и удалением аккаунта CloudComment.
        </p>
      </header>

      <AsyncState loading={loading} error={error}>
        {requirements && (
          <>
            {notice && (
              <div
                className="rounded-lg border px-4 py-3 text-sm"
                style={{ borderColor: 'var(--success-border)', backgroundColor: 'var(--success-bg)', color: 'var(--success)' }}
              >
                {notice}
              </div>
            )}

            <section
              className="cc-card space-y-4 p-5 text-left"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <div className="flex items-center gap-3">
                <div className="rounded-lg p-2" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
                  <Shield className="h-5 w-5" aria-hidden="true" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                    Документы и уведомления
                  </h2>
                  <p className="text-sm" style={{ color: 'var(--text)' }}>
                    Актуальные версии: политика {requirements.privacyPolicyVersion}, условия {requirements.termsVersion}.
                  </p>
                </div>
              </div>

              <ul className="space-y-2 text-sm">
                <li>
                  <a
                    className="inline-flex items-center gap-1 font-medium hover:underline"
                    href={requirements.privacyPolicyUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Политика конфиденциальности
                    <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  </a>
                </li>
                <li>
                  <a
                    className="inline-flex items-center gap-1 font-medium hover:underline"
                    href={requirements.termsUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Условия использования
                    <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  </a>
                </li>
                <li>
                  <a
                    className="inline-flex items-center gap-1 font-medium hover:underline"
                    href={requirements.personalDataNoticeUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Уведомление об обработке персональных данных
                    <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  </a>
                </li>
              </ul>
            </section>

            <section
              className="cc-card space-y-4 p-5 text-left"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                Экспорт данных
              </h2>
              <p className="text-sm" style={{ color: 'var(--text)' }}>
                Чтобы запросить копию ваших персональных данных, следуйте инструкции в уведомлении об обработке ПДн.
              </p>
              <a
                className="inline-flex items-center gap-1 text-sm font-medium hover:underline"
                href={requirements.dataExportInfoUrl}
                rel="noreferrer"
                style={{ color: 'var(--accent)' }}
                target="_blank"
              >
                Как запросить экспорт данных
                <ExternalLink className="h-4 w-4" aria-hidden="true" />
              </a>
            </section>

            <section
              className="cc-card space-y-4 p-5 text-left"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <div className="flex items-center gap-3">
                <div className="rounded-lg p-2" style={{ backgroundColor: 'var(--danger-bg)', color: 'var(--danger)' }}>
                  <Trash2 className="h-5 w-5" aria-hidden="true" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                    Удаление аккаунта
                  </h2>
                  <p className="text-sm" style={{ color: 'var(--text)' }}>
                    После подтверждения по email аккаунт будет удалён, а активные сессии отозваны.
                  </p>
                </div>
              </div>

              {deletionRequest ? (
                <div
                  className="rounded-lg border px-4 py-3 text-sm"
                  style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                >
                  <p style={{ color: 'var(--text-h)' }}>
                    Статус заявки: <strong>{deletionRequest.status}</strong>
                  </p>
                  <p style={{ color: 'var(--text)' }}>
                    Создана: {formatDateTime(deletionRequest.createdAt)} · действует до {formatDateTime(deletionRequest.expiresAt)}
                  </p>
                  <p className="mt-2" style={{ color: 'var(--text)' }}>
                    Проверьте email и подтвердите удаление по ссылке из письма.
                  </p>
                </div>
              ) : (
                <p className="text-sm" style={{ color: 'var(--text)' }}>
                  Активной заявки на удаление нет.
                </p>
              )}

              <button
                type="button"
                className="rounded-lg px-4 py-2 text-sm font-semibold transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={submitting}
                onClick={() => void handleDeletionRequest()}
                style={{ backgroundColor: 'var(--danger)', color: '#fff' }}
              >
                {submitting ? 'Отправляем запрос...' : deletionRequest ? 'Отправить письмо повторно' : 'Запросить удаление аккаунта'}
              </button>
            </section>
          </>
        )}
      </AsyncState>
    </div>
  )
}

export default AccountSettings

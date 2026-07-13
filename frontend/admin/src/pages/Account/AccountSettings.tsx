import { useEffect, useState } from 'react'
import { Download, ExternalLink, Shield, Trash2 } from 'lucide-react'

import {
  createAccountDeletionRequest,
  exportPersonalData,
  getCurrentAccountDeletionRequest,
  type AccountDeletionRequest,
} from '../../api/account'
import { getApiErrorMessage } from '../../api/auth'
import { getConsentRequirements, type ConsentRequirements } from '../../api/privacy'
import { AsyncState } from '../../components/common/AsyncState'
import { PageHeader } from '../../components/common/Workspace'
import { formatDateTime } from '../../utils/formatDate'

const AccountSettings = () => {
  const [requirements, setRequirements] = useState<ConsentRequirements | null>(null)
  const [deletionRequest, setDeletionRequest] = useState<AccountDeletionRequest | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [exporting, setExporting] = useState(false)
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

  async function handleDataExport() {
    setExporting(true)
    setError(null)
    setNotice(null)

    try {
      const personalData = await exportPersonalData()
      const blob = new Blob([JSON.stringify(personalData, null, 2)], { type: 'application/json;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `cloudcomment-personal-data-${new Date().toISOString().slice(0, 10)}.json`
      document.body.append(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      setNotice('Архив персональных данных скачан в формате JSON. Храните файл в безопасном месте.')
    } catch (err) {
      setError(getApiErrorMessage(err, 'Не удалось скачать персональные данные.'))
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="cc-page">
      <PageHeader
        eyebrow="Профиль"
        title="Аккаунт и конфиденциальность"
        description="Персональные данные, юридические документы и управление аккаунтом"
      />

      <AsyncState loading={loading} error={error}>
        {requirements && (
          <div className="space-y-5">
            {notice && (
              <div
                className="rounded-lg border px-4 py-3 text-sm"
                style={{ borderColor: 'var(--success-border)', backgroundColor: 'var(--success-bg)', color: 'var(--success)' }}
              >
                {notice}
              </div>
            )}

            <div className="cc-settings-grid">
              <section className="cc-section space-y-5 p-5 text-left md:p-6">
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

                <ul className="divide-y text-sm" style={{ borderColor: 'var(--border)' }}>
                <li>
                  <a
                    className="flex items-center justify-between gap-3 py-3 font-medium hover:underline"
                    href={requirements.privacyPolicyUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Политика конфиденциальности
                    <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
                  </a>
                </li>
                <li>
                  <a
                    className="flex items-center justify-between gap-3 py-3 font-medium hover:underline"
                    href={requirements.termsUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Условия использования
                    <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
                  </a>
                </li>
                <li>
                  <a
                    className="flex items-center justify-between gap-3 py-3 font-medium hover:underline"
                    href={requirements.personalDataNoticeUrl}
                    rel="noreferrer"
                    style={{ color: 'var(--accent)' }}
                    target="_blank"
                  >
                    Уведомление об обработке персональных данных
                    <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
                  </a>
                </li>
                </ul>
              </section>

              <section className="cc-section space-y-4 p-5 text-left md:p-6">
                <div className="flex items-center gap-3">
                  <div className="rounded-lg p-2" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
                    <Download className="h-5 w-5" aria-hidden="true" />
                  </div>
                  <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>Экспорт данных</h2>
                </div>
                <p className="text-sm" style={{ color: 'var(--text)' }}>
                  Скачайте копию профиля, согласий, сессий и связанных ресурсов. Файл содержит персональные данные — не передавайте его третьим лицам.
                </p>
                <button
                  type="button"
                  className="cc-button-primary disabled:cursor-not-allowed"
                  disabled={exporting}
                  onClick={() => void handleDataExport()}
                >
                  <Download className="h-4 w-4" aria-hidden="true" />
                  {exporting ? 'Готовим файл…' : 'Скачать мои данные'}
                </button>
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
            </div>

            <section className="cc-danger-section space-y-4 text-left">
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
                className="cc-button-danger disabled:cursor-not-allowed"
                disabled={submitting}
                onClick={() => void handleDeletionRequest()}
              >
                {submitting ? 'Отправляем запрос...' : deletionRequest ? 'Отправить письмо повторно' : 'Запросить удаление аккаунта'}
              </button>
            </section>
          </div>
        )}
      </AsyncState>
    </div>
  )
}

export default AccountSettings

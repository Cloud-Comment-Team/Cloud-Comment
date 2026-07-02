import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Copy, Globe, Power, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'

import { getApiErrorMessage } from '../../api/auth'
import { deleteSite, getEmbedCode, getSite, replaceAllowedOrigins, updateSite } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import type { EmbedCode, ModerationMode, Site } from '../../types/api'
import { formatOriginsInput, normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'
import { moderationModeLabels } from '../../utils/moderationModeLabels'

const SiteDetail = () => {
  const navigate = useNavigate()
  const { siteId = '' } = useParams()
  const [site, setSite] = useState<Site | null>(null)
  const [embedCode, setEmbedCode] = useState<EmbedCode | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [domain, setDomain] = useState('')
  const [moderationMode, setModerationMode] = useState<ModerationMode>('PRE_MODERATION')
  const [originsInput, setOriginsInput] = useState('')
  const [deleteConfirmationVisible, setDeleteConfirmationVisible] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadSite() {
      setLoading(true)
      setError(null)

      try {
        const [loadedSite, loadedEmbedCode] = await Promise.all([getSite(siteId), getEmbedCode(siteId)])
        if (cancelled) {
          return
        }
        setSite(loadedSite)
        setEmbedCode(loadedEmbedCode)
        setName(loadedSite.name)
        setDomain(loadedSite.domain)
        setModerationMode(loadedSite.moderationMode)
        setOriginsInput(formatOriginsInput(loadedSite.allowedOrigins))
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить сайт.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadSite()

    return () => {
      cancelled = true
    }
  }, [siteId])

  async function handleSaveSettings() {
    if (!site) {
      return
    }

    setSaving(true)
    try {
      const normalizedDomain = normalizeDomainInput(domain)
      if (!normalizedDomain) {
        toast.error('Домен должен быть hostname без схемы, пути и пробелов.')
        return
      }

      const { origins: allowedOrigins, error: originsError } = parseAllowedOriginsInput(originsInput)
      if (originsError) {
        toast.error(originsError)
        return
      }

      await updateSite(site.id, {
        name: name.trim(),
        domain: normalizedDomain,
        moderationMode,
      })
      const siteWithOrigins = await replaceAllowedOrigins(site.id, { allowedOrigins })
      setSite(siteWithOrigins)
      setName(siteWithOrigins.name)
      setDomain(siteWithOrigins.domain)
      setModerationMode(siteWithOrigins.moderationMode)
      setOriginsInput(formatOriginsInput(siteWithOrigins.allowedOrigins))
      toast.success('Настройки сайта сохранены')
    } catch (saveError) {
      toast.error(getApiErrorMessage(saveError, 'Не удалось сохранить настройки.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleToggleActive() {
    if (!site) {
      return
    }

    setSaving(true)
    try {
      const updatedSite = await updateSite(site.id, { isActive: !site.isActive })
      setSite(updatedSite)
      setName(updatedSite.name)
      setDomain(updatedSite.domain)
      setModerationMode(updatedSite.moderationMode)
      setOriginsInput(formatOriginsInput(updatedSite.allowedOrigins))
      toast.success(updatedSite.isActive ? 'Сайт активирован' : 'Сайт деактивирован')
    } catch (toggleError) {
      toast.error(getApiErrorMessage(toggleError, 'Не удалось изменить статус сайта.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteSite() {
    if (!site) {
      return
    }

    if (deleteConfirmation !== site.domain) {
      toast.error('Введите домен сайта для подтверждения удаления.')
      return
    }

    setSaving(true)
    try {
      await deleteSite(site.id)
      toast.success('Сайт удалён')
      navigate('/sites')
    } catch (deleteError) {
      toast.error(getApiErrorMessage(deleteError, 'Не удалось удалить сайт.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleCopyEmbed() {
    if (!embedCode) {
      return
    }

    try {
      await navigator.clipboard.writeText(embedCode.embedCode)
      toast.success('Embed-код скопирован')
    } catch {
      toast.error('Не удалось скопировать embed-код')
    }
  }

  return (
    <div className="cc-page">
      <Link
        to="/sites"
        className="mb-6 inline-flex items-center gap-2 text-sm font-medium hover:underline"
        style={{ color: 'var(--text)' }}
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        К списку сайтов
      </Link>

      <AsyncState loading={loading} error={error} empty={false}>
        {site && (
          <div className="space-y-6">
            <div className="cc-page-heading">
              <div>
                <p className="cc-eyebrow">Настройки проекта</p>
                <h1 className="cc-title flex items-center gap-2">
                  <Globe className="h-6 w-6" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                  {site.name}
                </h1>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="text-sm" style={{ color: 'var(--text)' }}>
                    {site.domain}
                  </span>
                  <Badge tone="accent">{moderationModeLabels[site.moderationMode]}</Badge>
                  <Badge tone={site.isActive ? 'success' : 'danger'}>
                    {site.isActive ? 'Активен' : 'Деактивирован'}
                  </Badge>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => void handleToggleActive()}
                  disabled={saving}
                  className="cc-button-secondary"
                >
                  <Power className="h-4 w-4" aria-hidden="true" />
                  {site.isActive ? 'Деактивировать' : 'Активировать'}
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteConfirmationVisible((current) => !current)}
                  disabled={saving}
                  className="cc-button-danger"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                  Удалить
                </button>
              </div>
            </div>

            <section
              className="cc-card p-5 md:p-6"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <h2 className="mb-4 text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                Настройки сайта
              </h2>
              <div className="grid gap-4 md:grid-cols-2">
                <label className="block">
                  <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                    Название
                  </span>
                  <input
                    className="cc-field"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                    Домен
                  </span>
                  <input
                    className="cc-field"
                    value={domain}
                    onChange={(event) => setDomain(event.target.value)}
                  />
                </label>
                <label className="block md:col-span-2">
                  <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                    Режим модерации
                  </span>
                  <select
                    className="cc-field"
                    value={moderationMode}
                    onChange={(event) => setModerationMode(event.target.value as ModerationMode)}
                  >
                    <option value="PRE_MODERATION">Пре-модерация</option>
                    <option value="POST_MODERATION">Пост-модерация</option>
                    <option value="DISABLED">Отключена</option>
                  </select>
                </label>
                <label className="block md:col-span-2">
                  <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                    Разрешённые origins
                  </span>
                  <textarea
                    className="cc-field min-h-28"
                    value={originsInput}
                    onChange={(event) => setOriginsInput(event.target.value)}
                  />
                </label>
              </div>
              <button
                type="button"
                onClick={() => void handleSaveSettings()}
                disabled={saving}
                className="cc-button-primary mt-4"
              >
                <Check className="h-4 w-4" aria-hidden="true" />
                {saving ? 'Сохраняем...' : 'Сохранить настройки'}
              </button>
            </section>

            <section
              className="cc-card p-5 md:p-6"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                    Embed-код
                  </h2>
                  <p className="text-sm" style={{ color: 'var(--text)' }}>
                    Вставьте этот фрагмент на страницу сайта, чтобы подключить виджет комментариев
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void handleCopyEmbed()}
                  className="cc-button-secondary"
                >
                  <Copy className="h-4 w-4" aria-hidden="true" />
                  Скопировать
                </button>
              </div>
              {embedCode && (
                <>
                  <pre
                    className="overflow-x-auto rounded-lg p-4 text-left text-sm"
                    style={{ backgroundColor: 'var(--code-bg)', color: 'var(--text-h)' }}
                  >
                    {embedCode.embedCode}
                  </pre>
                  <p className="mt-3 text-sm" style={{ color: 'var(--text)' }}>
                    URL скрипта: {embedCode.scriptUrl}
                  </p>
                </>
              )}
            </section>

            {deleteConfirmationVisible && (
              <section
                className="rounded-lg border p-5 shadow-sm md:p-6"
                style={{ backgroundColor: 'var(--danger-bg)', borderColor: 'var(--danger)' }}
              >
                <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                  <div className="max-w-2xl">
                    <h2 className="text-lg font-semibold" style={{ color: 'var(--danger)' }}>
                      Удаление сайта
                    </h2>
                    <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                      Будут удалены сайт, allowed origins, страницы и комментарии. Для подтверждения введите домен{' '}
                      <strong style={{ color: 'var(--text-h)' }}>{site.domain}</strong>.
                    </p>
                    <input
                      className="cc-field mt-4"
                      style={{
                        backgroundColor: 'var(--surface)',
                        borderColor: 'var(--danger)',
                        color: 'var(--text-h)',
                      }}
                      value={deleteConfirmation}
                      onChange={(event) => setDeleteConfirmation(event.target.value)}
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleDeleteSite()}
                    disabled={saving || deleteConfirmation !== site.domain}
                    className="inline-flex items-center justify-center gap-2 rounded-lg px-4 py-3 text-sm font-semibold text-white transition hover:-translate-y-0.5 disabled:opacity-50"
                    style={{ backgroundColor: 'var(--danger)' }}
                  >
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                    Удалить без восстановления
                  </button>
                </div>
              </section>
            )}
          </div>
        )}
      </AsyncState>
    </div>
  )
}

export default SiteDetail

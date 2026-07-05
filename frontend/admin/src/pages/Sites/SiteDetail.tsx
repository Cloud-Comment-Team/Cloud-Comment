import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Copy, Globe, Palette, Power, ShieldCheck, Sparkles, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'

import { getApiErrorMessage } from '../../api/auth'
import { checkAutoModeration, deleteSite, getEmbedCode, getSite, replaceAllowedOrigins, updateSite } from '../../api/sites'
import { OwnerAnalyticsPanel } from '../../components/analytics/OwnerAnalyticsPanel'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import type {
  AutoModerationCheckResponse,
  AutoModerationSettings,
  AutoModerationStrictness,
  CommentStatus,
  EmbedCode,
  ModerationMode,
  Site,
  WidgetCornerRadius,
  WidgetTheme,
} from '../../types/api'
import { formatOriginsInput, normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'
import { moderationModeLabels } from '../../utils/moderationModeLabels'

function parseBlockedWords(value: string): string[] {
  return Array.from(
    new Set(
      value
        .split(/\r?\n|,/)
        .map((word) => word.trim())
        .filter(Boolean),
    ),
  )
}

function formatBlockedWords(words: string[]): string {
  return words.join('\n')
}

const automodStrictnessLabels: Record<AutoModerationStrictness, string> = {
  OFF: 'Выключена',
  RELAXED: 'Мягкая',
  BALANCED: 'Баланс',
  STRICT: 'Строгая',
}

Object.assign(automodStrictnessLabels, {
  OFF: 'Выключена',
  RELAXED: 'Мягкая',
  BALANCED: 'Баланс',
  STRICT: 'Строгая',
})

const automodStrictnessDescriptions: Record<Exclude<AutoModerationStrictness, 'OFF'>, string> = {
  RELAXED: 'Меньше ложных срабатываний: держим только явные спам-сигналы.',
  BALANCED: 'Оптимально для MVP: спам уходит в spam, сомнительное попадает в очередь.',
  STRICT: 'Жестче для публичных сайтов: ссылки, капс и токсичность быстрее уходят на проверку.',
}

const automodStatusLabels: Record<CommentStatus, string> = {
  PENDING: 'На проверке',
  APPROVED: 'Будет опубликован',
  REJECTED: 'Отклонен',
  HIDDEN: 'Скрыт',
  SPAM: 'Спам',
}

const automodStatusTones: Record<CommentStatus, 'success' | 'warning' | 'danger' | 'muted'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  HIDDEN: 'muted',
  SPAM: 'danger',
}

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
  const [widgetTheme, setWidgetTheme] = useState<WidgetTheme>('AUTO')
  const [widgetAccentColor, setWidgetAccentColor] = useState('#0f766e')
  const [widgetCornerRadius, setWidgetCornerRadius] = useState<WidgetCornerRadius>('MEDIUM')
  const [autoModeration, setAutoModeration] = useState<AutoModerationSettings>({
    enabled: true,
    strictness: 'BALANCED',
    blockedWords: [],
    holdLinks: true,
    blockLinks: false,
    maxLinks: 2,
  })
  const [autoModerationBlockedWords, setAutoModerationBlockedWords] = useState('')
  const [autoModerationPreviewContent, setAutoModerationPreviewContent] = useState('')
  const [autoModerationPreview, setAutoModerationPreview] = useState<AutoModerationCheckResponse | null>(null)
  const [autoModerationPreviewError, setAutoModerationPreviewError] = useState<string | null>(null)
  const [autoModerationPreviewLoading, setAutoModerationPreviewLoading] = useState(false)
  const [originsInput, setOriginsInput] = useState('')
  const [deleteConfirmationVisible, setDeleteConfirmationVisible] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const safeWidgetAccentColor = /^#[0-9a-fA-F]{6}$/.test(widgetAccentColor)
    ? widgetAccentColor
    : '#0f766e'
  const updateAutoModeration = (patch: Partial<AutoModerationSettings>) => {
    setAutoModeration((current) => ({ ...current, ...patch }))
  }

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
        setWidgetTheme(loadedSite.widgetStyle.theme)
        setWidgetAccentColor(loadedSite.widgetStyle.accentColor)
        setWidgetCornerRadius(loadedSite.widgetStyle.cornerRadius)
        setAutoModeration(loadedSite.autoModeration)
        setAutoModerationBlockedWords(formatBlockedWords(loadedSite.autoModeration.blockedWords))
        setAutoModerationPreview(null)
        setAutoModerationPreviewError(null)
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

      if (!/^#[0-9a-fA-F]{6}$/.test(widgetAccentColor)) {
        toast.error('Цвет виджета должен быть в формате #RRGGBB')
        return
      }

      await updateSite(site.id, {
        name: name.trim(),
        domain: normalizedDomain,
        moderationMode,
        widgetStyle: {
          theme: widgetTheme,
          accentColor: widgetAccentColor,
          cornerRadius: widgetCornerRadius,
        },
        autoModeration: {
          ...autoModeration,
          strictness: autoModeration.enabled ? autoModeration.strictness : 'OFF',
          blockedWords: parseBlockedWords(autoModerationBlockedWords),
        },
      })
      const siteWithOrigins = await replaceAllowedOrigins(site.id, { allowedOrigins })
      setSite(siteWithOrigins)
      setName(siteWithOrigins.name)
      setDomain(siteWithOrigins.domain)
      setModerationMode(siteWithOrigins.moderationMode)
      setWidgetTheme(siteWithOrigins.widgetStyle.theme)
      setWidgetAccentColor(siteWithOrigins.widgetStyle.accentColor)
      setWidgetCornerRadius(siteWithOrigins.widgetStyle.cornerRadius)
      setAutoModeration(siteWithOrigins.autoModeration)
      setAutoModerationBlockedWords(formatBlockedWords(siteWithOrigins.autoModeration.blockedWords))
      setOriginsInput(formatOriginsInput(siteWithOrigins.allowedOrigins))
      toast.success('Настройки сайта сохранены')
    } catch (saveError) {
      toast.error(getApiErrorMessage(saveError, 'Не удалось сохранить настройки.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleCheckAutoModeration() {
    if (!site) {
      return
    }

    const content = autoModerationPreviewContent.trim()
    if (!content) {
      setAutoModerationPreview(null)
      setAutoModerationPreviewError('Введите пример комментария для проверки.')
      return
    }

    setAutoModerationPreviewLoading(true)
    setAutoModerationPreviewError(null)
    try {
      const result = await checkAutoModeration(site.id, content)
      setAutoModerationPreview(result)
    } catch (previewError) {
      setAutoModerationPreview(null)
      setAutoModerationPreviewError(getApiErrorMessage(previewError, 'Не удалось проверить текст.'))
    } finally {
      setAutoModerationPreviewLoading(false)
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
      setWidgetTheme(updatedSite.widgetStyle.theme)
      setWidgetAccentColor(updatedSite.widgetStyle.accentColor)
      setWidgetCornerRadius(updatedSite.widgetStyle.cornerRadius)
      setAutoModeration(updatedSite.autoModeration)
      setAutoModerationBlockedWords(formatBlockedWords(updatedSite.autoModeration.blockedWords))
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

            <OwnerAnalyticsPanel siteId={site.id} compact />

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
                <div
                  className="hidden rounded-lg border p-4 md:col-span-2"
                  style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <label className="inline-flex items-center gap-2 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                      <input
                        type="checkbox"
                        checked={autoModeration.enabled}
                        onChange={(event) => updateAutoModeration({
                          enabled: event.target.checked,
                          strictness: event.target.checked ? autoModeration.strictness : 'OFF',
                        })}
                      />
                      Автомодерация
                    </label>
                    <span className="rounded-full px-2.5 py-1 text-xs font-bold" style={{ backgroundColor: 'var(--accent-soft)', color: 'var(--accent)' }}>
                      {autoModeration.enabled
                        ? automodStrictnessLabels[autoModeration.strictness]
                        : automodStrictnessLabels.OFF}
                    </span>
                  </div>

                  <div className="mt-4 grid gap-4 md:grid-cols-3">
                    <label className="block">
                      <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                        Строгость
                      </span>
                      <select
                        className="cc-field"
                        value={autoModeration.strictness === 'OFF' ? 'BALANCED' : autoModeration.strictness}
                        disabled={!autoModeration.enabled}
                        onChange={(event) => updateAutoModeration({
                          strictness: event.target.value as AutoModerationStrictness,
                        })}
                      >
                        <option value="RELAXED">Мягкая</option>
                        <option value="BALANCED">Баланс</option>
                        <option value="STRICT">Строгая</option>
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                        Лимит ссылок
                      </span>
                      <input
                        type="number"
                        min={0}
                        max={20}
                        className="cc-field"
                        value={autoModeration.maxLinks}
                        disabled={!autoModeration.enabled}
                        onChange={(event) => updateAutoModeration({
                          maxLinks: Number(event.target.value),
                        })}
                      />
                    </label>
                    <div className="grid content-end gap-2">
                      <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                        <input
                          type="checkbox"
                          checked={autoModeration.holdLinks}
                          disabled={!autoModeration.enabled}
                          onChange={(event) => updateAutoModeration({ holdLinks: event.target.checked })}
                        />
                        Проверять ссылки
                      </label>
                      <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                        <input
                          type="checkbox"
                          checked={autoModeration.blockLinks}
                          disabled={!autoModeration.enabled}
                          onChange={(event) => updateAutoModeration({ blockLinks: event.target.checked })}
                        />
                        Блокировать ссылки
                      </label>
                    </div>
                  </div>

                  <label className="mt-4 block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Стоп-слова
                    </span>
                    <textarea
                      className="cc-field min-h-24"
                      value={autoModerationBlockedWords}
                      disabled={!autoModeration.enabled}
                      onChange={(event) => setAutoModerationBlockedWords(event.target.value)}
                    />
                  </label>
                </div>
                <div
                  className="rounded-lg border p-4 md:col-span-2"
                  style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="flex min-w-0 gap-3">
                      <span
                        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
                        style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
                      >
                        <ShieldCheck className="h-5 w-5" aria-hidden="true" />
                      </span>
                      <div className="min-w-0">
                        <h3 className="font-semibold" style={{ color: 'var(--text-h)' }}>
                          Автомодерация
                        </h3>
                        <p className="mt-1 text-sm leading-6" style={{ color: 'var(--text)' }}>
                          Прозрачные правила без внешнего AI: стоп-слова, ссылки, токсичность, спам и обфускация.
                        </p>
                      </div>
                    </div>
                    <Badge tone={autoModeration.enabled ? 'accent' : 'muted'}>
                      {autoModeration.enabled
                        ? automodStrictnessLabels[autoModeration.strictness]
                        : automodStrictnessLabels.OFF}
                    </Badge>
                  </div>

                  <div className="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(280px,0.9fr)]">
                    <div className="space-y-4">
                      <label className="inline-flex items-center gap-2 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                        <input
                          type="checkbox"
                          checked={autoModeration.enabled}
                          onChange={(event) => updateAutoModeration({
                            enabled: event.target.checked,
                            strictness: event.target.checked ? autoModeration.strictness : 'OFF',
                          })}
                        />
                        Включить автомодерацию
                      </label>

                      <div className="grid gap-4 md:grid-cols-2">
                        <label className="block">
                          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                            Строгость
                          </span>
                          <select
                            className="cc-field"
                            value={autoModeration.strictness === 'OFF' ? 'BALANCED' : autoModeration.strictness}
                            disabled={!autoModeration.enabled}
                            onChange={(event) => updateAutoModeration({
                              strictness: event.target.value as AutoModerationStrictness,
                            })}
                          >
                            <option value="RELAXED">Мягкая</option>
                            <option value="BALANCED">Баланс</option>
                            <option value="STRICT">Строгая</option>
                          </select>
                          {autoModeration.enabled && autoModeration.strictness !== 'OFF' && (
                            <span className="mt-2 block text-xs leading-5" style={{ color: 'var(--text)' }}>
                              {automodStrictnessDescriptions[autoModeration.strictness as Exclude<AutoModerationStrictness, 'OFF'>]}
                            </span>
                          )}
                        </label>

                        <label className="block">
                          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                            Лимит ссылок
                          </span>
                          <input
                            type="number"
                            min={0}
                            max={20}
                            className="cc-field"
                            value={autoModeration.maxLinks}
                            disabled={!autoModeration.enabled}
                            onChange={(event) => updateAutoModeration({
                              maxLinks: Number(event.target.value),
                            })}
                          />
                          <span className="mt-2 block text-xs leading-5" style={{ color: 'var(--text)' }}>
                            Если ссылок больше лимита, комментарий попадет в очередь или spam.
                          </span>
                        </label>
                      </div>

                      <div className="grid gap-2 sm:grid-cols-2">
                        <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                          <input
                            type="checkbox"
                            checked={autoModeration.holdLinks}
                            disabled={!autoModeration.enabled}
                            onChange={(event) => updateAutoModeration({ holdLinks: event.target.checked })}
                          />
                          Отправлять ссылки на проверку
                        </label>
                        <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                          <input
                            type="checkbox"
                            checked={autoModeration.blockLinks}
                            disabled={!autoModeration.enabled}
                            onChange={(event) => updateAutoModeration({ blockLinks: event.target.checked })}
                          />
                          Любая ссылка сразу spam
                        </label>
                      </div>

                      <label className="block">
                        <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                          Стоп-слова владельца
                        </span>
                        <textarea
                          className="cc-field min-h-28"
                          placeholder={'казино, спам\nили по одному слову на строку'}
                          value={autoModerationBlockedWords}
                          disabled={!autoModeration.enabled}
                          onChange={(event) => setAutoModerationBlockedWords(event.target.value)}
                        />
                        <span className="mt-2 block text-xs leading-5" style={{ color: 'var(--text)' }}>
                          Эти слова сильнее дефолтных правил и сразу поднимают score.
                        </span>
                      </label>
                    </div>

                    <div
                      className="rounded-lg border p-4"
                      style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}
                    >
                      <div className="mb-3 flex items-center gap-2">
                        <Sparkles className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                        <h4 className="font-semibold" style={{ color: 'var(--text-h)' }}>
                          Проверить текст
                        </h4>
                      </div>
                      <textarea
                        className="cc-field min-h-28"
                        placeholder="Например: казино ставки, быстрый заработок..."
                        value={autoModerationPreviewContent}
                        onChange={(event) => setAutoModerationPreviewContent(event.target.value)}
                      />
                      <button
                        type="button"
                        onClick={() => void handleCheckAutoModeration()}
                        disabled={autoModerationPreviewLoading}
                        className="cc-button-secondary mt-3"
                      >
                        {autoModerationPreviewLoading ? 'Проверяем...' : 'Проверить'}
                      </button>

                      {autoModerationPreviewError && (
                        <p className="mt-3 text-sm" style={{ color: 'var(--danger)' }}>
                          {autoModerationPreviewError}
                        </p>
                      )}

                      {autoModerationPreview && (
                        <div
                          className="mt-4 rounded-lg border p-3"
                          style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <Badge tone={automodStatusTones[autoModerationPreview.status]}>
                              {automodStatusLabels[autoModerationPreview.status]}
                            </Badge>
                            <span className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                              score {autoModerationPreview.score}
                            </span>
                          </div>
                          {autoModerationPreview.reason && (
                            <p className="mt-3 text-sm leading-6" style={{ color: 'var(--text-h)' }}>
                              {autoModerationPreview.reason}
                            </p>
                          )}
                          {autoModerationPreview.signals.length > 0 ? (
                            <ul className="mt-3 space-y-2">
                              {autoModerationPreview.signals.map((signal, index) => (
                                <li
                                  key={`${signal.category}-${index}`}
                                  className="rounded-md border px-3 py-2 text-sm"
                                  style={{ borderColor: 'var(--border)', color: 'var(--text)' }}
                                >
                                  <span className="font-semibold" style={{ color: 'var(--text-h)' }}>
                                    +{signal.score} {signal.category}
                                  </span>
                                  <span className="block">{signal.reason}</span>
                                </li>
                              ))}
                            </ul>
                          ) : (
                            <p className="mt-3 text-sm" style={{ color: 'var(--text)' }}>
                              Сигналы не найдены: текст считается чистым.
                            </p>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                <div className="grid gap-4 md:col-span-2 md:grid-cols-3">
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Тема виджета
                    </span>
                    <select
                      className="cc-field"
                      value={widgetTheme}
                      onChange={(event) => setWidgetTheme(event.target.value as WidgetTheme)}
                    >
                      <option value="AUTO">Авто</option>
                      <option value="LIGHT">Светлая</option>
                      <option value="DARK">Темная</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Акцент виджета
                    </span>
                    <div className="flex gap-2">
                      <input
                        type="color"
                        className="h-12 w-14 shrink-0 rounded-lg border bg-transparent p-1"
                        style={{ borderColor: 'var(--border)' }}
                        value={safeWidgetAccentColor}
                        onChange={(event) => setWidgetAccentColor(event.target.value)}
                        aria-label="Акцентный цвет виджета"
                      />
                      <input
                        className="cc-field"
                        value={widgetAccentColor}
                        onChange={(event) => setWidgetAccentColor(event.target.value)}
                        aria-label="HEX акцентного цвета виджета"
                      />
                    </div>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Скругления
                    </span>
                    <select
                      className="cc-field"
                      value={widgetCornerRadius}
                      onChange={(event) => setWidgetCornerRadius(event.target.value as WidgetCornerRadius)}
                    >
                      <option value="SMALL">Строже</option>
                      <option value="MEDIUM">Мягко</option>
                      <option value="LARGE">Максимально мягко</option>
                    </select>
                  </label>
                  <div
                    className="flex items-center gap-3 rounded-lg border px-4 py-3 md:col-span-3"
                    style={{
                      borderColor: safeWidgetAccentColor,
                      borderRadius: widgetCornerRadius === 'SMALL' ? 6 : widgetCornerRadius === 'LARGE' ? 20 : 12,
                      backgroundColor: `${safeWidgetAccentColor}18`,
                      color: 'var(--text-h)',
                    }}
                  >
                    <span
                      className="inline-flex h-9 w-9 items-center justify-center rounded-lg"
                      style={{ backgroundColor: `${safeWidgetAccentColor}24`, color: safeWidgetAccentColor }}
                    >
                      <Palette className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <span className="min-w-0 text-sm font-semibold">
                      Виджет будет использовать этот стиль на публичной странице и в демо.
                    </span>
                  </div>
                </div>
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

import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, CircleCheck, CircleDashed, Copy, Globe, Palette, Power, Trash2, TriangleAlert } from 'lucide-react'
import toast from 'react-hot-toast'

import { getApiErrorMessage } from '../../api/auth'
import { deleteSite, getEmbedCode, getSite, replaceAllowedOrigins, updateSite } from '../../api/sites'
import { OwnerAnalyticsPanel } from '../../components/analytics/OwnerAnalyticsPanel'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { Dialog } from '../../components/common/Workspace'
import { useInstallationStatus } from '../../hooks/useInstallationStatus'
import type {
  EmbedCode,
  ModerationMode,
  Site,
  SiteInstallationStatus,
  CommentReactionType,
  WidgetStyle,
} from '../../types/api'
import { formatOriginsInput, normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'
import { moderationModeLabels } from '../../utils/moderationModeLabels'
import { DEFAULT_WIDGET_STYLE, isWidgetAccentAccessible } from '../../utils/widgetStyle'
import { formatDateTime } from '../../utils/formatDate'

const AutoModerationPolicyPanel = lazy(() => import('../../components/automoderation/AutoModerationPolicyPanel'))

const reactionLabels: Record<CommentReactionType, string> = {
  LIKE: 'Нравится',
  LOVE: 'Любовь',
  LAUGH: 'Смешно',
  WOW: 'Удивление',
}

type SiteWorkspaceSection = 'overview' | 'installation' | 'appearance' | 'moderation' | 'origins' | 'danger'

const workspaceSections: Array<{ id: SiteWorkspaceSection; label: string }> = [
  { id: 'overview', label: 'Обзор' },
  { id: 'installation', label: 'Установка' },
  { id: 'appearance', label: 'Оформление' },
  { id: 'moderation', label: 'Модерация' },
  { id: 'origins', label: 'Origins' },
  { id: 'danger', label: 'Опасная зона' },
]

const installationLabels: Record<SiteInstallationStatus['status'], { title: string; description: string; tone: 'success' | 'warning' | 'danger' | 'muted' }> = {
  NEVER_SEEN: { title: 'Виджет ещё не подключался', description: 'Скопируйте код и откройте страницу с разрешённого origin.', tone: 'muted' },
  HEALTHY: { title: 'Установка работает', description: 'Виджет обращался к API в течение последних 24 часов.', tone: 'success' },
  STALE: { title: 'Давно не было запросов', description: 'Последнее успешное подключение было более 24 часов назад.', tone: 'warning' },
  REJECTED: { title: 'Подключение отклонено', description: 'Проверьте активность сайта, site ID и список разрешённых origins.', tone: 'danger' },
}

const installationReasonLabels: Record<SiteInstallationStatus['reason'], { title: string; description: string }> = {
  WIDGET_NOT_SEEN: {
    title: 'Виджет ещё не подключался',
    description: 'Скопируйте код и откройте страницу с разрешённого origin.',
  },
  RECENT_SUCCESS: {
    title: 'Установка работает',
    description: 'Виджет успешно обращался к API в течение последних 24 часов.',
  },
  SUCCESS_STALE: {
    title: 'Давно не было запросов',
    description: 'Последнее успешное подключение было более 24 часов назад. Откройте страницу с виджетом и повторите проверку.',
  },
  ORIGIN_CONFIGURATION_CHANGED: {
    title: 'Настройки origin изменились',
    description: 'Origin последнего успешного подключения больше не разрешён. Проверьте список origins и снова откройте страницу с виджетом.',
  },
  ORIGIN_REJECTED: {
    title: 'Origin страницы отклонён',
    description: 'Добавьте origin страницы целиком в разрешённый список и снова откройте страницу с виджетом.',
  },
  SITE_INACTIVE: {
    title: 'Сайт деактивирован',
    description: 'Активируйте сайт, чтобы публичный виджет снова мог обращаться к API.',
  },
}

const SiteDetail = () => {
  const navigate = useNavigate()
  const { siteId = '' } = useParams()
  const [site, setSite] = useState<Site | null>(null)
  const [embedCode, setEmbedCode] = useState<EmbedCode | null>(null)
  const [activeSection, setActiveSection] = useState<SiteWorkspaceSection>('overview')
  const [embedCopied, setEmbedCopied] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [policyDirty, setPolicyDirty] = useState(false)
  const [pendingSection, setPendingSection] = useState<SiteWorkspaceSection | null>(null)
  const [name, setName] = useState('')
  const [domain, setDomain] = useState('')
  const [moderationMode, setModerationMode] = useState<ModerationMode>('PRE_MODERATION')
  const [widgetStyle, setWidgetStyle] = useState<WidgetStyle>(DEFAULT_WIDGET_STYLE)
  const previewRef = useRef<HTMLIFrameElement>(null)
  const {
    status: installationStatus,
    error: installationStatusError,
    loading: installationStatusLoading,
    refreshing: refreshingStatus,
    polling: installationPolling,
    pollingPaused: installationPollingPaused,
    pollingTimedOut: installationPollingTimedOut,
    refresh: refreshInstallationStatus,
  } = useInstallationStatus(siteId, activeSection === 'installation')
  const [originsInput, setOriginsInput] = useState('')
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const allowNavigationRef = useRef(false)
  const safeWidgetAccentColor = /^#[0-9a-fA-F]{6}$/.test(widgetStyle.accentColor)
    ? widgetStyle.accentColor
    : '#0f766e'
  const widgetColorAccessible = isWidgetAccentAccessible(widgetStyle.accentColor)
  const widgetStyleDirty = site ? JSON.stringify(widgetStyle) !== JSON.stringify(site.widgetStyle) : false
  const settingsDirty = site ? (
    name.trim() !== site.name
    || normalizeDomainInput(domain) !== site.domain
    || moderationMode !== site.moderationMode
    || JSON.stringify(widgetStyle) !== JSON.stringify(site.widgetStyle)
    || originsInput !== formatOriginsInput(site.allowedOrigins)
    || policyDirty
  ) : false
  const hasNewerRejectedAttempt = Boolean(
    installationStatus?.lastRejectedAt
    && (!installationStatus.lastSuccessfulAt
      || Date.parse(installationStatus.lastRejectedAt) > Date.parse(installationStatus.lastSuccessfulAt)),
  )
  const navigationBlocker = useBlocker(() => settingsDirty && !allowNavigationRef.current)
  const updateWidgetStyle = <K extends keyof WidgetStyle>(key: K, value: WidgetStyle[K]) => {
    setWidgetStyle((current) => ({ ...current, [key]: value }))
  }

  useEffect(() => {
    let cancelled = false
    allowNavigationRef.current = false

    async function loadSite() {
      setLoading(true)
      setError(null)

      try {
        const [loadedSite, loadedEmbedCode] = await Promise.all([
          getSite(siteId),
          getEmbedCode(siteId),
        ])
        if (cancelled) {
          return
        }
        setSite(loadedSite)
        setEmbedCode(loadedEmbedCode)
        setEmbedCopied(localStorage.getItem(`cloud-comment:embed-copied:${siteId}`) === 'true')
        setName(loadedSite.name)
        setDomain(loadedSite.domain)
        setModerationMode(loadedSite.moderationMode)
        setWidgetStyle(loadedSite.widgetStyle)
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

  useEffect(() => {
    if (!settingsDirty) return
    const warnAboutUnsavedChanges = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warnAboutUnsavedChanges)
    return () => window.removeEventListener('beforeunload', warnAboutUnsavedChanges)
  }, [settingsDirty])

  useEffect(() => {
    previewRef.current?.contentWindow?.postMessage({ type: 'cloud-comment-preview', style: widgetStyle }, '*')
  }, [widgetStyle])

  async function handleSaveSettings() {
    if (!site || activeSection === 'installation' || activeSection === 'danger') return

    setSaving(true)
    try {
      let updatedSite = site
      if (activeSection === 'overview') {
        const normalizedDomain = normalizeDomainInput(domain)
        if (!normalizedDomain) {
          toast.error('Домен должен быть hostname без схемы, пути и пробелов.')
          return
        }
        updatedSite = await updateSite(site.id, { name: name.trim(), domain: normalizedDomain })
        setName(updatedSite.name)
        setDomain(updatedSite.domain)
      } else if (activeSection === 'moderation') {
        updatedSite = await updateSite(site.id, {
          moderationMode,
        })
        setModerationMode(updatedSite.moderationMode)
      } else if (activeSection === 'appearance') {
        if (!/^#[0-9a-fA-F]{6}$/.test(widgetStyle.accentColor)) {
          toast.error('Цвет виджета должен быть в формате #RRGGBB')
          return
        }
        if (!widgetColorAccessible) {
          toast.error('Акцент должен иметь контраст не ниже 3:1 и на светлой, и на тёмной поверхности.')
          return
        }
        updatedSite = await updateSite(site.id, { widgetStyle })
        setWidgetStyle(updatedSite.widgetStyle)
      } else if (activeSection === 'origins') {
        const { origins: allowedOrigins, error: originsError } = parseAllowedOriginsInput(originsInput)
        if (originsError) {
          toast.error(originsError)
          return
        }
        updatedSite = await replaceAllowedOrigins(site.id, { allowedOrigins })
        setOriginsInput(formatOriginsInput(updatedSite.allowedOrigins))
      }
      setSite(updatedSite)
      toast.success('Раздел сохранён')
      void refreshInstallationStatus(true, true)
    } catch (saveError) {
      toast.error(getApiErrorMessage(saveError, 'Не удалось сохранить раздел.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleToggleActive() {
    if (!site) {
      return
    }

    if (settingsDirty && !window.confirm('Есть несохранённые изменения. Изменить статус и сбросить их?')) return

    setSaving(true)
    try {
      const updatedSite = await updateSite(site.id, { isActive: !site.isActive })
      setSite(updatedSite)
      setName(updatedSite.name)
      setDomain(updatedSite.domain)
      setModerationMode(updatedSite.moderationMode)
      setWidgetStyle(updatedSite.widgetStyle)
      setOriginsInput(formatOriginsInput(updatedSite.allowedOrigins))
      toast.success(updatedSite.isActive ? 'Сайт активирован' : 'Сайт деактивирован')
      void refreshInstallationStatus(true, true)
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
      allowNavigationRef.current = true
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
      localStorage.setItem(`cloud-comment:embed-copied:${siteId}`, 'true')
      setEmbedCopied(true)
      toast.success('Embed-код скопирован')
    } catch {
      toast.error('Не удалось скопировать embed-код')
    }
  }

  async function handleRefreshInstallationStatus() {
    const result = await refreshInstallationStatus(false, true)
    if (!result.ok) {
      toast.error(result.error)
    }
  }

  function handleStayOnPage() {
    if (navigationBlocker.state === 'blocked') {
      navigationBlocker.reset()
    }
  }

  function handleLeavePage() {
    if (navigationBlocker.state === 'blocked') {
      allowNavigationRef.current = true
      navigationBlocker.proceed()
    }
  }

  function handleSectionChange(section: SiteWorkspaceSection) {
    if (activeSection === 'moderation' && section !== 'moderation' && policyDirty) {
      setPendingSection(section)
      return
    }
    setActiveSection(section)
  }

  function handleDiscardPolicyChanges() {
    if (!pendingSection) return
    setPolicyDirty(false)
    setActiveSection(pendingSection)
    setPendingSection(null)
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
                  onClick={() => handleSectionChange('danger')}
                  disabled={saving}
                  className="cc-button-danger"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                  Удалить
                </button>
              </div>
            </div>

            <nav className="cc-card flex gap-2 overflow-x-auto p-2" aria-label="Разделы сайта">
              {workspaceSections.map((section) => (
                <button
                  key={section.id}
                  type="button"
                  className={activeSection === section.id ? 'cc-button-primary shrink-0' : 'cc-button-secondary shrink-0'}
                  aria-current={activeSection === section.id ? 'page' : undefined}
                  onClick={() => handleSectionChange(section.id)}
                >
                  {section.label}
                </button>
              ))}
            </nav>

            {(activeSection === 'overview' || activeSection === 'installation') && installationStatus && (
              <section className="cc-card p-5 md:p-6" aria-labelledby="installation-health-title">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="flex min-w-0 gap-3">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
                      {installationStatus.status === 'HEALTHY' ? <CircleCheck className="h-5 w-5" /> : installationStatus.status === 'NEVER_SEEN' ? <CircleDashed className="h-5 w-5" /> : <TriangleAlert className="h-5 w-5" />}
                    </span>
                    <div>
                      <p className="cc-eyebrow">Диагностика установки</p>
                      <h2 id="installation-health-title" className="mt-1 text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                        {installationReasonLabels[installationStatus.reason].title}
                      </h2>
                      <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                        {installationReasonLabels[installationStatus.reason].description}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={installationLabels[installationStatus.status].tone}>{installationLabels[installationStatus.status].title}</Badge>
                    <button type="button" className="cc-button-secondary" disabled={refreshingStatus} onClick={() => void handleRefreshInstallationStatus()}>
                      {refreshingStatus ? 'Проверяем…' : 'Обновить'}
                    </button>
                  </div>
                </div>

                <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
                  <InstallationChecklistItem done={installationStatus.siteCreated} label="Сайт создан" />
                  <InstallationChecklistItem done={installationStatus.originConfigured} label="Origin настроен" />
                  <InstallationChecklistItem done={embedCopied} label="Код скопирован" />
                  <InstallationChecklistItem done={installationStatus.widgetSeen} label="Виджет увиден" />
                  <InstallationChecklistItem done={installationStatus.firstCommentReceived} label="Первый комментарий" />
                </div>

                {activeSection === 'installation' && installationPolling && (
                  <p className="mt-4 text-sm" role="status" aria-live="polite" style={{ color: 'var(--text)' }}>
                    Ждём реальный запрос виджета и проверяем установку автоматически…
                  </p>
                )}
                {activeSection === 'installation' && installationPollingPaused && (
                  <p className="mt-4 text-sm" role="status" aria-live="polite" style={{ color: 'var(--warning)' }}>
                    Автопроверка приостановлена, пока вкладка неактивна.
                  </p>
                )}
                {activeSection === 'installation' && installationPollingTimedOut && (
                  <p className="mt-4 text-sm" role="status" aria-live="polite" style={{ color: 'var(--warning)' }}>
                    За минуту запрос виджета не появился. Откройте страницу сайта и запустите проверку снова.
                  </p>
                )}

                {installationStatus.lastSuccessfulAt && (
                  <p className="mt-4 text-sm" style={{ color: 'var(--text)' }}>
                    Последний успешный запрос: {formatDateTime(installationStatus.lastSuccessfulAt)} · {installationStatus.lastSuccessfulOrigin}
                  </p>
                )}
                {hasNewerRejectedAttempt && installationStatus.lastRejectedAt && (
                  <p className="mt-2 text-sm" style={{ color: installationStatus.status === 'REJECTED' ? 'var(--danger)' : 'var(--warning)' }}>
                    Последнее отклонение: {formatDateTime(installationStatus.lastRejectedAt)} · {installationStatus.lastRejectedOrigin}
                    {installationStatus.status === 'HEALTHY' && ' Успешный запуск остаётся подтверждённым, но проверьте, не появилась ли новая страница с другим origin.'}
                  </p>
                )}
              </section>
            )}

            {(activeSection === 'overview' || activeSection === 'installation') && !installationStatus && !installationStatusLoading && installationStatusError && (
              <section className="cc-card flex flex-wrap items-center justify-between gap-4 p-5" role="alert" aria-live="polite">
                <div>
                  <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>Диагностика временно недоступна</h2>
                  <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                    Настройки сайта доступны независимо от диагностики. {installationStatusError}
                  </p>
                </div>
                <button type="button" className="cc-button-secondary" disabled={refreshingStatus} onClick={() => void handleRefreshInstallationStatus()}>
                  {refreshingStatus ? 'Проверяем…' : 'Повторить'}
                </button>
              </section>
            )}

            {(activeSection === 'overview' || activeSection === 'installation') && !installationStatus && installationStatusLoading && (
              <section className="cc-card p-5" role="status" aria-live="polite">
                <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>Проверяем установку…</h2>
                <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>Настройки сайта уже можно просматривать и изменять.</p>
              </section>
            )}

            {(['overview', 'appearance', 'moderation', 'origins'] as SiteWorkspaceSection[]).includes(activeSection) && <section
              className="cc-card p-5 md:p-6"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <h2 className="mb-4 text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
                {activeSection === 'overview' && 'Основные сведения'}
                {activeSection === 'appearance' && 'Оформление виджета'}
                {activeSection === 'moderation' && 'Модерация'}
                {activeSection === 'origins' && 'Разрешённые origins'}
              </h2>
              <div className="grid gap-4 md:grid-cols-2">
                {activeSection === 'overview' && <>
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
                </>}
                {activeSection === 'moderation' && <>
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
                <div className="md:col-span-2">
                  <button
                    type="button"
                    onClick={() => void handleSaveSettings()}
                    disabled={saving || moderationMode === site.moderationMode}
                    className="cc-button-primary"
                  >
                    <Check className="h-4 w-4" aria-hidden="true" />
                    {saving ? 'Сохраняем...' : 'Сохранить режим сайта'}
                  </button>
                  {moderationMode !== site.moderationMode && (
                    <p className="mt-2 text-sm" role="status" style={{ color: 'var(--warning)' }}>
                      Политика ниже пока моделируется для сохранённого режима «{moderationModeLabels[site.moderationMode]}».
                      Сохраните новый режим сайта, чтобы применить его к расчётам.
                    </p>
                  )}
                </div>
                <div className="min-w-0 md:col-span-2">
                  <Suspense fallback={<div className="cc-card p-5" role="status">Загружаем политики автомодерации…</div>}>
                    <AutoModerationPolicyPanel siteId={site.id} moderationMode={site.moderationMode} onDirtyChange={setPolicyDirty} />
                  </Suspense>
                </div>
                </>}

                {activeSection === 'appearance' && (
                <div className="grid gap-4 md:col-span-2 md:grid-cols-3">
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Тема виджета
                    </span>
                    <select
                      className="cc-field"
                      value={widgetStyle.theme}
                      onChange={(event) => updateWidgetStyle('theme', event.target.value as WidgetStyle['theme'])}
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
                        onChange={(event) => updateWidgetStyle('accentColor', event.target.value)}
                        aria-label="Акцентный цвет виджета"
                      />
                      <input
                        className="cc-field"
                        value={widgetStyle.accentColor}
                        onChange={(event) => updateWidgetStyle('accentColor', event.target.value)}
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
                      value={widgetStyle.cornerRadius}
                      onChange={(event) => updateWidgetStyle('cornerRadius', event.target.value as WidgetStyle['cornerRadius'])}
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
                      borderRadius: widgetStyle.cornerRadius === 'SMALL' ? 6 : widgetStyle.cornerRadius === 'LARGE' ? 20 : 12,
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

                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Плотность</span>
                    <select className="cc-field" value={widgetStyle.density} onChange={(event) => updateWidgetStyle('density', event.target.value as WidgetStyle['density'])}>
                      <option value="COMFORTABLE">Комфортная</option><option value="COMPACT">Компактная</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Ширина содержимого</span>
                    <select className="cc-field" value={widgetStyle.contentWidth} onChange={(event) => updateWidgetStyle('contentWidth', event.target.value as WidgetStyle['contentWidth'])}>
                      <option value="READABLE">Читаемая</option><option value="WIDE">Широкая</option><option value="FULL">На всю ширину</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Выравнивание</span>
                    <select className="cc-field" value={widgetStyle.alignment} onChange={(event) => updateWidgetStyle('alignment', event.target.value as WidgetStyle['alignment'])}>
                      <option value="CENTER">По центру</option><option value="LEFT">По левому краю</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Размер текста</span>
                    <select className="cc-field" value={widgetStyle.fontScale} onChange={(event) => updateWidgetStyle('fontScale', event.target.value as WidgetStyle['fontScale'])}>
                      <option value="SMALL">Мелкий</option><option value="MEDIUM">Средний</option><option value="LARGE">Крупный</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Шрифт</span>
                    <select className="cc-field" value={widgetStyle.fontFamily} onChange={(event) => updateWidgetStyle('fontFamily', event.target.value as WidgetStyle['fontFamily'])}>
                      <option value="INHERIT">Как на сайте</option><option value="SYSTEM">Системный</option><option value="SERIF">С засечками</option><option value="MONO">Моноширинный</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Глубина</span>
                    <select className="cc-field" value={widgetStyle.elevation} onChange={(event) => updateWidgetStyle('elevation', event.target.value as WidgetStyle['elevation'])}>
                      <option value="BORDER">Рамка</option><option value="SHADOW">Тень</option><option value="NONE">Без обводки</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Аватары</span>
                    <select className="cc-field" value={widgetStyle.avatarStyle} onChange={(event) => updateWidgetStyle('avatarStyle', event.target.value as WidgetStyle['avatarStyle'])}>
                      <option value="INITIALS">Инициалы</option><option value="HIDDEN">Скрыть</option>
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>Язык</span>
                    <select className="cc-field" value={widgetStyle.locale} onChange={(event) => updateWidgetStyle('locale', event.target.value as WidgetStyle['locale'])}>
                      <option value="RU">Русский</option><option value="EN">English</option>
                    </select>
                  </label>

                  <div className="grid gap-3 rounded-lg border p-4 md:col-span-3 md:grid-cols-2" style={{ borderColor: 'var(--border)' }}>
                    <label className="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={widgetStyle.showHeader} onChange={(event) => updateWidgetStyle('showHeader', event.target.checked)} />Показывать заголовок</label>
                    <label className="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={widgetStyle.showSort} onChange={(event) => updateWidgetStyle('showSort', event.target.checked)} />Показывать сортировку</label>
                    <label className="block"><span className="mb-2 block text-sm font-medium">Заголовок</span><input className="cc-field" maxLength={160} value={widgetStyle.headerTitle} onChange={(event) => updateWidgetStyle('headerTitle', event.target.value)} /></label>
                    <label className="block"><span className="mb-2 block text-sm font-medium">Название области комментариев</span><input className="cc-field" maxLength={160} value={widgetStyle.commentsTitle} onChange={(event) => updateWidgetStyle('commentsTitle', event.target.value)} /></label>
                    <label className="block"><span className="mb-2 block text-sm font-medium">Подсказка редактора</span><input className="cc-field" maxLength={160} value={widgetStyle.composerPlaceholder} onChange={(event) => updateWidgetStyle('composerPlaceholder', event.target.value)} /></label>
                    <label className="block"><span className="mb-2 block text-sm font-medium">Положение редактора</span><select className="cc-field" value={widgetStyle.composerPosition} onChange={(event) => updateWidgetStyle('composerPosition', event.target.value as WidgetStyle['composerPosition'])}><option value="BOTTOM">Снизу</option><option value="TOP">Сверху</option></select></label>
                    <label className="block"><span className="mb-2 block text-sm font-medium">Сортировка по умолчанию</span><select className="cc-field" value={widgetStyle.defaultSort} onChange={(event) => updateWidgetStyle('defaultSort', event.target.value as WidgetStyle['defaultSort'])}><option value="PINNED_FIRST">Закреплённые сначала</option><option value="NEWEST">Сначала новые</option><option value="OLDEST">Сначала старые</option><option value="TOP_REACTIONS">По реакциям</option></select></label>
                    <label className="block md:col-span-2"><span className="mb-2 block text-sm font-medium">Пустое состояние</span><input className="cc-field" maxLength={160} value={widgetStyle.emptyMessage} onChange={(event) => updateWidgetStyle('emptyMessage', event.target.value)} /></label>
                    <fieldset className="md:col-span-2"><legend className="mb-2 text-sm font-medium">Доступные реакции</legend><div className="flex flex-wrap gap-4">{(Object.keys(reactionLabels) as CommentReactionType[]).map((reaction) => <label key={reaction} className="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={widgetStyle.enabledReactions.includes(reaction)} onChange={(event) => updateWidgetStyle('enabledReactions', event.target.checked ? [...widgetStyle.enabledReactions, reaction] : widgetStyle.enabledReactions.filter((item) => item !== reaction))} />{reactionLabels[reaction]}</label>)}</div></fieldset>
                  </div>

                  <div className="md:col-span-3">
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
                      <span className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>Живой предварительный просмотр</span>
                      <div className="flex items-center gap-3">
                        {widgetStyleDirty && <Badge tone="warning">Есть несохранённые изменения</Badge>}
                        <button type="button" className="cc-button-secondary" onClick={() => setWidgetStyle(DEFAULT_WIDGET_STYLE)}>Сбросить оформление</button>
                      </div>
                    </div>
                    {!widgetColorAccessible && <p className="mb-3 text-sm" style={{ color: 'var(--danger)' }}>Цвет недостаточно контрастен для светлой или тёмной темы. Сохранение недоступно.</p>}
                    <iframe
                      ref={previewRef}
                      title="Предварительный просмотр виджета"
                      src="/widget-preview.html"
                      sandbox="allow-scripts"
                      className="h-[560px] w-full rounded-lg border bg-white"
                      style={{ borderColor: 'var(--border)' }}
                      onLoad={() => previewRef.current?.contentWindow?.postMessage({ type: 'cloud-comment-preview', style: widgetStyle }, '*')}
                    />
                  </div>
                </div>
                )}
                {activeSection === 'origins' && (
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
                )}
              </div>
              {activeSection !== 'moderation' && <button
                type="button"
                onClick={() => void handleSaveSettings()}
                disabled={saving || (activeSection === 'appearance' && !widgetColorAccessible)}
                className="cc-button-primary mt-4"
              >
                <Check className="h-4 w-4" aria-hidden="true" />
                {saving ? 'Сохраняем...' : 'Сохранить раздел'}
              </button>}
            </section>}

            {activeSection === 'overview' && <OwnerAnalyticsPanel siteId={site.id} variant="site-summary" />}

            {activeSection === 'installation' && <section
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
                    tabIndex={0}
                    aria-label="Embed-код виджета"
                  >
                    {embedCode.embedCode}
                  </pre>
                  <p className="mt-3 text-sm" style={{ color: 'var(--text)' }}>
                    URL скрипта: {embedCode.scriptUrl}
                  </p>
                </>
              )}
              <div className="mt-5 rounded-lg border p-4" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}>
                <h3 className="font-semibold" style={{ color: 'var(--text-h)' }}>Если виджет не появился</h3>
                <ul className="mt-3 list-disc space-y-2 pl-5 text-sm" style={{ color: 'var(--text)' }}>
                  <li>сравните site ID в коде с идентификатором этого сайта;</li>
                  <li>проверьте, что origin страницы целиком добавлен в разделе Origins;</li>
                  <li>убедитесь, что сайт активен и URL API доступен из браузера;</li>
                  <li>после исправления откройте страницу: диагностика обновится автоматически в течение минуты;</li>
                  <li>если время ожидания закончилось, нажмите «Обновить», чтобы проверить ещё раз.</li>
                </ul>
                <p className="mt-3 text-xs" style={{ color: 'var(--text)' }}>
                  CloudComment не загружает вашу страницу с сервера: проверка основана только на реальных запросах виджета.
                </p>
              </div>
            </section>}

            {activeSection === 'danger' && (
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
                      aria-label="Подтверждение домена для удаления"
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
      <Dialog
        title="Несохранённые изменения политики"
        open={pendingSection !== null}
        onClose={() => setPendingSection(null)}
        actions={(
          <>
            <button type="button" className="cc-button-secondary" onClick={() => setPendingSection(null)}>Остаться</button>
            <button type="button" className="cc-button-primary" onClick={handleDiscardPolicyChanges}>Перейти без сохранения</button>
          </>
        )}
      >
        <p className="text-sm" style={{ color: 'var(--text)' }}>
          Несохранённые поля черновика будут потеряны. Уже сохранённый на сервере черновик останется доступен.
        </p>
      </Dialog>
      <Dialog
        title="Несохранённые изменения"
        open={navigationBlocker.state === 'blocked'}
        onClose={handleStayOnPage}
        actions={(
          <>
            <button type="button" className="cc-button-secondary" onClick={handleStayOnPage}>
              Остаться
            </button>
            <button type="button" className="cc-button-primary" onClick={handleLeavePage}>
              Уйти без сохранения
            </button>
          </>
        )}
      >
        <p className="text-sm" style={{ color: 'var(--text)' }}>
          Если покинуть страницу сейчас, изменения в настройках сайта будут потеряны.
        </p>
      </Dialog>
    </div>
  )
}

function InstallationChecklistItem({ done, label }: { done: boolean; label: string }) {
  return (
    <div
      aria-label={`${label}: ${done ? 'готово' : 'не выполнено'}`}
      className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm"
      style={{ borderColor: 'var(--border)', color: done ? 'var(--text-h)' : 'var(--text)' }}
    >
      {done
        ? <CircleCheck className="h-4 w-4 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
        : <CircleDashed className="h-4 w-4 shrink-0" aria-hidden="true" />}
      <span>{label}</span>
    </div>
  )
}

export default SiteDetail

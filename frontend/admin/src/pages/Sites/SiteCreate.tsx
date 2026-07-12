import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { SubmitHandler } from 'react-hook-form'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  CircleDashed,
  Code2,
  Globe,
  Link2,
  Palette,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { createSite } from '../../api/sites'
import Input from '../../components/common/Input/Input'
import { API_BASE_URL } from '../../config/env'
import { useAuthStore } from '../../store'
import type { AutoModerationStrictness, ModerationMode, WidgetCornerRadius, WidgetTheme } from '../../types/api'
import { normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'
import { moderationModeLabels } from '../../utils/moderationModeLabels'
import { DEFAULT_WIDGET_STYLE } from '../../utils/widgetStyle'

interface SiteFormValues {
  name: string
  domain: string
  moderationMode: ModerationMode
  allowedOrigins: string
  widgetTheme: WidgetTheme
  widgetAccentColor: string
  widgetCornerRadius: WidgetCornerRadius
  automodEnabled: boolean
  automodStrictness: AutoModerationStrictness
  automodHoldLinks: boolean
  automodBlockLinks: boolean
  automodMaxLinks: number
  automodBlockedWords: string
}

const WIDGET_SCRIPT_PATH = '/widget/cloud-comment-widget.js'
const DEFAULT_WIDGET_ACCENT = '#0f766e'
const LEGACY_SITE_DRAFT_KEY = 'cloud-comment:site-create-draft:v1'
const SITE_DRAFT_KEY_PREFIX = 'cloud-comment:site-create-draft:v2'
const SITE_DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000

const DEFAULT_SITE_FORM_VALUES: SiteFormValues = {
  name: '',
  domain: '',
  moderationMode: 'PRE_MODERATION',
  allowedOrigins: '',
  widgetTheme: 'AUTO',
  widgetAccentColor: DEFAULT_WIDGET_ACCENT,
  widgetCornerRadius: 'MEDIUM',
  automodEnabled: true,
  automodStrictness: 'BALANCED',
  automodHoldLinks: true,
  automodBlockLinks: false,
  automodMaxLinks: 2,
  automodBlockedWords: '',
}

const stepFields: Array<Array<keyof SiteFormValues>> = [
  ['name', 'domain'],
  ['allowedOrigins', 'moderationMode', 'automodMaxLinks'],
  ['widgetTheme', 'widgetAccentColor', 'widgetCornerRadius'],
  [],
]

const wizardSteps = ['Проект', 'Доступ', 'Оформление', 'Проверка']

function getSiteDraftKey(ownerId: string): string {
  return `${SITE_DRAFT_KEY_PREFIX}:${ownerId}`
}

function removeSiteDraft(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // Локальный черновик не должен блокировать основной сценарий создания сайта.
  }
}

function readSiteDraft(draftKey: string): Partial<SiteFormValues> {
  try {
    const raw = localStorage.getItem(draftKey)
    if (!raw) return {}
    const draft = JSON.parse(raw) as { expiresAt?: number; values?: Partial<SiteFormValues> }
    if (!draft.expiresAt || draft.expiresAt <= Date.now() || !draft.values) {
      removeSiteDraft(draftKey)
      return {}
    }
    const values = draft.values as Record<string, unknown>
    return {
      name: typeof values.name === 'string' ? values.name : DEFAULT_SITE_FORM_VALUES.name,
      domain: typeof values.domain === 'string' ? values.domain : DEFAULT_SITE_FORM_VALUES.domain,
      moderationMode: ['PRE_MODERATION', 'POST_MODERATION', 'DISABLED'].includes(String(values.moderationMode))
        ? values.moderationMode as ModerationMode
        : DEFAULT_SITE_FORM_VALUES.moderationMode,
      allowedOrigins: typeof values.allowedOrigins === 'string' ? values.allowedOrigins : DEFAULT_SITE_FORM_VALUES.allowedOrigins,
      widgetTheme: ['AUTO', 'LIGHT', 'DARK'].includes(String(values.widgetTheme))
        ? values.widgetTheme as WidgetTheme
        : DEFAULT_SITE_FORM_VALUES.widgetTheme,
      widgetAccentColor: typeof values.widgetAccentColor === 'string' ? values.widgetAccentColor : DEFAULT_SITE_FORM_VALUES.widgetAccentColor,
      widgetCornerRadius: ['SMALL', 'MEDIUM', 'LARGE'].includes(String(values.widgetCornerRadius))
        ? values.widgetCornerRadius as WidgetCornerRadius
        : DEFAULT_SITE_FORM_VALUES.widgetCornerRadius,
      automodEnabled: typeof values.automodEnabled === 'boolean' ? values.automodEnabled : DEFAULT_SITE_FORM_VALUES.automodEnabled,
      automodStrictness: ['OFF', 'RELAXED', 'BALANCED', 'STRICT'].includes(String(values.automodStrictness))
        ? values.automodStrictness as AutoModerationStrictness
        : DEFAULT_SITE_FORM_VALUES.automodStrictness,
      automodHoldLinks: typeof values.automodHoldLinks === 'boolean' ? values.automodHoldLinks : DEFAULT_SITE_FORM_VALUES.automodHoldLinks,
      automodBlockLinks: typeof values.automodBlockLinks === 'boolean' ? values.automodBlockLinks : DEFAULT_SITE_FORM_VALUES.automodBlockLinks,
      automodMaxLinks: typeof values.automodMaxLinks === 'number' && Number.isFinite(values.automodMaxLinks)
        ? values.automodMaxLinks
        : DEFAULT_SITE_FORM_VALUES.automodMaxLinks,
      automodBlockedWords: typeof values.automodBlockedWords === 'string' ? values.automodBlockedWords : DEFAULT_SITE_FORM_VALUES.automodBlockedWords,
    }
  } catch {
    removeSiteDraft(draftKey)
    return {}
  }
}

const automodStrictnessLabels: Record<AutoModerationStrictness, string> = {
  OFF: '\u0412\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430',
  RELAXED: '\u041c\u044f\u0433\u043a\u0430\u044f',
  BALANCED: '\u0411\u0430\u043b\u0430\u043d\u0441',
  STRICT: '\u0421\u0442\u0440\u043e\u0433\u0430\u044f',
}

function toAbsolutePreviewUrl(value: string): string {
  if (/^https?:\/\//i.test(value) || typeof window === 'undefined') {
    return value
  }

  return new URL(value, window.location.origin).toString()
}

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

const SiteCreate = () => {
  const ownerId = useAuthStore((state) => state.user?.id ?? null)

  if (!ownerId) {
    return <p role="status" aria-live="polite">Загружаем данные владельца…</p>
  }

  return <SiteCreateForm key={ownerId} ownerId={ownerId} />
}

const SiteCreateForm = ({ ownerId }: { ownerId: string }) => {
  const navigate = useNavigate()
  const draftKey = getSiteDraftKey(ownerId)
  const [initialValues] = useState<SiteFormValues>(() => {
    removeSiteDraft(LEGACY_SITE_DRAFT_KEY)
    return { ...DEFAULT_SITE_FORM_VALUES, ...readSiteDraft(draftKey) }
  })
  const [serverError, setServerError] = useState<string | null>(null)
  const [currentStep, setCurrentStep] = useState(0)
  const previewRef = useRef<HTMLIFrameElement>(null)
  const {
    register,
    handleSubmit,
    control,
    setValue,
    trigger,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<SiteFormValues>({
    defaultValues: initialValues,
  })

  const watchedValues = useWatch({ control })
  const watchedName = watchedValues.name ?? ''
  const watchedDomain = watchedValues.domain ?? ''
  const watchedAllowedOrigins = watchedValues.allowedOrigins ?? ''
  const watchedModerationMode = watchedValues.moderationMode ?? 'PRE_MODERATION'
  const watchedWidgetTheme = watchedValues.widgetTheme ?? 'AUTO'
  const watchedWidgetAccentColor = watchedValues.widgetAccentColor ?? DEFAULT_WIDGET_ACCENT
  const watchedWidgetCornerRadius = watchedValues.widgetCornerRadius ?? 'MEDIUM'
  const watchedAutomodEnabled = watchedValues.automodEnabled ?? true
  const watchedAutomodStrictness = watchedValues.automodStrictness ?? 'BALANCED'
  const watchedAutomodBlockedWords = watchedValues.automodBlockedWords ?? ''
  const previewAccentColor = /^#[0-9a-fA-F]{6}$/.test(watchedWidgetAccentColor)
    ? watchedWidgetAccentColor
    : DEFAULT_WIDGET_ACCENT
  const normalizedDomain = normalizeDomainInput(watchedDomain)
  const parsedOrigins = useMemo(
    () => parseAllowedOriginsInput(watchedAllowedOrigins),
    [watchedAllowedOrigins],
  )
  const normalizedOriginsPreview = parsedOrigins.error ? [] : parsedOrigins.origins
  const hasExplicitOrigins = normalizedOriginsPreview.length > 0
  const previewName = watchedName.trim() || 'Новый сайт'
  const previewDomain = normalizedDomain || 'example.com'
  const previewOrigin = hasExplicitOrigins ? normalizedOriginsPreview[0] : `https://${previewDomain}`
  const previewModerationMode = watchedModerationMode
  const previewWidgetScriptUrl = toAbsolutePreviewUrl(WIDGET_SCRIPT_PATH)
  const previewApiBaseUrl = toAbsolutePreviewUrl(API_BASE_URL)
  const previewEmbedCode = `<script src="${previewWidgetScriptUrl}" data-site-id="<site-id>" data-api-base-url="${previewApiBaseUrl}"></script>`
  const previewBlockedWords = parseBlockedWords(watchedAutomodBlockedWords)
  const previewWidgetStyle = useMemo(() => ({
    ...DEFAULT_WIDGET_STYLE,
    theme: watchedWidgetTheme,
    accentColor: previewAccentColor,
    cornerRadius: watchedWidgetCornerRadius,
  }), [previewAccentColor, watchedWidgetCornerRadius, watchedWidgetTheme])

  useEffect(() => {
    try {
      localStorage.setItem(draftKey, JSON.stringify({
        expiresAt: Date.now() + SITE_DRAFT_TTL_MS,
        values: watchedValues,
      }))
    } catch {
      // Мастер остаётся рабочим, даже если браузер запретил локальное хранилище.
    }
  }, [draftKey, watchedValues])

  useEffect(() => {
    if (!watchedAutomodEnabled) {
      clearErrors('automodMaxLinks')
    }
  }, [clearErrors, watchedAutomodEnabled])

  useEffect(() => {
    previewRef.current?.contentWindow?.postMessage({ type: 'cloud-comment-preview', style: previewWidgetStyle }, '*')
  }, [previewWidgetStyle])

  async function goToNextStep() {
    const valid = await trigger(stepFields[currentStep], { shouldFocus: true })
    if (valid) setCurrentStep((step) => Math.min(step + 1, wizardSteps.length - 1))
  }

  const onSubmit: SubmitHandler<SiteFormValues> = async (values) => {
    setServerError(null)

    const domain = normalizeDomainInput(values.domain)
    if (!domain) {
      setServerError('Домен должен быть hostname без схемы, пути и пробелов.')
      return
    }

    const { origins: allowedOrigins, error: originsError } = parseAllowedOriginsInput(values.allowedOrigins)
    if (originsError) {
      setServerError(originsError)
      return
    }

    try {
      const site = await createSite({
        name: values.name.trim(),
        domain,
        moderationMode: values.moderationMode,
        allowedOrigins,
        widgetStyle: {
          ...DEFAULT_WIDGET_STYLE,
          theme: values.widgetTheme,
          accentColor: values.widgetAccentColor,
          cornerRadius: values.widgetCornerRadius,
        },
        autoModeration: {
          enabled: values.automodEnabled,
          strictness: values.automodEnabled ? values.automodStrictness : 'OFF',
          blockedWords: parseBlockedWords(values.automodBlockedWords),
          holdLinks: values.automodHoldLinks,
          blockLinks: values.automodBlockLinks,
          maxLinks: values.automodEnabled && Number.isInteger(values.automodMaxLinks)
            ? values.automodMaxLinks
            : DEFAULT_SITE_FORM_VALUES.automodMaxLinks,
        },
      })
      removeSiteDraft(draftKey)
      navigate(`/sites/${site.id}`)
    } catch (error) {
      setServerError(getApiErrorMessage(error, 'Не удалось создать сайт.'))
    }
  }

  return (
    <div className="cc-page mx-auto max-w-7xl">
      <Link
        to="/sites"
        className="mb-6 inline-flex items-center gap-2 text-sm font-medium hover:underline"
        style={{ color: 'var(--text)' }}
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        К списку сайтов
      </Link>

      <div className="cc-page-heading">
        <div>
          <p className="cc-eyebrow">Новый проект</p>
          <h1 className="cc-title">Подключение сайта</h1>
          <p className="cc-subtitle">
            Заполните настройки проекта и сразу проверьте, как виджет будет выглядеть на внешней странице.
          </p>
        </div>
      </div>

      <nav className="cc-card mb-6 grid grid-cols-2 gap-2 p-2 sm:grid-cols-4" aria-label="Шаги создания сайта">
        {wizardSteps.map((step, index) => (
          <button
            key={step}
            type="button"
            className={index === currentStep ? 'cc-button-primary' : 'cc-button-secondary'}
            disabled={index > currentStep}
            aria-current={index === currentStep ? 'step' : undefined}
            onClick={() => setCurrentStep(index)}
          >
            {index + 1}. {step}
          </button>
        ))}
      </nav>

      <form
        className="grid min-w-0 items-start gap-6 xl:grid-cols-[minmax(0,1fr)_420px]"
        onSubmit={(event) => {
          if (currentStep < wizardSteps.length - 1) {
            event.preventDefault()
            void goToNextStep()
            return
          }
          void handleSubmit(onSubmit)(event)
        }}
        noValidate
      >
        <div className="min-w-0 space-y-4">
          {serverError && (
            <div
              className="rounded-lg border px-4 py-3 text-sm"
              style={{ borderColor: 'var(--danger)', color: 'var(--danger)', backgroundColor: 'var(--danger-bg)' }}
            >
              {serverError}
            </div>
          )}

          <section className={`${currentStep === 0 ? '' : 'hidden'} cc-card p-5 md:p-6`}>
            <OnboardingStepHeader
              icon={<Sparkles className="h-5 w-5" aria-hidden="true" />}
              index="01"
              title="Проект и домен"
              description="Базовая карточка проекта и hostname, к которому будет привязан виджет."
            />

            <div className="mt-5 grid gap-4 md:grid-cols-2">
              <Input
                label="Название"
                placeholder="Manual MVP Demo"
                icon={<Globe className="h-5 w-5" aria-hidden="true" />}
                error={errors.name?.message}
                success={watchedName.trim() ? 'Покажем в админке и виджете' : undefined}
                {...register('name', {
                  required: 'Укажите название сайта',
                  validate: (value) => value.trim().length > 0 || 'Название не должно быть пустым',
                })}
              />

              <Input
                label="Домен"
                placeholder="example.com"
                icon={<Link2 className="h-5 w-5" aria-hidden="true" />}
                error={errors.domain?.message}
                success={normalizedDomain ? `Нормализовано: ${normalizedDomain}` : undefined}
                {...register('domain', {
                  required: 'Укажите домен сайта',
                  validate: (value) =>
                    normalizeDomainInput(value)
                      ? true
                      : 'Домен должен быть hostname без схемы, пути и пробелов.',
                })}
              />
            </div>
            <div className="mt-5 flex justify-end">
              <button type="button" className="cc-button-primary" onClick={() => void goToNextStep()}>Далее</button>
            </div>
          </section>

          <section className={`${currentStep === 1 ? '' : 'hidden'} cc-card p-5 md:p-6`}>
            <OnboardingStepHeader
              icon={<ShieldCheck className="h-5 w-5" aria-hidden="true" />}
              index="02"
              title="Доступ и модерация"
              description="Origins ограничивают подключение виджета, а режим модерации задает первый статус новых комментариев."
            />

            <div className="mt-5 grid gap-4">
              <div
                className="rounded-lg border p-4"
                style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <label className="inline-flex items-center gap-2 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                    <input type="checkbox" {...register('automodEnabled')} />
                    Автомодерация
                  </label>
                  <span className="rounded-full px-2.5 py-1 text-xs font-bold" style={{ backgroundColor: 'var(--accent-soft)', color: 'var(--accent)' }}>
                    {watchedAutomodEnabled ? automodStrictnessLabels[watchedAutomodStrictness] : automodStrictnessLabels.OFF}
                  </span>
                </div>

                <div className="mt-4 grid gap-4 md:grid-cols-3">
                  <label className="block text-left">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Строгость
                    </span>
                    <select className="cc-field" disabled={!watchedAutomodEnabled} {...register('automodStrictness')}>
                      <option value="RELAXED">Мягкая</option>
                      <option value="BALANCED">Баланс</option>
                      <option value="STRICT">Строгая</option>
                    </select>
                  </label>

                  <label className="block text-left">
                    <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                      Лимит ссылок
                    </span>
                    <input
                      id="automod-max-links"
                      type="number"
                      min={0}
                      max={20}
                      className="cc-field"
                      disabled={!watchedAutomodEnabled}
                      aria-required={watchedAutomodEnabled}
                      aria-invalid={errors.automodMaxLinks ? true : undefined}
                      aria-describedby={errors.automodMaxLinks ? 'automod-max-links-error' : 'automod-max-links-help'}
                      {...register('automodMaxLinks', {
                        valueAsNumber: true,
                        validate: (value) => (
                          !watchedAutomodEnabled
                          || (Number.isInteger(value) && value >= 0 && value <= 20)
                          || 'Введите целое число от 0 до 20.'
                        ),
                      })}
                    />
                    {errors.automodMaxLinks ? (
                      <span id="automod-max-links-error" role="alert" className="mt-2 block text-sm" style={{ color: 'var(--danger)' }}>
                        {errors.automodMaxLinks.message}
                      </span>
                    ) : (
                      <span id="automod-max-links-help" className="mt-2 block text-sm" style={{ color: 'var(--text)' }}>
                        Целое число от 0 до 20.
                      </span>
                    )}
                  </label>

                  <div className="grid content-end gap-2">
                    <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                      <input type="checkbox" disabled={!watchedAutomodEnabled} {...register('automodHoldLinks')} />
                      Проверять ссылки
                    </label>
                    <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text)' }}>
                      <input type="checkbox" disabled={!watchedAutomodEnabled} {...register('automodBlockLinks')} />
                      Блокировать ссылки
                    </label>
                  </div>
                </div>

                <label className="mt-4 block text-left">
                  <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                    Стоп-слова
                  </span>
                  <textarea
                    className="cc-field min-h-24"
                    placeholder={'казино, спам\nили по одному слову на строку'}
                    disabled={!watchedAutomodEnabled}
                    {...register('automodBlockedWords')}
                  />
                  <span className="mt-2 block text-sm" style={{ color: 'var(--text)' }}>
                    {previewBlockedWords.length > 0
                      ? `${previewBlockedWords.length} правил будет применено к новым комментариям`
                      : 'Базовые спам-сигналы уже включены'}
                  </span>
                </label>
              </div>

              <label className="block text-left">
                <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Режим модерации
                </span>
                <select
                  className="cc-field"
                  {...register('moderationMode')}
                >
                  <option value="PRE_MODERATION">Пре-модерация</option>
                  <option value="POST_MODERATION">Пост-модерация</option>
                  <option value="DISABLED">Отключена</option>
                </select>
              </label>

              <label className="block text-left">
                <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Разрешённые origins
                </span>
                <span className="mb-2 block text-sm" style={{ color: 'var(--text)' }}>
                  По одному origin на строку, например https://example.com
                </span>
                <textarea
                  className="cc-field min-h-32"
                  placeholder={`https://${previewDomain}`}
                  aria-invalid={errors.allowedOrigins ? true : undefined}
                  {...register('allowedOrigins', {
                    required: 'Укажите allowed origins',
                    validate: (value) => parseAllowedOriginsInput(value).error ?? true,
                  })}
                />
                <span
                  className="mt-2 block min-h-5 text-sm leading-5"
                  style={{ color: errors.allowedOrigins ? 'var(--danger)' : 'var(--text)' }}
                >
                  {errors.allowedOrigins?.message ?? (
                    normalizedOriginsPreview.length > 0
                      ? `${normalizedOriginsPreview.length} origin будет добавлен в domain policy`
                      : 'В превью показан пример origin, он не будет сохранен без заполнения поля'
                  )}
                </span>
              </label>
            </div>
            <div className="mt-5 flex justify-between gap-3">
              <button type="button" className="cc-button-secondary" onClick={() => setCurrentStep(0)}>Назад</button>
              <button type="button" className="cc-button-primary" onClick={() => void goToNextStep()}>Далее</button>
            </div>
          </section>

          <section className={`${currentStep === 2 ? '' : 'hidden'} cc-card p-5 md:p-6`}>
            <OnboardingStepHeader
              icon={<Palette className="h-5 w-5" aria-hidden="true" />}
              index="03"
              title="Внешний вид"
              description="Настройте тему, акцентный цвет и мягкость карточек виджета."
            />

            <div className="mt-5 grid gap-4 md:grid-cols-3">
              <label className="block text-left">
                <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Тема виджета
                </span>
                <select className="cc-field" {...register('widgetTheme')}>
                  <option value="AUTO">Авто</option>
                  <option value="LIGHT">Светлая</option>
                  <option value="DARK">Темная</option>
                </select>
              </label>

              <label className="block text-left">
                <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Акцент
                </span>
                <div className="flex gap-2">
                  <input
                    type="color"
                    className="h-12 w-14 shrink-0 rounded-lg border bg-transparent p-1"
                    style={{ borderColor: 'var(--border)' }}
                    aria-label="Акцентный цвет"
                    value={previewAccentColor}
                    onChange={(event) => {
                      setValue('widgetAccentColor', event.target.value, {
                        shouldDirty: true,
                        shouldValidate: true,
                      })
                    }}
                  />
                  <input
                    className="cc-field"
                    aria-label="HEX акцентного цвета"
                    {...register('widgetAccentColor', {
                      pattern: {
                        value: /^#[0-9a-fA-F]{6}$/,
                        message: 'Цвет должен быть в формате #RRGGBB',
                      },
                    })}
                  />
                </div>
                {errors.widgetAccentColor && (
                  <span className="mt-2 block text-sm" style={{ color: 'var(--danger)' }}>
                    {errors.widgetAccentColor.message}
                  </span>
                )}
              </label>

              <label className="block text-left">
                <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Скругления
                </span>
                <select className="cc-field" {...register('widgetCornerRadius')}>
                  <option value="SMALL">Строже</option>
                  <option value="MEDIUM">Мягко</option>
                  <option value="LARGE">Максимально мягко</option>
                </select>
              </label>
            </div>
            <div className="mt-5 flex justify-between gap-3">
              <button type="button" className="cc-button-secondary" onClick={() => setCurrentStep(1)}>Назад</button>
              <button type="button" className="cc-button-primary" onClick={() => void goToNextStep()}>Далее</button>
            </div>
          </section>

          <section className={`${currentStep === 3 ? '' : 'hidden'} cc-card p-5 md:p-6`}>
            <OnboardingStepHeader
              icon={<Code2 className="h-5 w-5" aria-hidden="true" />}
              index="04"
              title="Запуск виджета"
              description="После создания админка выдаст готовый embed-код с site id и public API URL."
            />

            <div className="mt-5 grid gap-3 sm:grid-cols-3">
              <ReadinessItem ready={Boolean(watchedName.trim())} label="Название" />
              <ReadinessItem ready={Boolean(normalizedDomain)} label="Домен" />
              <ReadinessItem ready={!parsedOrigins.error} label="Origins" />
            </div>

            <div className="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
              <button type="button" className="cc-button-secondary" onClick={() => setCurrentStep(2)}>Назад</button>
              <button type="submit" disabled={isSubmitting} className="cc-button-primary py-3 sm:min-w-48">
                {isSubmitting ? 'Создаём...' : 'Создать сайт'}
              </button>
            </div>
          </section>
        </div>

        <aside className="min-w-0 space-y-4 xl:sticky xl:top-6">
          <section className="cc-card overflow-hidden">
            <div className="flex items-center justify-between border-b px-5 py-4" style={{ borderColor: 'var(--border)' }}>
              <div>
                <p className="cc-eyebrow mb-1">Превью</p>
                <h2 className="text-lg font-bold" style={{ color: 'var(--text-h)' }}>
                  {previewName}
                </h2>
              </div>
              <span
                className="inline-flex h-10 w-10 items-center justify-center rounded-lg"
                style={{ backgroundColor: `${previewAccentColor}22`, color: previewAccentColor }}
              >
                <Palette className="h-5 w-5" aria-hidden="true" />
              </span>
            </div>

            <div className="space-y-4 p-5">
              <div
                className="rounded-lg border p-4"
                style={{ backgroundColor: 'var(--surface-muted)', borderColor: 'var(--border)' }}
              >
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                      {previewDomain}
                    </p>
                    <p className="mt-1 text-xs" style={{ color: 'var(--text)' }}>
                      {previewOrigin}
                    </p>
                  </div>
                  <span
                    className="rounded-full px-2.5 py-1 text-xs font-bold"
                    style={{ backgroundColor: `${previewAccentColor}22`, color: previewAccentColor }}
                  >
                    {moderationModeLabels[previewModerationMode]}
                  </span>
                </div>
                <div className="grid gap-2 text-sm">
                  {(hasExplicitOrigins ? normalizedOriginsPreview : [previewOrigin]).slice(0, 3).map((origin) => (
                    <span
                      key={origin}
                      className="flex min-w-0 items-center gap-2 rounded-md border px-3 py-2"
                      style={{
                        borderColor: 'var(--border)',
                        color: hasExplicitOrigins ? 'var(--text-h)' : 'var(--text)',
                        backgroundColor: hasExplicitOrigins ? 'var(--surface)' : 'var(--code-bg)',
                      }}
                    >
                      <span className="truncate">{origin}</span>
                      {!hasExplicitOrigins && (
                        <span className="ml-auto shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold" style={{ backgroundColor: 'var(--surface-muted)', color: 'var(--text)' }}>
                          пример
                        </span>
                      )}
                    </span>
                  ))}
                </div>
              </div>

              <iframe
                ref={previewRef}
                title="Предварительный просмотр создаваемого виджета"
                src="/widget-preview.html"
                sandbox="allow-scripts"
                className="h-[520px] w-full rounded-lg border bg-white"
                style={{ borderColor: 'var(--border)' }}
                onLoad={() => previewRef.current?.contentWindow?.postMessage({ type: 'cloud-comment-preview', style: previewWidgetStyle }, '*')}
              />
            </div>
          </section>

          <section className="cc-card p-5">
            <div className="mb-3 flex items-center gap-2">
              <Code2 className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>
                Черновик embed-кода
              </h2>
            </div>
            <p className="mb-3 text-sm leading-6" style={{ color: 'var(--text)' }}>
              Финальный код с настоящим site id появится на странице сайта после создания.
            </p>
            <pre
              className="max-w-full overflow-x-auto rounded-lg border p-3 text-xs leading-5"
              style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            >
              <code>{previewEmbedCode}</code>
            </pre>
          </section>
        </aside>
      </form>
    </div>
  )
}

function OnboardingStepHeader({
  icon,
  index,
  title,
  description,
}: {
  icon: ReactNode
  index: string
  title: string
  description: string
}) {
  return (
    <div className="flex gap-4">
      <span
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
      >
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-xs font-bold uppercase" style={{ color: 'var(--accent)' }}>
          Шаг {index}
        </p>
        <h2 className="mt-1 text-lg font-bold" style={{ color: 'var(--text-h)' }}>
          {title}
        </h2>
        <p className="mt-1 text-sm leading-6" style={{ color: 'var(--text)' }}>
          {description}
        </p>
      </div>
    </div>
  )
}

function ReadinessItem({ ready, label }: { ready: boolean; label: string }) {
  const Icon = ready ? CheckCircle2 : CircleDashed
  const statusLabel = ready ? 'Готово' : 'Нужно заполнить'

  return (
    <div
      aria-label={`${label}: ${statusLabel}`}
      className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold"
      style={{
        backgroundColor: ready ? 'var(--success-bg)' : 'var(--surface-muted)',
        borderColor: ready ? 'var(--success-border)' : 'var(--border)',
        color: ready ? 'var(--success)' : 'var(--text)',
      }}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{label}</span>
      <span className="ml-auto text-xs">{statusLabel}</span>
    </div>
  )
}

export default SiteCreate

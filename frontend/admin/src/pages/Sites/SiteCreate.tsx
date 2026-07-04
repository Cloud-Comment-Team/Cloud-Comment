import { useMemo, useState, type ReactNode } from 'react'
import type { SubmitHandler } from 'react-hook-form'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  Code2,
  Eye,
  Globe,
  Link2,
  MessageSquareText,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { createSite } from '../../api/sites'
import Input from '../../components/common/Input/Input'
import type { ModerationMode } from '../../types/api'
import { normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'
import { moderationModeLabels } from '../../utils/moderationModeLabels'

interface SiteFormValues {
  name: string
  domain: string
  moderationMode: ModerationMode
  allowedOrigins: string
}

const SiteCreate = () => {
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SiteFormValues>({
    defaultValues: {
      name: '',
      domain: '',
      moderationMode: 'PRE_MODERATION',
      allowedOrigins: '',
    },
  })

  const watchedValues = useWatch({ control })
  const watchedName = watchedValues.name ?? ''
  const watchedDomain = watchedValues.domain ?? ''
  const watchedAllowedOrigins = watchedValues.allowedOrigins ?? ''
  const watchedModerationMode = watchedValues.moderationMode ?? 'PRE_MODERATION'
  const normalizedDomain = normalizeDomainInput(watchedDomain)
  const parsedOrigins = useMemo(
    () => parseAllowedOriginsInput(watchedAllowedOrigins),
    [watchedAllowedOrigins],
  )
  const normalizedOriginsPreview = parsedOrigins.error ? [] : parsedOrigins.origins
  const previewName = watchedName.trim() || 'Новый сайт'
  const previewDomain = normalizedDomain || 'example.com'
  const previewOrigin = normalizedOriginsPreview[0] || `https://${previewDomain}`
  const previewModerationMode = watchedModerationMode
  const previewEmbedCode = `<script src="/widget/cloud-comment-widget.js" data-site-id="<site-id>" data-api-base-url="/api"></script>`

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
      })
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

      <form
        className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_420px]"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
      >
        <div className="space-y-4">
          {serverError && (
            <div
              className="rounded-lg border px-4 py-3 text-sm"
              style={{ borderColor: 'var(--danger)', color: 'var(--danger)', backgroundColor: 'var(--danger-bg)' }}
            >
              {serverError}
            </div>
          )}

          <section className="cc-card p-5 md:p-6">
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
                success={watchedName.trim() ? 'Название попадет в админку и превью' : undefined}
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
                success={normalizedDomain ? `Нормализуем как ${normalizedDomain}` : undefined}
                {...register('domain', {
                  required: 'Укажите домен сайта',
                  validate: (value) =>
                    normalizeDomainInput(value)
                      ? true
                      : 'Домен должен быть hostname без схемы, пути и пробелов.',
                })}
              />
            </div>
          </section>

          <section className="cc-card p-5 md:p-6">
            <OnboardingStepHeader
              icon={<ShieldCheck className="h-5 w-5" aria-hidden="true" />}
              index="02"
              title="Доступ и модерация"
              description="Origins ограничивают подключение виджета, а режим модерации задает первый статус новых комментариев."
            />

            <div className="mt-5 grid gap-4">
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
                      : 'Превью возьмет origin из домена до заполнения'
                  )}
                </span>
              </label>
            </div>
          </section>

          <section className="cc-card p-5 md:p-6">
            <OnboardingStepHeader
              icon={<Code2 className="h-5 w-5" aria-hidden="true" />}
              index="03"
              title="Запуск виджета"
              description="После создания админка выдаст готовый embed-код с site id и public API URL."
            />

            <div className="mt-5 grid gap-3 sm:grid-cols-3">
              <ReadinessItem ready={Boolean(watchedName.trim())} label="Название" />
              <ReadinessItem ready={Boolean(normalizedDomain)} label="Домен" />
              <ReadinessItem ready={!parsedOrigins.error} label="Origins" />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="cc-button-primary mt-5 w-full py-3"
            >
              {isSubmitting ? 'Создаём...' : 'Создать сайт'}
            </button>
          </section>
        </div>

        <aside className="space-y-4 xl:sticky xl:top-6">
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
                style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
              >
                <Eye className="h-5 w-5" aria-hidden="true" />
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
                    style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
                  >
                    {moderationModeLabels[previewModerationMode]}
                  </span>
                </div>
                <div className="grid gap-2 text-sm">
                  {(normalizedOriginsPreview.length > 0 ? normalizedOriginsPreview : [previewOrigin]).slice(0, 3).map((origin) => (
                    <span
                      key={origin}
                      className="truncate rounded-md border px-3 py-2"
                      style={{ borderColor: 'var(--border)', color: 'var(--text-h)', backgroundColor: 'var(--surface)' }}
                    >
                      {origin}
                    </span>
                  ))}
                </div>
              </div>

              <div
                className="rounded-lg border"
                style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
              >
                <div className="flex items-center justify-between border-b px-4 py-3" style={{ borderColor: 'var(--border)' }}>
                  <div>
                    <p className="text-xs font-bold uppercase" style={{ color: 'var(--accent)' }}>
                      Обсуждение
                    </p>
                    <p className="mt-1 font-bold" style={{ color: 'var(--text-h)' }}>
                      Комментарии
                    </p>
                  </div>
                  <span
                    className="rounded-full px-2.5 py-1 text-xs font-bold"
                    style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
                  >
                    CloudComment
                  </span>
                </div>
                <div className="space-y-3 p-4">
                  <PreviewComment author="AL" title="Алиса" text="Классный материал! Оставлю вопрос по теме." />
                  <PreviewComment author="OW" title="Владелец" text="Отвечу после модерации и проверки." muted />
                  <div
                    className="rounded-lg border px-3 py-3 text-sm"
                    style={{ borderColor: 'var(--border)', color: 'var(--text)', backgroundColor: 'var(--code-bg)' }}
                  >
                    Напишите комментарий
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section className="cc-card p-5">
            <div className="mb-3 flex items-center gap-2">
              <Code2 className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>
                Embed-код
              </h2>
            </div>
            <pre
              className="overflow-x-auto rounded-lg border p-3 text-xs leading-5"
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
  return (
    <div
      className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold"
      style={{
        backgroundColor: ready ? 'var(--success-bg)' : 'var(--surface-muted)',
        borderColor: ready ? 'var(--success-border)' : 'var(--border)',
        color: ready ? 'var(--success)' : 'var(--text)',
      }}
    >
      <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

function PreviewComment({
  author,
  title,
  text,
  muted = false,
}: {
  author: string
  title: string
  text: string
  muted?: boolean
}) {
  return (
    <article
      className="rounded-lg border p-3"
      style={{
        backgroundColor: muted ? 'var(--surface-muted)' : 'var(--surface)',
        borderColor: 'var(--border)',
      }}
    >
      <div className="mb-2 flex items-center gap-2">
        <span
          className="inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold"
          style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
        >
          {author}
        </span>
        <span className="font-semibold" style={{ color: 'var(--text-h)' }}>
          {title}
        </span>
        <MessageSquareText className="ml-auto h-4 w-4" style={{ color: 'var(--text)' }} aria-hidden="true" />
      </div>
      <p className="text-sm leading-6" style={{ color: 'var(--text-h)' }}>
        {text}
      </p>
    </article>
  )
}

export default SiteCreate

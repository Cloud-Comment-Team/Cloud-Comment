import { useState } from 'react'
import type { SubmitHandler } from 'react-hook-form'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, Globe } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { createSite } from '../../api/sites'
import Input from '../../components/common/Input/Input'
import type { ModerationMode } from '../../types/api'
import { parseOriginsInput } from '../../utils/origins'

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
    formState: { errors, isSubmitting },
  } = useForm<SiteFormValues>({
    defaultValues: {
      name: '',
      domain: '',
      moderationMode: 'PRE_MODERATION',
      allowedOrigins: 'https://example.com',
    },
  })

  const onSubmit: SubmitHandler<SiteFormValues> = async (values) => {
    setServerError(null)

    const allowedOrigins = parseOriginsInput(values.allowedOrigins)
    if (allowedOrigins.length === 0) {
      setServerError('Укажите хотя бы один allowed origin.')
      return
    }

    try {
      const site = await createSite({
        name: values.name.trim(),
        domain: values.domain.trim(),
        moderationMode: values.moderationMode,
        allowedOrigins,
      })
      navigate(`/sites/${site.id}`)
    } catch (error) {
      setServerError(getApiErrorMessage(error, 'Не удалось создать сайт.'))
    }
  }

  return (
    <div className="mx-auto max-w-2xl text-left">
      <Link
        to="/sites"
        className="mb-6 inline-flex items-center gap-2 text-sm hover:underline"
        style={{ color: 'var(--text)' }}
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        К списку сайтов
      </Link>

      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          Новый сайт
        </h1>
        <p className="mt-1" style={{ color: 'var(--text)' }}>
          Создайте проект и укажите домен с разрешёнными origin для виджета
        </p>
      </div>

      {serverError && (
        <div
          className="mb-6 rounded-lg border px-4 py-3 text-sm"
          style={{ borderColor: '#ff7875', color: '#a8071a', backgroundColor: 'rgba(255, 120, 117, 0.08)' }}
        >
          {serverError}
        </div>
      )}

      <form className="space-y-5 rounded-xl border p-6" style={{ borderColor: 'var(--border)' }} onSubmit={handleSubmit(onSubmit)} noValidate>
        <Input
          label="Название"
          placeholder="Example site"
          icon={<Globe className="h-5 w-5" aria-hidden="true" />}
          error={errors.name?.message}
          {...register('name', { required: 'Укажите название сайта' })}
        />

        <Input
          label="Домен"
          placeholder="example.com"
          error={errors.domain?.message}
          {...register('domain', { required: 'Укажите домен сайта' })}
        />

        <label className="block text-left">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Режим модерации
          </span>
          <select
            className="w-full rounded-lg border px-4 py-3 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            {...register('moderationMode')}
          >
            <option value="PRE_MODERATION">Пре-модерация</option>
            <option value="POST_MODERATION">Пост-модерация</option>
            <option value="DISABLED">Отключена</option>
          </select>
        </label>

        <label className="block text-left">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Allowed origins
          </span>
          <span className="mb-2 block text-sm" style={{ color: 'var(--text)' }}>
            По одному origin на строку, например https://example.com
          </span>
          <textarea
            className="min-h-32 w-full rounded-lg border px-4 py-3 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            {...register('allowedOrigins', { required: 'Укажите allowed origins' })}
          />
        </label>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-lg px-4 py-3 text-sm font-medium text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
          style={{ backgroundColor: 'var(--accent)' }}
        >
          {isSubmitting ? 'Создаём...' : 'Создать сайт'}
        </button>
      </form>
    </div>
  )
}

export default SiteCreate

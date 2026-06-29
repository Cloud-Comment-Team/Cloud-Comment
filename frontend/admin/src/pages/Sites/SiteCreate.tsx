import { useState } from 'react'
import type { SubmitHandler } from 'react-hook-form'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, Globe, Link2 } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { createSite } from '../../api/sites'
import Input from '../../components/common/Input/Input'
import type { ModerationMode } from '../../types/api'
import { normalizeDomainInput, parseAllowedOriginsInput } from '../../utils/origins'

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
      allowedOrigins: '',
    },
  })

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
    <div className="cc-page mx-auto max-w-3xl">
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
          Создайте проект и укажите домен с разрешёнными origin для виджета
          </p>
        </div>
      </div>

      {serverError && (
        <div className="mb-6 rounded-lg border px-4 py-3 text-sm" style={{ borderColor: 'var(--danger)', color: 'var(--danger)', backgroundColor: 'var(--danger-bg)' }}>
          {serverError}
        </div>
      )}

      <form
        className="cc-card space-y-5 p-5 md:p-6"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        onSubmit={handleSubmit(onSubmit)}
        noValidate
      >
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
          icon={<Link2 className="h-5 w-5" aria-hidden="true" />}
          error={errors.domain?.message}
          {...register('domain', { required: 'Укажите домен сайта' })}
        />

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
            placeholder="https://example.com"
            {...register('allowedOrigins', { required: 'Укажите allowed origins' })}
          />
        </label>

        <button
          type="submit"
          disabled={isSubmitting}
          className="cc-button-primary w-full py-3"
        >
          {isSubmitting ? 'Создаём...' : 'Создать сайт'}
        </button>
      </form>
    </div>
  )
}

export default SiteCreate

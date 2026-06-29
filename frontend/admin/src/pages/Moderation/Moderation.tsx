import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Check, EyeOff, RotateCcw, ShieldAlert, X } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { applyModerationAction, listComments } from '../../api/moderation'
import { listAllSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import type { Comment, CommentStatus, ModerationActionType, Site } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { getAvailableModerationActions } from '../../utils/moderationActions'

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'На модерации',
  APPROVED: 'Одобрен',
  REJECTED: 'Отклонён',
  HIDDEN: 'Скрыт',
  SPAM: 'Спам',
}

const statusTones: Record<CommentStatus, 'success' | 'warning' | 'danger' | 'muted'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  HIDDEN: 'muted',
  SPAM: 'danger',
}

const actionButtons: Record<
  ModerationActionType,
  {
    label: string
    icon: typeof Check
  }
> = {
  APPROVE: { label: 'Одобрить', icon: Check },
  REJECT: { label: 'Отклонить', icon: X },
  HIDE: { label: 'Скрыть', icon: EyeOff },
  MARK_SPAM: { label: 'Спам', icon: ShieldAlert },
  RESTORE: { label: 'Восстановить', icon: RotateCcw },
}

const emptyFilters = {
  siteId: '',
  pageId: '',
  status: '' as CommentStatus | '',
  pageUrl: '',
  search: '',
}

const Moderation = () => {
  const [sites, setSites] = useState<Site[]>([])
  const [sitesLoading, setSitesLoading] = useState(true)
  const [comments, setComments] = useState<Comment[]>([])
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionCommentId, setActionCommentId] = useState<string | null>(null)
  const [appliedFilters, setAppliedFilters] = useState(emptyFilters)
  const [siteId, setSiteId] = useState('')
  const [pageId, setPageId] = useState('')
  const [status, setStatus] = useState<CommentStatus | ''>('')
  const [pageUrl, setPageUrl] = useState('')
  const [search, setSearch] = useState('')
  const [reasons, setReasons] = useState<Record<string, string>>({})
  const [reloadKey, setReloadKey] = useState(0)

  const siteNamesById = useMemo(() => new Map(sites.map((site) => [site.id, site.name])), [sites])
  const filtersApplied = Object.values(appliedFilters).some(Boolean)

  useEffect(() => {
    let cancelled = false

    async function loadSites() {
      setSitesLoading(true)
      try {
        const allSites = await listAllSites()
        if (!cancelled) {
          setSites(allSites)
        }
      } catch {
        if (!cancelled) {
          setSites([])
        }
      } finally {
        if (!cancelled) {
          setSitesLoading(false)
        }
      }
    }

    void loadSites()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    async function fetchComments() {
      setLoading(true)
      setError(null)

      try {
        const response = await listComments({
          siteId: appliedFilters.siteId || undefined,
          pageId: appliedFilters.pageId.trim() || undefined,
          status: appliedFilters.status || undefined,
          pageUrl: appliedFilters.pageUrl.trim() || undefined,
          search: appliedFilters.search.trim() || undefined,
          sortBy: 'CREATED_AT',
          sortOrder: 'DESC',
          page,
          pageSize: 20,
        })
        if (!cancelled) {
          setComments(response.items)
          setTotalPages(response.totalPages)
          setTotalItems(response.totalItems)
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить комментарии.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void fetchComments()

    return () => {
      cancelled = true
    }
  }, [appliedFilters, page, reloadKey])

  async function handleAction(commentId: string, action: ModerationActionType) {
    setActionCommentId(commentId)
    try {
      await applyModerationAction(commentId, {
        action,
        reason: reasons[commentId]?.trim() || null,
      })
      toast.success('Статус комментария обновлён')
      setReasons((current) => {
        const next = { ...current }
        delete next[commentId]
        return next
      })
      setReloadKey((current) => current + 1)
    } catch (actionError) {
      toast.error(getApiErrorMessage(actionError, 'Не удалось выполнить действие модерации.'))
    } finally {
      setActionCommentId(null)
    }
  }

  function handleApplyFilters(event: FormEvent) {
    event.preventDefault()
    setAppliedFilters({ siteId, pageId, status, pageUrl, search })
    setPage(1)
  }

  function handleResetFilters() {
    setSiteId('')
    setPageId('')
    setStatus('')
    setPageUrl('')
    setSearch('')
    setAppliedFilters(emptyFilters)
    setPage(1)
  }

  return (
    <div className="text-left">
      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          Модерация комментариев
        </h1>
        <p className="mt-1" style={{ color: 'var(--text)' }}>
          Просматривайте очередь комментариев, фильтруйте по сайтам и страницам, меняйте статус
        </p>
      </div>

      <form
        className="mb-6 grid gap-4 rounded-lg border p-4 md:grid-cols-2 xl:grid-cols-3"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        onSubmit={handleApplyFilters}
      >
        <label className="block">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Сайт
          </span>
          <select
            className="w-full rounded-lg border px-3 py-2 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            value={siteId}
            disabled={sitesLoading}
            onChange={(event) => setSiteId(event.target.value)}
          >
            <option value="">Все сайты</option>
            {sites.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Page ID
          </span>
          <input
            className="w-full rounded-lg border px-3 py-2 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            placeholder="UUID страницы"
            value={pageId}
            onChange={(event) => setPageId(event.target.value)}
          />
        </label>

        <label className="block">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Статус
          </span>
          <select
            className="w-full rounded-lg border px-3 py-2 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            value={status}
            onChange={(event) => setStatus(event.target.value as CommentStatus | '')}
          >
            <option value="">Все статусы</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            URL страницы
          </span>
          <input
            className="w-full rounded-lg border px-3 py-2 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            placeholder="https://example.com/page"
            value={pageUrl}
            onChange={(event) => setPageUrl(event.target.value)}
          />
        </label>

        <label className="block md:col-span-2 xl:col-span-2">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Поиск по тексту
          </span>
          <input
            className="w-full rounded-lg border px-3 py-2 outline-none"
            style={{ backgroundColor: 'var(--code-bg)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            placeholder="Текст комментария"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>

        <div className="flex flex-wrap gap-2 md:col-span-2 xl:col-span-3">
          <button
            type="submit"
            className="rounded-lg px-4 py-2 text-sm font-medium text-white transition hover:opacity-90"
            style={{ backgroundColor: 'var(--accent)' }}
          >
            Применить фильтры
          </button>
          {filtersApplied && (
            <button
              type="button"
              className="rounded-lg border px-4 py-2 text-sm font-medium transition hover:opacity-80"
              style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
              onClick={handleResetFilters}
            >
              Сбросить
            </button>
          )}
        </div>
      </form>

      <AsyncState
        loading={loading}
        error={error}
        empty={!loading && !error && comments.length === 0}
        emptyMessage="Комментарии не найдены. Измените фильтры или дождитесь новых сообщений из виджета."
      >
        <div className="space-y-4">
          {comments.map((comment) => {
            const authorLabel =
              comment.author?.displayName || comment.author?.email || 'Гость'
            const siteName = siteNamesById.get(comment.siteId) ?? comment.siteId
            const availableActions = getAvailableModerationActions(comment.status)

            return (
              <article
                key={comment.id}
                className="rounded-lg border p-4"
                style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
              >
                <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-medium" style={{ color: 'var(--text-h)' }}>
                      {authorLabel}
                    </p>
                    <p className="text-sm" style={{ color: 'var(--text)' }}>
                      {siteName} · {comment.pageUrl}
                    </p>
                    <p className="text-xs" style={{ color: 'var(--text)' }}>
                      {formatDateTime(comment.createdAt)} · pageId: {comment.pageId}
                    </p>
                  </div>
                  <Badge tone={statusTones[comment.status]}>{statusLabels[comment.status]}</Badge>
                </div>

                <p className="mb-4 whitespace-pre-wrap text-sm" style={{ color: 'var(--text-h)' }}>
                  {comment.content}
                </p>

                {availableActions.length > 0 && (
                  <>
                    <label className="mb-4 block">
                      <span className="mb-2 block text-xs font-medium" style={{ color: 'var(--text-h)' }}>
                        Причина (необязательно)
                      </span>
                      <input
                        className="w-full rounded-lg border px-3 py-2 text-sm outline-none"
                        style={{
                          backgroundColor: 'var(--code-bg)',
                          borderColor: 'var(--border)',
                          color: 'var(--text-h)',
                        }}
                        placeholder="Причина для этого комментария"
                        value={reasons[comment.id] ?? ''}
                        onChange={(event) =>
                          setReasons((current) => ({
                            ...current,
                            [comment.id]: event.target.value,
                          }))
                        }
                      />
                    </label>

                    <div className="flex flex-wrap gap-2">
                      {availableActions.map((action) => {
                        const { label, icon: Icon } = actionButtons[action]
                        return (
                          <button
                            key={action}
                            type="button"
                            disabled={actionCommentId === comment.id}
                            onClick={() => void handleAction(comment.id, action)}
                            className="inline-flex items-center gap-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition hover:opacity-80 disabled:opacity-50"
                            style={{ borderColor: 'var(--border)', color: 'var(--text-h)' }}
                          >
                            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                            {label}
                          </button>
                        )
                      })}
                    </div>
                  </>
                )}
              </article>
            )
          })}

          <PaginationControls page={page} totalPages={totalPages} totalItems={totalItems} onPageChange={setPage} />
        </div>
      </AsyncState>

      {!sitesLoading && sites.length === 0 && !loading && (
        <p className="mt-4 text-sm" style={{ color: 'var(--text)' }}>
          Сначала создайте сайт в разделе{' '}
          <Link to="/sites" className="font-medium hover:underline" style={{ color: 'var(--accent)' }}>
            Сайты
          </Link>
          .
        </p>
      )}
    </div>
  )
}

export default Moderation

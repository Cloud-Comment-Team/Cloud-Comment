import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  AlertTriangle,
  CalendarClock,
  Check,
  CornerDownRight,
  EyeOff,
  Filter,
  Flame,
  Globe,
  Hash,
  Link2,
  RotateCcw,
  Search,
  ShieldAlert,
  Sparkles,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { applyModerationAction, listComments } from '../../api/moderation'
import { listAllSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import type {
  Comment,
  CommentStatus,
  ModerationActionNotification,
  ModerationActionType,
  ModerationPriority,
  NewCommentNotification,
  Site,
} from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { getAvailableModerationActions } from '../../utils/moderationActions'
import { useRealtimeEvent } from '../../components/realtime/useRealtime'

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'На модерации',
  APPROVED: 'Одобрен',
  REJECTED: 'Отклонен',
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

const priorityLabels: Record<ModerationPriority, string> = {
  LOW: 'Низкий приоритет',
  MEDIUM: 'Средний приоритет',
  HIGH: 'Высокий приоритет',
  URGENT: 'Срочно',
}

const priorityStyles: Record<ModerationPriority, { background: string; color: string; border: string }> = {
  LOW: {
    background: 'color-mix(in srgb, var(--surface-2) 82%, var(--accent) 18%)',
    color: 'var(--text)',
    border: 'var(--border)',
  },
  MEDIUM: {
    background: 'color-mix(in srgb, var(--status-pending-bg) 86%, var(--status-pending-accent) 14%)',
    color: 'var(--status-pending-accent)',
    border: 'var(--status-pending-border)',
  },
  HIGH: {
    background: 'color-mix(in srgb, var(--status-spam-bg) 88%, var(--status-spam-accent) 12%)',
    color: 'var(--status-spam-accent)',
    border: 'var(--status-spam-border)',
  },
  URGENT: {
    background: 'color-mix(in srgb, var(--status-rejected-bg) 84%, var(--status-rejected-accent) 16%)',
    color: 'var(--status-rejected-accent)',
    border: 'var(--status-rejected-border)',
  },
}

const priorityIcons: Record<ModerationPriority, LucideIcon> = {
  LOW: Sparkles,
  MEDIUM: AlertTriangle,
  HIGH: ShieldAlert,
  URGENT: Flame,
}

const statusCardStyles: Record<
  CommentStatus,
  {
    background: string
    border: string
    accent: string
    metaBackground: string
  }
> = {
  PENDING: {
    background: 'var(--status-pending-bg)',
    border: 'var(--status-pending-border)',
    accent: 'var(--status-pending-accent)',
    metaBackground: 'var(--status-pending-meta-bg)',
  },
  APPROVED: {
    background: 'var(--status-approved-bg)',
    border: 'var(--status-approved-border)',
    accent: 'var(--status-approved-accent)',
    metaBackground: 'var(--status-approved-meta-bg)',
  },
  REJECTED: {
    background: 'var(--status-rejected-bg)',
    border: 'var(--status-rejected-border)',
    accent: 'var(--status-rejected-accent)',
    metaBackground: 'var(--status-rejected-meta-bg)',
  },
  HIDDEN: {
    background: 'var(--status-hidden-bg)',
    border: 'var(--status-hidden-border)',
    accent: 'var(--status-hidden-accent)',
    metaBackground: 'var(--status-hidden-meta-bg)',
  },
  SPAM: {
    background: 'var(--status-spam-bg)',
    border: 'var(--status-spam-border)',
    accent: 'var(--status-spam-accent)',
    metaBackground: 'var(--status-spam-meta-bg)',
  },
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

function matchesCommentNotificationFilters(
  payload: NewCommentNotification,
  filters: typeof emptyFilters,
): boolean {
  if (filters.siteId && payload.siteId !== filters.siteId) {
    return false
  }
  if (filters.pageId && payload.pageId !== filters.pageId.trim()) {
    return false
  }
  if (filters.status && payload.status !== filters.status) {
    return false
  }
  if (filters.pageUrl.trim() && !payload.pageUrl.includes(filters.pageUrl.trim())) {
    return false
  }
  if (filters.search.trim() && !payload.contentPreview.toLowerCase().includes(filters.search.trim().toLowerCase())) {
    return false
  }
  return true
}

function matchesModerationActionFilters(
  payload: ModerationActionNotification,
  filters: typeof emptyFilters,
): boolean {
  if (filters.siteId && payload.siteId !== filters.siteId) {
    return false
  }
  if (filters.pageId && payload.pageId !== filters.pageId.trim()) {
    return false
  }
  if (filters.status && payload.fromStatus !== filters.status && payload.toStatus !== filters.status) {
    return false
  }
  return true
}

function getInitials(value: string): string {
  const trimmed = value.trim()
  return trimmed ? trimmed.slice(0, 2).toUpperCase() : '??'
}

function shortenId(value: string): string {
  return `${value.slice(0, 8)}...${value.slice(-6)}`
}

function parentAuthorLabel(comment: Comment): string {
  return comment.parent?.author?.displayName || comment.parent?.author?.email || 'Автор комментария'
}

function PriorityBadge({ priority, score }: { priority: ModerationPriority; score: number }) {
  const style = priorityStyles[priority]
  const Icon = priorityIcons[priority]

  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold"
      style={{ backgroundColor: style.background, borderColor: style.border, color: style.color }}
      title={`Оценка очереди: ${score}`}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {priorityLabels[priority]}
    </span>
  )
}

function MetadataItem({
  icon: Icon,
  label,
  value,
  title,
}: {
  icon: LucideIcon
  label: string
  value: string
  title?: string
}) {
  return (
    <div className="flex min-w-0 items-start gap-2">
      <span
        className="mt-0.5 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
      >
        <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block text-xs font-semibold" style={{ color: 'var(--text-h)' }}>
          {label}
        </span>
        <span className="block truncate text-xs" title={title ?? value} style={{ color: 'var(--text)' }}>
          {value}
        </span>
      </span>
    </div>
  )
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

  useRealtimeEvent((event) => {
    if (event.type === 'comment.created') {
      const payload: NewCommentNotification = event.payload
      if (matchesCommentNotificationFilters(payload, appliedFilters)) {
        setReloadKey((current) => current + 1)
      }
      return
    }

    if (event.type === 'comment.moderation_action_applied') {
      const payload: ModerationActionNotification = event.payload
      if (matchesModerationActionFilters(payload, appliedFilters)) {
        setReloadKey((current) => current + 1)
      }
    }
  })

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
          sortBy: 'SMART',
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
      toast.success('Статус комментария обновлен')
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
    <div className="cc-page">
      <div className="cc-page-heading">
        <div>
          <p className="cc-eyebrow">Очередь</p>
          <h1 className="cc-title">Модерация комментариев</h1>
          <p className="cc-subtitle">
            Сначала показываем комментарии с самым высоким риском: ожидание решения, спам-сигналы, ссылки,
            длинный текст и ответы внутри обсуждений.
          </p>
        </div>
      </div>

      <div
        className="mb-6 flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
      >
        <div className="flex items-start gap-3">
          <span
            className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
          >
            <Sparkles className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
              Умная очередь включена
            </p>
            <p className="text-sm leading-6" style={{ color: 'var(--text)' }}>
              Сортировка учитывает статус, причину автомодерации, ссылки, возраст комментария и контекст ответа.
            </p>
          </div>
        </div>
        <span className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--accent)' }}>
          SMART DESC
        </span>
      </div>

      <form
        className="cc-card mb-6 grid gap-4 p-4 md:grid-cols-2 xl:grid-cols-3"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        onSubmit={handleApplyFilters}
      >
        <div className="flex items-center gap-2 md:col-span-2 xl:col-span-3">
          <Filter className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
          <span className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
            Фильтры очереди
          </span>
        </div>

        <label className="block">
          <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            Сайт
          </span>
          <select
            className="cc-field py-2"
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
            className="cc-field py-2"
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
            className="cc-field py-2"
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
            className="cc-field py-2"
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
            className="cc-field py-2"
            placeholder="Текст комментария"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>

        <div className="flex flex-wrap gap-2 md:col-span-2 xl:col-span-3">
          <button type="submit" className="cc-button-primary">
            <Search className="h-4 w-4" aria-hidden="true" />
            Применить фильтры
          </button>
          {filtersApplied && (
            <button type="button" className="cc-button-secondary" onClick={handleResetFilters}>
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
            const authorLabel = comment.author?.displayName || comment.author?.email || 'Гость'
            const siteName = siteNamesById.get(comment.siteId) ?? comment.siteId
            const availableActions = getAvailableModerationActions(comment.status)
            const statusStyle = statusCardStyles[comment.status]
            const priorityReasons = comment.priorityReasons ?? []

            return (
              <article
                key={comment.id}
                className="cc-card overflow-hidden transition hover:-translate-y-0.5"
                style={{
                  backgroundColor: statusStyle.background,
                  borderColor: statusStyle.border,
                  borderLeftColor: statusStyle.accent,
                  borderLeftWidth: '5px',
                }}
              >
                <div
                  className="flex flex-wrap items-start justify-between gap-3 border-b px-4 py-4"
                  style={{ borderColor: statusStyle.border }}
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <div
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold"
                      style={{ backgroundColor: statusStyle.metaBackground, color: statusStyle.accent }}
                    >
                      {getInitials(authorLabel)}
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-semibold" style={{ color: 'var(--text-h)' }}>
                        {authorLabel}
                      </p>
                      <p className="text-xs" style={{ color: 'var(--text)' }}>
                        Автор комментария
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap justify-end gap-2">
                    <PriorityBadge priority={comment.priority} score={comment.priorityScore} />
                    <Badge tone={statusTones[comment.status]}>{statusLabels[comment.status]}</Badge>
                  </div>
                </div>

                <div
                  className="grid gap-3 border-b px-4 py-3 md:grid-cols-[minmax(0,1.1fr)_minmax(0,1.5fr)_minmax(0,0.9fr)_minmax(0,0.9fr)]"
                  style={{ backgroundColor: statusStyle.metaBackground, borderColor: statusStyle.border }}
                >
                  <MetadataItem icon={Globe} label="Сайт" value={siteName} />
                  <MetadataItem icon={Link2} label="Страница" value={comment.pageUrl} />
                  <MetadataItem icon={CalendarClock} label="Создан" value={formatDateTime(comment.createdAt)} />
                  <MetadataItem icon={Hash} label="ID страницы" value={shortenId(comment.pageId)} title={comment.pageId} />
                </div>

                {comment.parent && (
                  <div className="border-b px-4 py-4" style={{ borderColor: statusStyle.border }}>
                    <div
                      className="rounded-lg border p-3"
                      style={{ backgroundColor: statusStyle.metaBackground, borderColor: statusStyle.border }}
                    >
                      <div className="mb-2 flex flex-wrap items-center gap-2 text-xs" style={{ color: 'var(--text)' }}>
                        <CornerDownRight className="h-4 w-4" style={{ color: statusStyle.accent }} aria-hidden="true" />
                        <span className="font-semibold uppercase" style={{ color: 'var(--text-h)' }}>
                          Ответ на комментарий
                        </span>
                        <span>{parentAuthorLabel(comment)}</span>
                        <Badge tone={statusTones[comment.parent.status]}>{statusLabels[comment.parent.status]}</Badge>
                        <span>{formatDateTime(comment.parent.createdAt)}</span>
                      </div>
                      <p className="line-clamp-3 whitespace-pre-wrap text-sm leading-6" style={{ color: 'var(--text-h)' }}>
                        {comment.parent.content}
                      </p>
                    </div>
                  </div>
                )}

                <div className="px-4 py-4">
                  <p className="mb-2 text-xs font-semibold uppercase" style={{ color: 'var(--text)' }}>
                    Текст комментария
                  </p>
                  <p
                    className="whitespace-pre-wrap border-l-4 py-2 pl-4 text-sm leading-6"
                    style={{ borderColor: statusStyle.accent, color: 'var(--text-h)' }}
                  >
                    {comment.content}
                  </p>
                </div>

                {priorityReasons.length > 0 && (
                  <div className="border-t px-4 py-4" style={{ borderColor: statusStyle.border }}>
                    <div
                      className="rounded-lg border p-3"
                      style={{ backgroundColor: statusStyle.metaBackground, borderColor: statusStyle.border }}
                    >
                      <div className="mb-2 flex items-center gap-2">
                        <Sparkles className="h-4 w-4" style={{ color: statusStyle.accent }} aria-hidden="true" />
                        <span className="text-xs font-semibold uppercase" style={{ color: 'var(--text-h)' }}>
                          Почему выше в очереди
                        </span>
                        <span className="text-xs" style={{ color: 'var(--text)' }}>
                          score {comment.priorityScore}
                        </span>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        {priorityReasons.map((reason) => (
                          <span
                            key={reason}
                            className="rounded-full border px-2.5 py-1 text-xs font-medium"
                            style={{
                              backgroundColor: 'var(--surface)',
                              borderColor: statusStyle.border,
                              color: 'var(--text-h)',
                            }}
                          >
                            {reason}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                {comment.moderationReason && (
                  <div className="border-t px-4 py-4" style={{ borderColor: statusStyle.border }}>
                    <div
                      className="rounded-lg border p-3"
                      style={{ backgroundColor: statusStyle.metaBackground, borderColor: statusStyle.border }}
                    >
                      <div className="mb-2 flex items-center gap-2">
                        <ShieldAlert className="h-4 w-4" style={{ color: statusStyle.accent }} aria-hidden="true" />
                        <span className="text-xs font-semibold uppercase" style={{ color: 'var(--text-h)' }}>
                          Сработала автомодерация
                        </span>
                      </div>
                      <p className="text-sm leading-6" style={{ color: 'var(--text-h)' }}>
                        {comment.moderationReason}
                      </p>
                    </div>
                  </div>
                )}

                {availableActions.length > 0 && (
                  <div className="border-t px-4 py-4" style={{ borderColor: statusStyle.border }}>
                    <label className="mb-3 block">
                      <span className="mb-2 block text-xs font-medium" style={{ color: 'var(--text-h)' }}>
                        Причина (необязательно)
                      </span>
                      <input
                        className="cc-field py-2 text-sm"
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

                    <div className="flex flex-wrap items-center gap-2">
                      {availableActions.map((action) => {
                        const { label, icon: Icon } = actionButtons[action]
                        return (
                          <button
                            key={action}
                            type="button"
                            disabled={actionCommentId === comment.id}
                            onClick={() => void handleAction(comment.id, action)}
                            className="inline-flex items-center gap-1 rounded-lg border px-3 py-1.5 text-xs font-semibold transition hover:-translate-y-0.5 disabled:opacity-50"
                            style={{ borderColor: 'var(--border)', color: 'var(--text-h)' }}
                          >
                            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                            {label}
                          </button>
                        )
                      })}
                    </div>
                  </div>
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

import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  Check,
  ChevronRight,
  Clock3,
  EyeOff,
  Filter,
  RotateCcw,
  Search,
  ShieldAlert,
  Star,
  X,
} from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import {
  applyBulkModerationAction,
  applyModerationAction,
  deleteAutoModerationFeedback,
  getComment,
  getModerationCounts,
  listComments,
  undoBulkModerationOperation,
  undoModerationAction,
  updateCommentFlags,
  setAutoModerationFeedback,
} from '../../api/moderation'
import { listAllSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import { ActionBar, DataRow, Drawer, PageHeader } from '../../components/common/Workspace'
import { useRealtimeEvent } from '../../components/realtime/useRealtime'
import { useAuthStore } from '../../store'
import type {
  Comment,
  AutoModerationFeedback,
  AutoModerationFeedbackType,
  CommentStatus,
  ModerationCommand,
  ModerationCounts,
  Site,
} from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { getAvailableModerationActions } from '../../utils/moderationActions'
import { decisionLabels, executionModeLabels, signalLabel } from '../../components/automoderation/policyModel'

type QueueView = 'pending' | 'spam' | 'history'

interface SavedFilters {
  siteId: string
  pageUrl: string
  search: string
  favorite: boolean
}

type UndoTarget =
  | { kind: 'action'; id: string; affected: 1 }
  | { kind: 'operation'; id: string; affected: number }

interface UndoSummary {
  restored: number
  conflicts: Array<{ commentId: string; message: string }>
}

interface BulkFailure {
  commentId: string
  message: string
}

const FILTERS_KEY_PREFIX = 'cloud-comment:moderation-filters:v3'
const LEGACY_FILTERS_KEY = 'cloud-comment:moderation-filters:v2'
const emptyFilters: SavedFilters = { siteId: '', pageUrl: '', search: '', favorite: false }

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'Ожидает решения',
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

const actionLabels: Record<ModerationCommand, string> = {
  APPROVE: 'Одобрить',
  REJECT: 'Отклонить',
  HIDE: 'Скрыть',
  MARK_SPAM: 'В спам',
  RESTORE: 'Восстановить',
}

const actionIcons: Record<ModerationCommand, typeof Check> = {
  APPROVE: Check,
  REJECT: X,
  HIDE: EyeOff,
  MARK_SPAM: ShieldAlert,
  RESTORE: RotateCcw,
}

const viewStatuses: Record<QueueView, CommentStatus[]> = {
  pending: ['PENDING', 'SPAM'],
  spam: ['SPAM'],
  history: ['APPROVED', 'REJECTED', 'HIDDEN'],
}

function readSavedFilters(filtersKey: string): SavedFilters {
  try {
    localStorage.removeItem(LEGACY_FILTERS_KEY)
    const parsed = JSON.parse(localStorage.getItem(filtersKey) ?? '') as Partial<SavedFilters>
    return {
      siteId: typeof parsed.siteId === 'string' ? parsed.siteId : '',
      pageUrl: typeof parsed.pageUrl === 'string' ? parsed.pageUrl : '',
      search: typeof parsed.search === 'string' ? parsed.search : '',
      favorite: parsed.favorite === true,
    }
  } catch {
    return emptyFilters
  }
}

function readInitialFilters(searchParams: URLSearchParams, filtersKey: string): SavedFilters {
  const saved = readSavedFilters(filtersKey)
  const analyticsSource = searchParams.get('source') === 'analytics'
  if (analyticsSource) {
    return {
      ...emptyFilters,
      siteId: searchParams.get('siteId')?.trim() ?? '',
      pageUrl: searchParams.get('pageUrl')?.trim() ?? '',
    }
  }
  return {
    ...saved,
    siteId: searchParams.get('siteId')?.trim() || saved.siteId,
    pageUrl: searchParams.get('pageUrl')?.trim() || saved.pageUrl,
  }
}

function isQueueView(value: string | null): value is QueueView {
  return value === 'pending' || value === 'spam' || value === 'history'
}

function isCommentStatus(value: string | null): value is CommentStatus {
  return value !== null && Object.hasOwn(statusLabels, value)
}

function queueViewForStatus(status: CommentStatus): QueueView {
  if (status === 'PENDING') return 'pending'
  if (status === 'SPAM') return 'spam'
  return 'history'
}

function authorName(comment: Comment): string {
  return comment.author?.displayName || comment.author?.email || 'Гость'
}

const Moderation = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const ownerId = useAuthStore((state) => state.user?.id ?? 'anonymous')
  const filtersKey = `${FILTERS_KEY_PREFIX}:${ownerId}`
  const [initialFilters] = useState(() => readInitialFilters(searchParams, filtersKey))
  const requestedView = searchParams.get('view')
  const requestedStatus = searchParams.get('status')
  const exactStatus = isCommentStatus(requestedStatus) ? requestedStatus : null
  const view: QueueView = exactStatus
    ? queueViewForStatus(exactStatus)
    : isQueueView(requestedView) ? requestedView : 'pending'
  const selectedCommentId = searchParams.get('comment')

  const [sites, setSites] = useState<Site[]>([])
  const [comments, setComments] = useState<Comment[]>([])
  const [detailComment, setDetailComment] = useState<Comment | null>(null)
  const [counts, setCounts] = useState<ModerationCounts | null>(null)
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [undoTarget, setUndoTarget] = useState<UndoTarget | null>(null)
  const [undoSummary, setUndoSummary] = useState<UndoSummary | null>(null)
  const [bulkFailures, setBulkFailures] = useState<BulkFailure[]>([])
  const [reason, setReason] = useState('')
  const [filters, setFilters] = useState(initialFilters)
  const [appliedFilters, setAppliedFilters] = useState(initialFilters)

  const selectedComment = comments.find((comment) => comment.id === selectedCommentId)
    ?? (detailComment?.id === selectedCommentId ? detailComment : null)
  const siteNames = useMemo(() => new Map(sites.map((site) => [site.id, site.name])), [sites])
  const allVisibleSelected = comments.length > 0 && comments.every((comment) => selectedIds.has(comment.id))

  useEffect(() => {
    void listAllSites().then(setSites).catch(() => setSites([]))
  }, [])

  useRealtimeEvent((event) => {
    if (event.type === 'comment.created' || event.type === 'comment.moderation_action_applied') {
      setReloadKey((current) => current + 1)
    }
  })

  useEffect(() => {
    let cancelled = false
    Promise.all([
      listComments({
        siteId: appliedFilters.siteId || undefined,
        pageUrl: appliedFilters.pageUrl.trim() || undefined,
        search: appliedFilters.search.trim() || undefined,
        favorite: appliedFilters.favorite || undefined,
        status: exactStatus ?? undefined,
        statuses: exactStatus ? undefined : viewStatuses[view],
        sortBy: 'SMART',
        sortOrder: 'DESC',
        page,
        pageSize: 30,
      }),
      getModerationCounts().catch(() => null),
    ])
      .then(([response, nextCounts]) => {
        if (cancelled) return
        setComments(response.items)
        setTotalPages(response.totalPages)
        setTotalItems(response.totalItems)
        if (nextCounts) setCounts(nextCounts)
        setError(null)
        setSelectedIds((current) => new Set([...current].filter((id) => response.items.some((item) => item.id === id))))
      })
      .catch((loadError) => {
        if (!cancelled) setError(getApiErrorMessage(loadError, 'Не удалось загрузить очередь модерации.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [appliedFilters, exactStatus, page, reloadKey, view])

  useEffect(() => {
    if (!selectedCommentId || comments.some((comment) => comment.id === selectedCommentId)) return
    let cancelled = false
    void getComment(selectedCommentId)
      .then((comment) => {
        if (!cancelled) setDetailComment(comment)
      })
      .catch((loadError) => {
        if (!cancelled) toast.error(getApiErrorMessage(loadError, 'Не удалось открыть комментарий.'))
      })
    return () => {
      cancelled = true
    }
  }, [comments, selectedCommentId])

  function updateRoute(nextView: QueueView, commentId?: string, preserveStatus = false) {
    const next = new URLSearchParams(searchParams)
    next.set('view', nextView)
    if (!preserveStatus) next.delete('status')
    if (commentId) next.set('comment', commentId)
    else next.delete('comment')
    setSearchParams(next)
    if (nextView !== view || (!preserveStatus && exactStatus)) {
      setPage(1)
      setSelectedIds(new Set())
    }
  }

  async function runAction(commentIds: string[], action: ModerationCommand) {
    if (commentIds.length === 0) return
    setBusy(true)
    setUndoSummary(null)
    setBulkFailures([])
    try {
      if (commentIds.length === 1) {
        const result = await applyModerationAction(commentIds[0], { action, reason: reason.trim() || null })
        setUndoTarget({ kind: 'action', id: result.id, affected: 1 })
        setSelectedIds(new Set())
        toast.success('Действие выполнено')
      } else {
        const operationId = crypto.randomUUID()
        const result = await applyBulkModerationAction({
          operationId,
          commentIds,
          action,
          reason: reason.trim() || null,
        })
        const successful = result.items.filter((item) => item.success)
        const failedItems = result.items
          .filter((item) => !item.success)
          .map((item) => ({
            commentId: item.commentId,
            message: item.message || 'Не удалось применить действие',
          }))
        setBulkFailures(failedItems)
        setSelectedIds(new Set(failedItems.map((item) => item.commentId)))
        setUndoTarget(successful.length > 0
          ? { kind: 'operation', id: operationId, affected: successful.length }
          : null)
        toast.success(`Обработано: ${successful.length}${failedItems.length ? `, не удалось: ${failedItems.length}` : ''}`)
      }
      setReason('')
      setReloadKey((current) => current + 1)
    } catch (actionError) {
      toast.error(getApiErrorMessage(actionError, 'Не удалось выполнить действие модерации.'))
    } finally {
      setBusy(false)
    }
  }

  async function undoLastAction() {
    if (!undoTarget) return
    setBusy(true)
    try {
      if (undoTarget.kind === 'action') {
        await undoModerationAction(undoTarget.id)
        setUndoSummary({ restored: 1, conflicts: [] })
        toast.success('Последнее действие отменено')
      } else {
        const result = await undoBulkModerationOperation(undoTarget.id)
        const restored = result.items.filter((item) => item.success).length
        const conflicts = result.items
          .filter((item) => !item.success)
          .map((item) => ({
            commentId: item.commentId,
            message: item.message || 'Комментарий был изменён позднее или срок отмены истёк',
          }))
        setUndoSummary({ restored, conflicts })
        if (conflicts.length > 0) {
          toast(`Отменено: ${restored}, конфликтов: ${conflicts.length}`)
        } else {
          toast.success(`Массовое действие отменено для ${restored} комментариев`)
        }
      }
      setUndoTarget(null)
      setReloadKey((current) => current + 1)
    } catch (undoError) {
      toast.error(getApiErrorMessage(undoError, 'Не удалось отменить действие. Возможно, прошло больше 15 минут.'))
    } finally {
      setBusy(false)
    }
  }

  async function toggleFavorite(comment: Comment) {
    setBusy(true)
    try {
      await updateCommentFlags(comment.id, { favorite: !comment.favorite })
      setReloadKey((current) => current + 1)
    } catch (flagError) {
      toast.error(getApiErrorMessage(flagError, 'Не удалось обновить избранное.'))
    } finally {
      setBusy(false)
    }
  }

  function updateFeedback(commentId: string, feedback: AutoModerationFeedback | null) {
    const updateComment = (comment: Comment): Comment => comment.id === commentId && comment.autoModeration
      ? { ...comment, autoModeration: { ...comment.autoModeration, feedback } }
      : comment
    setComments((current) => current.map(updateComment))
    setDetailComment((current) => current ? updateComment(current) : current)
  }

  async function saveAutoModerationFeedback(comment: Comment, type: AutoModerationFeedbackType) {
    setBusy(true)
    try {
      updateFeedback(comment.id, await setAutoModerationFeedback(comment.id, type))
      toast.success('Обратная связь сохранена')
    } catch (feedbackError) {
      toast.error(getApiErrorMessage(feedbackError, 'Не удалось сохранить обратную связь.'))
    } finally {
      setBusy(false)
    }
  }

  async function removeAutoModerationFeedback(comment: Comment) {
    setBusy(true)
    try {
      await deleteAutoModerationFeedback(comment.id)
      updateFeedback(comment.id, null)
      toast.success('Отметка снята')
    } catch (feedbackError) {
      toast.error(getApiErrorMessage(feedbackError, 'Не удалось снять отметку.'))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement
      if (target.matches('input, textarea, select, button, [contenteditable="true"]') || event.metaKey || event.ctrlKey || event.altKey) return
      const targets = selectedIds.size > 0 ? [...selectedIds] : selectedComment ? [selectedComment.id] : []
      const shortcut: Record<string, ModerationCommand> = { a: 'APPROVE', s: 'MARK_SPAM', r: 'REJECT' }
      if (shortcut[event.key.toLowerCase()] && targets.length > 0) {
        event.preventDefault()
        void runAction(targets, shortcut[event.key.toLowerCase()])
      }
      if (event.key.toLowerCase() === 'u' && undoTarget) {
        event.preventDefault()
        void undoLastAction()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    localStorage.setItem(filtersKey, JSON.stringify(filters))
    setAppliedFilters(filters)
    setPage(1)
    const next = new URLSearchParams(searchParams)
    if (filters.siteId) next.set('siteId', filters.siteId)
    else next.delete('siteId')
    if (filters.pageUrl.trim()) next.set('pageUrl', filters.pageUrl.trim())
    else next.delete('pageUrl')
    next.delete('source')
    next.delete('comment')
    setSearchParams(next, { replace: true })
  }

  function resetFilters() {
    localStorage.removeItem(filtersKey)
    setFilters(emptyFilters)
    setAppliedFilters(emptyFilters)
    setPage(1)
    const next = new URLSearchParams(searchParams)
    next.delete('siteId')
    next.delete('pageUrl')
    next.delete('status')
    next.delete('source')
    next.delete('comment')
    setSearchParams(next, { replace: true })
  }

  function toggleSelection(commentId: string) {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (next.has(commentId)) next.delete(commentId)
      else next.add(commentId)
      return next
    })
  }

  return (
    <div className="cc-page">
      <PageHeader
        eyebrow="Рабочая очередь"
        title="Модерация"
        description="Разберите спорные комментарии без переходов между страницами. A — одобрить, S — спам, R — отклонить, U — отменить."
        actions={<Badge tone="warning">Требуют решения: {counts?.requiringDecision ?? '—'}</Badge>}
      />

      <nav className="mb-4 flex flex-wrap gap-2" aria-label="Представление очереди">
        {([
          ['pending', `К разбору (${(counts?.statuses.PENDING ?? 0) + (counts?.statuses.SPAM ?? 0)})`],
          ['spam', `Спам (${counts?.statuses.SPAM ?? 0})`],
          ['history', 'История решений'],
        ] as Array<[QueueView, string]>).map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={view === value ? 'cc-button-primary' : 'cc-button-secondary'}
            onClick={() => updateRoute(value)}
            aria-current={view === value ? 'page' : undefined}
          >
            {label}
          </button>
        ))}
      </nav>

      {exactStatus && (
        <div className="cc-action-bar mb-4" role="status">
          <span className="text-sm">
            Точный фильтр: <strong style={{ color: 'var(--text-h)' }}>{statusLabels[exactStatus]}</strong>
          </span>
          <button type="button" className="cc-button-secondary" onClick={() => updateRoute(view)}>
            Показать весь раздел
          </button>
        </div>
      )}

      <form className="cc-filter-bar mb-4" onSubmit={applyFilters}>
        <Filter className="mb-2 h-4 w-4" aria-hidden="true" />
        <label className="min-w-44 flex-1 text-xs font-semibold">
          Сайт
          <select
            className="cc-field mt-1 !px-3 !py-2 text-sm"
            value={filters.siteId}
            onChange={(event) => setFilters({ ...filters, siteId: event.target.value })}
          >
            <option value="">Все сайты</option>
            {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
          </select>
        </label>
        <label className="min-w-52 flex-[2] text-xs font-semibold">
          Поиск
          <div className="relative mt-1">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4" aria-hidden="true" />
            <input
              className="cc-field !py-2 !pl-9 text-sm"
              value={filters.search}
              placeholder="Текст комментария"
              onChange={(event) => setFilters({ ...filters, search: event.target.value })}
            />
          </div>
        </label>
        <label className="min-w-52 flex-[2] text-xs font-semibold">
          URL страницы
          <input
            className="cc-field mt-1 !px-3 !py-2 text-sm"
            value={filters.pageUrl}
            placeholder="https://example.com/page"
            onChange={(event) => setFilters({ ...filters, pageUrl: event.target.value })}
          />
        </label>
        <label className="mb-2 flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={filters.favorite}
            onChange={(event) => setFilters({ ...filters, favorite: event.target.checked })}
          />
          <Star className="h-4 w-4" aria-hidden="true" /> Избранные
        </label>
        <button className="cc-button-primary" type="submit">Применить</button>
        <button className="cc-button-secondary" type="button" onClick={resetFilters}>Сбросить</button>
      </form>

      {(selectedIds.size > 0 || undoTarget || undoSummary || bulkFailures.length > 0) && (
        <ActionBar label="Массовые действия">
          <div className="flex flex-wrap items-center gap-2">
            {selectedIds.size > 0 && <strong className="text-sm">Выбрано: {selectedIds.size}</strong>}
            {selectedIds.size > 0 && (['APPROVE', 'REJECT', 'MARK_SPAM'] as ModerationCommand[]).map((action) => {
              const Icon = actionIcons[action]
              return (
                <button key={action} type="button" className="cc-button-secondary" disabled={busy} onClick={() => void runAction([...selectedIds], action)}>
                  <Icon className="h-4 w-4" aria-hidden="true" /> {actionLabels[action]}
                </button>
              )
            })}
          </div>
          {undoTarget && (
            <button type="button" className="cc-button-secondary" disabled={busy} onClick={() => void undoLastAction()}>
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              {undoTarget.kind === 'operation'
                ? `Отменить массовое действие (${undoTarget.affected})`
                : 'Отменить последнее действие'}
            </button>
          )}
          {undoSummary && (
            <div className="min-w-64 text-sm" role="status">
              <strong>Результат отмены: восстановлено {undoSummary.restored}</strong>
              {undoSummary.conflicts.length > 0 && (
                <>
                  <p className="mt-1">Конфликты ({undoSummary.conflicts.length}):</p>
                  <ul className="mt-1 list-disc pl-5">
                    {undoSummary.conflicts.map((conflict) => (
                      <li key={conflict.commentId}>
                        Комментарий …{conflict.commentId.slice(-8)}: {conflict.message}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              <button
                type="button"
                className="cc-button-ghost mt-2"
                onClick={() => setUndoSummary(null)}
              >
                Скрыть результат
              </button>
            </div>
          )}
          {bulkFailures.length > 0 && (
            <div className="min-w-64 text-sm" role="alert">
              <strong>Не обработано: {bulkFailures.length}</strong>
              <ul className="mt-1 list-disc pl-5">
                {bulkFailures.map((failure) => (
                  <li key={failure.commentId}>Комментарий …{failure.commentId.slice(-8)}: {failure.message}</li>
                ))}
              </ul>
              <div className="mt-2 flex flex-wrap gap-2">
                <button type="button" className="cc-button-secondary" onClick={() => setSelectedIds(new Set(bulkFailures.map((failure) => failure.commentId)))}>
                  Выбрать неудавшиеся
                </button>
                <button type="button" className="cc-button-ghost" onClick={() => setBulkFailures([])}>Скрыть результат</button>
              </div>
            </div>
          )}
        </ActionBar>
      )}

      <div className="cc-table-shell mt-4">
        <div className="grid grid-cols-[2rem_minmax(0,1fr)_9rem_8rem_2rem] items-center gap-3 border-b px-4 py-3 text-xs font-semibold uppercase" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
          <input
            type="checkbox"
            aria-label="Выбрать все видимые комментарии"
            checked={allVisibleSelected}
            onChange={() => setSelectedIds(allVisibleSelected ? new Set() : new Set(comments.map((comment) => comment.id)))}
          />
          <span>Комментарий</span><span>Статус</span><span>Создан</span><span />
        </div>
        <AsyncState loading={loading} error={error} empty={!loading && !error && comments.length === 0} emptyMessage="В этом представлении комментариев нет.">
          {comments.map((comment) => (
            <DataRow key={comment.id} compact>
              <div className="grid w-full grid-cols-[2rem_minmax(0,1fr)_9rem_8rem_2rem] items-center gap-3 py-2">
                <input
                  type="checkbox"
                  aria-label={`Выбрать комментарий ${authorName(comment)}`}
                  checked={selectedIds.has(comment.id)}
                  onChange={() => toggleSelection(comment.id)}
                />
                <button type="button" className="min-w-0 text-left" onClick={() => updateRoute(view, comment.id, true)}>
                  <span className="flex items-center gap-2">
                    <strong className="truncate" style={{ color: 'var(--text-h)' }}>{authorName(comment)}</strong>
                    {comment.favorite && <Star className="h-3.5 w-3.5" aria-label="В избранном" />}
                  </span>
                  <span className="block truncate text-xs" style={{ color: 'var(--text)' }}>{comment.content}</span>
                  <span className="block truncate text-xs" style={{ color: 'var(--text)' }}>{siteNames.get(comment.siteId) ?? comment.pageUrl}</span>
                </button>
                <Badge tone={statusTones[comment.status]}>{statusLabels[comment.status]}</Badge>
                <time className="text-xs" dateTime={comment.createdAt}>{formatDateTime(comment.createdAt)}</time>
                <button type="button" className="cc-button-secondary !p-1.5" aria-label="Открыть подробности" onClick={() => updateRoute(view, comment.id, true)}>
                  <ChevronRight className="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </DataRow>
          ))}
        </AsyncState>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3 text-sm" style={{ color: 'var(--text)' }}>
        <span>Найдено: {totalItems}</span>
        <PaginationControls page={page} totalPages={totalPages} totalItems={totalItems} onPageChange={setPage} />
      </div>

      <Drawer title="Комментарий" open={selectedComment !== null} onClose={() => updateRoute(view, undefined, true)}>
        {selectedComment && (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={statusTones[selectedComment.status]}>{statusLabels[selectedComment.status]}</Badge>
              <span className="text-sm font-semibold">{authorName(selectedComment)}</span>
              <time className="ml-auto flex items-center gap-1 text-xs" dateTime={selectedComment.createdAt}>
                <Clock3 className="h-3.5 w-3.5" aria-hidden="true" /> {formatDateTime(selectedComment.createdAt)}
              </time>
            </div>
            <div className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', background: 'var(--surface-muted)' }}>
              <p className="whitespace-pre-wrap text-sm leading-6" style={{ color: 'var(--text-h)' }}>{selectedComment.content}</p>
            </div>
            {selectedComment.parent && (
              <div>
                <p className="mb-1 text-xs font-semibold uppercase">Ответ на комментарий</p>
                <p className="line-clamp-3 text-sm" style={{ color: 'var(--text)' }}>{selectedComment.parent.content}</p>
              </div>
            )}
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div><dt className="text-xs font-semibold">Сайт</dt><dd>{siteNames.get(selectedComment.siteId) ?? selectedComment.siteId}</dd></div>
              <div><dt className="text-xs font-semibold">Приоритет</dt><dd>{selectedComment.priority} · {selectedComment.priorityScore}</dd></div>
              <div className="sm:col-span-2"><dt className="text-xs font-semibold">Страница</dt><dd className="break-all">{selectedComment.pageUrl}</dd></div>
              {selectedComment.moderationReason && <div className="sm:col-span-2"><dt className="text-xs font-semibold">Причина автомодерации</dt><dd>{selectedComment.moderationReason}</dd></div>}
            </dl>
            {selectedComment.autoModeration ? (
              <section className="rounded-lg border p-4" aria-labelledby="comment-automoderation-title" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}>
                <div className="flex flex-wrap items-center gap-2">
                  <ShieldAlert className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                  <h3 id="comment-automoderation-title" className="font-semibold" style={{ color: 'var(--text-h)' }}>Решение автомодерации</h3>
                  <Badge tone={selectedComment.autoModeration.decision === 'APPROVE' ? 'success' : selectedComment.autoModeration.decision === 'REVIEW' ? 'warning' : 'danger'}>
                    {decisionLabels[selectedComment.autoModeration.decision]}
                  </Badge>
                </div>
                <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
                  <div><dt className="text-xs" style={{ color: 'var(--text)' }}>Версия</dt><dd className="font-semibold">{selectedComment.autoModeration.policyVersion}</dd></div>
                  <div><dt className="text-xs" style={{ color: 'var(--text)' }}>Режим</dt><dd className="font-semibold">{executionModeLabels[selectedComment.autoModeration.executionMode]}</dd></div>
                  <div><dt className="text-xs" style={{ color: 'var(--text)' }}>Score</dt><dd className="font-semibold">{selectedComment.autoModeration.score}</dd></div>
                </dl>
                {selectedComment.autoModeration.reason && <p className="mt-3 text-sm" style={{ color: 'var(--text-h)' }}>{selectedComment.autoModeration.reason}</p>}
                {selectedComment.autoModeration.signals.length > 0 && (
                  <ul className="mt-3 space-y-2">
                    {selectedComment.autoModeration.signals.map((signal, index) => (
                      <li key={`${signal.code}-${index}`} className="rounded-md border px-3 py-2 text-sm" style={{ borderColor: 'var(--border)' }}>
                        <strong style={{ color: 'var(--text-h)' }}>+{signal.score} {signalLabel(signal.code)}</strong>
                        {signal.message && <span className="block" style={{ color: 'var(--text)' }}>{signal.message}</span>}
                      </li>
                    ))}
                  </ul>
                )}
                <div className="mt-4 border-t pt-4" style={{ borderColor: 'var(--border)' }}>
                  <p className="text-xs leading-5" style={{ color: 'var(--text)' }}>Отметка помогает измерять качество политики. Текст комментария в обратную связь не копируется.</p>
                  {selectedComment.autoModeration.feedback ? (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <Badge tone="accent">{selectedComment.autoModeration.feedback.type === 'FALSE_POSITIVE' ? 'Ложное срабатывание' : 'Пропущенный нежелательный комментарий'}</Badge>
                      <button type="button" className="cc-button-secondary" disabled={busy} onClick={() => void removeAutoModerationFeedback(selectedComment)}>Снять отметку</button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="cc-button-secondary mt-3"
                      disabled={busy}
                      onClick={() => void saveAutoModerationFeedback(
                        selectedComment,
                        selectedComment.autoModeration?.decision === 'APPROVE' ? 'FALSE_NEGATIVE' : 'FALSE_POSITIVE',
                      )}
                    >
                      {selectedComment.autoModeration.decision === 'APPROVE' ? 'Это нежелательный комментарий' : 'Это допустимый комментарий'}
                    </button>
                  )}
                </div>
              </section>
            ) : (
              <section className="rounded-lg border p-4 text-sm" aria-label="Решение автомодерации" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
                Автомодерация не применялась к этому комментарию.
              </section>
            )}
            <label className="block text-sm font-semibold">
              Причина действия
              <textarea className="cc-field mt-1 min-h-20 text-sm" maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} />
            </label>
            <div className="flex flex-wrap gap-2">
              {getAvailableModerationActions(selectedComment.status).map((action) => {
                const Icon = actionIcons[action]
                return (
                  <button key={action} type="button" className={action === 'APPROVE' ? 'cc-button-primary' : 'cc-button-secondary'} disabled={busy} onClick={() => void runAction([selectedComment.id], action)}>
                    <Icon className="h-4 w-4" aria-hidden="true" /> {actionLabels[action]}
                  </button>
                )
              })}
              <button type="button" className="cc-button-secondary" disabled={busy} onClick={() => void toggleFavorite(selectedComment)}>
                <Star className="h-4 w-4" aria-hidden="true" /> {selectedComment.favorite ? 'Убрать из избранного' : 'В избранное'}
              </button>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default Moderation

import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { ExternalLink, Filter, LoaderCircle, MessageSquareText, Pin, Search, Send, Undo2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { getApiErrorMessage } from '../../api/auth'
import { createOwnerReply, getDiscussion, listDiscussions } from '../../api/discussions'
import { listAllSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import { PageHeader } from '../../components/common/Workspace'
import { useRealtimeEvent } from '../../components/realtime/useRealtime'
import type {
  DiscussionFilter,
  DiscussionSummary,
  DiscussionThread,
  Site,
} from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'

const views: Array<{ value: DiscussionFilter; label: string }> = [
  { value: 'ALL', label: 'Все' },
  { value: 'UNREAD', label: 'Непрочитанные' },
  { value: 'NEEDS_REPLY', label: 'Ждут ответа' },
]

const REPLY_DRAFT_PREFIX = 'cloud-comment:owner-reply-draft:v1:'

interface ReplyDraft {
  rootCommentId: string | null
  content: string
  operationId: string | null
}

const EMPTY_REPLY_DRAFT: ReplyDraft = { rootCommentId: null, content: '', operationId: null }

function readReplyDraft(rootCommentId: string): ReplyDraft {
  try {
    const stored = sessionStorage.getItem(`${REPLY_DRAFT_PREFIX}${rootCommentId}`)
    if (!stored) return { ...EMPTY_REPLY_DRAFT, rootCommentId }
    const parsed = JSON.parse(stored) as Partial<ReplyDraft>
    return {
      rootCommentId,
      content: typeof parsed.content === 'string' ? parsed.content.slice(0, 5000) : '',
      operationId: typeof parsed.operationId === 'string' ? parsed.operationId : null,
    }
  } catch {
    return { ...EMPTY_REPLY_DRAFT, rootCommentId }
  }
}

function writeReplyDraft(draft: ReplyDraft) {
  if (!draft.rootCommentId) return
  const key = `${REPLY_DRAFT_PREFIX}${draft.rootCommentId}`
  if (!draft.content) {
    sessionStorage.removeItem(key)
    return
  }
  sessionStorage.setItem(key, JSON.stringify({ content: draft.content, operationId: draft.operationId }))
}

function discussionView(value: string | null): DiscussionFilter {
  return views.some((item) => item.value === value) ? value as DiscussionFilter : 'ALL'
}

function positivePage(value: string | null): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1
}

function pageLabel(discussion: Pick<DiscussionSummary, 'pageTitle' | 'pageUrl'>): string {
  if (discussion.pageTitle?.trim()) return discussion.pageTitle.trim()
  try {
    const url = new URL(discussion.pageUrl)
    return `${url.hostname}${url.pathname === '/' ? '' : url.pathname}`
  } catch {
    return discussion.pageUrl
  }
}

function statusLabel(discussion: DiscussionSummary): string {
  if (discussion.unread) return 'Новое'
  return discussion.status === 'NEEDS_REPLY' ? 'Ждёт ответа' : 'В диалоге'
}

function statusTone(discussion: DiscussionSummary): 'accent' | 'warning' | 'muted' {
  if (discussion.unread) return 'accent'
  return discussion.status === 'NEEDS_REPLY' ? 'warning' : 'muted'
}

function ListSkeleton() {
  return (
    <div className="divide-y" style={{ borderColor: 'var(--border)' }} aria-label="Загрузка обсуждений">
      {[1, 2, 3, 4].map((item) => (
        <div key={item} className="space-y-3 px-4 py-5" aria-hidden="true">
          <div className="h-3 w-2/5 animate-pulse rounded" style={{ backgroundColor: 'var(--surface-muted)' }} />
          <div className="h-4 w-4/5 animate-pulse rounded" style={{ backgroundColor: 'var(--surface-muted)' }} />
          <div className="h-3 w-full animate-pulse rounded" style={{ backgroundColor: 'var(--surface-muted)' }} />
        </div>
      ))}
    </div>
  )
}

const Comments = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('discussion')
  const view = discussionView(searchParams.get('view'))
  const siteId = searchParams.get('siteId')?.trim() ?? ''
  const search = searchParams.get('search')?.trim() ?? ''
  const page = positivePage(searchParams.get('page'))
  const routeFilterKey = `${siteId}\n${search}`

  const [sites, setSites] = useState<Site[]>([])
  const [items, setItems] = useState<DiscussionSummary[]>([])
  const [thread, setThread] = useState<DiscussionThread | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [detailError, setDetailError] = useState<{ id: string; message: string } | null>(null)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [filterDraft, setFilterDraft] = useState({ routeKey: routeFilterKey, siteId, search })
  const [listReloadKey, setListReloadKey] = useState(0)
  const [detailReloadKey, setDetailReloadKey] = useState(0)
  const [realtimeRevision, setRealtimeRevision] = useState(0)
  const [replyDraft, setReplyDraft] = useState<ReplyDraft>(EMPTY_REPLY_DRAFT)
  const [replySendingId, setReplySendingId] = useState<string | null>(null)
  const [replyError, setReplyError] = useState<{ rootCommentId: string; message: string } | null>(null)
  const [replyNotice, setReplyNotice] = useState<{ rootCommentId: string; message: string } | null>(null)
  const replyInputRef = useRef<HTMLTextAreaElement>(null)

  const activeThread = thread?.summary.rootCommentId === selectedId ? thread : null
  const activeDetailError = detailError?.id === selectedId ? detailError.message : null
  const currentFilterDraft = filterDraft.routeKey === routeFilterKey
    ? filterDraft
    : { routeKey: routeFilterKey, siteId, search }
  const activeReplyDraft = selectedId
    ? replyDraft.rootCommentId === selectedId ? replyDraft : readReplyDraft(selectedId)
    : EMPTY_REPLY_DRAFT
  const activeReplyError = replyError?.rootCommentId === selectedId ? replyError.message : null
  const activeReplyNotice = replyNotice?.rootCommentId === selectedId ? replyNotice.message : ''
  const replySending = replySendingId === selectedId
  const selectedSummary = useMemo(
    () => items.find((item) => item.rootCommentId === selectedId) ?? activeThread?.summary ?? null,
    [activeThread, items, selectedId],
  )

  useEffect(() => {
    void listAllSites().then(setSites).catch(() => setSites([]))
  }, [])

  useRealtimeEvent((event) => {
    if (
      event.type === 'comment.created'
      || event.type === 'comment.owner_reply_created'
      || event.type === 'comment.moderation_action_applied'
    ) {
      setRealtimeRevision((current) => current + 1)
    }
  })

  useEffect(() => {
    let cancelled = false
    void listDiscussions({
      siteId: siteId || undefined,
      view,
      search: search || undefined,
      page,
      pageSize: 30,
    })
      .then((response) => {
        if (cancelled) return
        setItems(response.items)
        setTotalItems(response.totalItems)
        setTotalPages(response.totalPages)
        setListError(null)
      })
      .catch((error) => {
        if (!cancelled) setListError(getApiErrorMessage(error, 'Не удалось загрузить обсуждения.'))
      })
      .finally(() => {
        if (!cancelled) setListLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [listReloadKey, page, realtimeRevision, search, siteId, view])

  useEffect(() => {
    if (!selectedId) return
    let cancelled = false
    void getDiscussion(selectedId)
      .then((response) => {
        if (!cancelled) {
          setThread(response)
          setDetailError(null)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setDetailError({
            id: selectedId,
            message: getApiErrorMessage(error, 'Не удалось открыть обсуждение.'),
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [detailReloadKey, realtimeRevision, selectedId])

  function updateRoute(values: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams)
    Object.entries(values).forEach(([key, value]) => {
      if (value) next.set(key, value)
      else next.delete(key)
    })
    setSearchParams(next)
  }

  function selectDiscussion(rootCommentId: string) {
    updateRoute({ discussion: rootCommentId })
  }

  function changeView(nextView: DiscussionFilter) {
    setListLoading(true)
    setListError(null)
    updateRoute({ view: nextView === 'ALL' ? null : nextView, discussion: null, page: null })
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setListLoading(true)
    setListError(null)
    updateRoute({
      siteId: currentFilterDraft.siteId || null,
      search: currentFilterDraft.search.trim() || null,
      discussion: null,
      page: null,
    })
  }

  function retryList() {
    setListLoading(true)
    setListError(null)
    setListReloadKey((current) => current + 1)
  }

  function changeReplyDraft(content: string) {
    if (!selectedId) return
    const next = { rootCommentId: selectedId, content, operationId: null }
    setReplyDraft(next)
    writeReplyDraft(next)
    setReplyError(null)
    setReplyNotice(null)
  }

  async function submitReply(event: FormEvent) {
    event.preventDefault()
    if (!selectedId || replySendingId === selectedId) return
    const content = activeReplyDraft.content.trim()
    if (!content) {
      setReplyError({ rootCommentId: selectedId, message: 'Напишите ответ перед отправкой.' })
      replyInputRef.current?.focus()
      return
    }

    const operationId = activeReplyDraft.operationId ?? crypto.randomUUID()
    const pendingDraft = { rootCommentId: selectedId, content: activeReplyDraft.content, operationId }
    setReplyDraft(pendingDraft)
    writeReplyDraft(pendingDraft)
    setReplySendingId(selectedId)
    setReplyError(null)
    setReplyNotice(null)

    try {
      const response = await createOwnerReply(selectedId, { operationId, content })
      setThread((current) => {
        if (!current || current.summary.rootCommentId !== selectedId) return current
        const exists = current.messages.some((message) => message.id === response.message.id)
        return {
          summary: {
            ...current.summary,
            lastAuthor: response.message.author,
            lastMessage: response.message.content,
            lastActivityAt: response.message.createdAt,
            status: 'ACTIVE',
          },
          messages: exists ? current.messages : [...current.messages, response.message],
        }
      })
      setItems((current) => current.map((discussion) => discussion.rootCommentId === selectedId
        ? {
            ...discussion,
            lastAuthor: response.message.author,
            lastMessage: response.message.content,
            lastActivityAt: response.message.createdAt,
            status: 'ACTIVE',
          }
        : discussion))
      sessionStorage.removeItem(`${REPLY_DRAFT_PREFIX}${selectedId}`)
      setReplyDraft({ rootCommentId: selectedId, content: '', operationId: null })
      setReplyNotice({
        rootCommentId: selectedId,
        message: response.created ? 'Ответ опубликован.' : 'Ответ уже был опубликован ранее.',
      })
      setListReloadKey((current) => current + 1)
    } catch (error) {
      setReplyError({
        rootCommentId: selectedId,
        message: getApiErrorMessage(error, 'Не удалось опубликовать ответ. Текст сохранён — попробуйте ещё раз.'),
      })
      requestAnimationFrame(() => {
        const editor = replyInputRef.current
        if (editor?.dataset.rootCommentId === selectedId) editor.focus()
      })
    } finally {
      setReplySendingId((current) => current === selectedId ? null : current)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Рабочее пространство"
        title="Обсуждения"
        description="Читайте опубликованные ветки и возвращайтесь к тем, где посетитель ждёт ответа."
      />

      <section className="cc-card overflow-hidden" style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}>
        <form className="flex flex-col gap-3 border-b p-4 lg:flex-row lg:items-end" style={{ borderColor: 'var(--border)' }} onSubmit={applyFilters} role="search">
          <label className="min-w-0 flex-1 text-sm font-medium" style={{ color: 'var(--text)' }}>
            Поиск
            <span className="mt-1 flex items-center gap-2 rounded-lg border px-3" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}>
              <Search className="h-4 w-4 shrink-0" aria-hidden="true" />
              <input
                className="min-h-10 min-w-0 flex-1 bg-transparent text-sm outline-none"
                value={currentFilterDraft.search}
                maxLength={120}
                placeholder="Текст, страница или сайт"
                onChange={(event) => setFilterDraft({ ...currentFilterDraft, search: event.target.value })}
              />
            </span>
          </label>
          <label className="text-sm font-medium" style={{ color: 'var(--text)' }}>
            Сайт
            <select
              className="mt-1 min-h-10 w-full rounded-lg border px-3 text-sm lg:w-56"
              style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)', color: 'var(--text-h)' }}
              value={currentFilterDraft.siteId}
              onChange={(event) => setFilterDraft({ ...currentFilterDraft, siteId: event.target.value })}
            >
              <option value="">Все сайты</option>
              {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
            </select>
          </label>
          <button type="submit" className="cc-button-secondary min-h-10">
            <Filter className="h-4 w-4" aria-hidden="true" />
            Применить
          </button>
        </form>

        <div className="flex gap-2 overflow-x-auto border-b px-4 py-3" style={{ borderColor: 'var(--border)' }} aria-label="Фильтр обсуждений">
          {views.map((item) => (
            <button
              key={item.value}
              type="button"
              className={view === item.value ? 'cc-button-primary whitespace-nowrap' : 'cc-button-secondary whitespace-nowrap'}
              aria-pressed={view === item.value}
              onClick={() => changeView(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>

        {listError && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3 text-sm" style={{ borderColor: 'var(--status-rejected-border)', backgroundColor: 'var(--status-rejected-bg)', color: 'var(--status-rejected-accent)' }} role="alert">
            <span>{listError}</span>
            <button type="button" className="cc-button-secondary" onClick={retryList}>Повторить</button>
          </div>
        )}

        <div className="grid min-h-[34rem] xl:grid-cols-[360px_minmax(0,1fr)]">
          <section className={`${selectedId ? 'hidden xl:flex' : 'flex'} min-w-0 flex-col border-r`} style={{ borderColor: 'var(--border)' }} aria-label="Список обсуждений">
            {listLoading && items.length === 0 ? <ListSkeleton /> : null}
            {!listLoading && items.length === 0 && !listError ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 py-16 text-center">
                <MessageSquareText className="h-9 w-9" style={{ color: 'var(--text)' }} aria-hidden="true" />
                <div>
                  <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>Комментариев пока нет</h2>
                  <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>Проверьте установку виджета и опубликуйте первый реальный комментарий.</p>
                </div>
                <Link to="/sites" className="cc-button-secondary">Проверить установку</Link>
              </div>
            ) : null}
            {items.length > 0 && (
              <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
                {items.map((discussion) => {
                  const active = discussion.rootCommentId === selectedId
                  return (
                    <button
                      key={discussion.rootCommentId}
                      type="button"
                      className="group relative w-full px-4 py-4 text-left transition-colors"
                      style={{ backgroundColor: active ? 'var(--accent-bg)' : 'transparent' }}
                      aria-current={active ? 'true' : undefined}
                      aria-label={`Открыть обсуждение: ${pageLabel(discussion)}`}
                      onClick={() => selectDiscussion(discussion.rootCommentId)}
                    >
                      {discussion.unread && <span className="absolute left-1 top-5 h-2 w-2 rounded-full" style={{ backgroundColor: 'var(--accent)' }} aria-hidden="true" />}
                      <div className="flex items-start justify-between gap-3">
                        <span className="truncate text-xs font-semibold" style={{ color: 'var(--text)' }}>{discussion.siteName}</span>
                        <time className="shrink-0 text-xs" style={{ color: 'var(--text)' }}>{formatDateTime(discussion.lastActivityAt)}</time>
                      </div>
                      <div className="mt-1 flex items-center gap-2">
                        {discussion.pinned && <Pin className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--accent)' }} aria-label="Закреплено" />}
                        <strong className="truncate text-sm" style={{ color: 'var(--text-h)' }}>{pageLabel(discussion)}</strong>
                      </div>
                      <p className="mt-2 line-clamp-2 text-sm leading-5" style={{ color: 'var(--text)' }}>
                        <span className="font-medium">{discussion.lastAuthor.displayName}:</span> {discussion.lastMessage}
                      </p>
                      <div className="mt-3 flex items-center justify-between gap-2">
                        <Badge tone={statusTone(discussion)}>{statusLabel(discussion)}</Badge>
                        <span className="text-xs" style={{ color: 'var(--text)' }}>{discussion.replyCount} ответов</span>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
            {items.length > 0 && (
              <div className="mt-auto border-t p-4" style={{ borderColor: 'var(--border)' }}>
                <PaginationControls
                  page={page}
                  totalPages={totalPages}
                  totalItems={totalItems}
                  onPageChange={(nextPage) => {
                    setListLoading(true)
                    setListError(null)
                    updateRoute({ page: nextPage > 1 ? String(nextPage) : null, discussion: null })
                  }}
                />
              </div>
            )}
          </section>

          <section className={`${selectedId ? 'flex' : 'hidden xl:flex'} min-w-0 flex-col`} aria-label="Открытое обсуждение">
            {!selectedId && (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center">
                <MessageSquareText className="h-9 w-9" style={{ color: 'var(--text)' }} aria-hidden="true" />
                <h2 className="font-semibold" style={{ color: 'var(--text-h)' }}>Выберите обсуждение</h2>
                <p className="max-w-md text-sm" style={{ color: 'var(--text)' }}>Здесь появится вся ветка без сигналов автомодерации и лишних управляющих элементов.</p>
              </div>
            )}
            {selectedId && (
              <>
                <header className="border-b px-4 py-4 sm:px-6" style={{ borderColor: 'var(--border)' }}>
                  <button type="button" className="cc-button-secondary mb-4 xl:hidden" onClick={() => updateRoute({ discussion: null })}>
                    <Undo2 className="h-4 w-4" aria-hidden="true" />
                    К списку
                  </button>
                  {selectedSummary && (
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <p className="text-xs font-semibold" style={{ color: 'var(--text)' }}>{selectedSummary.siteName}</p>
                        <h2 className="mt-1 truncate text-lg font-semibold" style={{ color: 'var(--text-h)' }}>{pageLabel(selectedSummary)}</h2>
                        <p className="mt-1 truncate text-sm" style={{ color: 'var(--text)' }}>{selectedSummary.pageUrl}</p>
                      </div>
                      <div className="flex shrink-0 flex-wrap gap-2">
                        <a className="cc-button-secondary" href={selectedSummary.pageUrl} target="_blank" rel="noreferrer">
                          <ExternalLink className="h-4 w-4" aria-hidden="true" />
                          Открыть страницу
                        </a>
                        <Link className="cc-button-secondary" to={`/moderation?view=history&comment=${selectedSummary.rootCommentId}`}>
                          К решению
                        </Link>
                      </div>
                    </div>
                  )}
                </header>

                <div className="min-h-0 flex-1 overflow-y-auto px-4 py-6 sm:px-6">
                  {!activeThread && !activeDetailError ? (
                    <div className="space-y-6" aria-label="Загрузка ветки">
                      {[1, 2].map((item) => <div key={item} className="h-24 animate-pulse rounded-lg" style={{ backgroundColor: 'var(--surface-muted)' }} />)}
                    </div>
                  ) : null}
                  <AsyncState loading={false} error={activeDetailError} empty={false}>
                    {activeThread && (
                      <ol className="mx-auto max-w-3xl space-y-6" aria-label="Сообщения обсуждения">
                        {activeThread.messages.map((message, index) => (
                          <li key={message.id} className={index === 0 ? '' : 'border-l-2 pl-5 sm:pl-7'} style={{ borderColor: 'var(--border)' }}>
                            <article>
                              <header className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                                <strong style={{ color: 'var(--text-h)' }}>{message.author.displayName}</strong>
                                {message.author.owner && <Badge tone="accent">Автор сайта</Badge>}
                                {message.pinned && <Badge tone="muted">Закреплено</Badge>}
                                <time style={{ color: 'var(--text)' }}>{formatDateTime(message.createdAt)}</time>
                              </header>
                              <p className="mt-2 whitespace-pre-wrap break-words text-[15px] leading-6" style={{ color: 'var(--text-h)' }}>{message.content}</p>
                            </article>
                          </li>
                        ))}
                      </ol>
                    )}
                  </AsyncState>
                  {activeDetailError && (
                    <div className="mt-4 text-center">
                      <button
                        type="button"
                        className="cc-button-secondary"
                        onClick={() => {
                          setDetailError(null)
                          setDetailReloadKey((current) => current + 1)
                        }}
                      >
                        Повторить открытие
                      </button>
                    </div>
                  )}
                </div>
                {activeThread && !activeDetailError && (
                  <form className="border-t px-4 py-4 sm:px-6" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }} onSubmit={submitReply}>
                    <div className="mx-auto max-w-3xl">
                      <label className="block text-sm font-semibold" htmlFor="owner-reply-content" style={{ color: 'var(--text-h)' }}>
                        Ответить как владелец сайта
                      </label>
                      <textarea
                        ref={replyInputRef}
                        data-root-comment-id={selectedId}
                        id="owner-reply-content"
                        className="cc-field mt-2 min-h-24 resize-y"
                        value={activeReplyDraft.content}
                        maxLength={5000}
                        placeholder="Напишите ответ посетителю…"
                        disabled={replySending}
                        aria-describedby="owner-reply-help owner-reply-feedback"
                        onChange={(event) => changeReplyDraft(event.target.value)}
                      />
                      <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0 text-sm" id="owner-reply-feedback">
                          {activeReplyError && <p role="alert" style={{ color: 'var(--status-rejected-accent)' }}>{activeReplyError}</p>}
                          <p className="sr-only" aria-live="polite">{activeReplyNotice}</p>
                          {!activeReplyError && (
                            <p id="owner-reply-help" style={{ color: 'var(--text)' }}>
                              Ответ сразу появится в опубликованной ветке. {activeReplyDraft.content.length}/5000
                            </p>
                          )}
                        </div>
                        <button
                          type="submit"
                          className="cc-button-primary min-h-10"
                          disabled={replySending || !activeReplyDraft.content.trim()}
                        >
                          {replySending
                            ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                            : <Send className="h-4 w-4" aria-hidden="true" />}
                          {replySending ? 'Публикуем…' : 'Ответить'}
                        </button>
                      </div>
                    </div>
                  </form>
                )}
              </>
            )}
          </section>
        </div>
      </section>
    </div>
  )
}

export default Comments

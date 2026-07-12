import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Bell, BellRing, CheckCheck, ExternalLink, LoaderCircle, WifiOff, X } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../api/notifications'
import { useAuthStore } from '../../store'
import type { CommentStatus, NewCommentNotification, OwnerNotification } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { useRealtimeConnectionStatus, useRealtimeEvent } from '../realtime/useRealtime'

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'на модерации',
  APPROVED: 'одобрен',
  REJECTED: 'отклонён',
  HIDDEN: 'скрыт',
  SPAM: 'спам',
}

function fromRealtime(notification: NewCommentNotification): OwnerNotification {
  return {
    id: notification.notificationId,
    commentId: notification.commentId,
    siteId: notification.siteId,
    siteName: notification.siteName,
    pageId: notification.pageId,
    pageUrl: notification.pageUrl,
    parentId: notification.parentId,
    authorEmail: notification.authorEmail,
    contentPreview: notification.contentPreview,
    status: notification.status,
    readAt: null,
    createdAt: notification.createdAt,
  }
}

function moderationView(status: CommentStatus): 'pending' | 'spam' | 'history' {
  if (status === 'SPAM') return 'spam'
  if (status === 'PENDING') return 'pending'
  return 'history'
}

export function RealtimeNotifications() {
  const authStatus = useAuthStore((state) => state.status)
  const connectionStatus = useRealtimeConnectionStatus()
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<OwnerNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isOpen, setIsOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [markingAll, setMarkingAll] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const knownIdsRef = useRef(new Set<string>())
  const panelId = useId()

  useEffect(() => {
    if (authStatus !== 'authenticated') return
    let cancelled = false
    Promise.all([listNotifications(), getUnreadNotificationCount()])
      .then(([response, count]) => {
        if (cancelled) return
        setNotifications((current) => {
          const mergedById = new Map(response.items.map((notification) => [notification.id, notification]))
          current.forEach((notification) => {
            if (!mergedById.has(notification.id)) mergedById.set(notification.id, notification)
          })
          const merged = [...mergedById.values()]
            .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
            .slice(0, 20)
          knownIdsRef.current = new Set(merged.map((notification) => notification.id))
          return merged
        })
        setUnreadCount((current) => Math.max(current, count))
        setLoadError(null)
      })
      .catch((error) => {
        if (!cancelled) setLoadError(getApiErrorMessage(error, 'Не удалось загрузить уведомления.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [authStatus])

  useRealtimeEvent((event) => {
    if (event.type !== 'comment.created') return
    const incoming = fromRealtime(event.payload)
    if (knownIdsRef.current.has(incoming.id)) return
    knownIdsRef.current.add(incoming.id)
    setNotifications((current) => [incoming, ...current].slice(0, 20))
    setUnreadCount((current) => current + 1)
    toast.success(`Новый комментарий: ${incoming.siteName}`)
  })

  function closePanel() {
    setIsOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  useEffect(() => {
    if (!isOpen) return
    panelRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closePanel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [isOpen])

  async function openNotification(notification: OwnerNotification) {
    const previousReadAt = notification.readAt
    if (!previousReadAt) {
      const optimisticReadAt = new Date().toISOString()
      setNotifications((current) => current.map((item) => (
        item.id === notification.id ? { ...item, readAt: optimisticReadAt } : item
      )))
      setUnreadCount((current) => Math.max(0, current - 1))
      try {
        const updated = await markNotificationRead(notification.id)
        setNotifications((current) => current.map((item) => item.id === updated.id ? updated : item))
      } catch (error) {
        setNotifications((current) => current.map((item) => (
          item.id === notification.id ? { ...item, readAt: previousReadAt } : item
        )))
        setUnreadCount((current) => current + 1)
        toast.error(getApiErrorMessage(error, 'Не удалось отметить уведомление прочитанным.'))
        return
      }
    }
    closePanel()
    navigate(`/moderation?comment=${notification.commentId}&view=${moderationView(notification.status)}`)
  }

  async function markAllRead() {
    if (unreadCount === 0) return
    const previous = notifications
    const previousCount = unreadCount
    const readAt = new Date().toISOString()
    setMarkingAll(true)
    setNotifications((current) => current.map((notification) => ({ ...notification, readAt: notification.readAt ?? readAt })))
    setUnreadCount(0)
    try {
      await markAllNotificationsRead()
    } catch (error) {
      setNotifications(previous)
      setUnreadCount(previousCount)
      toast.error(getApiErrorMessage(error, 'Не удалось отметить уведомления прочитанными.'))
    } finally {
      setMarkingAll(false)
    }
  }

  if (authStatus !== 'authenticated') return null

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="relative inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border transition"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
        aria-label="Уведомления"
        aria-expanded={isOpen}
        aria-controls={panelId}
        onClick={() => setIsOpen((current) => !current)}
      >
        {unreadCount > 0 ? <BellRing className="h-5 w-5" /> : <Bell className="h-5 w-5" />}
        {unreadCount > 0 && (
          <span className="absolute -right-2 -top-2 min-w-5 rounded-full px-1.5 text-xs font-bold text-white" style={{ backgroundColor: 'var(--status-rejected-accent)' }}>
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && createPortal(<>
        <button type="button" className="fixed inset-0 z-40 cursor-default bg-slate-950/10" aria-label="Закрыть уведомления" onClick={closePanel} />
        <section
          ref={panelRef}
          id={panelId}
          role="dialog"
          aria-modal="true"
          aria-label="Центр уведомлений"
          tabIndex={-1}
          className="fixed inset-x-4 top-[4.5rem] z-50 max-h-[calc(100vh-5.5rem)] overflow-hidden rounded-lg border shadow-xl sm:left-auto sm:right-4 sm:w-[400px] lg:right-6"
          style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        >
          <div className="flex items-center justify-between gap-3 border-b p-4" style={{ borderColor: 'var(--border)' }}>
            <div>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>Уведомления</p>
              <p className="mt-1 text-xs" style={{ color: 'var(--text)' }}>Непрочитанных: {unreadCount}</p>
            </div>
            <div className="flex items-center gap-2">
              <button type="button" className="cc-button-secondary !px-2.5 !py-1.5 text-xs" disabled={markingAll || unreadCount === 0} onClick={() => void markAllRead()}>
                {markingAll ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : <CheckCheck className="h-3.5 w-3.5" />}
                Прочитать все
              </button>
              <button type="button" className="cc-button-secondary !p-2" aria-label="Закрыть уведомления" onClick={closePanel}>
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div className="max-h-[min(520px,calc(100vh-10rem))] overflow-y-auto p-2">
            {loading && <div className="flex items-center gap-2 p-4 text-sm"><LoaderCircle className="h-4 w-4 animate-spin" /> Загружаем уведомления…</div>}
            {loadError && <div className="rounded-lg border p-4 text-sm" style={{ borderColor: 'var(--status-rejected-border)', color: 'var(--status-rejected-accent)' }}>{loadError}</div>}
            {!loading && !loadError && notifications.length === 0 && (
              <div className="rounded-lg border border-dashed p-4 text-sm" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
                Уведомлений пока нет. Новые комментарии появятся здесь и сохранятся после перезагрузки.
              </div>
            )}
            {notifications.map((notification) => (
              <button
                key={notification.id}
                type="button"
                className="relative w-full rounded-lg p-3 text-left transition-colors hover:bg-slate-500/10 focus-visible:outline-none"
                style={{ backgroundColor: notification.readAt ? 'transparent' : 'var(--accent-bg)' }}
                onClick={() => void openNotification(notification)}
              >
                {!notification.readAt && <span className="absolute left-1 top-4 h-2 w-2 rounded-full" style={{ backgroundColor: 'var(--accent)' }} aria-label="Не прочитано" />}
                <div className="flex items-start justify-between gap-3 pl-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-h)' }}>{notification.siteName}</p>
                    <p className="mt-1 truncate text-xs" style={{ color: 'var(--text)' }}>{notification.pageUrl}</p>
                  </div>
                  <ExternalLink className="mt-0.5 h-4 w-4 shrink-0" style={{ color: 'var(--accent)' }} />
                </div>
                <p className="mt-2 line-clamp-2 pl-2 text-sm" style={{ color: 'var(--text-h)' }}>{notification.contentPreview}</p>
                <p className="mt-2 pl-2 text-xs" style={{ color: 'var(--text)' }}>
                  {notification.authorEmail ?? 'Гость'} · {statusLabels[notification.status]} · {formatDateTime(notification.createdAt)}
                </p>
              </button>
            ))}
          </div>

          {connectionStatus !== 'connected' && (
            <div className="flex items-center gap-2 border-t px-4 py-2 text-xs" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
              <WifiOff className="h-3.5 w-3.5" />
              {connectionStatus === 'connecting' ? 'Восстанавливаем live-обновления…' : 'Live-обновления временно недоступны'}
            </div>
          )}
        </section>
      </>, document.body)}
    </>
  )
}

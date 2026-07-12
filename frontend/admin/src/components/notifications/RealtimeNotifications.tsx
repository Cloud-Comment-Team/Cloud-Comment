import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Bell, BellRing, Circle, ExternalLink, WifiOff, X } from 'lucide-react'

import { useAuthStore } from '../../store'
import type { CommentStatus, NewCommentNotification } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { useRealtimeConnectionStatus, useRealtimeEvent } from '../realtime/useRealtime'

interface CommentNotification extends NewCommentNotification {
  receivedAt: string
}

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'на модерации',
  APPROVED: 'одобрен',
  REJECTED: 'отклонен',
  HIDDEN: 'скрыт',
  SPAM: 'спам',
}

export function RealtimeNotifications() {
  const authStatus = useAuthStore((state) => state.status)
  const connectionStatus = useRealtimeConnectionStatus()
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<CommentNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isOpen, setIsOpen] = useState(false)

  const latestNotifications = useMemo(() => notifications.slice(0, 6), [notifications])

  useRealtimeEvent((event) => {
    if (event.type !== 'comment.created') {
      return
    }

    const notification: CommentNotification = {
      ...event.payload,
      receivedAt: event.sentAt ?? new Date().toISOString(),
    }
    setNotifications((current) => [notification, ...current].slice(0, 20))
    setUnreadCount((current) => current + 1)
    toast.success(`Новый комментарий: ${notification.siteName}`)
  })

  function openPanel() {
    setIsOpen((current) => !current)
    setUnreadCount(0)
  }

  function openModeration() {
    setIsOpen(false)
    setUnreadCount(0)
    navigate('/moderation')
  }

  if (authStatus !== 'authenticated') {
    return null
  }

  const hasNotifications = latestNotifications.length > 0
  const connected = connectionStatus === 'connected'

  return (
    <div className="fixed right-4 top-20 z-50 lg:right-6 lg:top-6">
      <button
        type="button"
        className="relative inline-flex h-11 w-11 items-center justify-center rounded-xl border shadow-lg transition hover:-translate-y-0.5"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
        aria-label="Уведомления"
        onClick={openPanel}
      >
        {unreadCount > 0 ? <BellRing className="h-5 w-5" /> : <Bell className="h-5 w-5" />}
        <span
          className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border"
          style={{
            backgroundColor: connected ? 'var(--accent)' : 'var(--status-rejected-accent)',
            borderColor: 'var(--surface)',
          }}
        />
        {unreadCount > 0 && (
          <span
            className="absolute -right-2 -top-2 min-w-5 rounded-full px-1.5 text-xs font-bold text-white"
            style={{ backgroundColor: 'var(--status-rejected-accent)' }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          className="mt-3 w-[min(360px,calc(100vw-2rem))] overflow-hidden rounded-xl border shadow-2xl"
          style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        >
          <div className="flex items-center justify-between gap-3 border-b p-4" style={{ borderColor: 'var(--border)' }}>
            <div>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                Уведомления
              </p>
              <div className="mt-1 flex items-center gap-1.5 text-xs" style={{ color: 'var(--text)' }}>
                {connected ? <Circle className="h-2.5 w-2.5 fill-current" /> : <WifiOff className="h-3.5 w-3.5" />}
                {connectionStatus === 'connected' && 'Подключено'}
                {connectionStatus === 'connecting' && 'Подключаемся...'}
                {connectionStatus === 'disconnected' && 'Нет realtime-соединения'}
              </div>
            </div>
            <button
              type="button"
              className="inline-flex h-8 w-8 items-center justify-center rounded-lg border"
              style={{ borderColor: 'var(--border)', color: 'var(--text)' }}
              aria-label="Закрыть уведомления"
              onClick={() => setIsOpen(false)}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="max-h-[420px] overflow-y-auto p-2">
            {!hasNotifications && (
              <div className="rounded-lg border border-dashed p-4 text-sm" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
                Новых комментариев пока нет. Когда они появятся в виджете, владелец увидит их здесь без обновления страницы.
              </div>
            )}

            {latestNotifications.map((notification) => (
              <button
                key={`${notification.commentId}-${notification.receivedAt}`}
                type="button"
                className="w-full rounded-lg p-3 text-left transition hover:-translate-y-0.5"
                style={{ backgroundColor: 'var(--surface-2)' }}
                onClick={openModeration}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                      {notification.siteName}
                    </p>
                    <p className="mt-1 truncate text-xs" style={{ color: 'var(--text)' }}>
                      {notification.pageUrl}
                    </p>
                  </div>
                  <ExternalLink className="mt-0.5 h-4 w-4 shrink-0" style={{ color: 'var(--accent)' }} />
                </div>
                <p className="mt-2 line-clamp-2 text-sm" style={{ color: 'var(--text-h)' }}>
                  {notification.contentPreview}
                </p>
                <p className="mt-2 text-xs" style={{ color: 'var(--text)' }}>
                  {notification.authorEmail ?? 'Гость'} · {statusLabels[notification.status]} · {formatDateTime(notification.createdAt)}
                </p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

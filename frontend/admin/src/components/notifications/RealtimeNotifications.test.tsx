import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { NewCommentNotification, OwnerNotification, RealtimeEvent } from '../../types/api'
import { RealtimeNotifications } from './RealtimeNotifications'

const notificationApi = vi.hoisted(() => ({
  listNotifications: vi.fn(),
  getUnreadNotificationCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const realtime = vi.hoisted(() => ({
  handler: null as ((event: RealtimeEvent) => void) | null,
}))

vi.mock('../../api/notifications', () => notificationApi)
vi.mock('../../store', () => ({
  useAuthStore: (selector: (state: { status: string }) => unknown) => selector({ status: 'authenticated' }),
}))
vi.mock('../realtime/useRealtime', () => ({
  useRealtimeConnectionStatus: () => 'connected',
  useRealtimeEvent: (handler: (event: RealtimeEvent) => void) => {
    realtime.handler = handler
  },
}))

const stored: OwnerNotification = {
  id: '00000000-0000-0000-0000-000000000001',
  commentId: '00000000-0000-0000-0000-000000000011',
  siteId: '00000000-0000-0000-0000-000000000021',
  siteName: 'Сайт уведомлений',
  pageId: '00000000-0000-0000-0000-000000000031',
  pageUrl: 'https://example.com/article',
  parentId: null,
  authorEmail: 'author@example.com',
  contentPreview: 'Постоянное уведомление',
  status: 'PENDING',
  readAt: null,
  createdAt: '2026-07-12T12:00:00Z',
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}</output>
}

function renderNotifications() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <RealtimeNotifications />
      <LocationProbe />
    </MemoryRouter>,
  )
}

describe('постоянный центр уведомлений', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtime.handler = null
    notificationApi.listNotifications.mockResolvedValue({
      items: [stored], page: 1, pageSize: 20, totalItems: 1, totalPages: 1,
    })
    notificationApi.getUnreadNotificationCount.mockResolvedValue(1)
    notificationApi.markNotificationRead.mockResolvedValue({ ...stored, readAt: '2026-07-12T12:01:00Z' })
    notificationApi.markAllNotificationsRead.mockResolvedValue(undefined)
  })

  it('загружает сохранённый список и открывает конкретный комментарий', async () => {
    renderNotifications()
    await waitFor(() => expect(notificationApi.listNotifications).toHaveBeenCalledOnce())
    fireEvent.click(screen.getByRole('button', { name: 'Уведомления' }))
    expect(await screen.findByText('Постоянное уведомление')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Постоянное уведомление').closest('button')!)

    await waitFor(() => expect(notificationApi.markNotificationRead).toHaveBeenCalledWith(stored.id))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(
      `/moderation?comment=${stored.commentId}&view=pending`,
    ))
  })

  it('схлопывает повторную realtime-доставку по notificationId', async () => {
    renderNotifications()
    await waitFor(() => expect(realtime.handler).not.toBeNull())
    const payload: NewCommentNotification = {
      notificationId: '00000000-0000-0000-0000-000000000002',
      commentId: '00000000-0000-0000-0000-000000000012',
      siteId: stored.siteId,
      siteName: stored.siteName,
      pageId: stored.pageId,
      pageUrl: stored.pageUrl,
      parentId: null,
      authorEmail: 'new@example.com',
      contentPreview: 'Новое live-уведомление',
      status: 'SPAM',
      createdAt: '2026-07-12T12:02:00Z',
    }
    const event: RealtimeEvent = { type: 'comment.created', sentAt: payload.createdAt, payload }

    act(() => {
      realtime.handler!(event)
      realtime.handler!(event)
    })
    fireEvent.click(screen.getByRole('button', { name: 'Уведомления' }))

    expect(screen.getAllByText('Новое live-уведомление')).toHaveLength(1)
    expect(screen.getByText('Непрочитанных: 2')).toBeInTheDocument()
  })

  it('не теряет realtime-событие во время первоначальной загрузки', async () => {
    let resolveList!: (value: { items: OwnerNotification[]; page: number; pageSize: number; totalItems: number; totalPages: number }) => void
    notificationApi.listNotifications.mockImplementation(() => new Promise((resolve) => {
      resolveList = resolve
    }))
    notificationApi.getUnreadNotificationCount.mockResolvedValue(0)
    renderNotifications()
    await waitFor(() => expect(realtime.handler).not.toBeNull())

    const payload: NewCommentNotification = {
      notificationId: '00000000-0000-0000-0000-000000000003',
      commentId: '00000000-0000-0000-0000-000000000013',
      siteId: stored.siteId,
      siteName: stored.siteName,
      pageId: stored.pageId,
      pageUrl: stored.pageUrl,
      parentId: null,
      authorEmail: 'race@example.com',
      contentPreview: 'Уведомление во время загрузки',
      status: 'PENDING',
      createdAt: '2026-07-12T12:03:00Z',
    }
    act(() => realtime.handler!({ type: 'comment.created', sentAt: payload.createdAt, payload }))
    act(() => resolveList({ items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 }))

    fireEvent.click(screen.getByRole('button', { name: 'Уведомления' }))
    expect(await screen.findByText('Уведомление во время загрузки')).toBeInTheDocument()
    expect(screen.getByText('Непрочитанных: 1')).toBeInTheDocument()
  })

  it('отмечает все уведомления прочитанными оптимистично', async () => {
    renderNotifications()
    await waitFor(() => expect(notificationApi.getUnreadNotificationCount).toHaveBeenCalledOnce())
    fireEvent.click(screen.getByRole('button', { name: 'Уведомления' }))
    fireEvent.click(screen.getByRole('button', { name: 'Прочитать все' }))

    expect(screen.getByText('Непрочитанных: 0')).toBeInTheDocument()
    await waitFor(() => expect(notificationApi.markAllNotificationsRead).toHaveBeenCalledOnce())
  })
})

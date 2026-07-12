import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Comment } from '../../types/api'
import Moderation from './Moderation'

const moderationApi = vi.hoisted(() => ({
  listComments: vi.fn(),
  getComment: vi.fn(),
  getModerationCounts: vi.fn(),
  applyBulkModerationAction: vi.fn(),
  applyModerationAction: vi.fn(),
  undoModerationAction: vi.fn(),
  updateCommentFlags: vi.fn(),
}))

vi.mock('../../api/moderation', () => moderationApi)
vi.mock('../../api/sites', () => ({ listAllSites: vi.fn().mockResolvedValue([]) }))
vi.mock('../../components/realtime/useRealtime', () => ({ useRealtimeEvent: vi.fn() }))

const comments: Comment[] = [
  {
    id: '00000000-0000-0000-0000-000000000011',
    siteId: '00000000-0000-0000-0000-000000000021',
    pageId: '00000000-0000-0000-0000-000000000031',
    pageUrl: 'https://example.com/article',
    parentId: null,
    parent: null,
    author: { id: null, email: 'anna@example.com', displayName: 'Анна' },
    content: 'Первый комментарий',
    status: 'PENDING',
    moderationReason: null,
    pinned: false,
    favorite: false,
    priority: 'HIGH',
    priorityScore: 90,
    priorityReasons: ['Ожидает решения'],
    createdAt: '2026-07-12T12:00:00Z',
    updatedAt: '2026-07-12T12:00:00Z',
    replies: [],
  },
  {
    id: '00000000-0000-0000-0000-000000000012',
    siteId: '00000000-0000-0000-0000-000000000021',
    pageId: '00000000-0000-0000-0000-000000000031',
    pageUrl: 'https://example.com/article',
    parentId: null,
    parent: null,
    author: { id: null, email: 'boris@example.com', displayName: 'Борис' },
    content: 'Второй комментарий',
    status: 'SPAM',
    moderationReason: 'Найдена подозрительная ссылка',
    pinned: false,
    favorite: false,
    priority: 'URGENT',
    priorityScore: 140,
    priorityReasons: ['Спам'],
    createdAt: '2026-07-12T12:01:00Z',
    updatedAt: '2026-07-12T12:01:00Z',
    replies: [],
  },
]

describe('страница модерации', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    moderationApi.listComments.mockResolvedValue({
      items: comments,
      page: 1,
      pageSize: 30,
      totalItems: 2,
      totalPages: 1,
    })
    moderationApi.getModerationCounts.mockResolvedValue({
      statuses: { PENDING: 1, APPROVED: 0, REJECTED: 0, HIDDEN: 0, SPAM: 1 },
      requiringDecision: 2,
    })
    moderationApi.getComment.mockResolvedValue(comments[0])
    moderationApi.applyBulkModerationAction.mockResolvedValue({
      items: comments.map((comment) => ({
        commentId: comment.id,
        success: true,
        action: null,
        errorCode: null,
        message: null,
      })),
    })
  })

  it('по умолчанию запрашивает комментарии на модерации и спам', async () => {
    render(<MemoryRouter initialEntries={['/moderation']}><Moderation /></MemoryRouter>)

    await screen.findByText('Первый комментарий')
    expect(moderationApi.listComments).toHaveBeenCalledWith(expect.objectContaining({
      statuses: ['PENDING', 'SPAM'],
      sortBy: 'SMART',
    }))
    expect(screen.getByText('Требуют решения: 2')).toBeInTheDocument()
  })

  it('применяет одно массовое действие к выбранным строкам', async () => {
    render(<MemoryRouter initialEntries={['/moderation?view=pending']}><Moderation /></MemoryRouter>)
    await screen.findByText('Первый комментарий')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Выбрать комментарий Анна' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Выбрать комментарий Борис' }))
    fireEvent.click(screen.getByRole('button', { name: /Одобрить/ }))

    await waitFor(() => expect(moderationApi.applyBulkModerationAction).toHaveBeenCalledWith(expect.objectContaining({
      commentIds: comments.map((comment) => comment.id),
      action: 'APPROVE',
    })))
  })

  it('открывает комментарий по прямой ссылке, даже если его нет на текущей странице', async () => {
    moderationApi.listComments.mockResolvedValue({ items: [], page: 1, pageSize: 30, totalItems: 0, totalPages: 0 })
    render(
      <MemoryRouter initialEntries={[`/moderation?view=history&comment=${comments[0].id}`]}>
        <Moderation />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('dialog', { name: 'Комментарий' })).toBeInTheDocument()
    expect(moderationApi.getComment).toHaveBeenCalledWith(comments[0].id)
    expect(screen.getByText('Первый комментарий')).toBeInTheDocument()
  })
})

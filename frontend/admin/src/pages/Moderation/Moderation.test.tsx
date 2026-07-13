import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Comment } from '../../types/api'
import Moderation from './Moderation'

const moderationApi = vi.hoisted(() => ({
  listComments: vi.fn(),
  getComment: vi.fn(),
  getModerationCounts: vi.fn(),
  applyBulkModerationAction: vi.fn(),
  applyModerationAction: vi.fn(),
  undoBulkModerationOperation: vi.fn(),
  undoModerationAction: vi.fn(),
  updateCommentFlags: vi.fn(),
  setAutoModerationFeedback: vi.fn(),
  deleteAutoModerationFeedback: vi.fn(),
}))

vi.mock('../../api/moderation', () => moderationApi)
vi.mock('../../api/sites', () => ({ listAllSites: vi.fn().mockResolvedValue([]) }))
vi.mock('../../components/realtime/useRealtime', () => ({ useRealtimeEvent: vi.fn() }))

function CurrentLocation() {
  const location = useLocation()
  return <output data-testid="current-location">{location.pathname}{location.search}</output>
}

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
    autoModeration: null,
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
    autoModeration: {
      policyVersionId: '00000000-0000-0000-0000-000000000137',
      policyVersion: 3,
      executionMode: 'LIVE',
      decision: 'SPAM',
      score: 130,
      reason: 'Найдены признаки спама',
      signals: [{ code: 'SPAM_PHRASE', score: 55 }],
      evaluatedAt: '2026-07-12T12:01:00Z',
      feedback: null,
    },
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
    moderationApi.undoBulkModerationOperation.mockResolvedValue({ items: [] })
    moderationApi.setAutoModerationFeedback.mockResolvedValue({
      type: 'FALSE_POSITIVE',
      createdAt: '2026-07-12T12:02:00Z',
    })
    moderationApi.deleteAutoModerationFeedback.mockResolvedValue(undefined)
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

  it('применяет фильтры сайта и страницы из прямой ссылки', async () => {
    const pageUrl = 'https://example.com/article?a=1'
    render(
      <MemoryRouter initialEntries={[`/moderation?view=pending&status=PENDING&siteId=${comments[0].siteId}&pageUrl=${encodeURIComponent(pageUrl)}`]}>
        <Moderation />
        <CurrentLocation />
      </MemoryRouter>,
    )

    await screen.findByText('Первый комментарий')
    expect(moderationApi.listComments).toHaveBeenCalledWith(expect.objectContaining({
      siteId: comments[0].siteId,
      pageUrl,
      status: 'PENDING',
      statuses: undefined,
    }))
    expect(screen.getByText('Точный фильтр:')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /^Спам \(1\)$/ }))
    await waitFor(() => expect(moderationApi.listComments).toHaveBeenCalledWith(expect.objectContaining({
      status: undefined,
      statuses: ['SPAM'],
    })))
    expect(screen.getByTestId('current-location')).toHaveTextContent(
      `/moderation?view=spam&siteId=${comments[0].siteId}&pageUrl=${encodeURIComponent(pageUrl)}`,
    )
  })

  it.each([
    {
      name: 'глобальная ссылка очищает все сохранённые фильтры',
      href: '/moderation?view=pending&source=analytics&status=PENDING',
      expectedSiteId: undefined,
    },
    {
      name: 'ссылка сайта сохраняет только сайт и очищает старую страницу',
      href: `/moderation?view=pending&source=analytics&status=PENDING&siteId=${comments[0].siteId}`,
      expectedSiteId: comments[0].siteId,
    },
  ])('$name', async ({ href, expectedSiteId }) => {
    localStorage.setItem('cloud-comment:moderation-filters:v2', JSON.stringify({
      siteId: '00000000-0000-0000-0000-000000000099',
      pageUrl: 'https://saved.example.test/old-page',
      search: 'скрывающий запрос',
      favorite: true,
    }))

    render(<MemoryRouter initialEntries={[href]}><Moderation /></MemoryRouter>)

    await screen.findByText('Первый комментарий')
    expect(moderationApi.listComments).toHaveBeenCalledWith({
      siteId: expectedSiteId,
      pageUrl: undefined,
      search: undefined,
      favorite: undefined,
      status: 'PENDING',
      statuses: undefined,
      sortBy: 'SMART',
      sortOrder: 'DESC',
      page: 1,
      pageSize: 30,
    })
  })

  it('после ручного применения фильтров перестаёт считать аналитическую ссылку авторитетной', async () => {
    render(
      <MemoryRouter initialEntries={['/moderation?view=pending&source=analytics&status=PENDING']}>
        <Moderation />
        <CurrentLocation />
      </MemoryRouter>,
    )

    await screen.findByText('Первый комментарий')
    fireEvent.change(screen.getByLabelText('Поиск'), { target: { value: 'важный текст' } })
    fireEvent.click(screen.getByRole('button', { name: 'Применить' }))

    await waitFor(() => expect(moderationApi.listComments).toHaveBeenLastCalledWith(expect.objectContaining({
      search: 'важный текст',
      status: 'PENDING',
    })))
    expect(screen.getByTestId('current-location')).toHaveTextContent('/moderation?view=pending&status=PENDING')
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

  it('отменяет массовую операцию целиком и перечисляет конфликты', async () => {
    moderationApi.applyBulkModerationAction.mockImplementation(async (request: { operationId: string }) => ({
      items: comments.map((comment) => ({
        commentId: comment.id,
        success: true,
        action: {
          id: `${comment.id}-action`,
          commentId: comment.id,
          action: 'APPROVE',
          fromStatus: comment.status,
          toStatus: 'APPROVED',
          reason: null,
          performedBy: { id: 'owner-id', email: 'owner@example.com' },
          operationId: request.operationId,
          revertsActionId: null,
          createdAt: '2026-07-13T12:00:00Z',
        },
        errorCode: null,
        message: null,
      })),
    }))
    moderationApi.undoBulkModerationOperation.mockResolvedValue({
      items: [
        {
          commentId: comments[0].id,
          success: true,
          action: null,
          errorCode: null,
          message: null,
        },
        {
          commentId: comments[1].id,
          success: false,
          action: null,
          errorCode: 'UNDO_CONFLICT',
          message: 'Комментарий был изменён позднее',
        },
      ],
    })
    render(<MemoryRouter initialEntries={['/moderation?view=pending']}><Moderation /></MemoryRouter>)
    await screen.findByText('Первый комментарий')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Выбрать комментарий Анна' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Выбрать комментарий Борис' }))
    fireEvent.click(screen.getByRole('button', { name: /Одобрить/ }))

    const undoButton = await screen.findByRole('button', { name: 'Отменить массовое действие (2)' })
    const operationId = moderationApi.applyBulkModerationAction.mock.calls[0][0].operationId
    fireEvent.click(undoButton)

    await waitFor(() => expect(moderationApi.undoBulkModerationOperation).toHaveBeenCalledWith(operationId))
    expect(await screen.findByText('Результат отмены: восстановлено 1')).toBeInTheDocument()
    expect(screen.getByText('Конфликты (1):')).toBeInTheDocument()
    expect(screen.getByText(/Комментарий …00000012: Комментарий был изменён позднее/)).toBeInTheDocument()
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

  it('показывает решение политики и сохраняет обратную связь без текста комментария', async () => {
    render(<MemoryRouter initialEntries={[`/moderation?comment=${comments[1].id}`]}><Moderation /></MemoryRouter>)

    await screen.findByRole('dialog', { name: 'Комментарий' })
    expect(screen.getByText('Решение автомодерации')).toBeInTheDocument()
    expect(screen.getByText('Версия')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Это допустимый комментарий' }))

    await waitFor(() => expect(moderationApi.setAutoModerationFeedback).toHaveBeenCalledWith(
      comments[1].id,
      'FALSE_POSITIVE',
    ))
    expect(moderationApi.setAutoModerationFeedback.mock.calls[0]).not.toContain(comments[1].content)
    expect(await screen.findByText('Ложное срабатывание')).toBeInTheDocument()
  })
})

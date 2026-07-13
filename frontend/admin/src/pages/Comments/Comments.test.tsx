import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { DiscussionSummary, DiscussionThread } from '../../types/api'
import Comments from './Comments'

const discussionsApi = vi.hoisted(() => ({
  listDiscussions: vi.fn(),
  getDiscussion: vi.fn(),
  createOwnerReply: vi.fn(),
}))

vi.mock('../../api/discussions', () => discussionsApi)
vi.mock('../../api/sites', () => ({ listAllSites: vi.fn().mockResolvedValue([]) }))
vi.mock('../../components/realtime/useRealtime', () => ({ useRealtimeEvent: vi.fn() }))

const rootId = '00000000-0000-4000-8000-000000000174'
const siteId = '00000000-0000-4000-8000-000000000175'

const summary: DiscussionSummary = {
  rootCommentId: rootId,
  siteId,
  siteName: 'Редакция',
  pageId: '00000000-0000-4000-8000-000000000176',
  pageUrl: 'https://example.com/article',
  pageTitle: 'Новая статья',
  lastAuthor: { id: null, displayName: 'Анна', owner: false },
  lastMessage: 'Интересная публикация',
  lastActivityAt: '2026-07-13T12:00:00Z',
  replyCount: 1,
  unread: true,
  status: 'NEEDS_REPLY',
  pinned: true,
}

const thread: DiscussionThread = {
  summary,
  messages: [
    {
      id: rootId,
      parentId: null,
      author: { id: null, displayName: 'Анна', owner: false },
      content: 'Интересная публикация',
      createdAt: '2026-07-13T11:55:00Z',
      updatedAt: '2026-07-13T11:55:00Z',
      pinned: true,
    },
    {
      id: '00000000-0000-4000-8000-000000000177',
      parentId: rootId,
      author: { id: '00000000-0000-4000-8000-000000000178', displayName: 'Автор', owner: true },
      content: 'Спасибо за отзыв',
      createdAt: '2026-07-13T12:00:00Z',
      updatedAt: '2026-07-13T12:00:00Z',
      pinned: false,
    },
  ],
}

function CurrentLocation() {
  const location = useLocation()
  return <output data-testid="current-location">{location.pathname}{location.search}</output>
}

function HistoryBack() {
  const navigate = useNavigate()
  return <button type="button" onClick={() => navigate(-1)}>Назад в истории</button>
}

describe('страница обсуждений', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    discussionsApi.listDiscussions.mockResolvedValue({
      items: [summary],
      page: 1,
      pageSize: 30,
      totalItems: 1,
      totalPages: 1,
    })
    discussionsApi.getDiscussion.mockResolvedValue(thread)
    discussionsApi.createOwnerReply.mockResolvedValue({
      created: true,
      message: {
        id: '00000000-0000-4000-8000-000000000179',
        parentId: rootId,
        author: { id: '00000000-0000-4000-8000-000000000178', displayName: 'Автор сайта', owner: true },
        content: 'Новый ответ владельца',
        createdAt: '2026-07-13T12:05:00Z',
        updatedAt: '2026-07-13T12:05:00Z',
        pinned: false,
      },
    })
  })

  it('открывает адресуемую ветку и применяет фильтры из прямой ссылки', async () => {
    render(
      <MemoryRouter initialEntries={[`/comments?view=UNREAD&siteId=${siteId}&search=${encodeURIComponent('продукт')}&page=2&discussion=${rootId}`]}>
        <Comments />
        <CurrentLocation />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Спасибо за отзыв')).toBeInTheDocument()
    expect(discussionsApi.listDiscussions).toHaveBeenCalledWith({
      siteId,
      view: 'UNREAD',
      search: 'продукт',
      page: 2,
      pageSize: 30,
    })
    expect(discussionsApi.getDiscussion).toHaveBeenCalledWith(rootId)
    expect(screen.getByText('Автор сайта')).toBeInTheDocument()
    expect(screen.queryByText(/example\.com@/)).not.toBeInTheDocument()
  })

  it('выбирает обсуждение из master-списка и сохраняет выбор в URL', async () => {
    render(
      <MemoryRouter initialEntries={['/comments']}>
        <Comments />
        <CurrentLocation />
      </MemoryRouter>,
    )

    const row = await screen.findByRole('button', { name: 'Открыть обсуждение: Новая статья' })
    fireEvent.click(row)

    await waitFor(() => expect(discussionsApi.getDiscussion).toHaveBeenCalledWith(rootId))
    expect(screen.getByTestId('current-location')).toHaveTextContent(`/comments?discussion=${rootId}`)
    expect(await screen.findByText('Спасибо за отзыв')).toBeInTheDocument()
  })

  it('показывает честное пустое состояние и ссылку на установку', async () => {
    discussionsApi.listDiscussions.mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 30,
      totalItems: 0,
      totalPages: 0,
    })

    render(<MemoryRouter initialEntries={['/comments']}><Comments /></MemoryRouter>)

    expect(await screen.findByText('Комментариев пока нет')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Проверить установку' })).toHaveAttribute('href', '/sites')
  })

  it('восстанавливает видимые фильтры при Back без рассинхронизации с API', async () => {
    render(
      <MemoryRouter
        initialEntries={['/comments?search=старое', '/comments?search=новое']}
        initialIndex={1}
      >
        <Comments />
        <HistoryBack />
      </MemoryRouter>,
    )

    await waitFor(() => expect(discussionsApi.listDiscussions).toHaveBeenCalledWith(expect.objectContaining({ search: 'новое' })))
    expect(screen.getByPlaceholderText('Текст, страница или сайт')).toHaveValue('новое')

    fireEvent.click(screen.getByRole('button', { name: 'Назад в истории' }))

    await waitFor(() => expect(discussionsApi.listDiscussions).toHaveBeenCalledWith(expect.objectContaining({ search: 'старое' })))
    expect(screen.getByPlaceholderText('Текст, страница или сайт')).toHaveValue('старое')
  })

  it('сохраняет список при независимой ошибке detail и повторяет только открытие', async () => {
    discussionsApi.getDiscussion
      .mockRejectedValueOnce(new Error('detail failed'))
      .mockResolvedValueOnce(thread)

    render(
      <MemoryRouter initialEntries={[`/comments?discussion=${rootId}`]}>
        <Comments />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Не удалось открыть обсуждение.')).toBeInTheDocument()
    expect(screen.getByText('Интересная публикация')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Повторить открытие' }))
    expect(await screen.findByText('Спасибо за отзыв')).toBeInTheDocument()
    expect(discussionsApi.listDiscussions).toHaveBeenCalledTimes(1)
    expect(discussionsApi.getDiscussion).toHaveBeenCalledTimes(2)
  })

  it('публикует ответ владельца один раз и очищает черновик после успеха', async () => {
    render(
      <MemoryRouter initialEntries={[`/comments?discussion=${rootId}`]}>
        <Comments />
      </MemoryRouter>,
    )

    const editor = await screen.findByLabelText('Ответить как владелец сайта')
    fireEvent.change(editor, { target: { value: '  Новый ответ владельца  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ответить' }))

    await waitFor(() => expect(discussionsApi.createOwnerReply).toHaveBeenCalledWith(
      rootId,
      { operationId: expect.any(String), content: 'Новый ответ владельца' },
    ))
    expect(await screen.findByText('Новый ответ владельца')).toBeInTheDocument()
    expect(editor).toHaveValue('')
    expect(screen.getByText('Ответ опубликован.')).toBeInTheDocument()
    expect(sessionStorage.getItem(`cloud-comment:owner-reply-draft:v1:${rootId}`)).toBeNull()
  })

  it('сохраняет текст и operationId при ошибке для безопасного повтора', async () => {
    discussionsApi.createOwnerReply
      .mockRejectedValueOnce(new Error('network failed'))
      .mockResolvedValueOnce({
        created: false,
        message: {
          id: '00000000-0000-4000-8000-000000000179',
          parentId: rootId,
          author: { id: null, displayName: 'Автор сайта', owner: true },
          content: 'Ответ после ошибки',
          createdAt: '2026-07-13T12:05:00Z',
          updatedAt: '2026-07-13T12:05:00Z',
          pinned: false,
        },
      })
    render(
      <MemoryRouter initialEntries={[`/comments?discussion=${rootId}`]}>
        <Comments />
      </MemoryRouter>,
    )

    const editor = await screen.findByLabelText('Ответить как владелец сайта')
    fireEvent.change(editor, { target: { value: 'Ответ после ошибки' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ответить' }))

    expect(await screen.findByText('Не удалось опубликовать ответ. Текст сохранён — попробуйте ещё раз.')).toBeInTheDocument()
    expect(editor).toHaveValue('Ответ после ошибки')
    const firstOperationId = discussionsApi.createOwnerReply.mock.calls[0][1].operationId

    fireEvent.click(screen.getByRole('button', { name: 'Ответить' }))
    await waitFor(() => expect(discussionsApi.createOwnerReply).toHaveBeenCalledTimes(2))
    expect(discussionsApi.createOwnerReply.mock.calls[1][1].operationId).toBe(firstOperationId)
    expect(await screen.findByText('Ответ уже был опубликован ранее.')).toBeInTheDocument()
  })
})

import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { OwnerAnalytics } from '../../types/api'
import { OwnerAnalyticsPanel } from './OwnerAnalyticsPanel'

const analyticsApi = vi.hoisted(() => ({ getOwnerAnalytics: vi.fn() }))

vi.mock('../../api/analytics', () => analyticsApi)
vi.mock('../realtime/useRealtime', () => ({ useRealtimeEvent: vi.fn() }))
vi.mock('./AnalyticsTrendChart', () => ({
  default: () => <div data-testid="analytics-chart">График загружен</div>,
}))

const SITE_ID = '00000000-0000-0000-0000-000000000132'

const analytics: OwnerAnalytics = {
  range: '30d',
  siteId: null,
  timeZone: 'Europe/Moscow',
  bucketGranularity: 'DAY',
  from: '2026-06-14T00:00:00Z',
  to: '2026-07-13T12:00:00Z',
  summary: { sites: 1, pages: 1, comments: 2, replies: 0, reactions: 3, pending: 1, approved: 1, rejected: 0, hidden: 0, spam: 0 },
  comparison: {
    previousFrom: '2026-05-15T00:00:00Z',
    previousTo: '2026-06-13T23:59:59Z',
    comments: { current: 2, previous: 0, absoluteChange: 2, percentageChange: null },
    reactions: { current: 3, previous: 1, absoluteChange: 2, percentageChange: 200 },
    automaticDecisions: { current: 1, previous: 0, absoluteChange: 1, percentageChange: null },
    manualDecisions: { current: 1, previous: 1, absoluteChange: 0, percentageChange: 0 },
    undoActions: { current: 0, previous: 0, absoluteChange: 0, percentageChange: null },
  },
  workload: { requiringDecision: 1, oldestPendingAt: '2026-07-13T10:00:00Z', automaticDecisions: 1, manualDecisions: 1, undoActions: 0 },
  commentsOverTime: [
    { bucket: '2026-07-12', total: 1, approved: 1, pending: 0, spam: 0 },
    { bucket: '2026-07-13', total: 1, approved: 0, pending: 1, spam: 0 },
  ],
  moderationDistribution: [
    { status: 'APPROVED', count: 1 },
    { status: 'PENDING', count: 1 },
  ],
  moderationFunnel: [
    { status: 'APPROVED', count: 1 },
    { status: 'PENDING', count: 1 },
  ],
  reactionDistribution: [{ type: 'LIKE', count: 3 }],
  topPages: [],
  activeCommenters: [],
}

describe('рабочий обзор аналитики', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    analyticsApi.getOwnerAnalytics.mockResolvedValue(analytics)
  })

  it('ставит очередь первой и ведёт в отфильтрованную модерацию сайта', async () => {
    analyticsApi.getOwnerAnalytics.mockResolvedValue({ ...analytics, siteId: SITE_ID })
    render(
      <MemoryRouter>
        <OwnerAnalyticsPanel siteId={SITE_ID} variant="site-summary" />
      </MemoryRouter>,
    )

    expect(await screen.findByText('1 требуют решения')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Открыть очередь' })).toHaveAttribute(
      'href',
      `/moderation?view=pending&source=analytics&siteId=${SITE_ID}`,
    )
    expect(screen.getByText('Новое к предыдущему периоду (0)')).toBeInTheDocument()
    expect(screen.queryByTestId('analytics-chart')).not.toBeInTheDocument()
    expect(screen.queryByText('Показать данные таблицей')).not.toBeInTheDocument()
  })

  it('лениво показывает график и доступную таблицу только в полном обзоре', async () => {
    render(<MemoryRouter><OwnerAnalyticsPanel /></MemoryRouter>)

    expect(await screen.findByTestId('analytics-chart')).toBeInTheDocument()
    expect(screen.getByText('Распределение статусов')).toBeInTheDocument()
    expect(screen.getByText('Показать данные таблицей')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ожидает решения: 1/ })).toHaveAttribute('href', '/moderation?view=pending&source=analytics&status=PENDING')
  })
})

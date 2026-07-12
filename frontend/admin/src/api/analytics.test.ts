import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('./client', () => ({ apiClient: client }))

import { getOwnerAnalytics } from './analytics'

const emptyAnalytics = {
  range: '30d',
  siteId: null,
  timeZone: 'Europe/Moscow',
  bucketGranularity: 'DAY',
  from: '2026-06-14T00:00:00Z',
  to: '2026-07-13T12:00:00Z',
  summary: {
    sites: 0,
    pages: 0,
    comments: 0,
    replies: 0,
    reactions: 0,
    pending: 0,
    approved: 0,
    rejected: 0,
    hidden: 0,
    spam: 0,
  },
  comparison: null,
  workload: {
    requiringDecision: 0,
    oldestPendingAt: null,
    automaticDecisions: 0,
    manualDecisions: 0,
    undoActions: 0,
  },
  commentsOverTime: [],
  moderationDistribution: [],
  moderationFunnel: [],
  reactionDistribution: [],
  topPages: [],
  activeCommenters: [],
} as const

describe('API аналитики владельца', () => {
  beforeEach(() => {
    client.get.mockReset()
    client.get.mockResolvedValue({ data: emptyAnalytics })
  })

  it('передаёт выбранный часовой пояс вместе с периодом и сайтом', async () => {
    const result = await getOwnerAnalytics({
      range: '90d',
      siteId: '00000000-0000-0000-0000-000000000132',
      timeZone: 'Asia/Yekaterinburg',
    })

    expect(client.get).toHaveBeenCalledWith('/analytics/owner', {
      params: {
        range: '90d',
        siteId: '00000000-0000-0000-0000-000000000132',
        timeZone: 'Asia/Yekaterinburg',
      },
    })
    expect(result.moderationDistribution).toEqual(result.moderationFunnel)
  })

  it.each(['DAY', 'WEEK', 'MONTH'] as const)('принимает гранулярность %s', async (bucketGranularity) => {
    client.get.mockResolvedValue({ data: { ...emptyAnalytics, bucketGranularity } })
    await expect(getOwnerAnalytics({ timeZone: 'UTC' })).resolves.toMatchObject({ bucketGranularity })
  })

  it('отклоняет неполный ответ до отрисовки панели', async () => {
    client.get.mockResolvedValue({
      data: { ...emptyAnalytics, workload: undefined, moderationDistribution: undefined },
    })

    await expect(getOwnerAnalytics({ timeZone: 'UTC' })).rejects.toThrow(
      'Некорректный ответ API с аналитикой.',
    )
  })
})

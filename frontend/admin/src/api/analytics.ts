import type { AnalyticsRange, OwnerAnalytics } from '../types/api'
import { apiClient } from './client'

export interface GetOwnerAnalyticsParams {
  range?: AnalyticsRange
  siteId?: string
  timeZone?: string
}

export async function getOwnerAnalytics(params: GetOwnerAnalyticsParams = {}): Promise<OwnerAnalytics> {
  const response = await apiClient.get<OwnerAnalytics>('/analytics/owner', {
    params: {
      ...params,
      timeZone: params.timeZone ?? getBrowserTimeZone(),
    },
  })
  assertOwnerAnalyticsResponse(response.data)
  return response.data
}

export function getBrowserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

function assertOwnerAnalyticsResponse(value: OwnerAnalytics): void {
  if (
    !value
    || !value.summary
    || !value.workload
    || !['DAY', 'WEEK', 'MONTH'].includes(value.bucketGranularity)
    || typeof value.timeZone !== 'string'
    || !Array.isArray(value.commentsOverTime)
    || !Array.isArray(value.moderationDistribution)
  ) {
    throw new Error('Некорректный ответ API с аналитикой.')
  }
}

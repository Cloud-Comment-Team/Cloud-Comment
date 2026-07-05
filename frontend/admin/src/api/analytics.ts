import type { AnalyticsRange, OwnerAnalytics } from '../types/api'
import { apiClient } from './client'

export interface GetOwnerAnalyticsParams {
  range?: AnalyticsRange
  siteId?: string
}

export async function getOwnerAnalytics(params: GetOwnerAnalyticsParams = {}): Promise<OwnerAnalytics> {
  const response = await apiClient.get<OwnerAnalytics>('/analytics/owner', { params })
  assertOwnerAnalyticsResponse(response.data)
  return response.data
}

function assertOwnerAnalyticsResponse(value: OwnerAnalytics): void {
  if (!value || !value.summary || !Array.isArray(value.commentsOverTime)) {
    throw new Error('Некорректный ответ API с аналитикой.')
  }
}

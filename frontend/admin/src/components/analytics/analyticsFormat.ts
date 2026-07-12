import type {
  AnalyticsBucketGranularity,
  AnalyticsMetricComparison,
  AnalyticsRange,
  CommentStatus,
} from '../../types/api'

type ModerationView = 'pending' | 'spam' | 'history'

const rangeLabels: Record<AnalyticsRange, string> = {
  '7d': '7 дней',
  '30d': '30 дней',
  '90d': '90 дней',
  all: 'Всё время',
}

export function analyticsRangeLabel(range: AnalyticsRange): string {
  return rangeLabels[range]
}

export function formatAnalyticsBucket(value: string, granularity: AnalyticsBucketGranularity): string {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, Math.max(0, month - 1), day || 1))
  const options: Intl.DateTimeFormatOptions = granularity === 'MONTH'
    ? { month: 'short', year: 'numeric', timeZone: 'UTC' }
    : { day: '2-digit', month: 'short', timeZone: 'UTC' }
  return new Intl.DateTimeFormat('ru-RU', options).format(date)
}

export function formatAnalyticsDateTime(value: string | null, timeZone: string): string {
  if (!value) return 'нет данных'
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  }).format(new Date(value))
}

export function formatWaitingTime(oldestPendingAt: string | null, measuredAt: string): string {
  if (!oldestPendingAt) return 'Очередь разобрана'
  const durationMinutes = Math.max(0, Math.floor(
    (new Date(measuredAt).getTime() - new Date(oldestPendingAt).getTime()) / 60_000,
  ))
  const days = Math.floor(durationMinutes / 1_440)
  const hours = Math.floor((durationMinutes % 1_440) / 60)
  const minutes = durationMinutes % 60
  if (days > 0) return `Самый старый ждёт ${days} дн. ${hours} ч.`
  if (hours > 0) return `Самый старый ждёт ${hours} ч. ${minutes} мин.`
  return `Самый старый ждёт ${minutes} мин.`
}

export function comparisonLabel(comparison: AnalyticsMetricComparison | undefined): string {
  if (!comparison || (comparison.current === 0 && comparison.previous === 0)) return 'Без изменений'
  if (comparison.previous === 0) return 'Новое'
  if (comparison.percentageChange === null || !Number.isFinite(comparison.percentageChange)) {
    const sign = comparison.absoluteChange > 0 ? '+' : ''
    return `${sign}${comparison.absoluteChange}`
  }
  const rounded = Math.round(comparison.percentageChange)
  return `${rounded > 0 ? '+' : ''}${rounded}%`
}

export function comparisonDescription(
  comparison: AnalyticsMetricComparison | undefined,
  range: AnalyticsRange,
): string {
  if (!comparison) return range === 'all' ? 'Сравнение недоступно для всего времени' : 'Нет предыдущего периода'
  return `${comparisonLabel(comparison)} к предыдущему периоду (${comparison.previous})`
}

export function moderationViewForStatus(status: CommentStatus): ModerationView {
  if (status === 'PENDING') return 'pending'
  if (status === 'SPAM') return 'spam'
  return 'history'
}

export function moderationHref({
  view,
  status,
  siteId,
  pageUrl,
}: {
  view: ModerationView
  status?: CommentStatus
  siteId?: string | null
  pageUrl?: string
}): string {
  const params = new URLSearchParams({ view, source: 'analytics' })
  if (status) params.set('status', status)
  if (siteId) params.set('siteId', siteId)
  if (pageUrl) params.set('pageUrl', pageUrl)
  return `/moderation?${params.toString()}`
}

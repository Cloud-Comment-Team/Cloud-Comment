import { Component, Suspense, lazy, useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  Activity,
  BarChart3,
  Bot,
  MessageSquare,
  MousePointerClick,
  RotateCcw,
  ShieldCheck,
  Smile,
  UserCheck,
} from 'lucide-react'

import { getOwnerAnalytics } from '../../api/analytics'
import { getApiErrorMessage } from '../../api/auth'
import type {
  AnalyticsMetricComparison,
  AnalyticsRange,
  CommentStatus,
  ModerationActionNotification,
  ModerationStatusCount,
  NewCommentNotification,
  OwnerAnalytics,
  ReactionTypeCount,
  TopPageAnalytics,
} from '../../types/api'
import { AsyncState } from '../common/AsyncState'
import { Badge } from '../common/Badge'
import { useRealtimeEvent } from '../realtime/useRealtime'
import {
  analyticsRangeLabel,
  comparisonDescription,
  comparisonLabel,
  formatAnalyticsBucket,
  formatAnalyticsDateTime,
  formatWaitingTime,
  moderationHref,
  moderationViewForStatus,
} from './analyticsFormat'

const AnalyticsTrendChart = lazy(() => import('./AnalyticsTrendChart'))

interface OwnerAnalyticsPanelProps {
  siteId?: string
  variant?: 'full' | 'site-summary'
}

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'Ожидает решения',
  APPROVED: 'Одобрено',
  REJECTED: 'Отклонено',
  HIDDEN: 'Скрыто',
  SPAM: 'Спам',
}

const statusTones: Record<CommentStatus, 'success' | 'warning' | 'danger' | 'muted'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  HIDDEN: 'muted',
  SPAM: 'danger',
}

const reactionMeta: Record<ReactionTypeCount['type'], { emoji: string; label: string }> = {
  LIKE: { emoji: '👍', label: 'Нравится' },
  LOVE: { emoji: '💚', label: 'Любовь' },
  LAUGH: { emoji: '😄', label: 'Смешно' },
  WOW: { emoji: '✨', label: 'Вау' },
}

export function OwnerAnalyticsPanel({ siteId, variant = 'full' }: OwnerAnalyticsPanelProps) {
  const [range, setRange] = useState<AnalyticsRange>('30d')
  const [analytics, setAnalytics] = useState<OwnerAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useRealtimeEvent((event) => {
    if (event.type === 'comment.created') {
      const payload: NewCommentNotification = event.payload
      if (!siteId || payload.siteId === siteId) setReloadKey((current) => current + 1)
      return
    }
    if (event.type === 'comment.moderation_action_applied') {
      const payload: ModerationActionNotification = event.payload
      if (!siteId || payload.siteId === siteId) setReloadKey((current) => current + 1)
    }
  })

  useEffect(() => {
    let cancelled = false

    async function loadAnalytics() {
      setLoading(true)
      setError(null)
      try {
        const data = await getOwnerAnalytics({ range, siteId })
        if (!cancelled) setAnalytics(data)
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить аналитику.'))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadAnalytics()
    return () => {
      cancelled = true
    }
  }, [range, reloadKey, siteId])

  const headingId = siteId ? 'site-analytics-title' : 'owner-analytics-title'

  return (
    <section className="space-y-5" aria-labelledby={headingId}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="cc-eyebrow">Аналитика</p>
          <h2 id={headingId} className="text-xl font-bold md:text-2xl" style={{ color: 'var(--text-h)' }}>
            {variant === 'site-summary' ? 'Краткая сводка сайта' : 'Рабочий обзор'}
          </h2>
          {analytics && (
            <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
              Периоды рассчитаны в часовом поясе {analytics.timeZone}
            </p>
          )}
        </div>
        <RangeSelector value={range} onChange={setRange} />
      </div>

      <AsyncState loading={loading} error={error} empty={false}>
        {analytics && (variant === 'site-summary'
          ? <SiteSummary analytics={analytics} />
          : <FullAnalytics analytics={analytics} />)}
      </AsyncState>
    </section>
  )
}

function RangeSelector({ value, onChange }: { value: AnalyticsRange; onChange: (range: AnalyticsRange) => void }) {
  const ranges: AnalyticsRange[] = ['7d', '30d', '90d', 'all']
  return (
    <div
      className="inline-flex max-w-full overflow-x-auto rounded-lg border p-1"
      style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}
      role="group"
      aria-label="Период аналитики"
    >
      {ranges.map((range) => (
        <button
          key={range}
          type="button"
          onClick={() => onChange(range)}
          aria-pressed={value === range}
          className="shrink-0 rounded-md px-3 py-2 text-sm font-semibold transition"
          style={{
            backgroundColor: value === range ? 'var(--accent)' : 'transparent',
            color: value === range ? 'var(--accent-contrast)' : 'var(--text)',
          }}
        >
          {analyticsRangeLabel(range)}
        </button>
      ))}
    </div>
  )
}

function SiteSummary({ analytics }: { analytics: OwnerAnalytics }) {
  return (
    <div className="grid gap-4 lg:grid-cols-[1.35fr_1fr_1fr]">
      <QueueCard analytics={analytics} compact />
      <MetricCard
        icon={<MessageSquare className="h-5 w-5" aria-hidden="true" />}
        label="Комментарии"
        value={analytics.summary.comments}
        comparison={analytics.comparison?.comments}
        range={analytics.range}
      />
      <MetricCard
        icon={<Smile className="h-5 w-5" aria-hidden="true" />}
        label="Реакции"
        value={analytics.summary.reactions}
        comparison={analytics.comparison?.reactions}
        range={analytics.range}
      />
    </div>
  )
}

function FullAnalytics({ analytics }: { analytics: OwnerAnalytics }) {
  return (
    <div className="space-y-5">
      <div className="grid gap-4 xl:grid-cols-[1.45fr_1fr]">
        <QueueCard analytics={analytics} />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
          <MetricCard
            icon={<MessageSquare className="h-5 w-5" aria-hidden="true" />}
            label="Комментарии"
            value={analytics.summary.comments}
            comparison={analytics.comparison?.comments}
            range={analytics.range}
          />
          <MetricCard
            icon={<Smile className="h-5 w-5" aria-hidden="true" />}
            label="Реакции"
            value={analytics.summary.reactions}
            comparison={analytics.comparison?.reactions}
            range={analytics.range}
          />
        </div>
      </div>

      <TrendCard analytics={analytics} />

      <div className="grid gap-5 xl:grid-cols-2">
        <DistributionCard distribution={analytics.moderationDistribution} siteId={analytics.siteId} />
        <ReactionCard reactions={analytics.reactionDistribution} />
      </div>

      <TopPagesCard pages={analytics.topPages} />
    </div>
  )
}

function QueueCard({ analytics, compact = false }: { analytics: OwnerAnalytics; compact?: boolean }) {
  const { workload } = analytics
  return (
    <article className="cc-card p-5 md:p-6" aria-labelledby="analytics-queue-title">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <span className="inline-flex h-11 w-11 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--warning-bg)', color: 'var(--warning)' }}>
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </span>
          <p className="mt-4 text-sm font-semibold" style={{ color: 'var(--text)' }}>Очередь модерации</p>
          <h3 id="analytics-queue-title" className="mt-1 text-3xl font-bold" style={{ color: 'var(--text-h)' }}>
            {workload.requiringDecision} требуют решения
          </h3>
          <p className="mt-2 text-sm" style={{ color: workload.oldestPendingAt ? 'var(--warning)' : 'var(--success)' }}>
            {formatWaitingTime(workload.oldestPendingAt, analytics.to)}
          </p>
          {workload.oldestPendingAt && (
            <p className="mt-1 text-xs" style={{ color: 'var(--text)' }}>
              С {formatAnalyticsDateTime(workload.oldestPendingAt, analytics.timeZone)}
            </p>
          )}
        </div>
        <Link
          to={moderationHref({ view: 'pending', siteId: analytics.siteId })}
          className="cc-button-primary shrink-0"
        >
          Открыть очередь
        </Link>
      </div>

      {!compact && (
        <dl className="mt-6 grid gap-3 border-t pt-5 sm:grid-cols-3" style={{ borderColor: 'var(--border)' }}>
          <WorkloadValue icon={<Bot className="h-4 w-4" />} label="Автоматически" value={workload.automaticDecisions} comparison={analytics.comparison?.automaticDecisions} />
          <WorkloadValue icon={<UserCheck className="h-4 w-4" />} label="Вручную" value={workload.manualDecisions} comparison={analytics.comparison?.manualDecisions} />
          <WorkloadValue icon={<RotateCcw className="h-4 w-4" />} label="Отменено" value={workload.undoActions} comparison={analytics.comparison?.undoActions} />
        </dl>
      )}
    </article>
  )
}

function WorkloadValue({ icon, label, value, comparison }: { icon: ReactNode; label: string; value: number; comparison?: AnalyticsMetricComparison }) {
  return (
    <div className="rounded-lg border p-3" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}>
      <dt className="flex items-center gap-2 text-xs font-semibold" style={{ color: 'var(--text)' }}>{icon}{label}</dt>
      <dd className="mt-2 flex items-baseline justify-between gap-2">
        <strong className="text-xl" style={{ color: 'var(--text-h)' }}>{value}</strong>
        {comparison && <span className="text-xs" style={{ color: 'var(--text)' }}>{comparisonLabel(comparison)}</span>}
      </dd>
    </div>
  )
}

function MetricCard({ icon, label, value, comparison, range }: {
  icon: ReactNode
  label: string
  value: number
  comparison?: AnalyticsMetricComparison
  range: AnalyticsRange
}) {
  return (
    <article className="cc-card p-5">
      <div className="flex items-start justify-between gap-4">
        <span className="inline-flex h-11 w-11 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
          {icon}
        </span>
        <span className="text-3xl font-bold" style={{ color: 'var(--text-h)' }}>{value}</span>
      </div>
      <h3 className="mt-4 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>{label}</h3>
      <p className="mt-1 text-sm" style={{ color: 'var(--text)' }} title={comparisonDescription(comparison, range)}>
        {comparisonDescription(comparison, range)}
      </p>
    </article>
  )
}

function TrendCard({ analytics }: { analytics: OwnerAnalytics }) {
  const hasComments = analytics.commentsOverTime.some((point) => point.total > 0)
  return (
    <article className="cc-card p-5 md:p-6" aria-labelledby="analytics-trend-title">
      <CardHeader
        icon={<Activity className="h-5 w-5" aria-hidden="true" />}
        title="Динамика комментариев"
        subtitle={`Интервал: ${granularityLabel(analytics.bucketGranularity).toLowerCase()}`}
        titleId="analytics-trend-title"
      />
      {hasComments ? (
        <>
          <p id="analytics-axis-description" className="mt-4 text-xs" style={{ color: 'var(--text)' }}>
            По горизонтали — период, по вертикали — количество комментариев. Значения доступны с клавиатуры и в таблице.
          </p>
          <ChartErrorBoundary key={`${analytics.range}-${analytics.bucketGranularity}-${analytics.commentsOverTime.length}`}>
            <Suspense fallback={<ChartFallback />}>
              <AnalyticsTrendChart points={analytics.commentsOverTime} granularity={analytics.bucketGranularity} />
            </Suspense>
          </ChartErrorBoundary>
        </>
      ) : (
        <div className="mt-5 rounded-lg border border-dashed px-4 py-8 text-center" style={{ borderColor: 'var(--border)' }}>
          <MessageSquare className="mx-auto h-7 w-7" style={{ color: 'var(--text)' }} aria-hidden="true" />
          <p className="mt-3 font-semibold" style={{ color: 'var(--text-h)' }}>Комментариев за период пока нет</p>
          <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>Проверьте установку виджета или выберите другой период.</p>
          <Link to="/sites" className="cc-button-secondary mt-4">Перейти к сайтам</Link>
        </div>
      )}
      <AnalyticsDataTable analytics={analytics} />
    </article>
  )
}

function AnalyticsDataTable({ analytics }: { analytics: OwnerAnalytics }) {
  return (
    <details className="mt-4 rounded-lg border" style={{ borderColor: 'var(--border)' }}>
      <summary className="cursor-pointer px-4 py-3 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
        Показать данные таблицей
      </summary>
      <div
        className="overflow-x-auto border-t"
        style={{ borderColor: 'var(--border)' }}
        role="region"
        aria-label="Табличные данные аналитики, доступна горизонтальная прокрутка"
        tabIndex={0}
      >
        <table className="w-full min-w-[640px] text-left text-sm">
          <caption className="sr-only">Комментарии по интервалам выбранного периода</caption>
          <thead style={{ color: 'var(--text)' }}>
            <tr>
              <th scope="col" className="px-4 py-3">Период</th>
              <th scope="col" className="px-4 py-3 text-right">Всего</th>
              <th scope="col" className="px-4 py-3 text-right">Одобрено</th>
              <th scope="col" className="px-4 py-3 text-right">Ожидает решения</th>
              <th scope="col" className="px-4 py-3 text-right">Спам</th>
            </tr>
          </thead>
          <tbody>
            {analytics.commentsOverTime.map((point) => (
              <tr key={point.bucket} className="border-t" style={{ borderColor: 'var(--border)' }}>
                <th scope="row" className="px-4 py-3 font-medium" style={{ color: 'var(--text-h)' }}>
                  {formatAnalyticsBucket(point.bucket, analytics.bucketGranularity)}
                </th>
                <td className="px-4 py-3 text-right">{point.total}</td>
                <td className="px-4 py-3 text-right">{point.approved}</td>
                <td className="px-4 py-3 text-right">{point.pending}</td>
                <td className="px-4 py-3 text-right">{point.spam}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}

function DistributionCard({ distribution, siteId }: { distribution: ModerationStatusCount[]; siteId: string | null }) {
  const total = distribution.reduce((sum, item) => sum + item.count, 0)
  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<BarChart3 className="h-5 w-5" aria-hidden="true" />}
        title="Распределение статусов"
        subtitle="Текущее состояние комментариев выбранного периода"
      />
      <div className="mt-5 space-y-3">
        {distribution.map((item) => {
          const percent = total === 0 ? 0 : Math.round((item.count / total) * 100)
          return (
            <Link
              key={item.status}
              to={moderationHref({ view: moderationViewForStatus(item.status), status: item.status, siteId })}
              className="block rounded-lg border p-3 transition hover:border-[var(--border-strong)]"
              style={{ borderColor: 'var(--border)' }}
              aria-label={`${statusLabels[item.status]}: ${item.count}. Открыть отфильтрованный экран.`}
            >
              <div className="flex items-center justify-between gap-3">
                <Badge tone={statusTones[item.status]}>{statusLabels[item.status]}</Badge>
                <span className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>{item.count} · {percent}%</span>
              </div>
              <div className="mt-2 h-1.5 rounded-full" style={{ backgroundColor: 'var(--surface-muted)' }}>
                <div className="h-full rounded-full" style={{ width: `${percent}%`, backgroundColor: `var(--${statusTones[item.status] === 'muted' ? 'border-strong' : statusTones[item.status]})` }} />
              </div>
            </Link>
          )
        })}
      </div>
    </article>
  )
}

function ReactionCard({ reactions }: { reactions: ReactionTypeCount[] }) {
  const total = reactions.reduce((sum, reaction) => sum + reaction.count, 0)
  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<Smile className="h-5 w-5" aria-hidden="true" />}
        title="Вовлечённость"
        subtitle="Какими реакциями отвечают участники обсуждений"
      />
      {reactions.length > 0 ? (
        <dl className="mt-5 grid gap-3 sm:grid-cols-2">
          {reactions.map((reaction) => {
            const meta = reactionMeta[reaction.type]
            return (
              <div key={reaction.type} className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}>
                <dt className="flex items-center justify-between gap-3 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                  <span><span className="mr-2 text-xl" aria-hidden="true">{meta.emoji}</span>{meta.label}</span>
                  <strong className="text-xl">{reaction.count}</strong>
                </dt>
                <dd className="mt-1 text-xs" style={{ color: 'var(--text)' }}>
                  {total > 0 ? Math.round((reaction.count / total) * 100) : 0}% всех реакций
                </dd>
              </div>
            )
          })}
        </dl>
      ) : (
        <p className="mt-5 rounded-lg border border-dashed px-4 py-8 text-center text-sm" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
          Реакций за период пока нет.
        </p>
      )}
    </article>
  )
}

function TopPagesCard({ pages }: { pages: TopPageAnalytics[] }) {
  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<MousePointerClick className="h-5 w-5" aria-hidden="true" />}
        title="Страницы, требующие внимания"
        subtitle="Приоритетные статусы и активность по страницам"
      />
      {pages.length > 0 ? (
        <div className="mt-5 grid gap-3 lg:grid-cols-2">
          {pages.map((page, index) => {
            const status = topPageFilterStatus(page)
            const content = <TopPageContent page={page} index={index} status={status} />
            if (!status) {
              return (
                <div
                  key={page.pageId}
                  className="rounded-lg border p-4"
                  style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                >
                  {content}
                  <p className="mt-2 text-xs" style={{ color: 'var(--text)' }}>Для этой сводки нет точного статусного фильтра.</p>
                </div>
              )
            }
            return (
              <Link
                key={page.pageId}
                to={moderationHref({
                  view: moderationViewForStatus(status),
                  status,
                  siteId: page.siteId,
                  pageUrl: page.pageUrl,
                })}
                className="rounded-lg border p-4 transition hover:border-[var(--border-strong)]"
                style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
                aria-label={`#${index + 1} ${page.siteName}: открыть статус «${statusLabels[status]}»`}
              >
                {content}
              </Link>
            )
          })}
        </div>
      ) : (
        <p className="mt-5 rounded-lg border border-dashed px-4 py-8 text-center text-sm" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>
          Страниц с обсуждениями за период пока нет.
        </p>
      )}
    </article>
  )
}

function topPageFilterStatus(page: TopPageAnalytics): CommentStatus | null {
  if (page.pending > 0) return 'PENDING'
  if (page.spam > 0) return 'SPAM'
  if (page.approved > 0) return 'APPROVED'
  return null
}

function TopPageContent({ page, index, status }: { page: TopPageAnalytics; index: number; status: CommentStatus | null }) {
  const statusCount = status === 'PENDING' ? page.pending : status === 'SPAM' ? page.spam : status === 'APPROVED' ? page.approved : 0
  return (
    <>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-bold" style={{ color: 'var(--text-h)' }}>#{index + 1} {page.siteName}</p>
          <p className="mt-1 truncate text-sm" title={page.pageUrl} style={{ color: 'var(--text)' }}>{page.pageUrl}</p>
        </div>
        {status
          ? <Badge tone={statusTones[status]}>{statusLabels[status]}: {statusCount}</Badge>
          : <Badge tone="muted">Без точного фильтра</Badge>}
      </div>
      <p className="mt-3 text-xs" style={{ color: 'var(--text)' }}>
        {page.comments} комментариев · {page.pending} ожидают · {page.spam} спам · {page.reactions} реакций
      </p>
    </>
  )
}

function CardHeader({ icon, title, subtitle, titleId }: { icon: ReactNode; title: string; subtitle: string; titleId?: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>{icon}</span>
      <div>
        <h3 id={titleId} className="font-semibold" style={{ color: 'var(--text-h)' }}>{title}</h3>
        <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>{subtitle}</p>
      </div>
    </div>
  )
}

function granularityLabel(granularity: OwnerAnalytics['bucketGranularity']): string {
  if (granularity === 'DAY') return 'День'
  if (granularity === 'WEEK') return 'Неделя'
  return 'Месяц'
}

function ChartFallback() {
  return (
    <div className="mt-5 h-72 animate-pulse rounded-lg" style={{ backgroundColor: 'var(--surface-muted)' }} role="status">
      <span className="sr-only">Загружаем график…</span>
    </div>
  )
}

class ChartErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch() {
    // Таблица ниже остаётся доступной даже при ошибке графического пакета.
  }

  render() {
    if (this.state.failed) {
      return (
        <p className="mt-5 rounded-lg border px-4 py-4 text-sm" style={{ borderColor: 'var(--warning)', backgroundColor: 'var(--warning-bg)', color: 'var(--warning)' }} role="alert">
          График временно недоступен. Все значения можно посмотреть в таблице ниже.
        </p>
      )
    }
    return this.props.children
  }
}

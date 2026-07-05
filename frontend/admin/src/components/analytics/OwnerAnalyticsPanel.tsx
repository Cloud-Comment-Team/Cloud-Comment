import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Activity, BarChart3, Clock, Globe, MessageSquare, MousePointerClick, Smile, Users } from 'lucide-react'
import { motion, useReducedMotion } from 'framer-motion'

import { getApiErrorMessage } from '../../api/auth'
import { getOwnerAnalytics } from '../../api/analytics'
import type {
  ActiveCommenter,
  AnalyticsRange,
  CommentStatus,
  CommentTimePoint,
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

interface OwnerAnalyticsPanelProps {
  siteId?: string
  compact?: boolean
}

const rangeLabels: Record<AnalyticsRange, string> = {
  '7d': '7 дней',
  '30d': '30 дней',
  '90d': '90 дней',
  all: 'Все время',
}

const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'На модерации',
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

const dateFormatter = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short' })
const dateTimeFormatter = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
})

export function OwnerAnalyticsPanel({ siteId, compact = false }: OwnerAnalyticsPanelProps) {
  const [range, setRange] = useState<AnalyticsRange>('30d')
  const [analytics, setAnalytics] = useState<OwnerAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useRealtimeEvent((event) => {
    if (event.type === 'comment.created') {
      const payload = event.payload as NewCommentNotification
      if (!siteId || payload.siteId === siteId) {
        setReloadKey((current) => current + 1)
      }
      return
    }

    if (event.type === 'comment.moderation_action_applied') {
      const payload = event.payload as ModerationActionNotification
      if (!siteId || payload.siteId === siteId) {
        setReloadKey((current) => current + 1)
      }
    }
  })

  useEffect(() => {
    let cancelled = false

    async function loadAnalytics() {
      setLoading(true)
      setError(null)

      try {
        const data = await getOwnerAnalytics({ range, siteId })
        if (!cancelled) {
          setAnalytics(data)
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить аналитику.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadAnalytics()

    return () => {
      cancelled = true
    }
  }, [range, reloadKey, siteId])

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="cc-eyebrow">Аналитика</p>
          <h2 className="text-xl font-bold md:text-2xl" style={{ color: 'var(--text-h)' }}>
            {siteId ? 'Пульс сайта' : 'Пульс всех проектов'}
          </h2>
        </div>
        <RangeSelector value={range} onChange={setRange} />
      </div>

      <AsyncState loading={loading} error={error} empty={false}>
        {analytics && (
          <div className="space-y-5">
            <SummaryGrid analytics={analytics} compact={compact} />

            <div className={compact ? 'grid gap-5 xl:grid-cols-2' : 'grid gap-5 xl:grid-cols-[1.5fr_1fr]'}>
              <TrendCard points={analytics.commentsOverTime} />
              <FunnelCard funnel={analytics.moderationFunnel} />
            </div>

            <div className={compact ? 'grid gap-5 xl:grid-cols-2' : 'grid gap-5 xl:grid-cols-[1fr_1.2fr]'}>
              <ReactionCard reactions={analytics.reactionDistribution} />
              <TopPagesCard pages={analytics.topPages} />
            </div>

            {!compact && <ActiveCommentersCard commenters={analytics.activeCommenters} />}
          </div>
        )}
      </AsyncState>
    </section>
  )
}

function RangeSelector({
  value,
  onChange,
}: {
  value: AnalyticsRange
  onChange: (range: AnalyticsRange) => void
}) {
  return (
    <div
      className="inline-flex rounded-lg border p-1"
      style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}
      aria-label="Период аналитики"
    >
      {(Object.keys(rangeLabels) as AnalyticsRange[]).map((range) => (
        <button
          key={range}
          type="button"
          onClick={() => onChange(range)}
          className="rounded-md px-3 py-2 text-sm font-semibold transition"
          style={{
            backgroundColor: value === range ? 'var(--accent)' : 'transparent',
            color: value === range ? 'var(--accent-contrast)' : 'var(--text)',
          }}
        >
          {rangeLabels[range]}
        </button>
      ))}
    </div>
  )
}

function SummaryGrid({ analytics, compact }: { analytics: OwnerAnalytics; compact: boolean }) {
  const cards = [
    {
      label: 'Комментарии',
      value: analytics.summary.comments,
      hint: `${analytics.summary.replies} ответов`,
      icon: <MessageSquare className="h-5 w-5" aria-hidden="true" />,
      tone: 'accent' as const,
    },
    {
      label: 'На модерации',
      value: analytics.summary.pending,
      hint: 'ждут решения',
      icon: <Clock className="h-5 w-5" aria-hidden="true" />,
      tone: 'warning' as const,
    },
    {
      label: 'Одобрено',
      value: analytics.summary.approved,
      hint: 'видно в виджете',
      icon: <Activity className="h-5 w-5" aria-hidden="true" />,
      tone: 'success' as const,
    },
    {
      label: 'Реакции',
      value: analytics.summary.reactions,
      hint: `${analytics.summary.pages} страниц`,
      icon: <Smile className="h-5 w-5" aria-hidden="true" />,
      tone: 'accent' as const,
    },
    {
      label: 'Спам и отклонения',
      value: analytics.summary.spam + analytics.summary.rejected,
      hint: `${analytics.summary.spam} спам`,
      icon: <BarChart3 className="h-5 w-5" aria-hidden="true" />,
      tone: 'danger' as const,
    },
    {
      label: 'Сайты',
      value: analytics.summary.sites,
      hint: compact ? 'в этом проекте' : 'под управлением',
      icon: <Globe className="h-5 w-5" aria-hidden="true" />,
      tone: 'accent' as const,
    },
  ]

  return (
    <div className={`grid gap-4 ${compact ? 'sm:grid-cols-2 xl:grid-cols-3' : 'md:grid-cols-2 xl:grid-cols-3'}`}>
      {cards.map((card) => (
        <MetricCard key={card.label} {...card} />
      ))}
    </div>
  )
}

function MetricCard({
  icon,
  label,
  value,
  hint,
  tone,
}: {
  icon: ReactNode
  label: string
  value: number
  hint: string
  tone: 'accent' | 'success' | 'warning' | 'danger'
}) {
  const reducedMotion = useReducedMotion()

  return (
    <motion.div
      className="cc-card p-5"
      whileHover={reducedMotion ? undefined : { y: -3 }}
      transition={reducedMotion ? undefined : { duration: 0.16 }}
    >
      <div className="flex items-start justify-between gap-4">
        <span
          className="inline-flex h-11 w-11 items-center justify-center rounded-lg"
          style={{ backgroundColor: `var(--${tone}-bg)`, color: `var(--${tone})` }}
        >
          {icon}
        </span>
        <span className="text-3xl font-bold" style={{ color: 'var(--text-h)' }}>
          {value}
        </span>
      </div>
      <p className="mt-4 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
        {label}
      </p>
      <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
        {hint}
      </p>
    </motion.div>
  )
}

function TrendCard({ points }: { points: CommentTimePoint[] }) {
  const max = Math.max(1, ...points.map((point) => point.total))
  const chartPoints = useMemo(() => {
    if (points.length === 0) {
      return ''
    }
    return points
      .map((point, index) => {
        const x = points.length === 1 ? 50 : (index / (points.length - 1)) * 100
        const y = 92 - (point.total / max) * 72
        return `${x},${y}`
      })
      .join(' ')
  }, [max, points])

  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<Activity className="h-5 w-5" aria-hidden="true" />}
        title="Динамика комментариев"
        subtitle="Новые комментарии по выбранному периоду"
      />
      {points.length > 0 ? (
        <div className="mt-5">
          <svg className="h-56 w-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" role="img">
            <polyline
              points={chartPoints}
              fill="none"
              stroke="var(--accent)"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2.4"
              vectorEffect="non-scaling-stroke"
            />
            {points.map((point, index) => {
              const x = points.length === 1 ? 50 : (index / (points.length - 1)) * 100
              const y = 92 - (point.total / max) * 72
              return (
                <circle
                  key={point.bucket}
                  cx={x}
                  cy={y}
                  r="1.6"
                  fill="var(--surface)"
                  stroke="var(--accent)"
                  strokeWidth="1.4"
                  vectorEffect="non-scaling-stroke"
                />
              )
            })}
          </svg>
          <div className="mt-3 grid grid-cols-3 gap-2 text-xs" style={{ color: 'var(--text)' }}>
            <span>{formatBucket(points[0]?.bucket)}</span>
            <span className="text-center">max {max}</span>
            <span className="text-right">{formatBucket(points.at(-1)?.bucket)}</span>
          </div>
        </div>
      ) : (
        <EmptyAnalytics message="Пока нет комментариев в этом периоде." />
      )}
    </article>
  )
}

function FunnelCard({ funnel }: { funnel: ModerationStatusCount[] }) {
  const max = Math.max(1, ...funnel.map((item) => item.count))

  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<BarChart3 className="h-5 w-5" aria-hidden="true" />}
        title="Воронка модерации"
        subtitle="Как распределены статусы комментариев"
      />
      <div className="mt-5 space-y-3">
        {funnel.map((item) => (
          <div key={item.status}>
            <div className="mb-1 flex items-center justify-between gap-3">
              <Badge tone={statusTones[item.status]}>{statusLabels[item.status]}</Badge>
              <span className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                {item.count}
              </span>
            </div>
            <div className="h-2 rounded-full" style={{ backgroundColor: 'var(--surface-muted)' }}>
              <div
                className="h-full rounded-full transition-all"
                style={{
                  width: item.count === 0 ? 0 : `${Math.max(4, (item.count / max) * 100)}%`,
                  backgroundColor: `var(--${statusTones[item.status] === 'muted' ? 'border-strong' : statusTones[item.status]})`,
                }}
              />
            </div>
          </div>
        ))}
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
        title="Реакции"
        subtitle="Что пользователи ставят под комментариями"
      />
      {reactions.length > 0 ? (
        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          {reactions.map((reaction) => {
            const meta = reactionMeta[reaction.type]
            return (
              <div
                key={reaction.type}
                className="rounded-lg border p-4"
                style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="text-2xl" aria-hidden="true">
                    {meta.emoji}
                  </span>
                  <span className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
                    {reaction.count}
                  </span>
                </div>
                <p className="mt-2 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                  {meta.label}
                </p>
                <p className="text-xs" style={{ color: 'var(--text)' }}>
                  {total > 0 ? Math.round((reaction.count / total) * 100) : 0}% всех реакций
                </p>
              </div>
            )
          })}
        </div>
      ) : (
        <EmptyAnalytics message="Реакций пока нет." />
      )}
    </article>
  )
}

function TopPagesCard({ pages }: { pages: TopPageAnalytics[] }) {
  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<MousePointerClick className="h-5 w-5" aria-hidden="true" />}
        title="Самые обсуждаемые страницы"
        subtitle="Где пользователи чаще всего вступают в диалог"
      />
      {pages.length > 0 ? (
        <div className="mt-5 space-y-3">
          {pages.map((page, index) => (
            <div
              key={page.pageId}
              className="rounded-lg border p-4"
              style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
            >
              <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-bold" style={{ color: 'var(--text-h)' }}>
                    #{index + 1} {page.siteName}
                  </p>
                  <p className="mt-1 truncate text-sm" title={page.pageUrl} style={{ color: 'var(--text)' }}>
                    {page.pageUrl}
                  </p>
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                  <Badge tone="accent">{page.comments} комм.</Badge>
                  <Badge tone="success">{page.approved} одобр.</Badge>
                  {page.pending > 0 && <Badge tone="warning">{page.pending} ждут</Badge>}
                  {page.spam > 0 && <Badge tone="danger">{page.spam} spam</Badge>}
                </div>
              </div>
              <p className="mt-3 text-xs" style={{ color: 'var(--text)' }}>
                {page.replies} ответов · {page.reactions} реакций · последняя активность{' '}
                {formatDateTime(page.lastCommentAt)}
              </p>
            </div>
          ))}
        </div>
      ) : (
        <EmptyAnalytics message="Пока нет страниц с обсуждениями." />
      )}
    </article>
  )
}

function ActiveCommentersCard({ commenters }: { commenters: ActiveCommenter[] }) {
  return (
    <article className="cc-card p-5 md:p-6">
      <CardHeader
        icon={<Users className="h-5 w-5" aria-hidden="true" />}
        title="Активные комментаторы"
        subtitle="Кто чаще всего участвует в обсуждениях"
      />
      {commenters.length > 0 ? (
        <div className="mt-5 grid gap-3 lg:grid-cols-2">
          {commenters.map((commenter) => {
            const label = commenter.displayName || commenter.email || 'Анонимный автор'
            return (
              <div
                key={`${commenter.userId ?? commenter.email}-${commenter.lastActivityAt}`}
                className="rounded-lg border p-4"
                style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-bold" style={{ color: 'var(--text-h)' }}>
                      {label}
                    </p>
                    {commenter.email && (
                      <p className="truncate text-xs" style={{ color: 'var(--text)' }}>
                        {commenter.email}
                      </p>
                    )}
                  </div>
                  <Badge tone="accent">{commenter.comments} комм.</Badge>
                </div>
                <p className="mt-3 text-xs" style={{ color: 'var(--text)' }}>
                  {commenter.approved} одобрено · {commenter.pending} на проверке · {commenter.rejectedOrSpam}{' '}
                  отклонено/spam · {formatDateTime(commenter.lastActivityAt)}
                </p>
              </div>
            )
          })}
        </div>
      ) : (
        <EmptyAnalytics message="Активных комментаторов пока нет." />
      )}
    </article>
  )
}

function CardHeader({ icon, title, subtitle }: { icon: ReactNode; title: string; subtitle: string }) {
  return (
    <div className="flex items-start gap-3">
      <span
        className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
      >
        {icon}
      </span>
      <div>
        <h3 className="font-semibold" style={{ color: 'var(--text-h)' }}>
          {title}
        </h3>
        <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
          {subtitle}
        </p>
      </div>
    </div>
  )
}

function EmptyAnalytics({ message }: { message: string }) {
  return (
    <div
      className="mt-5 rounded-lg border border-dashed px-4 py-8 text-center text-sm"
      style={{ borderColor: 'var(--border)', color: 'var(--text)' }}
    >
      {message}
    </div>
  )
}

function formatBucket(value?: string) {
  if (!value) {
    return '—'
  }
  return dateFormatter.format(new Date(`${value}T00:00:00Z`))
}

function formatDateTime(value: string | null) {
  if (!value) {
    return 'нет данных'
  }
  return dateTimeFormatter.format(new Date(value))
}

import { useCallback, useState } from 'react'
import { useReducedMotion } from 'framer-motion'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts'

import type { AnalyticsBucketGranularity, CommentTimePoint } from '../../types/api'
import { formatAnalyticsBucket } from './analyticsFormat'

interface AnalyticsTrendChartProps {
  points: CommentTimePoint[]
  granularity: AnalyticsBucketGranularity
}

interface RechartsPointProps {
  cx?: number
  cy?: number
  index?: number
  payload?: CommentTimePoint
}

interface FocusablePointProps extends RechartsPointProps {
  granularity: AnalyticsBucketGranularity
  persistent: boolean
}

function FocusablePoint({
  cx,
  cy,
  index,
  payload,
  granularity,
  persistent,
}: FocusablePointProps) {
  const [focused, setFocused] = useState(false)
  if (cx === undefined || cy === undefined || index === undefined || !payload) return <></>
  const visible = persistent || focused
  const label = `${formatAnalyticsBucket(payload.bucket, granularity)}: ${commentCountLabel(payload.total)}`

  return (
    <>
      <circle
        cx={cx}
        cy={cy}
        r={persistent && !focused ? 5 : focused ? 4 : 8}
        fill={persistent && !focused ? 'var(--accent)' : 'var(--surface)'}
        stroke="var(--accent)"
        strokeWidth={focused ? 3 : persistent ? 2 : 0}
        opacity={visible ? 1 : 0}
        tabIndex={0}
        role="img"
        aria-label={label}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
      {persistent && (
        <text
          x={cx}
          y={cy - 12}
          textAnchor="middle"
          fill="var(--text-h)"
          fontSize={12}
          fontWeight={700}
          aria-hidden="true"
        >
          {payload.total}
        </text>
      )}
      {focused && (
        <foreignObject
          x={Math.max(4, cx - 176)}
          y={cy > 96 ? cy - 88 : cy + 10}
          width={172}
          height={78}
          pointerEvents="none"
        >
          <div
            className="rounded-lg border px-3 py-2 text-sm shadow-md"
            style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
            role="status"
            aria-live="polite"
          >
            <strong className="block">{formatAnalyticsBucket(payload.bucket, granularity)}</strong>
            <span>{commentCountLabel(payload.total)}</span>
          </div>
        </foreignObject>
      )}
    </>
  )
}

export default function AnalyticsTrendChart({ points, granularity }: AnalyticsTrendChartProps) {
  const reducedMotion = useReducedMotion()
  const maxValue = Math.max(2, ...points.map((point) => point.total))
  const persistentPoint = points.length === 1
  const renderPoint = useCallback((props: RechartsPointProps) => (
    <FocusablePoint
      {...props}
      granularity={granularity}
      persistent={persistentPoint}
    />
  ), [granularity, persistentPoint])

  return (
    <div
      className="relative h-72 w-full"
      role="group"
      aria-label="График динамики комментариев"
      aria-describedby="analytics-axis-description"
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart
          data={points}
          margin={{ top: 12, right: 12, bottom: 12, left: 2 }}
          accessibilityLayer
        >
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="3 4" />
          <XAxis
            dataKey="bucket"
            tickFormatter={(value: string) => formatAnalyticsBucket(value, granularity)}
            tick={{ fill: 'var(--text)', fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: 'var(--border-strong)' }}
            minTickGap={28}
          />
          <YAxis
            allowDecimals={false}
            domain={[0, maxValue]}
            width={42}
            tick={{ fill: 'var(--text)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            content={(props) => <AnalyticsTooltip {...props} granularity={granularity} />}
            cursor={{ stroke: 'var(--border-strong)', strokeDasharray: '3 4' }}
            isAnimationActive={!reducedMotion}
          />
          <Line
            type="stepAfter"
            dataKey="total"
            name="Комментарии"
            stroke="var(--accent)"
            strokeWidth={2.5}
            dot={renderPoint}
            activeDot={{ r: 4, fill: 'var(--surface)', stroke: 'var(--accent)', strokeWidth: 3 }}
            isAnimationActive={!reducedMotion}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function commentCountLabel(value: number): string {
  const mod10 = value % 10
  const mod100 = value % 100
  if (mod10 === 1 && mod100 !== 11) return `${value} комментарий`
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${value} комментария`
  return `${value} комментариев`
}

function AnalyticsTooltip({
  active,
  payload,
  granularity,
}: TooltipContentProps & { granularity: AnalyticsBucketGranularity }) {
  const point = payload?.[0]?.payload as CommentTimePoint | undefined
  if (!active || !point) return null

  return <PointDetails point={point} granularity={granularity} />
}

function PointDetails({ point, granularity }: { point: CommentTimePoint; granularity: AnalyticsBucketGranularity }) {
  return (
    <div
      className="rounded-lg border px-3 py-2 text-sm shadow-md"
      style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
      role="status"
    >
      <p className="font-semibold" style={{ color: 'var(--text-h)' }}>
        {formatAnalyticsBucket(point.bucket, granularity)}
      </p>
      <dl className="mt-2 grid grid-cols-[1fr_auto] gap-x-4 gap-y-1" style={{ color: 'var(--text)' }}>
        <dt>Всего</dt><dd className="font-semibold">{point.total}</dd>
        <dt>Одобрено</dt><dd>{point.approved}</dd>
        <dt>Ожидает решения</dt><dd>{point.pending}</dd>
        <dt>Спам</dt><dd>{point.spam}</dd>
      </dl>
    </div>
  )
}

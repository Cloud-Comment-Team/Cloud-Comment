import type { Meta, StoryObj } from '@storybook/react-vite'

import type { AnalyticsBucketGranularity, CommentTimePoint } from '../../types/api'
import AnalyticsTrendChart from './AnalyticsTrendChart'

const meta = {
  title: 'Аналитика/Динамика комментариев',
  component: AnalyticsTrendChart,
  parameters: {
    layout: 'padded',
  },
  decorators: [
    (Story) => (
      <section className="cc-card max-w-5xl p-5 md:p-6">
        <h2 className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>
          Динамика комментариев
        </h2>
        <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
          Значения доступны с клавиатуры, а точки появляются только при наведении или фокусе.
        </p>
        <div className="mt-5">
          <Story />
        </div>
      </section>
    ),
  ],
} satisfies Meta<typeof AnalyticsTrendChart>

export default meta
type Story = StoryObj<typeof meta>

function point(bucket: string, total: number): CommentTimePoint {
  const pending = Math.min(total, Math.floor(total / 4))
  const spam = Math.min(total - pending, Math.floor(total / 8))
  return {
    bucket,
    total,
    approved: Math.max(0, total - pending - spam),
    pending,
    spam,
  }
}

function story(points: CommentTimePoint[], granularity: AnalyticsBucketGranularity): Story {
  return { args: { points, granularity } }
}

export const EmptyData = story([], 'DAY')

export const OneValue = story([point('2026-07-13', 1)], 'DAY')

export const TwoValues = story([
  point('2026-07-12', 1),
  point('2026-07-13', 2),
], 'DAY')

export const NinetyDaysByWeek = story([
  point('2026-04-13', 3),
  point('2026-04-20', 8),
  point('2026-04-27', 5),
  point('2026-05-04', 13),
  point('2026-05-11', 21),
  point('2026-05-18', 17),
  point('2026-05-25', 34),
  point('2026-06-01', 27),
  point('2026-06-08', 44),
  point('2026-06-15', 39),
  point('2026-06-22', 58),
  point('2026-06-29', 51),
  point('2026-07-06', 63),
], 'WEEK')

export const AllTimeByMonth = story([
  point('2026-01-01', 12),
  point('2026-02-01', 31),
  point('2026-03-01', 26),
  point('2026-04-01', 55),
  point('2026-05-01', 72),
  point('2026-06-01', 88),
  point('2026-07-01', 41),
], 'MONTH')

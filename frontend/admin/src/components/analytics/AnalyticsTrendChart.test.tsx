import { cloneElement, type ReactElement } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal<typeof import('recharts')>()
  return {
    ...original,
    ResponsiveContainer: ({ children }: { children: ReactElement<{ width?: number; height?: number }> }) => cloneElement(children, {
      width: 800,
      height: 288,
    }),
  }
})

import AnalyticsTrendChart from './AnalyticsTrendChart'

const points = [
  { bucket: '2026-07-12', total: 1, approved: 1, pending: 0, spam: 0 },
  { bucket: '2026-07-13', total: 2, approved: 1, pending: 1, spam: 0 },
]

describe('график динамики комментариев', () => {
  it('объясняет оси и оставляет значения доступными с клавиатуры без постоянных точек', async () => {
    const user = userEvent.setup()
    render(
      <>
        <p id="analytics-axis-description">Описание осей</p>
        <AnalyticsTrendChart points={points} granularity="DAY" />
      </>,
    )

    const chart = screen.getByRole('group', { name: 'График динамики комментариев' })
    expect(chart).toHaveAttribute('aria-describedby', 'analytics-axis-description')

    const firstPoint = await screen.findByRole('img', { name: /12.*июл.*1 комментарий/i })
    const secondPoint = screen.getByRole('img', { name: /13.*июл.*2 комментария/i })
    expect(firstPoint).toHaveAttribute('tabindex', '0')
    expect(firstPoint).toHaveAttribute('opacity', '0')
    expect(secondPoint).toHaveAttribute('opacity', '0')

    await user.tab()
    expect(document.activeElement).toHaveClass('recharts-surface')
    await user.tab()
    await waitFor(() => expect(firstPoint).toHaveAttribute('opacity', '1'))
    expect(document.activeElement).toBe(firstPoint)
    expect(firstPoint).toBe(screen.getByRole('img', { name: /12.*июл.*1 комментарий/i }))

    await user.tab()
    expect(document.activeElement).toBe(secondPoint)
    await waitFor(() => expect(secondPoint).toHaveAttribute('opacity', '1'))
    expect(screen.getByRole('status')).toHaveTextContent('2 комментария')

    await user.tab({ shift: true })
    expect(document.activeElement).toBe(firstPoint)
    await waitFor(() => expect(firstPoint).toHaveAttribute('opacity', '1'))
  })

  it('постоянно показывает и подписывает единственное значение', async () => {
    const { container } = render(
      <>
        <p id="analytics-axis-description">Описание осей</p>
        <AnalyticsTrendChart points={[points[0]]} granularity="DAY" />
      </>,
    )

    const point = await screen.findByRole('img', { name: /12.*июл.*1 комментарий/i })
    expect(point).toHaveAttribute('opacity', '1')
    expect(point).toHaveAttribute('fill', 'var(--accent)')
    expect(container.querySelector('.recharts-line-dots text')).toHaveTextContent('1')
  })
})

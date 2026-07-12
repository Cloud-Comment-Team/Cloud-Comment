import { describe, expect, it } from 'vitest'

import {
  analyticsRangeLabel,
  comparisonDescription,
  comparisonLabel,
  formatAnalyticsBucket,
  formatWaitingTime,
  moderationHref,
  moderationViewForStatus,
} from './analyticsFormat'

describe('форматирование аналитики', () => {
  it('подписывает дневные, недельные и месячные интервалы', () => {
    expect(formatAnalyticsBucket('2026-07-13', 'DAY')).toMatch(/13.*июл/i)
    expect(formatAnalyticsBucket('2026-07-06', 'WEEK')).toMatch(/06.*июл/i)
    expect(formatAnalyticsBucket('2026-07-01', 'MONTH')).toMatch(/июл.*2026/i)
  })

  it('не изображает сравнение для всего времени', () => {
    expect(comparisonDescription(undefined, 'all')).toBe('Сравнение недоступно для всего времени')
    expect(comparisonDescription(undefined, '30d')).toBe('Нет предыдущего периода')
    expect(comparisonLabel(undefined)).toBe('Без изменений')
  })

  it('показывает рост относительно нулевого периода как новое значение', () => {
    const comparison = {
      current: 2,
      previous: 0,
      absoluteChange: 2,
      percentageChange: null,
    }
    expect(comparisonLabel(comparison)).toBe('Новое')
    expect(comparisonDescription(comparison, '30d')).toContain('Новое')
  })

  it('считает ожидание относительно времени ответа сервера', () => {
    expect(formatWaitingTime(null, '2026-07-13T12:00:00Z')).toBe('Очередь разобрана')
    expect(formatWaitingTime('2026-07-12T09:30:00Z', '2026-07-13T12:00:00Z')).toBe(
      'Самый старый ждёт 1 дн. 2 ч.',
    )
  })

  it('создаёт предсказуемые ссылки на отфильтрованную модерацию', () => {
    expect(moderationViewForStatus('PENDING')).toBe('pending')
    expect(moderationViewForStatus('SPAM')).toBe('spam')
    expect(moderationViewForStatus('APPROVED')).toBe('history')
    expect(moderationHref({
      view: 'pending',
      status: 'PENDING',
      siteId: '00000000-0000-0000-0000-000000000132',
      pageUrl: 'https://example.test/страница?a=1',
    })).toBe(
      '/moderation?view=pending&source=analytics&status=PENDING&siteId=00000000-0000-0000-0000-000000000132&pageUrl=https%3A%2F%2Fexample.test%2F%D1%81%D1%82%D1%80%D0%B0%D0%BD%D0%B8%D1%86%D0%B0%3Fa%3D1',
    )
  })

  it('сохраняет русские подписи периодов', () => {
    expect(analyticsRangeLabel('7d')).toBe('7 дней')
    expect(analyticsRangeLabel('all')).toBe('Всё время')
  })
})

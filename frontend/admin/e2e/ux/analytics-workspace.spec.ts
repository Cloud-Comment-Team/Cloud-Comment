import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

import type {
  AnalyticsBucketGranularity,
  AnalyticsComparison,
  AnalyticsRange,
  CommentTimePoint,
  OwnerAnalytics,
} from '../../src/types/api'

const OWNER_ID = '00000000-0000-0000-0000-000000000001'
const SITE_ID = '00000000-0000-0000-0000-000000000132'

async function authenticate(page: Page) {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: {
      id: OWNER_ID,
      email: 'owner@example.test',
      roles: ['OWNER'],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.goto('/login')
  await page.evaluate(() => localStorage.setItem('cloud-comment.admin.authToken', 'test-token'))
}

function point(bucket: string, total: number, pending = 0, spam = 0): CommentTimePoint {
  return {
    bucket,
    total,
    approved: Math.max(0, total - pending - spam),
    pending,
    spam,
  }
}

function metric(current: number, previous: number) {
  const absoluteChange = current - previous
  return {
    current,
    previous,
    absoluteChange,
    percentageChange: previous === 0 ? null : (absoluteChange / previous) * 100,
  }
}

function comparison(comments: number): AnalyticsComparison {
  return {
    previousFrom: '2026-05-15T00:00:00Z',
    previousTo: '2026-06-13T23:59:59Z',
    comments: metric(comments, Math.max(0, comments - 2)),
    reactions: metric(4, 2),
    automaticDecisions: metric(8, 4),
    manualDecisions: metric(3, 5),
    undoActions: metric(1, 0),
  }
}

function analytics({
  range = '30d',
  bucketGranularity = 'DAY',
  points = [],
  comparisonValue = comparison(points.reduce((sum, item) => sum + item.total, 0)),
  siteId = null,
  withDetails = false,
}: {
  range?: AnalyticsRange
  bucketGranularity?: AnalyticsBucketGranularity
  points?: CommentTimePoint[]
  comparisonValue?: AnalyticsComparison | null
  siteId?: string | null
  withDetails?: boolean
} = {}): OwnerAnalytics {
  const comments = points.reduce((sum, item) => sum + item.total, 0)
  const pending = points.reduce((sum, item) => sum + item.pending, 0)
  const spam = points.reduce((sum, item) => sum + item.spam, 0)
  const moderationDistribution = [
    { status: 'PENDING' as const, count: pending },
    { status: 'APPROVED' as const, count: Math.max(0, comments - pending - spam) },
    { status: 'REJECTED' as const, count: 0 },
    { status: 'HIDDEN' as const, count: 0 },
    { status: 'SPAM' as const, count: spam },
  ]

  return {
    range,
    siteId,
    timeZone: 'Europe/Moscow',
    bucketGranularity,
    from: range === 'all' ? null : '2026-06-14T00:00:00Z',
    to: '2026-07-13T12:00:00Z',
    summary: {
      sites: siteId ? 1 : 3,
      pages: comments > 0 ? 1 : 0,
      comments,
      replies: 0,
      reactions: comments > 0 ? 4 : 0,
      pending,
      approved: Math.max(0, comments - pending - spam),
      rejected: 0,
      hidden: 0,
      spam,
    },
    comparison: comparisonValue,
    workload: {
      requiringDecision: pending + spam,
      oldestPendingAt: pending + spam > 0 ? '2026-07-12T09:30:00Z' : null,
      automaticDecisions: comments > 0 ? 8 : 0,
      manualDecisions: comments > 0 ? 3 : 0,
      undoActions: comments > 0 ? 1 : 0,
    },
    commentsOverTime: points,
    moderationDistribution,
    moderationFunnel: moderationDistribution,
    reactionDistribution: comments > 0 ? [{ type: 'LIKE', count: 4 }] : [],
    topPages: withDetails ? [{
      pageId: '00000000-0000-0000-0000-000000000232',
      siteId: SITE_ID,
      siteName: 'Документация',
      pageUrl: 'https://example.test/guide?tab=comments',
      comments,
      replies: 2,
      reactions: 4,
      approved: Math.max(0, comments - pending - spam),
      pending,
      spam,
      lastCommentAt: '2026-07-13T10:00:00Z',
    }] : [],
    activeCommenters: [],
  }
}

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).disableRules(['color-contrast']).analyze()
  expect(results.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([])
}

test('панель передаёт часовой пояс и остаётся доступной для одного и двух интервалов', async ({ page }) => {
  await authenticate(page)
  const requestedTimeZones: string[] = []
  await page.route('**/api/analytics/owner**', (route) => {
    const requestUrl = new URL(route.request().url())
    requestedTimeZones.push(requestUrl.searchParams.get('timeZone') ?? '')
    const range = (requestUrl.searchParams.get('range') ?? '30d') as AnalyticsRange
    const points = range === '7d'
      ? [point('2026-07-12', 1), point('2026-07-13', 2, 1)]
      : [point('2026-07-13', 1)]
    return route.fulfill({ json: analytics({ range, points }) })
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Рабочий обзор' })).toBeVisible()
  await expect(page.getByText('Периоды рассчитаны в часовом поясе Europe/Moscow')).toBeVisible()
  await expect.poll(() => requestedTimeZones[0]).not.toBe('')
  expect(requestedTimeZones[0]).toMatch(/^[A-Za-z_+-]+(?:\/[A-Za-z_+-]+)*$/)

  const focusedPoint = page.locator('circle[tabindex="0"]').first()
  await expect(focusedPoint).toBeVisible()
  await expect(focusedPoint).toHaveCSS('opacity', '1')
  await focusedPoint.focus()
  await expect(focusedPoint).toHaveCSS('opacity', '1')
  await expect(focusedPoint).toHaveAttribute('aria-label', /1 комментарий/)

  await page.getByText('Показать данные таблицей', { exact: true }).click()
  const table = page.getByRole('table', { name: 'Комментарии по интервалам выбранного периода' })
  await expect(table.getByRole('row')).toHaveCount(2)

  await page.getByRole('button', { name: '7 дней' }).click()
  await expect(page.getByRole('button', { name: '7 дней' })).toHaveAttribute('aria-pressed', 'true')
  const reloadedTableSummary = page.getByText('Показать данные таблицей', { exact: true })
  await expect(reloadedTableSummary).toBeVisible()
  if (await page.locator('details').getAttribute('open') === null) {
    await reloadedTableSummary.click()
  }
  await expect(page.getByRole('table', { name: 'Комментарии по интервалам выбранного периода' }).getByRole('row')).toHaveCount(3)
  await expect(page.locator('circle[tabindex="0"]')).toHaveCount(2)
  await expect(page.locator('circle[tabindex="0"]').first()).toHaveCSS('opacity', '0')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)
  await expectNoSeriousAccessibilityViolations(page)
})

test('нулевые данные показывают полезный следующий шаг и пустую доступную таблицу', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop-light')
  await authenticate(page)
  await page.route('**/api/analytics/owner**', (route) => route.fulfill({
    json: analytics({ points: [], comparisonValue: null }),
  }))

  await page.goto('/')
  await expect(page.getByText('Комментариев за период пока нет')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Перейти к сайтам' })).toHaveAttribute('href', '/sites')
  await expect(page.getByText('Очередь разобрана')).toBeVisible()
  await expect(page.getByText('Нет предыдущего периода')).toHaveCount(2)
  await page.getByText('Показать данные таблицей', { exact: true }).click()
  await expect(page.getByRole('table', { name: 'Комментарии по интервалам выбранного периода' }).getByRole('row')).toHaveCount(1)
  await expectNoSeriousAccessibilityViolations(page)
})

test('большой набор меняет гранулярность и ведёт на отфильтрованные экраны', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop-light')
  await authenticate(page)
  const weeklyPoints = [
    '2026-04-13', '2026-04-20', '2026-04-27', '2026-05-04', '2026-05-11',
    '2026-05-18', '2026-05-25', '2026-06-01', '2026-06-08', '2026-06-15',
    '2026-06-22', '2026-06-29', '2026-07-06',
  ].map((bucket, index) => point(bucket, [3, 8, 5, 13, 21, 17, 34, 27, 44, 39, 58, 51, 63][index], index === 12 ? 2 : 0, index === 12 ? 1 : 0))
  const monthlyPoints = ['01', '02', '03', '04', '05', '06', '07']
    .map((month, index) => point(`2026-${month}-01`, (index + 1) * 12))

  await page.route('**/api/analytics/owner**', (route) => {
    const range = (new URL(route.request().url()).searchParams.get('range') ?? '30d') as AnalyticsRange
    if (range === '90d') {
      return route.fulfill({ json: analytics({ range, bucketGranularity: 'WEEK', points: weeklyPoints, withDetails: true }) })
    }
    if (range === 'all') {
      return route.fulfill({ json: analytics({ range, bucketGranularity: 'MONTH', points: monthlyPoints, comparisonValue: null }) })
    }
    return route.fulfill({ json: analytics({ range, points: [point('2026-07-13', 2, 1)] }) })
  })

  await page.goto('/')
  await page.getByRole('button', { name: '90 дней' }).click()
  await expect(page.getByText('Интервал: неделя')).toBeVisible()
  await page.getByText('Показать данные таблицей', { exact: true }).click()
  await expect(page.getByRole('table', { name: 'Комментарии по интервалам выбранного периода' }).getByRole('row')).toHaveCount(14)
  await expect(page.getByRole('link', { name: 'Открыть очередь' })).toHaveAttribute('href', '/moderation?view=pending&source=analytics')
  await expect(page.getByRole('link', { name: /Ожидает решения: 2\. Открыть отфильтрованный экран/ })).toHaveAttribute(
    'href',
    '/moderation?view=pending&source=analytics&status=PENDING',
  )
  await expect(page.getByRole('link', { name: /#1 Документация/ })).toHaveAttribute(
    'href',
    `/moderation?view=pending&source=analytics&status=PENDING&siteId=${SITE_ID}&pageUrl=https%3A%2F%2Fexample.test%2Fguide%3Ftab%3Dcomments`,
  )

  await page.getByRole('button', { name: 'Всё время' }).click()
  await expect(page.getByText('Интервал: месяц')).toBeVisible()
  await expect(page.getByText('Сравнение недоступно для всего времени')).toHaveCount(2)
  await expectNoSeriousAccessibilityViolations(page)
})

test('аналитическая ссылка очищает сохранённые фильтры и применяет один статус', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop-light')
  await authenticate(page)
  const commentRequests: URL[] = []
  await page.route('**/api/sites', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/moderation/counts', (route) => route.fulfill({ json: {
    statuses: { PENDING: 0, APPROVED: 0, REJECTED: 0, HIDDEN: 0, SPAM: 0 },
    requiringDecision: 0,
  } }))
  await page.route('**/api/moderation/comments**', (route) => {
    commentRequests.push(new URL(route.request().url()))
    return route.fulfill({ json: { items: [], page: 1, pageSize: 30, totalItems: 0, totalPages: 0 } })
  })

  await page.evaluate(() => localStorage.setItem('cloud-comment:moderation-filters:v2', JSON.stringify({
    siteId: '00000000-0000-0000-0000-000000000099',
    pageUrl: 'https://saved.example.test/old-page',
    search: 'скрывающий запрос',
    favorite: true,
  })))

  await page.goto('/moderation?view=pending&source=analytics&status=PENDING')
  await expect(page.getByText('Точный фильтр:')).toBeVisible()
  await expect.poll(() => commentRequests.length).toBeGreaterThan(0)
  expect(Object.fromEntries(commentRequests.at(-1)!.searchParams)).toEqual({
    status: 'PENDING',
    sortBy: 'SMART',
    sortOrder: 'DESC',
    page: '1',
    pageSize: '30',
  })

  const requestsBeforeSiteLink = commentRequests.length
  await page.goto(`/moderation?view=pending&source=analytics&status=PENDING&siteId=${SITE_ID}`)
  await expect.poll(() => commentRequests.length).toBeGreaterThan(requestsBeforeSiteLink)
  expect(Object.fromEntries(commentRequests.at(-1)!.searchParams)).toEqual({
    siteId: SITE_ID,
    status: 'PENDING',
    sortBy: 'SMART',
    sortOrder: 'DESC',
    page: '1',
    pageSize: '30',
  })

  const requestsBeforeViewChange = commentRequests.length
  await page.getByRole('button', { name: /^Спам \(0\)$/ }).click()
  await expect(page).toHaveURL(new RegExp(`view=spam(?![^#]*status=)`))
  await expect.poll(() => commentRequests.length).toBeGreaterThan(requestsBeforeViewChange)
  expect(Object.fromEntries(commentRequests.at(-1)!.searchParams)).toEqual({
    siteId: SITE_ID,
    statuses: 'SPAM',
    sortBy: 'SMART',
    sortOrder: 'DESC',
    page: '1',
    pageSize: '30',
  })
})

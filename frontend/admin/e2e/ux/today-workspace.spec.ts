import { expect, test, type Page } from '@playwright/test'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

const OWNER_ID = '00000000-0000-0000-0000-000000000001'
const SITE_ID = '00000000-0000-0000-0000-000000000209'

async function authenticate(page: Page) {
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'offline-csrf-token' } }))
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: { id: OWNER_ID, email: 'owner@example.test', roles: ['OWNER'] } }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({ json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 } }))
}

test('«Сегодня» ведёт к первому действию и не возвращает графики на главную', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/sites?*', (route) => route.fulfill({ json: {
    items: [{ id: SITE_ID, ownerId: OWNER_ID, name: 'Редакция', domain: 'example.test', isActive: true }],
    page: 1, pageSize: 100, totalItems: 1, totalPages: 1,
  } }))
  await page.route(`**/api/sites/${SITE_ID}/installation-status`, (route) => route.fulfill({ json: {
    status: 'REJECTED', reason: 'ORIGIN_REJECTED', siteCreated: true, originConfigured: true,
    widgetSeen: false, firstCommentReceived: false, lastSuccessfulOrigin: null, lastSuccessfulAt: null,
    lastRejectedOrigin: 'https://wrong.example', lastRejectedAt: '2026-07-13T12:00:00Z',
  } }))
  await page.route('**/api/moderation/counts', (route) => route.fulfill({ json: {
    requiringDecision: 4, statuses: { PENDING: 3, SPAM: 1, APPROVED: 0, REJECTED: 0, HIDDEN: 0 },
  } }))
  await page.route('**/api/discussions?*', (route) => route.fulfill({ json: {
    items: [{
      rootCommentId: '00000000-0000-0000-0000-000000000210', siteId: SITE_ID, siteName: 'Редакция',
      pageId: 'page', pageUrl: 'https://example.test/article', pageTitle: 'Материал дня',
      lastAuthor: { id: null, displayName: 'Анна', owner: false }, lastMessage: 'Нужна дополнительная ссылка',
      lastActivityAt: '2026-07-13T12:30:00Z', replyCount: 2, unread: true, status: 'NEEDS_REPLY', pinned: false,
    }], page: 1, pageSize: 5, totalItems: 1, totalPages: 1,
  } }))

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Сегодня' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Требует внимания' })).toBeVisible()
  await expect(page.locator(`a[href="/sites/${SITE_ID}?section=installation"]`)).toHaveAttribute('href', `/sites/${SITE_ID}?section=installation`)
  await expect(page.getByRole('link', { name: /Разобрать/ })).toHaveAttribute('href', '/moderation')
  await expect(page.getByText('Материал дня')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Быстрые действия' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Динамика комментариев' })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)
  await expectNoSeriousAccessibilityViolations(page)
})

test('ошибка одного источника не скрывает остальные блоки', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/sites?*', (route) => route.fulfill({ json: { items: [], page: 1, pageSize: 100, totalItems: 0, totalPages: 0 } }))
  await page.route('**/api/moderation/counts', (route) => route.fulfill({ status: 503, json: { message: 'offline' } }))
  await page.route('**/api/discussions?*', (route) => route.fulfill({ json: { items: [], page: 1, pageSize: 5, totalItems: 0, totalPages: 0 } }))

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Подключите первый сайт' })).toBeVisible()
  await expect(page.getByText('Часть данных не обновилась')).toBeVisible()
})

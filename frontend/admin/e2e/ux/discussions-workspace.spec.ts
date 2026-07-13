import { expect, test, type Page } from '@playwright/test'

import type { DiscussionSummary, DiscussionThread } from '../../src/types/api'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

const OWNER_ID = '00000000-0000-4000-8000-000000000001'
const SITE_ID = '00000000-0000-4000-8000-000000000174'
const ROOT_ID = '00000000-0000-4000-8000-000000000175'

const summary: DiscussionSummary = {
  rootCommentId: ROOT_ID,
  siteId: SITE_ID,
  siteName: 'Редакция',
  pageId: '00000000-0000-4000-8000-000000000176',
  pageUrl: 'https://example.test/article',
  pageTitle: 'Новая статья',
  lastAuthor: { id: null, displayName: 'Анна', owner: false },
  lastMessage: 'Интересная публикация про продукт',
  lastActivityAt: '2026-07-13T12:00:00Z',
  replyCount: 1,
  unread: true,
  status: 'NEEDS_REPLY',
  pinned: false,
}

const thread: DiscussionThread = {
  summary,
  messages: [
    {
      id: ROOT_ID,
      parentId: null,
      author: { id: null, displayName: 'Анна', owner: false },
      content: 'Интересная публикация про продукт',
      createdAt: '2026-07-13T11:55:00Z',
      updatedAt: '2026-07-13T11:55:00Z',
      pinned: false,
    },
    {
      id: '00000000-0000-4000-8000-000000000177',
      parentId: ROOT_ID,
      author: { id: OWNER_ID, displayName: 'Редакция', owner: true },
      content: 'Спасибо, что написали нам.',
      createdAt: '2026-07-13T12:00:00Z',
      updatedAt: '2026-07-13T12:00:00Z',
      pinned: false,
    },
  ],
}

async function authenticate(page: Page) {
  await page.route('**/api/auth/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', token: 'offline-csrf-token' },
  }))
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
  await page.route('**/api/sites?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 100, totalItems: 0, totalPages: 0 },
  }))
}

test('master-detail обсуждений сохраняет фильтры и работает как отдельный экран на узкой ширине', async ({ page }) => {
  await authenticate(page)
  const listRequests: URL[] = []
  await page.route(`**/api/discussions/${ROOT_ID}`, (route) => route.fulfill({ json: thread }))
  await page.route('**/api/discussions?**', (route) => {
    listRequests.push(new URL(route.request().url()))
    return route.fulfill({
      json: { items: [summary], page: 1, pageSize: 30, totalItems: 1, totalPages: 1 },
    })
  })

  await page.goto('/comments?view=UNREAD&search=%D0%BF%D1%80%D0%BE%D0%B4%D1%83%D0%BA%D1%82')
  await expect(page.getByRole('heading', { name: 'Обсуждения', exact: true })).toBeVisible()
  const row = page.getByRole('button', { name: 'Открыть обсуждение: Новая статья' })
  await expect(row).toBeVisible()
  await expect.poll(() => listRequests.length).toBeGreaterThan(0)
  expect(listRequests[0].searchParams.get('view')).toBe('UNREAD')
  expect(listRequests[0].searchParams.get('search')).toBe('продукт')

  await row.click()
  await expect(page).toHaveURL(new RegExp(`discussion=${ROOT_ID}`))
  await expect(page.getByText('Спасибо, что написали нам.')).toBeVisible()
  await expect(page.getByText('Автор сайта')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Открыть страницу' })).toHaveAttribute('href', summary.pageUrl)

  const viewportWidth = page.viewportSize()?.width ?? 0
  if (viewportWidth < 1280) {
    await expect(row).toBeHidden()
    await page.getByRole('button', { name: 'К списку' }).click()
    await expect(row).toBeVisible()
    await expect(page).not.toHaveURL(/discussion=/)
  } else {
    await expect(row).toBeVisible()
    await expect(page.getByRole('button', { name: 'К списку' })).toBeHidden()
  }

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)
  await expectNoSeriousAccessibilityViolations(page)
})

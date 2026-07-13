import { expect, test, type Page } from '@playwright/test'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

const sites = Array.from({ length: 7 }, (_, index) => ({
  id: `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
  ownerId: '00000000-0000-0000-0000-000000000001',
  name: ['Редакция', 'Документация', 'Блог продукта', 'Справочный центр', 'Новости', 'Академия', 'Сообщество'][index],
  domain: `project-${index + 1}.example`,
  publicKey: `public-${index + 1}`,
  moderationMode: index % 2 === 0 ? 'PRE_MODERATION' : 'POST_MODERATION',
  isActive: index !== 5,
  allowedOrigins: [`https://project-${index + 1}.example`],
  createdAt: `2026-07-${String(13 - index).padStart(2, '0')}T09:00:00Z`,
  updatedAt: '2026-07-13T09:00:00Z',
}))

async function authenticate(page: Page) {
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'offline-csrf-token' } }))
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: { id: '00000000-0000-0000-0000-000000000001', email: 'owner@example.test', roles: ['OWNER'], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({ json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 } }))
}

test.beforeEach(async ({ page }) => {
  await authenticate(page)
})

test('список сайтов использует рабочую ширину и единую плотную композицию', async ({ page }) => {
  await page.route('**/api/sites?*', (route) => route.fulfill({
    json: { items: sites, page: 1, pageSize: 20, totalItems: sites.length, totalPages: 1 },
  }))
  await page.goto('/sites')

  await expect(page.getByRole('heading', { level: 1, name: 'Сайты' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Редакция/ })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)
  await expectNoSeriousAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('sites-composition.png', { animations: 'disabled', fullPage: true })
})

test('аккаунт разделяет основные настройки и опасное действие', async ({ page }) => {
  await page.route('**/api/privacy/consent-requirements', (route) => route.fulfill({ json: {
    privacyPolicyVersion: '2026-07-01',
    termsVersion: '2026-07-01',
    privacyPolicyUrl: '/legal/privacy-policy.html',
    termsUrl: '/legal/terms.html',
    personalDataNoticeUrl: '/legal/personal-data-notice.html',
    dataExportInfoUrl: '/legal/data-export.html',
  } }))
  await page.route('**/api/account/deletion-requests/current', (route) => route.fulfill({ status: 404, json: { message: 'Not found' } }))
  await page.goto('/account')

  await expect(page.getByRole('heading', { level: 1, name: 'Аккаунт и конфиденциальность' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Документы и уведомления' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Скачать мои данные' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Удаление аккаунта' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)
  await expectNoSeriousAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('account-composition.png', { animations: 'disabled', fullPage: true })
})

import { expect, test } from '@playwright/test'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', token: 'offline-csrf-token' },
  }))
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: { id: '00000000-0000-0000-0000-000000000001', email: 'owner@example.test', roles: ['OWNER'], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({ json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 } }))
  await page.addInitScript(() => localStorage.setItem('cloud-comment.admin.authToken', 'legacy-token-must-be-removed'))
  await page.goto('/')
  await expect.poll(() => page.evaluate(() => localStorage.getItem('cloud-comment.admin.authToken'))).toBeNull()
})

test('desktop-шапка не дублирует sidebar, а мобильная остаётся функциональной', async ({ page }) => {
  const header = page.locator('header')
  const sidebar = page.locator('aside[aria-label="Основная навигация"]')
  const viewportWidth = page.viewportSize()?.width ?? 0
  const themeButtonName = /Включить (светлую|тёмную) тему/

  await expect(page.getByRole('button', { name: 'Уведомления', exact: true })).toBeVisible()
  await expect(page.locator('button[aria-label="Уведомления"]')).toHaveCount(1)
  await expect(page.locator('button[title*="тему"]')).toHaveCount(1)

  if (viewportWidth >= 1024) {
    await expect(header).toBeHidden()
    await expect(sidebar.getByText('Панель владельца', { exact: true })).toBeVisible()
    await expect(sidebar.getByRole('button', { name: themeButtonName })).toBeVisible()
    await expect(sidebar.getByRole('button', { name: 'Уведомления', exact: true })).toBeVisible()
  } else {
    await expect.poll(async () => {
      const box = await sidebar.boundingBox()
      return box ? box.x + box.width : 0
    }).toBeLessThanOrEqual(1)
    await expect(header.getByText('CloudComment', { exact: true })).toBeVisible()
    await expect(header.getByRole('button', { name: themeButtonName })).toBeVisible()
    await expect(header.getByRole('button', { name: 'Открыть меню' })).toBeVisible()
    await header.getByRole('button', { name: 'Открыть меню' }).click()
    await expect.poll(async () => (await sidebar.boundingBox())?.x ?? -1).toBeGreaterThanOrEqual(0)
  }

  await expectNoSeriousAccessibilityViolations(page)
})

test('кнопка уведомлений не меняет положение при открытии панели', async ({ page }) => {

  const button = page.getByRole('button', { name: 'Уведомления', exact: true })
  await expect(button).toBeVisible()
  const before = await button.boundingBox()
  await button.click()
  await expect(page.getByText('Уведомления', { exact: true }).last()).toBeVisible()
  const after = await button.boundingBox()
  expect(before).not.toBeNull()
  expect(after).not.toBeNull()
  expect(Math.abs((before?.x ?? 0) - (after?.x ?? 0))).toBeLessThanOrEqual(1)
  expect(Math.abs((before?.y ?? 0) - (after?.y ?? 0))).toBeLessThanOrEqual(1)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: 'Центр уведомлений' })).toBeHidden()
  expect(await button.evaluate((element) => element === document.activeElement)).toBe(true)
})

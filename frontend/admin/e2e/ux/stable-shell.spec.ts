import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: { id: '00000000-0000-0000-0000-000000000001', email: 'owner@example.test', roles: ['OWNER'], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.goto('/login')
  await page.evaluate(() => localStorage.setItem('cloud-comment.admin.authToken', 'test-token'))
  await page.goto('/')
})

test('desktop-шапка не дублирует sidebar, а мобильная остаётся функциональной', async ({ page }) => {
  const header = page.locator('header')
  const sidebar = page.locator('aside[aria-label="Основная навигация"]')
  const viewportWidth = page.viewportSize()?.width ?? 0
  const themeButtonName = /Включить (светлую|тёмную) тему/

  await expect(page.getByRole('button', { name: 'Уведомления', exact: true })).toBeVisible()
  await expect(sidebar.getByText('Панель владельца', { exact: true })).toBeVisible()
  await expect(sidebar.getByRole('button', { name: themeButtonName })).toBeVisible()

  if (viewportWidth >= 1024) {
    await expect(header.getByText('CloudComment', { exact: true })).toBeHidden()
    await expect(header.getByRole('button', { name: themeButtonName })).toBeHidden()
  } else {
    await expect(header.getByText('CloudComment', { exact: true })).toBeVisible()
    await expect(header.getByRole('button', { name: themeButtonName })).toBeVisible()
    await expect(header.getByRole('button', { name: 'Открыть меню' })).toBeVisible()
  }

  const results = await new AxeBuilder({ page }).disableRules(['color-contrast']).analyze()
  expect(results.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([])
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

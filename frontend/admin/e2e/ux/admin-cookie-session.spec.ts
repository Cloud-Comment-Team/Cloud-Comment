import { expect, test, type Route } from '@playwright/test'

const USER = {
  id: '00000000-0000-0000-0000-000000000167',
  email: 'owner@example.test',
  roles: ['OWNER'],
  createdAt: '2026-07-13T00:00:00Z',
  updatedAt: '2026-07-13T00:00:00Z',
}

async function fulfillUnauthorized(route: Route) {
  await route.fulfill({
    status: 401,
    json: { error: { code: 'INVALID_SESSION', message: 'Требуется вход.' } },
  })
}

test('admin использует HttpOnly cookie и CSRF без bearer/localStorage', async ({ page }) => {
  let authenticated = false
  let adminLoginCalls = 0
  let widgetLogoutCalls = 0

  await page.route('**/api/auth/csrf', (route) => route.fulfill({
    headers: {
      'Set-Cookie': 'cloud_comment_admin_csrf=csrf-cookie; Path=/; HttpOnly; SameSite=Strict',
    },
    json: { headerName: 'X-CSRF-TOKEN', token: 'csrf-token' },
  }))
  await page.route('**/api/auth/me', async (route) => {
    expect(route.request().headers().authorization).toBeUndefined()
    if (!authenticated) {
      return fulfillUnauthorized(route)
    }
    return route.fulfill({ json: USER })
  })
  await page.route('**/api/auth/login', async (route) => {
    adminLoginCalls += 1
    expect(route.request().headers().authorization).toBeUndefined()
    expect(route.request().headers()['x-csrf-token']).toBe('csrf-token')
    authenticated = true
    return route.fulfill({
      headers: {
        'Set-Cookie': 'cloud_comment_admin_session=opaque-session; Path=/; HttpOnly; SameSite=Strict',
      },
      json: { expiresAt: '2026-07-14T00:00:00Z', user: USER },
    })
  })
  await page.route('**/api/auth/logout', async (route) => {
    expect(route.request().headers().authorization).toBeUndefined()
    expect(route.request().headers()['x-csrf-token']).toBe('csrf-token')
    authenticated = false
    return route.fulfill({
      status: 204,
      headers: {
        'Set-Cookie': 'cloud_comment_admin_session=; Path=/; HttpOnly; Max-Age=0; SameSite=Strict',
      },
    })
  })
  await page.route('**/api/realtime/tickets', (route) => {
    expect(route.request().headers().authorization).toBeUndefined()
    expect(route.request().headers()['x-csrf-token']).toBe('csrf-token')
    return route.fulfill({ json: { ticket: 'offline-ticket' } })
  })
  await page.route('**/api/public/sites/*/auth/login', (route) => route.fulfill({
    json: { token: 'widget-bearer', tokenType: 'Bearer', expiresAt: '2026-07-14T00:00:00Z', user: USER },
  }))
  await page.route('**/api/public/sites/*/auth/logout', (route) => {
    widgetLogoutCalls += 1
    return route.fulfill({ status: 204 })
  })
  await page.route('**/api/public/sites/*/pages/comments', async (route) => {
    if (!route.request().headers().authorization) {
      return fulfillUnauthorized(route)
    }
    return route.fulfill({ status: 201, json: { id: 'comment-id' } })
  })
  await page.route('**/api/sites', async (route) => {
    if (!authenticated) {
      return fulfillUnauthorized(route)
    }
    return route.fulfill({ json: [] })
  })
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.route('**/api/analytics/owner**', (route) => route.fulfill({ status: 503, json: {} }))

  await page.addInitScript(() => localStorage.setItem('cloud-comment.admin.authToken', 'legacy-token'))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Вход в админку' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('cloud-comment.admin.authToken'))).toBeNull()

  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Пароль', { exact: true }).fill('Password123!')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expect(page.getByRole('heading', { name: 'Сегодня' })).toBeVisible()
  expect(adminLoginCalls).toBe(1)
  expect(await page.evaluate(() => document.cookie)).not.toContain('cloud_comment_admin_session')
  expect(await page.evaluate(() => document.cookie)).not.toContain('cloud_comment_admin_csrf')

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Сегодня' })).toBeVisible()
  await page.goto('/login')
  await expect(page).toHaveURL(/\/$/)

  const widgetSessionResult = await page.evaluate(async () => {
    const loginResponse = await fetch('/api/public/sites/site-id/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'owner@example.test', password: 'Password123!' }),
    })
    const widgetSession = await loginResponse.json() as { token: string }
    const logoutResponse = await fetch('/api/public/sites/site-id/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${widgetSession.token}` },
    })
    const meResponse = await fetch('/api/auth/me')
    return { logoutStatus: logoutResponse.status, meStatus: meResponse.status }
  })
  expect(widgetSessionResult).toEqual({ logoutStatus: 204, meStatus: 200 })
  expect(widgetLogoutCalls).toBe(1)

  const publicCookieOnlyStatus = await page.evaluate(async () => {
    const response = await fetch('/api/public/sites/site-id/pages/comments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pageUrl: 'https://example.test/article', content: 'Комментарий' }),
    })
    return response.status
  })
  expect(publicCookieOnlyStatus).toBe(401)

  const logoutButton = page.getByRole('button', { name: 'Выйти' })
  if ((page.viewportSize()?.width ?? 0) < 1024) {
    await page.getByRole('button', { name: 'Открыть меню' }).click()
    await expect(page.locator('aside[aria-label="Основная навигация"]')).toHaveClass(/translate-x-0/)
  }
  await expect(logoutButton).toBeInViewport()
  await logoutButton.click()
  await expect(page).toHaveURL(/\/login$/)

  const bearerOnlyAdminStatus = await page.evaluate(async () => {
    const response = await fetch('/api/sites', { headers: { Authorization: 'Bearer widget-bearer' } })
    return response.status
  })
  expect(bearerOnlyAdminStatus).toBe(401)
})

test('вкладки блокируют mutation UI до проверки новой cookie identity', async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Межвкладочная синхронизация проверяется один раз в desktop Chromium.')

  const nextUser = { ...USER, id: '00000000-0000-0000-0000-000000000168', email: 'next-owner@example.test' }
  let currentUser: typeof USER | null = null
  let delayNextMe = false
  let releaseStaleMe: (() => void) | undefined
  let staleMeStarted: (() => void) | undefined
  let meCalls = 0

  await context.route('**/api/auth/csrf', (route) => route.fulfill({
    headers: { 'Set-Cookie': 'cloud_comment_admin_csrf=csrf-cookie; Path=/; HttpOnly; SameSite=Strict' },
    json: { headerName: 'X-CSRF-TOKEN', token: 'csrf-token' },
  }))
  await context.route('**/api/auth/me', async (route) => {
    meCalls += 1
    const responseUser = currentUser
    if (delayNextMe) {
      delayNextMe = false
      staleMeStarted?.()
      await new Promise<void>((resolve) => {
        releaseStaleMe = resolve
      })
    }
    if (!responseUser) {
      return fulfillUnauthorized(route)
    }
    return route.fulfill({ json: responseUser })
  })
  await context.route('**/api/auth/login', async (route) => {
    const credentials = route.request().postDataJSON() as { email: string }
    currentUser = credentials.email === nextUser.email ? nextUser : USER
    return route.fulfill({
      headers: { 'Set-Cookie': 'cloud_comment_admin_session=opaque-session; Path=/; HttpOnly; SameSite=Strict' },
      json: { expiresAt: '2026-07-14T00:00:00Z', user: currentUser },
    })
  })
  await context.route('**/api/auth/logout', (route) => {
    currentUser = null
    return route.fulfill({
      status: 204,
      headers: { 'Set-Cookie': 'cloud_comment_admin_session=; Path=/; HttpOnly; Max-Age=0; SameSite=Strict' },
    })
  })
  await context.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-ticket' } }))
  await context.route(/\/api\/sites(?:\?.*)?$/, (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 100, totalItems: 0, totalPages: 0 },
  }))
  await context.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await context.route('**/api/notifications?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await context.route('**/api/analytics/owner**', (route) => route.fulfill({ status: 503, json: {} }))

  await page.goto('/login')
  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Пароль', { exact: true }).fill('Password123!')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expect(page.getByRole('heading', { name: 'Сегодня' })).toBeVisible()

  const secondPage = await context.newPage()
  await secondPage.goto('/')
  await expect(secondPage.getByRole('heading', { name: 'Сегодня' })).toBeVisible()
  await expect(page.locator('aside').getByText(USER.email)).toBeVisible()
  await expect(secondPage.locator('aside').getByText(USER.email)).toBeVisible()

  const staleRequestStarted = new Promise<void>((resolve) => {
    staleMeStarted = resolve
  })
  delayNextMe = true
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await staleRequestStarted

  await secondPage.getByRole('button', { name: 'Выйти' }).click()
  await expect(secondPage).toHaveURL(/\/login$/)
  await expect(page).toHaveURL(/\/login$/)
  await secondPage.getByLabel('Email').fill(nextUser.email)
  await secondPage.getByLabel('Пароль', { exact: true }).fill('Password123!')
  await secondPage.getByRole('button', { name: 'Войти' }).click()
  await expect(secondPage.getByRole('heading', { name: 'Сегодня' })).toBeVisible()

  await expect(page.getByRole('status')).toHaveText('Проверяем доступ…')
  await expect(page.getByRole('link', { name: 'Создать сайт' })).toHaveCount(0)
  releaseStaleMe?.()

  await expect(page.getByRole('heading', { name: 'Сегодня' })).toBeVisible()
  await expect(page.locator('aside').getByText(nextUser.email)).toBeVisible()
  expect(meCalls).toBeGreaterThanOrEqual(3)

  await secondPage.getByRole('button', { name: 'Выйти' }).click()
  await expect(page).toHaveURL(/\/login$/)
  const callsAfterLogout = meCalls
  await page.waitForTimeout(500)
  expect(meCalls).toBe(callsAfterLogout)
})

test('401 realtime-ticket завершает auth-сессию без цикла reconnect', async ({ page }) => {
  let ticketCalls = 0
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: USER }))
  await page.route('**/api/auth/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', token: 'csrf-token' },
  }))
  await page.route('**/api/realtime/tickets', async (route) => {
    ticketCalls += 1
    await fulfillUnauthorized(route)
  })
  await page.route('**/api/sites', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.route('**/api/analytics/owner**', (route) => route.fulfill({ status: 503, json: {} }))

  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  const callsAfterSessionExpiry = ticketCalls
  await page.waitForTimeout(2_500)
  expect(ticketCalls).toBe(callsAfterSessionExpiry)
  expect(ticketCalls).toBeLessThanOrEqual(2)
})

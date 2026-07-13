import { expect, test } from '@playwright/test'

const API_ORIGIN = (process.env.E2E_BASE_URL ?? 'http://localhost').replace(/\/+$/, '')
const EXTERNAL_ORIGIN = process.env.E2E_EXTERNAL_BASE_URL ?? 'http://127.0.0.1'
const DEMO_SITE_ID = process.env.E2E_DEMO_SITE_ID ?? 'c680d246-8876-4049-975c-73ccf029408f'
const UNKNOWN_COMMENT_ID = '00000000-0000-0000-0000-000000000099'

test('cross-origin PATCH и DELETE проходят preflight и достигают backend', async ({ page }) => {
  await page.goto(`${EXTERNAL_ORIGIN}/demo-page.html`)

  const statuses = await page.evaluate(async ({ apiOrigin, siteId, commentId }) => {
    const endpoint = `${apiOrigin}/api/public/sites/${siteId}/comments/${commentId}`
    const patch = await fetch(endpoint, {
      method: 'PATCH',
      headers: {
        Authorization: 'Bearer invalid-widget-session',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content: 'Проверка CORS' }),
    })
    const deletion = await fetch(endpoint, {
      method: 'DELETE',
      headers: { Authorization: 'Bearer invalid-widget-session' },
    })
    return { patch: patch.status, deletion: deletion.status }
  }, {
    apiOrigin: API_ORIGIN,
    siteId: DEMO_SITE_ID,
    commentId: UNKNOWN_COMMENT_ID,
  })

  expect(statuses).toEqual({ patch: 401, deletion: 401 })
})

test('лишний cross-origin заголовок блокируется до actual request', async ({ page }) => {
  await page.goto(`${EXTERNAL_ORIGIN}/demo-page.html`)

  const result = await page.evaluate(async ({ apiOrigin, siteId, commentId }) => {
    try {
      await fetch(`${apiOrigin}/api/public/sites/${siteId}/comments/${commentId}`, {
        method: 'PATCH',
        headers: {
          Authorization: 'Bearer invalid-widget-session',
          'Content-Type': 'application/json',
          'X-Admin-Token': 'forbidden',
        },
        body: JSON.stringify({ content: 'Запрос не должен дойти до backend' }),
      })
      return 'resolved'
    } catch {
      return 'blocked'
    }
  }, {
    apiOrigin: API_ORIGIN,
    siteId: DEMO_SITE_ID,
    commentId: UNKNOWN_COMMENT_ID,
  })

  expect(result).toBe('blocked')
})

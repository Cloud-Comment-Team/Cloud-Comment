import { expect, test } from '@playwright/test'

const API_ORIGIN = (process.env.E2E_BASE_URL ?? 'http://localhost').replace(/\/+$/, '')
const EXTERNAL_ORIGIN = process.env.E2E_EXTERNAL_BASE_URL ?? 'http://127.0.0.1'
const DEMO_SITE_ID = process.env.E2E_DEMO_SITE_ID ?? 'c680d246-8876-4049-975c-73ccf029408f'
const UNKNOWN_COMMENT_ID = '00000000-0000-0000-0000-000000000099'

test('legacy host не может выполнить PATCH и DELETE без widget context', async ({ page }) => {
  await page.goto(`${EXTERNAL_ORIGIN}/demo-page.html`)

  const results = await page.evaluate(async ({ apiOrigin, siteId, commentId }) => {
    const endpoint = `${apiOrigin}/api/public/sites/${siteId}/comments/${commentId}`
    const request = async (method: 'PATCH' | 'DELETE') => {
      try {
        await fetch(endpoint, {
          method,
          headers: {
            Authorization: 'Bearer invalid-widget-session',
            ...(method === 'PATCH' ? { 'Content-Type': 'application/json' } : {}),
          },
          ...(method === 'PATCH' ? { body: JSON.stringify({ content: 'Проверка CORS' }) } : {}),
        })
        return 'resolved'
      } catch {
        return 'blocked'
      }
    }
    return { patch: await request('PATCH'), deletion: await request('DELETE') }
  }, {
    apiOrigin: API_ORIGIN,
    siteId: DEMO_SITE_ID,
    commentId: UNKNOWN_COMMENT_ID,
  })

  expect(results).toEqual({ patch: 'blocked', deletion: 'blocked' })
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

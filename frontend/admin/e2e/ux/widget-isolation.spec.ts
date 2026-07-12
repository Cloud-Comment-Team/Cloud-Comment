import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

test('виджет изолирован от стилей страницы через Shadow DOM', async ({ page }) => {
  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: '00000000-0000-0000-0000-000000000001', moderationMode: 'POST_MODERATION', style: { theme: 'LIGHT', accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.goto('/login')
  await page.evaluate(() => {
    document.head.insertAdjacentHTML('beforeend', '<style>button{position:fixed!important;color:rgb(255,0,0)!important}</style>')
    document.body.innerHTML = '<div id="comments"></div>'
  })
  await page.addScriptTag({ path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js') })
  await page.evaluate(() => window.CloudCommentWidget?.init({
    siteId: '00000000-0000-0000-0000-000000000001',
    apiBaseUrl: 'https://api.example.test',
    pageUrl: 'https://site.example.test/article',
    target: '#comments',
  }))

  const host = page.locator('#comments')
  await expect(host).toBeVisible()
  expect(await host.evaluate((element) => Boolean(element.shadowRoot))).toBe(true)
  const buttonStyle = await host.evaluate((element) => {
    const button = element.shadowRoot?.querySelector('button')
    return button ? { position: getComputedStyle(button).position, color: getComputedStyle(button).color } : null
  })
  expect(buttonStyle?.position).not.toBe('fixed')
  expect(buttonStyle?.color).not.toBe('rgb(255, 0, 0)')
})

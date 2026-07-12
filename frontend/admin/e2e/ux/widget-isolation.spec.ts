import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
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

  await expect(host.getByRole('button', { name: 'Войти, чтобы участвовать' })).toBeVisible()
  await expect(host.locator('.cloud-comment__auth-form')).toHaveCount(0)
  await host.getByRole('button', { name: 'Войти, чтобы участвовать' }).click()
  await expect(host.locator('.cloud-comment__auth-form')).toBeVisible()
  await expect(host.locator('.cloud-comment__auth-form input[name="email"]')).toBeFocused()

  await host.evaluate((element) => { (element as HTMLElement).style.width = '320px' })
  const responsiveState = await host.evaluate((element) => {
    const shell = element.shadowRoot?.querySelector<HTMLElement>('.cloud-comment')
    const form = element.shadowRoot?.querySelector<HTMLElement>('.cloud-comment__auth-form')
    return shell && form ? {
      noHorizontalOverflow: shell.scrollWidth <= shell.clientWidth,
      formColumns: getComputedStyle(form).gridTemplateColumns,
    } : null
  })
  expect(responsiveState?.noHorizontalOverflow).toBe(true)
  expect(responsiveState?.formColumns.split(' ')).toHaveLength(1)
  const accessibility = await new AxeBuilder({ page }).disableRules(['color-contrast']).analyze()
  expect(accessibility.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([])
})

test('виджет стабильно отображает сто комментариев', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Нагрузочный DOM-сценарий достаточно выполнить в одном профиле')
  const comments = Array.from({ length: 100 }, (_, index) => ({
    id: `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
    siteId: '00000000-0000-0000-0000-000000000001',
    pageId: '00000000-0000-0000-0000-000000000002',
    parentId: null,
    author: { id: '00000000-0000-0000-0000-000000000003', email: 'author@example.com', displayName: `Автор ${index + 1}` },
    content: `Комментарий ${index + 1}`,
    status: 'APPROVED',
    createdAt: '2026-07-12T00:00:00Z',
    updatedAt: '2026-07-12T00:00:00Z',
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: false,
    reactions: [],
    replyCount: 0,
    replies: [],
  }))
  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: '00000000-0000-0000-0000-000000000001', moderationMode: 'POST_MODERATION', style: { theme: 'LIGHT', accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => route.fulfill({
    json: { items: comments, page: 1, pageSize: 100, totalItems: 100, totalPages: 1 },
  }))
  await page.route('**/privacy/consent-requirements', (route) => route.fulfill({
    json: { privacyPolicyVersion: 'test', termsVersion: 'test', privacyPolicyUrl: '#', termsUrl: '#', personalDataNoticeUrl: '#', dataExportInfoUrl: '#' },
  }))
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments" style="width:320px"></div>' })
  await page.addScriptTag({ path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js') })
  const startedAt = Date.now()
  await page.evaluate(() => window.CloudCommentWidget?.init({
    siteId: '00000000-0000-0000-0000-000000000001',
    apiBaseUrl: 'https://api.example.test',
    pageUrl: 'https://site.example.test/article',
    target: '#comments',
  }))

  const host = page.locator('#comments')
  await expect(host.locator('.cloud-comment__comment')).toHaveCount(100)
  expect(Date.now() - startedAt).toBeLessThan(3000)
  expect(await host.evaluate((element) => {
    const shell = element.shadowRoot?.querySelector<HTMLElement>('.cloud-comment')
    return shell ? shell.scrollWidth <= shell.clientWidth : false
  })).toBe(true)
})

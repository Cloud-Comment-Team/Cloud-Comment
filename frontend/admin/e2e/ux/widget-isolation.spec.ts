import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

test('autoInit канонизирует текущий pageUrl перед запросом комментариев', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Проверку контракта URL достаточно выполнить в одном профиле')

  await page.route('**/widget/cloud-comment-widget.js', (route) => route.fulfill({
    path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js'),
    contentType: 'application/javascript',
  }))

  let requestedPageUrl: string | null = null
  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: '00000000-0000-0000-0000-000000000001', moderationMode: 'POST_MODERATION', style: { theme: 'LIGHT', accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => {
    requestedPageUrl = new URL(route.request().url()).searchParams.get('pageUrl')
    return route.fulfill({
      json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
    })
  })

  await page.goto('/login?tab=comments&q=100%&utm_source=mail&FBCLID=click&gbraid=campaign#comments')
  const expectedPageUrl = new URL(page.url())
  expectedPageUrl.search = '?tab=comments&q=100%25'
  expectedPageUrl.hash = ''
  await page.evaluate(() => {
    document.body.innerHTML = '<div id="cloud-comment-widget"></div>'
  })
  await page.evaluate(() => new Promise<void>((resolveScript, rejectScript) => {
    const script = document.createElement('script')
    script.src = '/widget/cloud-comment-widget.js'
    script.dataset.siteId = '00000000-0000-0000-0000-000000000001'
    script.dataset.apiBaseUrl = 'https://api.example.test'
    script.addEventListener('load', () => resolveScript())
    script.addEventListener('error', () => rejectScript(new Error('Не удалось загрузить bundle виджета')))
    document.body.append(script)
  }))

  await expect.poll(() => requestedPageUrl).toBe(expectedPageUrl.href)
  await expect(page).toHaveURL(/#comments$/)
})

test('manual init одинаково канонизирует pageUrl для GET и POST', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Проверку URL-контракта достаточно выполнить в одном профиле')

  const requestedGetPageUrls: Array<string | null> = []
  let postedPageUrl: string | null = null
  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: '00000000-0000-0000-0000-000000000001', moderationMode: 'POST_MODERATION', style: { theme: 'LIGHT', accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/privacy/consent-requirements', (route) => route.fulfill({
    json: { privacyPolicyVersion: 'test', termsVersion: 'test', privacyPolicyUrl: '#', termsUrl: '#', personalDataNoticeUrl: '#', dataExportInfoUrl: '#' },
  }))
  await page.route('**/public/sites/**/auth/me', (route) => route.fulfill({
    json: { id: '00000000-0000-0000-0000-000000000003', email: 'author@example.test', displayName: 'Автор' },
  }))
  await page.route('**/public/sites/**/pages/comments**', async (route) => {
    if (route.request().method() === 'POST') {
      postedPageUrl = (await route.request().postDataJSON() as { pageUrl: string }).pageUrl
      return route.fulfill({
        status: 201,
        json: {
          id: '00000000-0000-0000-0000-000000000004',
          siteId: '00000000-0000-0000-0000-000000000001',
          pageId: '00000000-0000-0000-0000-000000000002',
          parentId: null,
          author: { id: '00000000-0000-0000-0000-000000000003', displayName: 'Автор' },
          content: 'Проверка canonical URL',
          status: 'APPROVED',
          createdAt: '2026-07-13T00:00:00Z',
          updatedAt: '2026-07-13T00:00:00Z',
          editedAt: null,
          pinned: false,
          ownedByCurrentUser: true,
          reactions: [],
          replyCount: 0,
          replies: [],
        },
      })
    }

    requestedGetPageUrls.push(new URL(route.request().url()).searchParams.get('pageUrl'))
    return route.fulfill({
      json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
    })
  })

  await page.goto('/login')
  await page.evaluate(() => {
    localStorage.setItem('cloud-comment.widget.authToken', 'test-session-token')
    document.body.innerHTML = '<div id="comments"></div>'
  })
  await page.addScriptTag({ path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js') })
  await page.evaluate(() => window.CloudCommentWidget?.init({
    siteId: '00000000-0000-0000-0000-000000000001',
    apiBaseUrl: 'https://api.example.test',
    pageUrl: 'https://site.example.test/article?q=hello+world&q=hello%20world&next=%2Ffeed&%ZZ=x&code=flow&ticket=thread&sid=section&utm_source=mail&gbraid=campaign&SESSION=secret&client_secret=secret&x-amz-signature=signed#comments',
    target: '#comments',
  }))

  const host = page.locator('#comments')
  await host.getByRole('textbox', { name: 'Написать комментарий' }).fill('Проверка canonical URL')
  await host.getByRole('button', { name: 'Отправить' }).click()

  const expectedPageUrl = 'https://site.example.test/article?q=hello+world&q=hello%20world&next=%2Ffeed&%25ZZ=x&code=flow&ticket=thread&sid=section'
  await expect.poll(() => postedPageUrl).toBe(expectedPageUrl)
  await expect.poll(() => requestedGetPageUrls.length).toBeGreaterThanOrEqual(2)
  expect(requestedGetPageUrls.every((pageUrl) => pageUrl === expectedPageUrl)).toBe(true)
})

test('виджет изолирован от стилей страницы через Shadow DOM', async ({ page }, testInfo) => {
  const theme = testInfo.project.name === 'tablet-dark' ? 'DARK' : 'LIGHT'
  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: '00000000-0000-0000-0000-000000000001', moderationMode: 'POST_MODERATION', style: { theme, accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.route('**/privacy/consent-requirements', (route) => route.fulfill({
    json: { privacyPolicyVersion: 'test', termsVersion: 'test', privacyPolicyUrl: '#', termsUrl: '#', personalDataNoticeUrl: '#', dataExportInfoUrl: '#' },
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
  await expect(host).not.toContainText('Загружаем комментарии...')
  await expectNoSeriousAccessibilityViolations(page)
})

test('виджет стабильно отображает сто комментариев', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Нагрузочный DOM-сценарий достаточно выполнить в одном профиле')
  const comments = Array.from({ length: 100 }, (_, index) => ({
    id: `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
    siteId: '00000000-0000-0000-0000-000000000001',
    pageId: '00000000-0000-0000-0000-000000000002',
    parentId: null,
    author: {
      id: '00000000-0000-0000-0000-000000000003',
      displayName: index === 0 ? 'author@example.com' : `Автор ${index + 1}`,
    },
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
  await expect(host).toContainText('Участник')
  await expect(host).not.toContainText('author@example.com')
  expect(Date.now() - startedAt).toBeLessThan(3000)
  expect(await host.evaluate((element) => {
    const shell = element.shadowRoot?.querySelector<HTMLElement>('.cloud-comment')
    return shell ? shell.scrollWidth <= shell.clientWidth : false
  })).toBe(true)
})

test('реальный bundle сохраняет черновик и фокус при перерисовках и ошибке отправки', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Сценарий устойчивости фокуса достаточно выполнить в одном профиле')

  const comment = {
    id: '00000000-0000-0000-0000-000000000010',
    siteId: '00000000-0000-0000-0000-000000000001',
    pageId: '00000000-0000-0000-0000-000000000002',
    parentId: null,
    author: { id: '00000000-0000-0000-0000-000000000003', displayName: 'Анна' },
    content: 'Исходный комментарий',
    status: 'APPROVED',
    createdAt: '2026-07-13T10:00:00Z',
    updatedAt: '2026-07-13T10:00:00Z',
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: true,
    reactions: [
      { type: 'LIKE', emoji: '👍', label: 'Нравится', count: 0, reactedByCurrentUser: false },
      { type: 'LOVE', emoji: '❤️', label: 'Люблю', count: 0, reactedByCurrentUser: false },
      { type: 'LAUGH', emoji: '😂', label: 'Смешно', count: 0, reactedByCurrentUser: false },
      { type: 'WOW', emoji: '😮', label: 'Удивительно', count: 0, reactedByCurrentUser: false },
    ],
    replyCount: 0,
    replies: [],
  }

  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: { siteId: comment.siteId, moderationMode: 'POST_MODERATION', style: { theme: 'LIGHT', accentColor: '#0f766e', cornerRadius: 'MEDIUM' } },
  }))
  await page.route('**/privacy/consent-requirements', (route) => route.fulfill({
    json: { privacyPolicyVersion: 'test', termsVersion: 'test', privacyPolicyUrl: '#', termsUrl: '#', personalDataNoticeUrl: '#', dataExportInfoUrl: '#' },
  }))
  await page.route('**/public/sites/**/auth/me', (route) => route.fulfill({
    json: { id: comment.author.id, email: 'anna@example.test', roles: ['COMMENTER'], createdAt: comment.createdAt, updatedAt: comment.updatedAt },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({ status: 500, json: { error: { message: 'Сервер не сохранил комментарий' } } })
    }
    return route.fulfill({ json: { items: [comment], page: 1, pageSize: 20, totalItems: 1, totalPages: 1 } })
  })
  await page.route('**/public/sites/**/comments/**/reaction', (route) => route.fulfill({
    json: {
      reactions: comment.reactions.map((reaction) => ({
        ...reaction,
        count: reaction.type === 'LIKE' ? 1 : 0,
        reactedByCurrentUser: reaction.type === 'LIKE',
      })),
    },
  }))

  await page.goto('/login')
  await page.evaluate(() => {
    localStorage.setItem('cloud-comment.widget.authToken', 'widget-test-token')
    document.body.innerHTML = '<div id="comments"></div>'
  })
  await page.addScriptTag({ path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js') })
  await page.evaluate(() => window.CloudCommentWidget?.init({
    siteId: '00000000-0000-0000-0000-000000000001',
    apiBaseUrl: 'https://api.example.test',
    pageUrl: 'https://site.example.test/article',
    target: '#comments',
    theme: 'light',
  }))

  const host = page.locator('#comments')
  await expect(host.locator('.cloud-comment__comment')).toHaveCount(1)
  const draft = '  Черновик должен сохраниться  '
  await host.locator("[data-cloud-comment-form='comment'] textarea").fill(draft)

  const reaction = host.locator("[data-reaction-type='LIKE']")
  await reaction.focus()
  await reaction.click()
  await expect(reaction).toContainText('1')
  await expect(reaction).toBeFocused()
  await expect(host.locator("[data-cloud-comment-form='comment'] textarea")).toHaveValue(draft)

  const profile = host.locator("[data-profile-action='toggle']")
  await profile.focus()
  await profile.click()
  await expect(profile).toHaveAttribute('aria-expanded', 'true')
  await expect(profile).toBeFocused()

  const sort = host.locator("[data-comment-sort='true']")
  await sort.focus()
  await sort.selectOption('NEWEST')
  await expect(sort).toHaveValue('NEWEST')
  await expect(sort).toBeFocused()

  const textarea = host.locator("[data-cloud-comment-form='comment'] textarea")
  await textarea.evaluate((element: HTMLTextAreaElement) => {
    element.focus()
    element.setSelectionRange(2, 12, 'forward')
  })
  await host.locator("[data-cloud-comment-form='comment'] button[type='submit']").click()
  await expect(host).toContainText('Сервер не сохранил комментарий')
  await expect(textarea).toHaveValue(draft)
  await expect(textarea).toBeFocused()
  expect(await textarea.evaluate((element: HTMLTextAreaElement) => [
    element.selectionStart,
    element.selectionEnd,
    element.selectionDirection,
  ])).toEqual([2, 12, 'forward'])
})

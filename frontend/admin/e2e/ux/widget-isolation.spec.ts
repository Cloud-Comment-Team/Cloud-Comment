import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type FrameLocator, type Page, type Route, type TestInfo } from '@playwright/test'

const SITE_ID = '00000000-0000-0000-0000-000000000001'
const WIDGET_ORIGIN = 'https://widget.example.test'
const API_ORIGIN = 'https://api.example.test'
const CONTEXT_TOKEN = 'widget-frame-context-token'.padEnd(43, 'x')
const COMMENTER_BEARER = 'widget-commenter-bearer'
const loaderBundle = resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js')
const frameBundle = resolve('../../widget-frontend/dist/widget/cloud-comment-widget-frame.js')
const widgetStyles = resolve('../../widget-frontend/dist/widget/cloud-comment-widget.css')

type MockComment = ReturnType<typeof createComment>

type WidgetServerOptions = {
  comments?: MockComment[]
  failCommentPost?: boolean
  paginateComments?: boolean
}

type WidgetServerState = {
  bootstrapPageUrls: string[]
  bootstrapOrigins: Array<string | null>
  requestedPageUrls: string[]
  requestedCommentPages: Array<{ sort: string; page: number }>
  postedPageUrl: string | null
  contextlessRequests: string[]
  accountRequests: string[]
  commenterAuthorization: string[]
  configOrigins: Array<string | null>
  exchangeCount: number
  exchangeOrigins: Array<string | null>
  loginCount: number
  loginOrigins: Array<string | null>
  meCount: number
}

function isPrimaryBrowser(testInfo: TestInfo): boolean {
  return testInfo.project.name === 'desktop-light' || testInfo.project.name === 'widget-chromium'
}

async function setupWidgetServer(page: Page, options: WidgetServerOptions = {}): Promise<WidgetServerState> {
  const comments = [...(options.comments ?? [])]
  const state: WidgetServerState = {
    bootstrapPageUrls: [],
    bootstrapOrigins: [],
    requestedPageUrls: [],
    requestedCommentPages: [],
    postedPageUrl: null,
    contextlessRequests: [],
    accountRequests: [],
    commenterAuthorization: [],
    configOrigins: [],
    exchangeCount: 0,
    exchangeOrigins: [],
    loginCount: 0,
    loginOrigins: [],
    meCount: 0,
  }

  await page.context().route(`${WIDGET_ORIGIN}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    if (method === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    if (url.pathname === '/widget/cloud-comment-widget-frame.js') {
      await route.fulfill({
        path: frameBundle,
        contentType: 'application/javascript; charset=utf-8',
        headers: { 'Cache-Control': 'no-store' },
      })
      return
    }
    if (url.pathname === '/widget/cloud-comment-widget.css') {
      await route.fulfill({
        path: widgetStyles,
        contentType: 'text/css; charset=utf-8',
        headers: { 'Cache-Control': 'no-store' },
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/widget-frame`) {
      const parentOrigin = new URL(page.url()).origin
      await route.fulfill({
        status: 200,
        contentType: 'text/html; charset=utf-8',
        headers: {
          'Cache-Control': 'no-store',
          'Content-Security-Policy': `default-src 'none'; script-src ${WIDGET_ORIGIN}; style-src 'self' 'unsafe-inline'; connect-src ${WIDGET_ORIGIN}; frame-ancestors ${parentOrigin}`,
          ...corsHeaders(),
        },
        body: '<!doctype html><html lang="ru"><head><meta charset="UTF-8"><meta name="referrer" content="no-referrer"><style>html,body{margin:0;padding:0;background:transparent}</style></head><body><div id="cloud-comment-frame-root"></div><script src="/widget/cloud-comment-widget-frame.js"></script></body></html>',
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/widget-context/bootstrap`) {
      const body = request.postDataJSON() as { publicKey: string; pageUrl: string }
      state.bootstrapPageUrls.push(body.pageUrl)
      state.bootstrapOrigins.push(request.headers().origin ?? null)
      const fingerprint = createHash('sha256').update(Buffer.from(body.publicKey, 'base64url')).digest('base64url')
      await fulfillJson(route, {
        ticket: 'T'.repeat(43),
        expiresAt: '2099-01-01T00:00:00Z',
        canonicalPageUrl: body.pageUrl,
        publicKeyFingerprint: fingerprint,
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/widget-context/exchange`) {
      const body = request.postDataJSON() as { ticket?: string; proof?: string }
      expect(body.ticket).toBe('T'.repeat(43))
      expect(body.proof).toMatch(/^[A-Za-z0-9_-]{80,100}$/)
      state.exchangeCount += 1
      state.exchangeOrigins.push(request.headers().origin ?? null)
      await fulfillJson(route, { contextToken: CONTEXT_TOKEN, expiresAt: '2099-01-01T00:00:00Z' })
      return
    }

    if (url.pathname.startsWith(`/api/public/sites/${SITE_ID}/`)) {
      if (url.pathname.includes('/account/')) {
        state.accountRequests.push(`${method} ${url.pathname}`)
      }
      if (request.headers()['x-cloudcomment-widget-context'] !== CONTEXT_TOKEN) {
        state.contextlessRequests.push(`${method} ${url.pathname}`)
      }
      const authorization = request.headers().authorization
      if (authorization) {
        state.commenterAuthorization.push(authorization)
      }
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/config`) {
      state.configOrigins.push(request.headers().origin ?? null)
      await fulfillJson(route, {
        siteId: SITE_ID,
        moderationMode: 'POST_MODERATION',
        style: {
          theme: 'LIGHT',
          accentColor: '#0f766e',
          cornerRadius: 'MEDIUM',
          showSort: true,
          defaultSort: 'PINNED_FIRST',
        },
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/privacy/consent-requirements`) {
      await fulfillJson(route, {
        privacyPolicyVersion: 'test',
        termsVersion: 'test',
        privacyPolicyUrl: '/legal/privacy-policy.html',
        termsUrl: '/legal/terms.html',
        personalDataNoticeUrl: '/legal/personal-data-notice.html',
        dataExportInfoUrl: '/legal/personal-data-notice.html#data-export',
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/pages/comments`) {
      const pageUrlHeader = request.headers()['x-cloudcomment-page-url'] ?? null
      if (method === 'POST') {
        state.postedPageUrl = pageUrlHeader
        if (options.failCommentPost) {
          await fulfillJson(route, { error: { message: 'Сервер не сохранил комментарий' } }, 500)
          return
        }
        const body = request.postDataJSON() as { content: string; parentId: string | null; pageUrl: string }
        expect(body.pageUrl).toBe(pageUrlHeader)
        const comment = createComment(comments.length + 100, { content: body.content, parentId: body.parentId })
        comments.unshift(comment)
        await fulfillJson(route, comment, 201)
        return
      }
      state.requestedPageUrls.push(pageUrlHeader)
      if (options.paginateComments) {
        const sort = url.searchParams.get('sort') ?? 'PINNED_FIRST'
        const requestedPage = Number(url.searchParams.get('page') ?? '1')
        state.requestedCommentPages.push({ sort, page: requestedPage })
        const sorted = sort === 'NEWEST' ? [...comments].reverse() : comments
        let items = sorted.slice((requestedPage - 1) * 20, requestedPage * 20)
        if (sort === 'PINNED_FIRST' && requestedPage === 2) {
          items = [comments[19], ...comments.slice(20, 39)]
        } else if (sort === 'PINNED_FIRST' && requestedPage === 3) {
          items = comments.slice(39)
        }
        await fulfillJson(route, {
          items,
          page: requestedPage,
          pageSize: 20,
          totalItems: comments.length,
          totalPages: Math.ceil(comments.length / 20),
        })
        return
      }
      await fulfillJson(route, { items: comments, page: 1, pageSize: 20, totalItems: comments.length, totalPages: 1 })
      return
    }
    if (/\/api\/public\/sites\/[^/]+\/comments\/[^/]+\/replies$/u.test(url.pathname)) {
      state.requestedPageUrls.push(request.headers()['x-cloudcomment-page-url'] ?? null)
      await fulfillJson(route, { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
      return
    }
    if (url.pathname.endsWith('/reaction') && method === 'PUT') {
      await fulfillJson(route, {
        reactions: reactionSet().map((reaction) => ({
          ...reaction,
          count: reaction.type === 'LIKE' ? 1 : 0,
          reactedByCurrentUser: reaction.type === 'LIKE',
        })),
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/auth/login` && method === 'POST') {
      state.loginCount += 1
      state.loginOrigins.push(request.headers().origin ?? null)
      await fulfillJson(route, {
        token: COMMENTER_BEARER,
        tokenType: 'Bearer',
        expiresAt: '2099-01-01T00:00:00Z',
        user: commenterUser(),
      })
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/auth/me`) {
      state.meCount += 1
      expect(request.headers().authorization).toBe(`Bearer ${COMMENTER_BEARER}`)
      await fulfillJson(route, commenterUser())
      return
    }
    if (url.pathname === `/api/public/sites/${SITE_ID}/auth/logout` && method === 'POST') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    if (url.pathname.startsWith('/legal/')) {
      await route.fulfill({
        status: 200,
        contentType: 'text/html; charset=utf-8',
        headers: { ...corsHeaders(), 'Cache-Control': 'no-store' },
        body: `<!doctype html><html lang="ru"><title>Юридический документ</title><body><h1>${url.pathname}</h1></body></html>`,
      })
      return
    }
    await fulfillJson(route, { error: { message: `Неожиданный mock URL: ${url.pathname}` } }, 404)
  })

  return state
}

async function mountWidget(
  page: Page,
  pageUrl: string,
  target = '#comments',
  apiOrigin = API_ORIGIN
): Promise<FrameLocator> {
  await page.addScriptTag({ path: loaderBundle })
  await page.evaluate(({ siteId, widgetOrigin, apiOrigin, canonicalPageUrl, selector }) => {
    const instance = window.CloudCommentWidget?.init({
      siteId,
      apiBaseUrl: `${apiOrigin}/api`,
      frameBaseUrl: widgetOrigin,
      pageUrl: canonicalPageUrl,
      target: selector,
      theme: 'light',
    })
    ;(window as typeof window & { __cloudCommentTestInstance?: { destroy: () => void } })
      .__cloudCommentTestInstance = instance
  }, { siteId: SITE_ID, widgetOrigin: WIDGET_ORIGIN, apiOrigin, canonicalPageUrl: pageUrl, selector: target })
  return page.frameLocator(`${target} iframe`)
}

async function loginInFrame(frame: FrameLocator): Promise<void> {
  await frame.getByRole('button', { name: 'Войти, чтобы участвовать' }).click()
  await frame.locator("form[data-cloud-comment-form='auth'] input[name='email']").fill('author@example.test')
  await frame.locator("form[data-cloud-comment-form='auth'] input[name='password']").fill('password-123')
  await frame.locator("form[data-cloud-comment-form='auth'] button[data-cloud-comment-submit]").click()
  await expect(frame.getByText('Вы вошли как author@example.test')).toBeVisible()
}

test('autoInit канонизирует pageUrl, а host не получает UI или секреты', async ({ page }, testInfo) => {
  test.skip(!isPrimaryBrowser(testInfo), 'Контракт URL достаточно проверить в основном Chromium-профиле')
  const state = await setupWidgetServer(page)
  await page.goto('/login?tab=comments&q=100%&utm_source=mail&FBCLID=click&gbraid=campaign#comments')
  const expectedPageUrl = new URL(page.url())
  expectedPageUrl.search = '?tab=comments&q=100%25'
  expectedPageUrl.hash = ''
  await page.evaluate(() => { document.body.innerHTML = '<div id="cloud-comment-widget"></div>' })
  await page.evaluate(({ siteId, widgetOrigin, apiOrigin }) => new Promise<void>((resolveScript, rejectScript) => {
    const script = document.createElement('script')
    script.src = '/widget/cloud-comment-widget.js'
    script.dataset.siteId = siteId
    script.dataset.apiBaseUrl = `${apiOrigin}/api`
    script.dataset.frameBaseUrl = widgetOrigin
    script.addEventListener('load', () => resolveScript())
    script.addEventListener('error', () => rejectScript(new Error('Не удалось загрузить bundle виджета')))
    document.body.append(script)
  }), { siteId: SITE_ID, widgetOrigin: WIDGET_ORIGIN, apiOrigin: API_ORIGIN })

  const host = page.locator('#cloud-comment-widget')
  const frame = page.frameLocator('#cloud-comment-widget iframe')
  await expect(frame.getByText('Комментарии').first()).toBeVisible()
  await expect.poll(() => state.bootstrapPageUrls).toEqual([expectedPageUrl.href])
  expect(await host.evaluate((element) => Boolean(element.shadowRoot))).toBe(false)
  await expect(host.locator('form')).toHaveCount(0)
  const frameSrc = await host.locator('iframe').getAttribute('src')
  expect(frameSrc).toBe(`${WIDGET_ORIGIN}/api/public/sites/${SITE_ID}/widget-frame`)
  expect(frameSrc).not.toMatch(/(?:token|article|pageUrl)/i)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toMatch(/bearer|context|password/i)
  expect(state.contextlessRequests).toEqual([])
  expect(state.exchangeCount).toBe(1)
  await expect(page).toHaveURL(/#comments$/)
})

test('manual init использует один canonical pageUrl для GET и POST внутри frame', async ({ page }, testInfo) => {
  test.skip(!isPrimaryBrowser(testInfo), 'Контракт URL достаточно проверить в основном Chromium-профиле')
  const state = await setupWidgetServer(page)
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments"></div>' })
  const rawPageUrl = `${new URL(page.url()).origin}/article?q=hello+world&q=hello%20world&next=%2Ffeed&%ZZ=x&code=flow&ticket=thread&sid=section&utm_source=mail&gbraid=campaign&SESSION=secret&client_secret=secret&x-amz-signature=signed#comments`
  const frame = await mountWidget(page, rawPageUrl)
  await loginInFrame(frame)
  await frame.locator("[data-cloud-comment-form='comment'] textarea").fill('Проверка canonical URL')
  await frame.locator("[data-cloud-comment-form='comment'] button[data-cloud-comment-submit]").click()

  const expectedPageUrl = `${new URL(page.url()).origin}/article?q=hello+world&q=hello%20world&next=%2Ffeed&%25ZZ=x&code=flow&ticket=thread&sid=section`
  await expect.poll(() => state.postedPageUrl).toBe(expectedPageUrl)
  await expect.poll(() => state.requestedPageUrls.length).toBeGreaterThanOrEqual(2)
  expect(state.requestedPageUrls.every((value) => value === expectedPageUrl)).toBe(true)
  expect(state.commenterAuthorization).toContain(`Bearer ${COMMENTER_BEARER}`)
  expect(state.contextlessRequests).toEqual([])
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain(COMMENTER_BEARER)
})

test('навигация A→B сохраняет site bearer, но другой parent origin его не видит', async ({ page }) => {
  const state = await setupWidgetServer(page)
  await page.goto('/login')
  const parentOrigin = new URL(page.url()).origin
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments-a"></div><div id="comments-b"></div>' })

  const frameA = await mountWidget(page, `${parentOrigin}/article-a`, '#comments-a')
  await loginInFrame(frameA)
  expect(state.loginCount).toBe(1)
  expect(state.exchangeCount).toBe(1)

  await page.evaluate(() => {
    (window as typeof window & { __cloudCommentTestInstance?: { destroy: () => void } })
      .__cloudCommentTestInstance?.destroy()
  })
  await expect(page.locator('#comments-a iframe')).toHaveCount(0)

  const frameB = await mountWidget(page, `${parentOrigin}/article-b`, '#comments-b')
  await expect(frameB.getByText('author@example.test').first()).toBeVisible()
  await expect(frameB.locator("[data-cloud-comment-form='comment'] textarea")).not.toHaveAttribute('readonly')
  expect(state.loginCount).toBe(1)
  expect(state.meCount).toBe(1)
  expect(state.exchangeCount).toBe(2)

  await page.route('https://other-parent.example/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: '<!doctype html><html lang="ru"><body><div id="comments-other"></div></body></html>',
    })
  })
  await page.goto('https://other-parent.example/article')
  const otherFrame = await mountWidget(page, 'https://other-parent.example/article', '#comments-other')
  await expect(otherFrame.getByRole('button', { name: 'Войти, чтобы участвовать' })).toBeVisible()
  await expect(otherFrame.locator("[data-cloud-comment-form='comment'] textarea")).toHaveAttribute('readonly', '')
  expect(state.loginCount).toBe(1)
  expect(state.meCount).toBe(1)
  expect(state.exchangeCount).toBe(3)
})

test('dedicated iframe изолирует стили, наследует только fontFamily и открывает legal link напрямую', async ({ page }) => {
  const state = await setupWidgetServer(page)
  await page.goto('/login')
  await page.evaluate(() => {
    document.head.insertAdjacentHTML('beforeend', '<style>button{position:fixed!important;color:rgb(255,0,0)!important}</style>')
    document.body.innerHTML = '<div id="comments" style="font-family:&quot;Brand Sans&quot;, Arial, sans-serif"></div>'
  })
  const frame = await mountWidget(page, `${new URL(page.url()).origin}/article`)
  const iframe = page.locator('#comments iframe')
  await expect(frame.getByRole('button', { name: 'Войти, чтобы участвовать' })).toBeVisible()
  await expect(iframe).toHaveAttribute('sandbox', 'allow-scripts allow-same-origin allow-popups')
  expect((await iframe.getAttribute('sandbox'))?.split(' ')).not.toContain('allow-popups-to-escape-sandbox')
  expect(await page.locator('#comments').evaluate((element) => Boolean(element.shadowRoot))).toBe(false)

  const presentation = await frame.locator('html').evaluate(() => ({
    fontFamily: document.documentElement.style.fontFamily,
    colorScheme: document.documentElement.style.colorScheme,
  }))
  expect(presentation.fontFamily.replace(/["']/gu, '')).toBe('Brand Sans, Arial, sans-serif')
  expect(presentation.colorScheme).toBe('light')
  const buttonStyle = await frame.getByRole('button', { name: 'Войти, чтобы участвовать' }).evaluate((button) => ({
    position: getComputedStyle(button).position,
    color: getComputedStyle(button).color,
  }))
  expect(buttonStyle.position).not.toBe('fixed')
  expect(buttonStyle.color).not.toBe('rgb(255, 0, 0)')

  await frame.getByRole('button', { name: 'Войти, чтобы участвовать' }).click()
  await frame.getByRole('button', { name: 'Регистрация' }).click()
  const policyLink = frame.getByRole('link', { name: 'политика' })
  await expect(policyLink).toHaveAttribute('target', '_blank')
  await expect(policyLink).toHaveAttribute('rel', 'noopener noreferrer')
  const popupPromise = page.waitForEvent('popup')
  await policyLink.click()
  const popup = await popupPromise
  await expect(popup).toHaveURL(`${WIDGET_ORIGIN}/legal/privacy-policy.html`)
  await popup.close()

  await frame.getByRole('button', { name: 'Войти', exact: true }).first().click()
  await frame.locator("form[data-cloud-comment-form='auth'] input[name='email']").fill('author@example.test')
  await frame.locator("form[data-cloud-comment-form='auth'] input[name='password']").fill('password-123')
  await frame.locator("form[data-cloud-comment-form='auth'] button[data-cloud-comment-submit]").click()
  await expect(frame.getByText('Вы вошли как author@example.test')).toBeVisible()

  await frame.locator("[data-profile-action='toggle']").click()
  await expect(frame.locator("[data-account-action='logout']")).toBeVisible()
  await expect(frame.locator("[data-account-action='export-data']")).toHaveCount(0)
  await expect(frame.locator("[data-account-action='delete']")).toHaveCount(0)
  expect(state.accountRequests).toEqual([])

  await iframe.evaluate((element) => { (element as HTMLElement).style.width = '320px' })
  expect(await frame.locator('.cloud-comment').evaluate((shell) => shell.scrollWidth <= shell.clientWidth)).toBe(true)
  const accessibility = await new AxeBuilder({ page }).include('#comments').disableRules(['color-contrast']).analyze()
  expect(accessibility.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([])
  expect(state.contextlessRequests).toEqual([])
  expect(state.configOrigins).toEqual([null])
  expect(state.exchangeOrigins).toEqual([WIDGET_ORIGIN])
  expect(state.loginOrigins).toEqual([WIDGET_ORIGIN])
})

test('одинаковый widget и API origin завершается fail closed без iframe и bootstrap', async ({ page }) => {
  const state = await setupWidgetServer(page)
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments"></div>' })
  await page.addScriptTag({ path: loaderBundle })
  await page.evaluate(({ siteId, widgetOrigin, pageUrl }) => {
    window.CloudCommentWidget?.init({
      siteId,
      apiBaseUrl: `${widgetOrigin}/api`,
      frameBaseUrl: widgetOrigin,
      pageUrl,
      target: '#comments',
    })
  }, { siteId: SITE_ID, widgetOrigin: WIDGET_ORIGIN, pageUrl: `${new URL(page.url()).origin}/article` })

  await expect(page.locator('#comments [role="alert"]'))
    .toHaveText('Нужен отдельный адрес виджета')
  await expect(page.locator('#comments iframe')).toHaveCount(0)
  expect(state.bootstrapPageUrls).toEqual([])
  expect(state.exchangeCount).toBe(0)
  expect(state.loginCount).toBe(0)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toMatch(/bearer|context|password/i)
  expect(state.contextlessRequests).toEqual([])
})

test('frame runtime стабильно отображает сто комментариев', async ({ page }, testInfo) => {
  test.skip(!isPrimaryBrowser(testInfo), 'Нагрузочный сценарий достаточно проверить в основном Chromium-профиле')
  const comments = Array.from({ length: 100 }, (_, index) => createComment(index + 1, {
    authorDisplayName: index === 0 ? 'author@example.com' : `Автор ${index + 1}`,
  }))
  await setupWidgetServer(page, { comments })
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments" style="width:320px"></div>' })
  const startedAt = Date.now()
  const frame = await mountWidget(page, `${new URL(page.url()).origin}/article`)

  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(100)
  await expect(frame.locator('body')).toContainText('Участник')
  await expect(frame.locator('body')).not.toContainText('author@example.com')
  expect(Date.now() - startedAt).toBeLessThan(3000)
  expect(await frame.locator('.cloud-comment').evaluate((shell) => shell.scrollWidth <= shell.clientWidth)).toBe(true)
})

test('iframe догружает корни без дублей и сбрасывает страницу при сортировке', async ({ page }, testInfo) => {
  test.skip(!isPrimaryBrowser(testInfo), 'Функциональный сценарий достаточно проверить в основном Chromium-профиле')
  const comments = Array.from({ length: 45 }, (_, index) => createComment(index + 1))
  const state = await setupWidgetServer(page, { comments, paginateComments: true })
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments"></div>' })
  const frame = await mountWidget(page, `${new URL(page.url()).origin}/article`)

  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(20)
  await frame.getByRole('button', { name: 'Показать ещё комментарии (25)' }).click()
  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(39)
  await frame.getByRole('button', { name: 'Показать ещё комментарии (6)' }).click()
  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(45)
  await expect(frame.locator("[data-load-comments='true']")).toHaveCount(0)
  await expect(frame.locator('body')).toContainText('Комментарий 45')

  await frame.locator("[data-comment-sort='true']").selectOption('NEWEST')

  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(20)
  await expect(frame.locator('.cloud-comment__comment').first()).toContainText('Комментарий 45')
  await expect(frame.getByRole('button', { name: 'Показать ещё комментарии (25)' })).toBeVisible()
  expect(state.requestedCommentPages).toEqual([
    { sort: 'PINNED_FIRST', page: 1 },
    { sort: 'PINNED_FIRST', page: 2 },
    { sort: 'PINNED_FIRST', page: 3 },
    { sort: 'NEWEST', page: 1 },
  ])
  expect(state.contextlessRequests).toEqual([])
})

test('frame bundle сохраняет draft, focus и selection при перерисовках и POST 500', async ({ page }, testInfo) => {
  test.skip(!isPrimaryBrowser(testInfo), 'Сценарий фокуса достаточно проверить в основном Chromium-профиле')
  const comment = createComment(10, { ownedByCurrentUser: true, content: 'Исходный комментарий' })
  await setupWidgetServer(page, { comments: [comment], failCommentPost: true })
  await page.goto('/login')
  await page.evaluate(() => { document.body.innerHTML = '<div id="comments"></div>' })
  const frame = await mountWidget(page, `${new URL(page.url()).origin}/article`)
  await loginInFrame(frame)
  await expect(frame.locator('.cloud-comment__comment')).toHaveCount(1)
  const draft = '  Черновик должен сохраниться  '
  const textarea = frame.locator("[data-cloud-comment-form='comment'] textarea")
  await textarea.fill(draft)

  const reaction = frame.locator("[data-reaction-type='LIKE']")
  await reaction.focus()
  await reaction.click()
  await expect(reaction).toContainText('1')
  await expect(reaction).toBeFocused()
  await expect(textarea).toHaveValue(draft)

  const profile = frame.locator("[data-profile-action='toggle']")
  await profile.focus()
  await profile.click()
  await expect(profile).toHaveAttribute('aria-expanded', 'true')
  await expect(profile).toBeFocused()

  const sort = frame.locator("[data-comment-sort='true']")
  await sort.focus()
  await sort.selectOption('NEWEST')
  await expect(sort).toHaveValue('NEWEST')
  await expect(sort).toBeFocused()
  await textarea.evaluate((element: HTMLTextAreaElement) => {
    element.focus()
    element.setSelectionRange(2, 12, 'forward')
  })
  await frame.locator("[data-cloud-comment-form='comment'] button[data-cloud-comment-submit]").click()
  await expect(frame.locator('body')).toContainText('Сервер не сохранил комментарий')
  await expect(textarea).toHaveValue(draft)
  await expect(textarea).toBeFocused()
  expect(await textarea.evaluate((element: HTMLTextAreaElement) => [
    element.selectionStart,
    element.selectionEnd,
    element.selectionDirection,
  ])).toEqual([2, 12, 'forward'])
})

function createComment(index: number, overrides: {
  content?: string
  parentId?: string | null
  authorDisplayName?: string
  ownedByCurrentUser?: boolean
} = {}) {
  const timestamp = '2026-07-13T10:00:00Z'
  return {
    id: `00000000-0000-0000-0000-${String(index).padStart(12, '0')}`,
    siteId: SITE_ID,
    pageId: '00000000-0000-0000-0000-000000000002',
    parentId: overrides.parentId ?? null,
    author: { id: '00000000-0000-0000-0000-000000000003', displayName: overrides.authorDisplayName ?? 'Анна' },
    content: overrides.content ?? `Комментарий ${index}`,
    status: 'APPROVED',
    createdAt: timestamp,
    updatedAt: timestamp,
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: overrides.ownedByCurrentUser ?? false,
    reactions: reactionSet(),
    replyCount: 0,
    replies: [],
  }
}

function reactionSet() {
  return [
    { type: 'LIKE', emoji: '👍', label: 'Нравится', count: 0, reactedByCurrentUser: false },
    { type: 'LOVE', emoji: '❤️', label: 'Люблю', count: 0, reactedByCurrentUser: false },
    { type: 'LAUGH', emoji: '😂', label: 'Смешно', count: 0, reactedByCurrentUser: false },
    { type: 'WOW', emoji: '😮', label: 'Удивительно', count: 0, reactedByCurrentUser: false },
  ]
}

function commenterUser() {
  return {
    id: '00000000-0000-0000-0000-000000000003',
    email: 'author@example.test',
    roles: ['COMMENTER'],
    createdAt: '2026-07-13T00:00:00Z',
    updatedAt: '2026-07-13T00:00:00Z',
  }
}

async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body, headers: corsHeaders() })
}

function corsHeaders(): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,PUT,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization,Content-Type,X-CloudComment-Widget-Context,X-CloudComment-Page-Url',
    'Access-Control-Max-Age': '600',
    'Cache-Control': 'no-store',
  }
}

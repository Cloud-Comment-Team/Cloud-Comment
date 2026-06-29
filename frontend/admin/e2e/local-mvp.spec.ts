import { expect, test } from '@playwright/test'

const ADMIN_ORIGIN = 'http://127.0.0.1:5173'
const API_BASE_URL = `${ADMIN_ORIGIN}/api`
const PASSWORD = 'Password123!'

test('local MVP flow: auth, site admin, public comments API and widget script', async ({ page, request }) => {
  const suffix = Date.now().toString(36)
  const email = `e2e-${suffix}@example.com`
  const siteName = `E2E Site ${suffix}`
  const domain = `e2e-${suffix}.example.com`
  const commentText = `E2E comment ${suffix}`
  const pageUrl = `${ADMIN_ORIGIN}/external-page/${suffix}`

  await page.goto('/register')
  await expect(page.getByRole('heading', { name: 'Регистрация' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Повторите пароль').fill(PASSWORD)
  await page.getByRole('button', { name: 'Зарегистрироваться' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Вход в админку' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Войти' }).click()

  await expect(page.getByRole('heading', { name: 'Дашборд' })).toBeVisible()
  await page.getByRole('link', { name: 'Сайты' }).click()
  await expect(page.getByRole('heading', { name: 'Сайты' })).toBeVisible()
  await page.getByRole('link', { name: 'Создать сайт' }).click()
  await expect(page.getByRole('heading', { name: 'Новый сайт' })).toBeVisible()

  await page.getByLabel('Название').fill(siteName)
  await page.getByLabel('Домен').fill(domain)
  await page.getByLabel('Режим модерации').selectOption('POST_MODERATION')
  await page.getByLabel('Allowed origins').fill(`${ADMIN_ORIGIN}\nhttp://localhost:5173`)
  await page.getByRole('button', { name: 'Создать сайт' }).click()

  await expect(page.getByRole('heading', { name: siteName })).toBeVisible()
  await expect(page.getByText(domain)).toBeVisible()
  const siteId = page.url().match(/\/sites\/([0-9a-f-]{36})$/)?.[1]
  expect(siteId, 'created site id should be present in URL').toBeTruthy()
  await expect(page.getByText(`data-site-id="${siteId}"`)).toBeVisible()

  const token = await page.evaluate(() => window.localStorage.getItem('cloud-comment.admin.authToken'))
  expect(token, 'login should store bearer token').toBeTruthy()

  const createCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      pageUrl,
      parentId: null,
      content: commentText,
    },
  })
  expect(createCommentResponse.status()).toBe(201)
  await expect(createCommentResponse).toBeOK()
  const createdComment = await createCommentResponse.json()
  expect(createdComment).toMatchObject({
    siteId,
    content: commentText,
    status: 'APPROVED',
  })

  const listCommentsResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })
  await expect(listCommentsResponse).toBeOK()
  const commentsPage = await listCommentsResponse.json()
  expect(commentsPage.items).toHaveLength(1)
  expect(commentsPage.items[0]).toMatchObject({
    content: commentText,
    status: 'APPROVED',
  })

  await page.goto('/')
  await page.evaluate(
    ({ apiBaseUrl, currentPageUrl, scriptUrl, widgetSiteId }) =>
      new Promise<void>((resolve, reject) => {
        const script = document.createElement('script')
        script.src = scriptUrl
        script.dataset.siteId = widgetSiteId
        script.dataset.apiBaseUrl = apiBaseUrl
        script.dataset.pageUrl = currentPageUrl
        script.onload = () => resolve()
        script.onerror = () => reject(new Error('CloudComment widget script failed to load'))
        document.body.append(script)
      }),
    {
      apiBaseUrl: 'http://localhost/api',
      currentPageUrl: pageUrl,
      scriptUrl: 'http://localhost/widget/cloud-comment-widget.js',
      widgetSiteId: siteId,
    },
  )

  const widget = page.locator('#cloud-comment-widget')
  await expect(widget.locator('.cloud-comment__title')).toHaveText('Comments')
  await expect(widget.locator('.cloud-comment__meta')).toContainText(`site: ${siteId}`)
  await expect(widget.locator('.cloud-comment__meta')).toContainText(`page: ${pageUrl}`)
})

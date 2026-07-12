import { expect, test } from '@playwright/test'

const ADMIN_ORIGIN = (process.env.E2E_BASE_URL ?? 'http://localhost').replace(/\/+$/, '')
const API_BASE_URL = `${ADMIN_ORIGIN}/api`
const PASSWORD = 'Password123!'
const WIDGET_ACCENT_COLOR = '#7c3aed'

test('local MVP flow: auth, site admin, public comments API and widget script', async ({ page, request, context }) => {
  const suffix = Date.now().toString(36)
  const email = `e2e-${suffix}@example.com`
  const siteName = `E2E Site ${suffix}`
  const domain = `e2e-${suffix}.example.com`
  const commentText = `E2E comment ${suffix}`
  const editedCommentText = `E2E edited comment ${suffix}`
  const apiReplyText = `E2E API reply ${suffix}`
  const pendingWidgetReplyText = `E2E widget pending reply ${suffix}`
  const deleteCommentText = `E2E comment to delete ${suffix}`
  const blockedWord = `blocked-${suffix}`
  const spamCommentText = `Automod should catch ${blockedWord}`
  const pageUrl = `${ADMIN_ORIGIN}/demo-page.html`

  await page.goto('/register')
  await expect(page.getByRole('heading', { name: 'Регистрация' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Повторите пароль').fill(PASSWORD)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Зарегистрироваться' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Вход в админку' })).toBeVisible()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Войти' }).click()

  await expect(page.getByRole('heading', { name: 'Панель владельца сайта' })).toBeVisible()
  await page.getByRole('link', { name: 'Сайты' }).click()
  await expect(page.getByRole('heading', { name: 'Сайты' })).toBeVisible()
  await page.getByRole('link', { name: 'Создать сайт' }).first().click()
  await expect(page.getByRole('heading', { name: 'Подключение сайта' })).toBeVisible()

  await page.getByRole('textbox', { name: 'Название' }).fill(siteName)
  await page.getByRole('textbox', { name: 'Домен' }).fill(domain)
  await page.getByLabel('Режим модерации').selectOption('POST_MODERATION')
  await page.getByRole('textbox', { name: /Разрешённые origins/ }).fill(`${ADMIN_ORIGIN}\nhttp://127.0.0.1:5173`)
  await page.getByLabel('Тема виджета').selectOption('DARK')
  await page.getByRole('textbox', { name: 'HEX акцентного цвета' }).fill(WIDGET_ACCENT_COLOR)
  await page.getByLabel('Скругления').selectOption('LARGE')

  const onboardingPreview = page.locator('aside')
  await expect(onboardingPreview.getByRole('heading', { name: siteName })).toBeVisible()
  await expect(onboardingPreview.getByText(domain)).toBeVisible()
  await expect(onboardingPreview.getByText(ADMIN_ORIGIN, { exact: true }).first()).toBeVisible()
  await expect(onboardingPreview.getByText('Пост-модерация')).toBeVisible()
  await expect(onboardingPreview.getByText('Черновик embed-кода')).toBeVisible()
  await expect(onboardingPreview.getByText('Опубликован сразу')).toBeVisible()
  await expect(onboardingPreview.getByText(`Стиль: Темная, ${WIDGET_ACCENT_COLOR}, large`)).toBeVisible()

  await page.getByRole('button', { name: 'Создать сайт' }).click()

  await expect(page).toHaveURL(/\/sites\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: 'Подключение сайта' })).toBeHidden()
  await expect(page.getByRole('heading', { name: siteName })).toBeVisible()
  await expect(page.getByText(domain, { exact: true }).first()).toBeVisible()
  const siteId = page.url().match(/\/sites\/([0-9a-f-]{36})$/)?.[1]
  expect(siteId, 'created site id should be present in URL').toBeTruthy()
  await expect(page.getByText(`data-site-id="${siteId}"`)).toBeVisible()

  const token = await page.evaluate(() => window.localStorage.getItem('cloud-comment.admin.authToken'))
  expect(token, 'login should store bearer token').toBeTruthy()

  const configResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/config`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
  })
  await expect(configResponse).toBeOK()
  expect(await configResponse.json()).toMatchObject({
    siteId,
    moderationMode: 'POST_MODERATION',
    style: {
      theme: 'DARK',
      accentColor: WIDGET_ACCENT_COLOR,
      cornerRadius: 'LARGE',
    },
  })

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

  const createReplyResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      pageUrl,
      parentId: createdComment.id,
      content: apiReplyText,
    },
  })
  await expect(createReplyResponse).toBeOK()
  const createdReply = await createReplyResponse.json()
  expect(createdReply).toMatchObject({
    siteId,
    parentId: createdComment.id,
    content: apiReplyText,
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
  expect(commentsPage.items[0].replies).toHaveLength(1)
  expect(commentsPage.items[0].replies[0]).toMatchObject({
    content: apiReplyText,
    parentId: createdComment.id,
    status: 'APPROVED',
  })

  const reactionResponse = await request.put(`${API_BASE_URL}/public/sites/${siteId}/comments/${createdComment.id}/reaction`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      type: 'LOVE',
    },
  })
  await expect(reactionResponse).toBeOK()
  const reactionSummary = await reactionResponse.json()
  expect(reactionSummary.reactions).toEqual(expect.arrayContaining([
    expect.objectContaining({
      type: 'LOVE',
      count: 1,
      reactedByCurrentUser: true,
    }),
  ]))

  const viewerCommentsResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })

  const rejectedOriginResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/config`, {
    headers: {
      Origin: `${ADMIN_ORIGIN}.evil.example`,
    },
  })
  expect(rejectedOriginResponse.status()).toBe(404)
  await expect(viewerCommentsResponse).toBeOK()
  const viewerComments = await viewerCommentsResponse.json()
  expect(viewerComments.items[0]).toMatchObject({
    id: createdComment.id,
    ownedByCurrentUser: true,
  })
  expect(viewerComments.items[0].reactions).toEqual(expect.arrayContaining([
    expect.objectContaining({
      type: 'LOVE',
      count: 1,
      reactedByCurrentUser: true,
    }),
  ]))

  const editCommentResponse = await request.patch(`${API_BASE_URL}/public/sites/${siteId}/comments/${createdComment.id}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      content: editedCommentText,
    },
  })
  await expect(editCommentResponse).toBeOK()
  const editedComment = await editCommentResponse.json()
  expect(editedComment).toMatchObject({
    id: createdComment.id,
    content: editedCommentText,
    status: 'APPROVED',
    ownedByCurrentUser: true,
  })
  expect(editedComment.editedAt).toEqual(expect.any(String))

  const updateFlagsResponse = await request.patch(`${API_BASE_URL}/moderation/comments/${createdComment.id}/flags`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    data: {
      pinned: true,
      favorite: true,
    },
  })
  await expect(updateFlagsResponse).toBeOK()
  expect(await updateFlagsResponse.json()).toMatchObject({ pinned: true, favorite: true })

  const favoriteQueueResponse = await request.get(`${API_BASE_URL}/moderation/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    params: {
      favorite: 'true',
      page: '1',
      pageSize: '20',
    },
  })
  await expect(favoriteQueueResponse).toBeOK()
  expect((await favoriteQueueResponse.json()).items).toEqual(expect.arrayContaining([
    expect.objectContaining({ id: createdComment.id, pinned: true, favorite: true }),
  ]))

  const pinnedPublicResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      sort: 'TOP_REACTIONS',
      page: '1',
      pageSize: '20',
    },
  })
  await expect(pinnedPublicResponse).toBeOK()
  const pinnedPublicComments = await pinnedPublicResponse.json()
  expect(pinnedPublicComments.items[0]).toMatchObject({ id: createdComment.id, pinned: true })
  expect(pinnedPublicComments.items[0]).not.toHaveProperty('favorite')

  const commentsAfterEditResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })
  await expect(commentsAfterEditResponse).toBeOK()
  const commentsAfterEdit = await commentsAfterEditResponse.json()
  expect(commentsAfterEdit.items[0]).toMatchObject({
    id: createdComment.id,
    content: editedCommentText,
    ownedByCurrentUser: true,
  })

  const createDeletedCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      pageUrl,
      parentId: null,
      content: deleteCommentText,
    },
  })
  await expect(createDeletedCommentResponse).toBeOK()
  const deletedCommentCandidate = await createDeletedCommentResponse.json()

  const deleteCommentResponse = await request.delete(`${API_BASE_URL}/public/sites/${siteId}/comments/${deletedCommentCandidate.id}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
  })
  expect(deleteCommentResponse.status()).toBe(204)

  const commentsAfterDeleteResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })
  await expect(commentsAfterDeleteResponse).toBeOK()
  expect(JSON.stringify(await commentsAfterDeleteResponse.json())).not.toContain(deleteCommentText)

  const enableAutomodResponse = await request.patch(`${API_BASE_URL}/sites/${siteId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    data: {
      autoModeration: {
        enabled: true,
        strictness: 'STRICT',
        blockedWords: [blockedWord],
        holdLinks: true,
        blockLinks: false,
        maxLinks: 2,
      },
    },
  })
  await expect(enableAutomodResponse).toBeOK()

  const createSpamCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      pageUrl,
      parentId: null,
      content: spamCommentText,
    },
  })
  expect(createSpamCommentResponse.status()).toBe(201)
  const spamComment = await createSpamCommentResponse.json()
  expect(spamComment).toMatchObject({
    content: spamCommentText,
    status: 'SPAM',
  })

  const commentsAfterAutomodResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })
  await expect(commentsAfterAutomodResponse).toBeOK()
  expect(JSON.stringify(await commentsAfterAutomodResponse.json())).not.toContain(spamCommentText)

  const enablePreModerationResponse = await request.patch(`${API_BASE_URL}/sites/${siteId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    data: {
      moderationMode: 'PRE_MODERATION',
    },
  })
  await expect(enablePreModerationResponse).toBeOK()

  const moderationPage = await context.newPage()
  await moderationPage.goto('/moderation')
  await expect(moderationPage.getByRole('heading', { name: 'Модерация комментариев' })).toBeVisible()

  const realtimeCommentText = `E2E realtime comment ${suffix}`
  const realtimeCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ADMIN_ORIGIN,
    },
    data: {
      pageUrl,
      parentId: null,
      content: realtimeCommentText,
    },
  })
  expect(realtimeCommentResponse.status()).toBe(201)
  await expect(moderationPage.getByText(realtimeCommentText)).toBeVisible({ timeout: 15_000 })

  const analyticsResponse = await request.get(`${API_BASE_URL}/analytics/owner`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    params: { range: '7d' },
  })
  await expect(analyticsResponse).toBeOK()
  expect((await analyticsResponse.json()).summary.comments).toBeGreaterThan(0)

  await page.goto(`/demo-page.html?siteId=${siteId}`)

  const widget = page.locator('#comments')
  const widgetShell = widget.locator('.cloud-comment')
  await expect(widget.locator('.cloud-comment__title')).toHaveText('Комментарии')
  await expect(widget.locator('.cloud-comment__comment-content')).toContainText([editedCommentText, apiReplyText])
  await expect(widget.locator('.cloud-comment__pinned')).toHaveText('Закреплён')
  const sortSelector = widget.locator('select[data-comment-sort="true"]')
  await expect(sortSelector).toHaveValue('PINNED_FIRST')
  await sortSelector.selectOption('TOP_REACTIONS')
  await expect(sortSelector).toHaveValue('TOP_REACTIONS')
  await expect(widgetShell).toHaveAttribute('data-theme', 'dark')
  await expect(widgetShell).toHaveAttribute('data-radius', 'large')
  await expect
    .poll(() => widgetShell.evaluate((element) => getComputedStyle(element).getPropertyValue('--cc-custom-accent').trim()))
    .toBe(WIDGET_ACCENT_COLOR)

  const loveButton = widget.locator('button[data-reaction-type="LOVE"]').first()
  const wowButton = widget.locator('button[data-reaction-type="WOW"]').first()
  await expect(loveButton).toHaveClass(/cloud-comment__reaction--active/)
  await expect(loveButton.locator('.cloud-comment__reaction-count')).toHaveText('1')
  await wowButton.click()
  await expect(wowButton).toHaveClass(/cloud-comment__reaction--active/)
  await expect(wowButton.locator('.cloud-comment__reaction-count')).toHaveText('1')
  await expect(loveButton.locator('.cloud-comment__reaction-count')).toHaveText('0')

  await widget.locator('.cloud-comment__reply-button').first().click()
  await expect(widget.locator('.cloud-comment__reply-context')).toBeVisible()
  await widget.locator('textarea[name="comment"]').fill(pendingWidgetReplyText)
  await widget.locator('.cloud-comment__form .cloud-comment__button').click()

  await expect(widget.locator('.cloud-comment__message--notice')).toBeVisible()
  await expect(widget.getByText(pendingWidgetReplyText)).toBeVisible()
  await expect(widget.locator('.cloud-comment__status')).toBeVisible()

  const publicCommentsAfterPendingReply = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: {
      Origin: ADMIN_ORIGIN,
    },
    params: {
      pageUrl,
      page: '1',
      pageSize: '20',
    },
  })
  await expect(publicCommentsAfterPendingReply).toBeOK()
  const commentsAfterPendingReply = await publicCommentsAfterPendingReply.json()
  expect(JSON.stringify(commentsAfterPendingReply)).not.toContain(pendingWidgetReplyText)

  await moderationPage.getByRole('button', { name: 'Выйти' }).click()
  await expect(moderationPage).toHaveURL(/\/login$/)
})

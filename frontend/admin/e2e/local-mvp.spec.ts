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
  await page.reload()
  await expect(page.getByRole('textbox', { name: 'Название' })).toHaveValue(siteName)
  await expect(page.getByRole('textbox', { name: 'Домен' })).toHaveValue(domain)
  await page.getByRole('button', { name: 'Далее' }).click()
  await page.getByLabel('Режим модерации').selectOption('POST_MODERATION')
  await page.getByRole('textbox', { name: /Разрешённые origins/ }).fill(
    `${ADMIN_ORIGIN}\nhttp://127.0.0.1\nhttp://127.0.0.1:5173`,
  )
  await page.getByRole('button', { name: 'Далее' }).click()
  await page.getByLabel('Тема виджета').selectOption('DARK')
  await page.getByRole('textbox', { name: 'HEX акцентного цвета' }).fill(WIDGET_ACCENT_COLOR)
  await page.getByLabel('Скругления').selectOption('LARGE')
  await page.getByRole('button', { name: 'Далее' }).click()

  const onboardingPreview = page.locator('aside')
  await expect(onboardingPreview.getByRole('heading', { name: siteName })).toBeVisible()
  await expect(onboardingPreview.getByText(domain)).toBeVisible()
  await expect(onboardingPreview.getByText(ADMIN_ORIGIN, { exact: true }).first()).toBeVisible()
  await expect(onboardingPreview.getByText('Пост-модерация')).toBeVisible()
  await expect(onboardingPreview.getByText('Черновик embed-кода')).toBeVisible()
  await expect(page.frameLocator('iframe[title="Предварительный просмотр создаваемого виджета"]').getByText('Комментарии').first()).toBeVisible()

  await page.getByRole('button', { name: 'Создать сайт' }).click()

  await expect(page).toHaveURL(/\/sites\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: 'Подключение сайта' })).toBeHidden()
  await expect(page.getByRole('heading', { name: siteName })).toBeVisible()
  await expect(page.getByText(domain, { exact: true }).first()).toBeVisible()
  const siteId = page.url().match(/\/sites\/([0-9a-f-]{36})$/)?.[1]
  expect(siteId, 'created site id should be present in URL').toBeTruthy()
  await expect(page.getByRole('heading', { name: 'Виджет ещё не подключался' })).toBeVisible()
  await page.getByRole('button', { name: 'Установка' }).click()
  await expect(page.getByText(`data-site-id="${siteId}"`)).toBeVisible()

  const token = await page.evaluate(() => window.localStorage.getItem('cloud-comment.admin.authToken'))
  expect(token, 'login should store bearer token').toBeTruthy()

  const initialRejectedOriginResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/config`, {
    headers: {
      Origin: `${ADMIN_ORIGIN}.evil.example`,
    },
  })
  expect(initialRejectedOriginResponse.status()).toBe(404)
  const initiallyRejectedInstallationResponse = await request.get(`${API_BASE_URL}/sites/${siteId}/installation-status`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(initiallyRejectedInstallationResponse).toBeOK()
  expect(await initiallyRejectedInstallationResponse.json()).toMatchObject({
    status: 'REJECTED',
    reason: 'ORIGIN_REJECTED',
    lastRejectedOrigin: `${ADMIN_ORIGIN}.evil.example`,
  })

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

  const healthyInstallationResponse = await request.get(`${API_BASE_URL}/sites/${siteId}/installation-status`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(healthyInstallationResponse).toBeOK()
  expect(await healthyInstallationResponse.json()).toMatchObject({
    status: 'HEALTHY',
    reason: 'RECENT_SUCCESS',
    widgetSeen: true,
    lastSuccessfulOrigin: ADMIN_ORIGIN,
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

  for (const index of [2, 3, 4]) {
    const response = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
      headers: { Authorization: `Bearer ${token}`, Origin: ADMIN_ORIGIN },
      data: { pageUrl, parentId: createdComment.id, content: `${apiReplyText} ${index}` },
    })
    await expect(response).toBeOK()
  }

  const limitedRepliesResponse = await request.get(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: { Origin: ADMIN_ORIGIN },
    params: { pageUrl, page: '1', pageSize: '20', replyLimit: '1' },
  })
  await expect(limitedRepliesResponse).toBeOK()
  const limitedRepliesPage = await limitedRepliesResponse.json()
  expect(limitedRepliesPage.items[0].replyCount).toBe(4)
  expect(limitedRepliesPage.items[0].replies).toHaveLength(1)

  const repliesPageResponse = await request.get(
    `${API_BASE_URL}/public/sites/${siteId}/comments/${createdComment.id}/replies`,
    { headers: { Origin: ADMIN_ORIGIN }, params: { page: '2', pageSize: '2' } },
  )
  await expect(repliesPageResponse).toBeOK()
  const repliesPage = await repliesPageResponse.json()
  expect(repliesPage.totalItems).toBe(4)
  expect(repliesPage.items).toHaveLength(2)

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
  const rejectedInstallationResponse = await request.get(`${API_BASE_URL}/sites/${siteId}/installation-status`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(rejectedInstallationResponse).toBeOK()
  expect(await rejectedInstallationResponse.json()).toMatchObject({
    status: 'HEALTHY',
    reason: 'RECENT_SUCCESS',
    lastRejectedOrigin: `${ADMIN_ORIGIN}.evil.example`,
    lastSuccessfulOrigin: ADMIN_ORIGIN,
  })
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

  const policiesAfterLegacyResponse = await request.get(`${API_BASE_URL}/sites/${siteId}/automoderation/policies`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(policiesAfterLegacyResponse).toBeOK()
  const policiesAfterLegacy = await policiesAfterLegacyResponse.json()
  expect(policiesAfterLegacy.activePolicy).toMatchObject({
    enabled: true,
    preset: 'CUSTOM',
    executionMode: 'LIVE',
    active: true,
  })

  const createShadowDraftResponse = await request.post(`${API_BASE_URL}/sites/${siteId}/automoderation/policies`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { preset: 'CUSTOM', enabled: true, executionMode: 'SHADOW' },
  })
  expect(createShadowDraftResponse.status()).toBe(201)
  const createdShadowDraft = await createShadowDraftResponse.json()
  const shadowBlockedWord = `shadow-${suffix}`
  const updateShadowDraftResponse = await request.patch(
    `${API_BASE_URL}/sites/${siteId}/automoderation/policies/${createdShadowDraft.id}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        expectedRevision: createdShadowDraft.revision,
        enabled: true,
        preset: 'CUSTOM',
        executionMode: 'SHADOW',
        reviewThreshold: 45,
        spamThreshold: 90,
        cleanAction: 'APPROVE',
        linkAction: 'REVIEW',
        maxLinks: 2,
        blockedWords: [shadowBlockedWord],
      },
    },
  )
  await expect(updateShadowDraftResponse).toBeOK()
  const shadowDraft = await updateShadowDraftResponse.json()

  const simulateShadowResponse = await request.post(
    `${API_BASE_URL}/sites/${siteId}/automoderation/policies/${shadowDraft.id}/simulate`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { content: `Проверка ${shadowBlockedWord}` },
    },
  )
  await expect(simulateShadowResponse).toBeOK()
  expect(await simulateShadowResponse.json()).toMatchObject({
    decision: 'SPAM',
    baselineStatus: 'APPROVED',
    effectiveStatus: 'APPROVED',
    applied: false,
  })

  const publishShadowResponse = await request.post(
    `${API_BASE_URL}/sites/${siteId}/automoderation/policies/${shadowDraft.id}/publish`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        expectedRevision: shadowDraft.revision,
        expectedActiveVersionId: policiesAfterLegacy.activePolicy.id,
      },
    },
  )
  await expect(publishShadowResponse).toBeOK()
  const shadowPolicy = await publishShadowResponse.json()
  expect(shadowPolicy).toMatchObject({ executionMode: 'SHADOW', active: true })

  const shadowCommentText = `E2E shadow ${shadowBlockedWord}`
  const createShadowCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: { Authorization: `Bearer ${token}`, Origin: ADMIN_ORIGIN },
    data: { pageUrl, parentId: null, content: shadowCommentText },
  })
  expect(createShadowCommentResponse.status()).toBe(201)
  const shadowComment = await createShadowCommentResponse.json()
  expect(shadowComment).toMatchObject({ content: shadowCommentText, status: 'APPROVED' })
  expect(shadowComment).not.toHaveProperty('autoModeration')

  const shadowModerationResponse = await request.get(`${API_BASE_URL}/moderation/comments/${shadowComment.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(shadowModerationResponse).toBeOK()
  expect(await shadowModerationResponse.json()).toMatchObject({
    id: shadowComment.id,
    status: 'APPROVED',
    moderationReason: null,
    autoModeration: {
      policyVersionId: shadowPolicy.id,
      executionMode: 'SHADOW',
      decision: 'SPAM',
      feedback: null,
    },
  })

  const feedbackResponse = await request.put(
    `${API_BASE_URL}/moderation/comments/${shadowComment.id}/automoderation-feedback`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { type: 'FALSE_POSITIVE' },
    },
  )
  await expect(feedbackResponse).toBeOK()
  expect(await feedbackResponse.json()).toMatchObject({ type: 'FALSE_POSITIVE' })

  const createLiveDraftResponse = await request.post(`${API_BASE_URL}/sites/${siteId}/automoderation/policies`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { preset: 'CUSTOM', enabled: true, executionMode: 'LIVE' },
  })
  expect(createLiveDraftResponse.status()).toBe(201)
  const liveDraft = await createLiveDraftResponse.json()
  const publishLiveResponse = await request.post(
    `${API_BASE_URL}/sites/${siteId}/automoderation/policies/${liveDraft.id}/publish`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        expectedRevision: liveDraft.revision,
        expectedActiveVersionId: shadowPolicy.id,
      },
    },
  )
  await expect(publishLiveResponse).toBeOK()
  const livePolicy = await publishLiveResponse.json()
  expect(livePolicy).toMatchObject({ executionMode: 'LIVE', active: true })

  const liveCommentText = `E2E live ${shadowBlockedWord}`
  const createLiveCommentResponse = await request.post(`${API_BASE_URL}/public/sites/${siteId}/pages/comments`, {
    headers: { Authorization: `Bearer ${token}`, Origin: ADMIN_ORIGIN },
    data: { pageUrl, parentId: null, content: liveCommentText },
  })
  expect(createLiveCommentResponse.status()).toBe(201)
  expect(await createLiveCommentResponse.json()).toMatchObject({ content: liveCommentText, status: 'SPAM' })

  const rollbackShadowResponse = await request.post(
    `${API_BASE_URL}/sites/${siteId}/automoderation/versions/${shadowPolicy.id}/rollback`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { expectedActiveVersionId: livePolicy.id },
    },
  )
  await expect(rollbackShadowResponse).toBeOK()
  expect(await rollbackShadowResponse.json()).toMatchObject({
    executionMode: 'SHADOW',
    active: true,
    basedOnVersionId: shadowPolicy.id,
  })

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
  await expect(moderationPage.getByRole('heading', { name: 'Модерация', exact: true })).toBeVisible()
  await moderationPage.getByRole('button', { name: 'Уведомления', exact: true }).click()
  await expect(moderationPage.getByRole('dialog', { name: 'Центр уведомлений' })).toBeVisible()

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
  const realtimeComment = await realtimeCommentResponse.json()
  await expect(moderationPage.getByText(realtimeCommentText)).toBeVisible({ timeout: 15_000 })

  const storedNotificationsResponse = await request.get(`${API_BASE_URL}/notifications`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(storedNotificationsResponse).toBeOK()
  const storedNotifications = await storedNotificationsResponse.json()
  const realtimeNotification = storedNotifications.items.find(
    (notification: { commentId: string }) => notification.commentId === realtimeComment.id,
  )
  expect(realtimeNotification).toMatchObject({
    commentId: realtimeComment.id,
    siteId,
    contentPreview: realtimeCommentText,
    readAt: null,
  })

  const unreadBeforeReloadResponse = await request.get(`${API_BASE_URL}/notifications/unread-count`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(unreadBeforeReloadResponse).toBeOK()
  const unreadBeforeReload = (await unreadBeforeReloadResponse.json()).unreadCount
  expect(unreadBeforeReload).toBeGreaterThan(0)

  await moderationPage.keyboard.press('Escape')
  await expect(moderationPage.getByRole('dialog', { name: 'Центр уведомлений' })).toBeHidden()
  await moderationPage.reload()
  await moderationPage.getByRole('button', { name: 'Уведомления', exact: true }).click()
  const notificationPanel = moderationPage.getByRole('dialog', { name: 'Центр уведомлений' })
  await expect(notificationPanel.getByText(realtimeCommentText)).toBeVisible()
  const realtimeNotificationButton = notificationPanel.locator('button').filter({ hasText: realtimeCommentText })
  await expect(realtimeNotificationButton).toHaveCount(1)
  await realtimeNotificationButton.click()
  await expect(moderationPage).toHaveURL(new RegExp(`/moderation\\?comment=${realtimeComment.id}&view=pending$`))

  const unreadAfterOpenResponse = await request.get(`${API_BASE_URL}/notifications/unread-count`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(unreadAfterOpenResponse).toBeOK()
  expect((await unreadAfterOpenResponse.json()).unreadCount).toBeLessThan(unreadBeforeReload)

  const moderationCountsResponse = await request.get(`${API_BASE_URL}/moderation/counts`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(moderationCountsResponse).toBeOK()
  const moderationCounts = await moderationCountsResponse.json()
  expect(moderationCounts.statuses.SPAM).toBeGreaterThanOrEqual(1)
  expect(moderationCounts.statuses.PENDING).toBeGreaterThanOrEqual(1)
  expect(moderationCounts.requiringDecision).toBeGreaterThanOrEqual(2)

  const repeatedStatusesResponse = await request.get(
    `${API_BASE_URL}/moderation/comments?statuses=PENDING&statuses=SPAM&page=1&pageSize=30`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  await expect(repeatedStatusesResponse).toBeOK()
  const repeatedStatuses = await repeatedStatusesResponse.json()
  expect(repeatedStatuses.items.map((item: { id: string }) => item.id)).toEqual(
    expect.arrayContaining([spamComment.id, realtimeComment.id]),
  )

  const conflictingStatusesResponse = await request.get(
    `${API_BASE_URL}/moderation/comments?status=PENDING&statuses=SPAM`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  expect(conflictingStatusesResponse.status()).toBe(400)

  const operationId = crypto.randomUUID()
  const bulkModerationResponse = await request.post(`${API_BASE_URL}/moderation/comments/bulk-actions`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      operationId,
      commentIds: [spamComment.id, realtimeComment.id],
      action: 'APPROVE',
      reason: 'Проверено в E2E',
    },
  })
  await expect(bulkModerationResponse).toBeOK()
  const bulkModeration = await bulkModerationResponse.json()
  expect(bulkModeration.items).toHaveLength(2)
  expect(bulkModeration.items.every((item: { success: boolean }) => item.success)).toBe(true)
  expect(bulkModeration.items.every((item: { action: { operationId: string } }) => item.action.operationId === operationId)).toBe(true)

  const realtimeModerationAction = bulkModeration.items.find(
    (item: { commentId: string }) => item.commentId === realtimeComment.id,
  ).action
  const undoModerationResponse = await request.post(
    `${API_BASE_URL}/moderation/actions/${realtimeModerationAction.id}/undo`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  expect(undoModerationResponse.status()).toBe(201)
  expect(await undoModerationResponse.json()).toMatchObject({
    action: 'UNDO',
    revertsActionId: realtimeModerationAction.id,
  })

  const restoredCommentResponse = await request.get(`${API_BASE_URL}/moderation/comments/${realtimeComment.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(restoredCommentResponse).toBeOK()
  expect(await restoredCommentResponse.json()).toMatchObject({ status: 'PENDING' })

  const analyticsResponse = await request.get(`${API_BASE_URL}/analytics/owner`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    params: { range: '7d', timeZone: 'Europe/Moscow' },
  })
  await expect(analyticsResponse).toBeOK()
  const analytics = await analyticsResponse.json()
  expect(analytics).toMatchObject({
    range: '7d',
    timeZone: 'Europe/Moscow',
    bucketGranularity: 'DAY',
  })
  expect(analytics.summary.comments).toBeGreaterThan(0)
  expect(analytics.comparison.comments.current).toBe(analytics.summary.comments)
  expect(analytics.workload.requiringDecision).toBeGreaterThan(0)
  expect(analytics.workload.automaticDecisions).toBeGreaterThan(0)
  expect(analytics.workload.manualDecisions).toBeGreaterThan(0)
  expect(analytics.workload.undoActions).toBeGreaterThan(0)
  expect(analytics.moderationDistribution).toEqual(analytics.moderationFunnel)

  for (const [range, bucketGranularity] of [['30d', 'DAY'], ['90d', 'WEEK']] as const) {
    const rangedAnalyticsResponse = await request.get(`${API_BASE_URL}/analytics/owner`, {
      headers: { Authorization: `Bearer ${token}` },
      params: { range, timeZone: 'America/New_York' },
    })
    await expect(rangedAnalyticsResponse).toBeOK()
    expect(await rangedAnalyticsResponse.json()).toMatchObject({
      range,
      timeZone: 'America/New_York',
      bucketGranularity,
      comparison: {
        comments: { current: expect.any(Number), previous: expect.any(Number) },
      },
    })
  }

  const allTimeAnalyticsResponse = await request.get(`${API_BASE_URL}/analytics/owner`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { range: 'all', timeZone: 'UTC' },
  })
  await expect(allTimeAnalyticsResponse).toBeOK()
  expect(await allTimeAnalyticsResponse.json()).toMatchObject({
    range: 'all',
    timeZone: 'UTC',
    bucketGranularity: 'MONTH',
    comparison: null,
  })

  const invalidTimeZoneResponse = await request.get(`${API_BASE_URL}/analytics/owner`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { range: '7d', timeZone: 'Mars/Olympus' },
  })
  expect(invalidTimeZoneResponse.status()).toBe(400)

  await page.goto(`/demo-page.html?siteId=${siteId}`)

  const widget = page.locator('#comments')
  const widgetShell = widget.locator('.cloud-comment')
  await expect(widget.locator('.cloud-comment__title')).toHaveText('Комментарии')
  await expect(widget.locator('.cloud-comment__comment-content')).toContainText([editedCommentText, apiReplyText])
  await expect(widget.getByRole('button', { name: 'Показать ещё ответы (1)' })).toBeVisible()
  await widget.getByRole('button', { name: 'Показать ещё ответы (1)' }).click()
  await expect(widget.locator('.cloud-comment__replies .cloud-comment__comment')).toHaveCount(4)
  await widget.getByRole('button', { name: 'Скрыть ответы' }).click()
  await expect(widget.locator('.cloud-comment__replies')).toHaveCount(0)
  await widget.getByRole('button', { name: 'Показать ответы (4)' }).click()
  await expect(widget.locator('.cloud-comment__replies .cloud-comment__comment')).toHaveCount(4)
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

  const externalPage = await context.newPage()
  await externalPage.goto(`http://127.0.0.1/demo-page.html?siteId=${siteId}`)
  await externalPage.evaluate(
    ({ currentSiteId, apiBaseUrl }) => {
      window.CloudCommentWidget.init({
        siteId: currentSiteId,
        apiBaseUrl,
        pageUrl: 'http://127.0.0.1/demo-page.html',
        target: '#comments',
      })
    },
    { currentSiteId: siteId!, apiBaseUrl: API_BASE_URL },
  )
  const externalWidget = externalPage.locator('#comments')
  await expect(externalWidget.locator('.cloud-comment__title')).toHaveText('Комментарии')
  await externalWidget.getByRole('button', { name: 'Войти, чтобы участвовать' }).click()
  await externalWidget.getByRole('button', { name: 'Регистрация' }).click()
  await expect(externalWidget.getByRole('link', { name: 'политика' })).toHaveAttribute(
    'href',
    `${ADMIN_ORIGIN}/legal/privacy-policy.html`,
  )
  await expect(externalWidget.getByRole('link', { name: 'условия' })).toHaveAttribute(
    'href',
    `${ADMIN_ORIGIN}/legal/terms.html`,
  )

  await moderationPage.getByRole('button', { name: 'Выйти' }).click()
  await expect(moderationPage).toHaveURL(/\/login$/)
})

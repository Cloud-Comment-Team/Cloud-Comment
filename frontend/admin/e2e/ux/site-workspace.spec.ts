import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const SITE_ID = '00000000-0000-0000-0000-000000000133'
const OWNER_ID = '00000000-0000-0000-0000-000000000001'
const OTHER_OWNER_ID = '00000000-0000-0000-0000-000000000002'
const OWNER_DRAFT_KEY = `cloud-comment:site-create-draft:v2:${OWNER_ID}`

async function authenticate(page: Page) {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    json: { id: OWNER_ID, email: 'owner@example.test', roles: ['OWNER'], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  }))
  await page.route('**/api/realtime/tickets', (route) => route.fulfill({ json: { ticket: 'offline-test-ticket' } }))
  await page.route('**/api/notifications/unread-count', (route) => route.fulfill({ json: { unreadCount: 0 } }))
  await page.route('**/api/notifications?*', (route) => route.fulfill({
    json: { items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 },
  }))
  await page.goto('/login')
  await page.evaluate(() => localStorage.setItem('cloud-comment.admin.authToken', 'test-token'))
}

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).disableRules(['color-contrast']).analyze()
  expect(results.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([])
}

test('мастер хранит черновик, проверяет шаги и использует реальный preview', async ({ page }) => {
  await authenticate(page)
  await page.evaluate(({ ownerDraftKey, otherOwnerId }) => {
    localStorage.setItem('cloud-comment:site-create-draft:v1', JSON.stringify({
      expiresAt: Date.now() + 60_000,
      values: { name: 'Чужой общий черновик' },
    }))
    localStorage.setItem(`cloud-comment:site-create-draft:v2:${otherOwnerId}`, JSON.stringify({
      expiresAt: Date.now() + 60_000,
      values: { name: 'Черновик другого владельца' },
    }))
    localStorage.setItem(ownerDraftKey, JSON.stringify({
      expiresAt: Date.now() + 60_000,
      values: { name: { invalid: true }, automodBlockedWords: 42 },
    }))
  }, { ownerDraftKey: OWNER_DRAFT_KEY, otherOwnerId: OTHER_OWNER_ID })
  await page.goto('/sites/new')

  await expect(page.getByRole('navigation', { name: 'Шаги создания сайта' })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Название' })).toHaveValue('')
  await page.getByRole('textbox', { name: 'Название' }).press('Enter')
  await expect(page.getByRole('textbox', { name: 'Название' })).toBeFocused()
  await expect(page.getByText('Укажите название сайта')).toBeVisible()
  await page.getByRole('textbox', { name: 'Название' }).fill('Черновик сайта')
  await page.getByRole('textbox', { name: 'Домен' }).fill('draft.example.com')
  await page.reload()
  await expect(page.getByRole('textbox', { name: 'Название' })).toHaveValue('Черновик сайта')
  await expect(page.getByRole('textbox', { name: 'Домен' })).toHaveValue('draft.example.com')
  await expect.poll(() => page.evaluate(() => localStorage.getItem('cloud-comment:site-create-draft:v1'))).toBeNull()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true)

  await page.getByRole('button', { name: 'Далее' }).click()
  await expect(page.getByRole('textbox', { name: /Разрешённые origins/ })).toBeVisible()
  await page.getByRole('textbox', { name: /Разрешённые origins/ }).fill('https://draft.example.com')
  await page.getByRole('spinbutton', { name: 'Лимит ссылок' }).fill('2.5')
  await page.getByRole('button', { name: 'Далее' }).click()
  await expect(page.getByRole('spinbutton', { name: 'Лимит ссылок' })).toBeFocused()
  await expect(page.getByRole('alert').filter({ hasText: 'Введите целое число от 0 до 20.' })).toBeVisible()
  await page.getByRole('checkbox', { name: 'Автомодерация' }).uncheck()
  await page.getByRole('button', { name: 'Далее' }).click()
  await expect(page.getByLabel('Тема виджета')).toBeVisible()

  const preview = page.frameLocator('iframe[title="Предварительный просмотр создаваемого виджета"]')
  await expect(preview.getByText('Комментарии').first()).toBeVisible()
  await expect(page.getByRole('button', { name: /4\. Проверка/ })).toBeDisabled()
})

test('рабочее пространство разделяет установку, настройки и опасную зону', async ({ page }) => {
  await authenticate(page)
  let installationStatusAvailable = true
  let installationReason: 'ORIGIN_REJECTED' | 'ORIGIN_CONFIGURATION_CHANGED' | 'RECENT_SUCCESS' = 'ORIGIN_REJECTED'
  let currentSiteName = 'Рабочий сайт'
  await page.route(new RegExp(`/api/sites/${SITE_ID}(?:/[^?]+)?(?:\\?.*)?$`), (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/automoderation/policies')) {
      return route.fulfill({ json: {
        activePolicy: {
          id: '00000000-0000-0000-0000-000000000137', siteId: SITE_ID, version: 1, revision: 1,
          state: 'PUBLISHED', enabled: true, preset: 'BALANCED', executionMode: 'SHADOW',
          reviewThreshold: 45, spamThreshold: 90, cleanAction: 'APPROVE', linkAction: 'REVIEW',
          maxLinks: 2, blockedWords: [], active: true, basedOnVersionId: null,
          createdAt: '2026-07-12T12:00:00Z', updatedAt: '2026-07-12T12:00:00Z', publishedAt: '2026-07-12T12:00:00Z',
        },
        draft: null,
        versions: [],
      } })
    }
    if (path.endsWith('/installation-status')) {
      if (!installationStatusAvailable) {
        return route.fulfill({ status: 500, json: { message: 'Диагностика недоступна' } })
      }
      return route.fulfill({ json: {
        status: installationReason === 'ORIGIN_REJECTED' ? 'REJECTED' : installationReason === 'RECENT_SUCCESS' ? 'HEALTHY' : 'STALE',
        reason: installationReason, siteCreated: true, originConfigured: true,
        widgetSeen: true, firstCommentReceived: false, lastSuccessfulOrigin: 'https://example.com',
        lastSuccessfulAt: '2026-07-12T12:00:00Z', lastRejectedOrigin: 'https://evil.example', lastRejectedAt: '2026-07-12T12:05:00Z',
      } })
    }
    if (path.endsWith('/embed-code')) {
      return route.fulfill({ json: {
        siteId: SITE_ID,
        scriptUrl: 'https://cloud.example/widget/cloud-comment-widget.js',
        embedCode: `<script data-site-id="${SITE_ID}"></script>`,
        dataAttributes: {},
      } })
    }
    if (route.request().method() === 'DELETE') {
      return route.fulfill({ status: 204 })
    }
    if (route.request().method() === 'PATCH') {
      const requestBody = route.request().postDataJSON() as { name?: string }
      currentSiteName = requestBody.name ?? currentSiteName
    }
    return route.fulfill({ json: {
      id: SITE_ID, ownerId: OWNER_ID, name: currentSiteName,
      domain: 'example.com', publicKey: 'public-key', moderationMode: 'PRE_MODERATION', isActive: true,
      allowedOrigins: ['https://example.com'], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      widgetStyle: {
        version: 2, theme: 'AUTO', accentColor: '#0f766e', cornerRadius: 'MEDIUM', density: 'COMFORTABLE',
        contentWidth: 'READABLE', alignment: 'CENTER', fontScale: 'MEDIUM', fontFamily: 'INHERIT',
        showHeader: true, headerTitle: 'Комментарии', composerPosition: 'BOTTOM', defaultSort: 'PINNED_FIRST',
        showSort: true, enabledReactions: ['LIKE', 'LOVE', 'LAUGH', 'WOW'], avatarStyle: 'INITIALS',
        elevation: 'BORDER', locale: 'RU', commentsTitle: 'Комментарии', composerPlaceholder: 'Напишите комментарий',
        emptyMessage: 'Пока нет комментариев.',
      },
      autoModeration: { enabled: true, strictness: 'BALANCED', blockedWords: [], holdLinks: true, blockLinks: false, maxLinks: 2 },
    } })
  })
  await page.route('**/api/analytics/owner**', (route) => route.fulfill({ json: {
    range: '30d', siteId: SITE_ID, timeZone: 'Europe/Moscow', bucketGranularity: 'DAY',
    from: '2026-06-13T00:00:00Z', to: '2026-07-12T12:00:00Z',
    summary: { sites: 1, pages: 0, comments: 0, replies: 0, reactions: 0, pending: 0, approved: 0, rejected: 0, hidden: 0, spam: 0 },
    comparison: null,
    workload: { requiringDecision: 0, oldestPendingAt: null, automaticDecisions: 0, manualDecisions: 0, undoActions: 0 },
    commentsOverTime: [], moderationDistribution: [], moderationFunnel: [],
    reactionDistribution: [], topPages: [], activeCommenters: [],
  } }))

  await page.goto(`/sites/${SITE_ID}`)
  await expect(page.getByRole('navigation', { name: 'Разделы сайта' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Основные сведения' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Origin страницы отклонён' })).toBeVisible()
  await expect(page.getByLabel('Первый комментарий: не выполнено')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Краткая сводка сайта' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Динамика комментариев' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Распределение статусов' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Вовлечённость' })).toHaveCount(0)
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('button', { name: 'Установка', exact: true }).click()
  await expect(page.getByText(`data-site-id="${SITE_ID}"`)).toBeVisible()
  await expect(page.getByText('Если виджет не появился')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('button', { name: 'Оформление', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Оформление виджета' })).toBeVisible()
  await expect(page.getByTitle('Предварительный просмотр виджета')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('button', { name: 'Модерация', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Модерация', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Политика автомодерации' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('button', { name: 'Origins', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Разрешённые origins' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
  await page.getByRole('button', { name: 'Опасная зона', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Удаление сайта' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  installationReason = 'RECENT_SUCCESS'
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Установка работает' })).toBeVisible()
  await expect(page.getByText(/Успешный запуск остаётся подтверждённым/)).toBeVisible()

  installationReason = 'ORIGIN_CONFIGURATION_CHANGED'
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Настройки origin изменились' })).toBeVisible()

  installationStatusAvailable = false
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Основные сведения' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Диагностика временно недоступна' })).toBeVisible()
  await page.getByRole('textbox', { name: 'Название' }).fill('Сохранённое название')
  await page.getByRole('button', { name: 'Сохранить раздел' }).click()
  await expect(page.getByText('Раздел сохранён')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Название' })).toHaveValue('Сохранённое название')
  await expect(page.getByRole('heading', { name: 'Диагностика временно недоступна' })).toBeVisible()

  await page.getByRole('textbox', { name: 'Название' }).fill('Несохранённое название')
  await page.getByRole('link', { name: 'К списку сайтов' }).click()
  await expect(page.getByRole('dialog', { name: 'Несохранённые изменения' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Остаться' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: 'Несохранённые изменения' })).toHaveCount(0)
  await expect(page).toHaveURL(new RegExp(`/sites/${SITE_ID}$`))
  await page.getByRole('link', { name: 'К списку сайтов' }).click()
  await expect(page.getByRole('dialog', { name: 'Несохранённые изменения' })).toBeVisible()
  await page.getByRole('button', { name: 'Остаться' }).click()
  await expect(page).toHaveURL(new RegExp(`/sites/${SITE_ID}$`))

  await page.getByRole('button', { name: 'Опасная зона', exact: true }).click()
  await page.getByRole('textbox', { name: 'Подтверждение домена для удаления' }).fill('example.com')
  await page.getByRole('button', { name: 'Удалить без восстановления' }).click()
  await expect(page).toHaveURL(/\/sites$/)
  await expect(page.getByRole('dialog', { name: 'Несохранённые изменения' })).toHaveCount(0)
})

import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

type Comment = {
  id: string
  siteId: string
  pageId: string
  parentId: null
  author: { id: string; displayName: string }
  content: string
  status: 'APPROVED'
  createdAt: string
  updatedAt: string
  editedAt: null
  pinned: boolean
  ownedByCurrentUser: boolean
  reactions: never[]
  replyCount: number
  replies: never[]
}

const SITE_ID = '00000000-0000-0000-0000-000000000001'

function comment(index: number): Comment {
  return {
    id: `00000000-0000-0000-0001-${String(index).padStart(12, '0')}`,
    siteId: SITE_ID,
    pageId: '00000000-0000-0000-0000-000000000002',
    parentId: null,
    author: {
      id: '00000000-0000-0000-0000-000000000003',
      displayName: `Автор ${index}`,
    },
    content: `Комментарий ${index}`,
    status: 'APPROVED',
    createdAt: `2026-07-13T10:${String(index).padStart(2, '0')}:00Z`,
    updatedAt: `2026-07-13T10:${String(index).padStart(2, '0')}:00Z`,
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: false,
    reactions: [],
    replyCount: 0,
    replies: [],
  }
}

test('виджет догружает 45 корней без дублей и сбрасывает страницу при сортировке', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-light', 'Функциональный сценарий достаточно выполнить в одном профиле')

  const comments = Array.from({ length: 45 }, (_, index) => comment(index + 1))
  const requestedPages: Array<{ sort: string; page: number }> = []

  await page.route('**/public/sites/**/config', (route) => route.fulfill({
    json: {
      siteId: SITE_ID,
      moderationMode: 'POST_MODERATION',
      style: {
        theme: 'LIGHT',
        accentColor: '#0f766e',
        cornerRadius: 'MEDIUM',
        showSort: true,
        defaultSort: 'PINNED_FIRST',
      },
    },
  }))
  await page.route('**/privacy/consent-requirements', (route) => route.fulfill({
    json: {
      privacyPolicyVersion: 'test',
      termsVersion: 'test',
      privacyPolicyUrl: '#',
      termsUrl: '#',
      personalDataNoticeUrl: '#',
      dataExportInfoUrl: '#',
    },
  }))
  await page.route('**/public/sites/**/pages/comments**', (route) => {
    const url = new URL(route.request().url())
    const sort = url.searchParams.get('sort') ?? 'PINNED_FIRST'
    const requestedPage = Number(url.searchParams.get('page') ?? '1')
    requestedPages.push({ sort, page: requestedPage })

    const sorted = sort === 'NEWEST' ? [...comments].reverse() : comments
    let items = sorted.slice((requestedPage - 1) * 20, requestedPage * 20)
    if (sort === 'PINNED_FIRST' && requestedPage === 2) {
      items = [comments[19], ...comments.slice(20, 39)]
    } else if (sort === 'PINNED_FIRST' && requestedPage === 3) {
      items = comments.slice(39)
    }

    return route.fulfill({
      json: {
        items,
        page: requestedPage,
        pageSize: 20,
        totalItems: comments.length,
        totalPages: 3,
      },
    })
  })

  await page.goto('/login')
  await page.evaluate(() => {
    document.body.innerHTML = '<div id="comments"></div>'
  })
  await page.addScriptTag({ path: resolve('../../widget-frontend/dist/widget/cloud-comment-widget.js') })
  await page.evaluate(({ siteId }) => window.CloudCommentWidget?.init({
    siteId,
    apiBaseUrl: 'https://api.example.test',
    pageUrl: 'https://site.example.test/article',
    target: '#comments',
  }), { siteId: SITE_ID })

  const host = page.locator('#comments')
  await expect(host.locator('.cloud-comment__comment')).toHaveCount(20)
  await host.getByRole('button', { name: 'Показать ещё комментарии (25)' }).click()
  await expect(host.locator('.cloud-comment__comment')).toHaveCount(39)
  await host.getByRole('button', { name: 'Показать ещё комментарии (6)' }).click()
  await expect(host.locator('.cloud-comment__comment')).toHaveCount(45)
  await expect(host.locator("[data-load-comments='true']")).toHaveCount(0)
  await expect(host).toContainText('Комментарий 45')

  await host.locator("[data-comment-sort='true']").selectOption('NEWEST')

  await expect(host.locator('.cloud-comment__comment')).toHaveCount(20)
  await expect(host.locator('.cloud-comment__comment').first()).toContainText('Комментарий 45')
  await expect(host.getByRole('button', { name: 'Показать ещё комментарии (25)' })).toBeVisible()
  expect(requestedPages).toEqual([
    { sort: 'PINNED_FIRST', page: 1 },
    { sort: 'PINNED_FIRST', page: 2 },
    { sort: 'PINNED_FIRST', page: 3 },
    { sort: 'NEWEST', page: 1 },
  ])
})

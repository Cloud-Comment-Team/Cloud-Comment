import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AutoModerationPolicy } from '../../types/api'
import AutoModerationPolicyPanel from './AutoModerationPolicyPanel'

const automoderationApi = vi.hoisted(() => ({
  getAutoModerationPolicies: vi.fn(),
  createAutoModerationDraft: vi.fn(),
  updateAutoModerationDraft: vi.fn(),
  deleteAutoModerationDraft: vi.fn(),
  simulateAutoModerationPolicy: vi.fn(),
  publishAutoModerationDraft: vi.fn(),
  rollbackAutoModerationPolicy: vi.fn(),
}))

vi.mock('../../api/automoderation', () => automoderationApi)

const activePolicy: AutoModerationPolicy = {
  id: '00000000-0000-0000-0000-000000000137',
  siteId: '00000000-0000-0000-0000-000000000001',
  version: 3,
  revision: 1,
  state: 'PUBLISHED',
  enabled: true,
  preset: 'BALANCED',
  executionMode: 'SHADOW',
  reviewThreshold: 45,
  spamThreshold: 90,
  cleanAction: 'APPROVE',
  linkAction: 'REVIEW',
  maxLinks: 2,
  blockedWords: [],
  active: true,
  basedOnVersionId: null,
  createdAt: '2026-07-13T10:00:00Z',
  updatedAt: '2026-07-13T10:00:00Z',
  publishedAt: '2026-07-13T10:00:00Z',
}

const draft: AutoModerationPolicy = {
  ...activePolicy,
  id: '00000000-0000-0000-0000-000000000138',
  version: null,
  revision: 4,
  state: 'DRAFT',
  active: false,
  basedOnVersionId: activePolicy.id,
  publishedAt: null,
}

describe('политики автомодерации', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    automoderationApi.getAutoModerationPolicies.mockResolvedValue({
      activePolicy,
      draft,
      versions: [activePolicy],
    })
    automoderationApi.updateAutoModerationDraft.mockImplementation((
      _siteId: string,
      _policyId: string,
      request: Record<string, unknown>,
    ) => Promise.resolve({ ...draft, ...request, revision: 5 }))
    automoderationApi.simulateAutoModerationPolicy.mockResolvedValue({
      score: 130,
      decision: 'SPAM',
      baselineStatus: 'PENDING',
      effectiveStatus: 'PENDING',
      applied: false,
      reason: 'Найдены признаки спама',
      signals: [{ code: 'SPAM_PHRASE', score: 55 }],
    })
  })

  it('показывает пресеты, режим наблюдения и детерминированную матрицу', async () => {
    render(<AutoModerationPolicyPanel siteId={activePolicy.siteId} moderationMode="PRE_MODERATION" />)

    expect(await screen.findByRole('heading', { name: 'Политика автомодерации' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Сбалансированная/ })).toBeChecked()
    expect(screen.getByRole('radio', { name: /Наблюдение/ })).toBeChecked()
    expect(screen.getByRole('table')).toHaveTextContent('от 45 до 89')
    expect(screen.getByText(/статус останется «На проверке»/)).toBeInTheDocument()
    expect(screen.getAllByText('Версия 3')).toHaveLength(2)
  })

  it('проверяет собственные пороги и сохраняет нормализованные стоп-слова', async () => {
    const onDirtyChange = vi.fn()
    render(<AutoModerationPolicyPanel siteId={activePolicy.siteId} moderationMode="POST_MODERATION" onDirtyChange={onDirtyChange} />)
    await screen.findByRole('heading', { name: 'Политика автомодерации' })

    fireEvent.click(screen.getByRole('radio', { name: /Своя/ }))
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Порог проверки' }), { target: { value: '100' } })
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Порог спама' }), { target: { value: '90' } })
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить черновик' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Порог спама должен быть больше')
    expect(automoderationApi.updateAutoModerationDraft).not.toHaveBeenCalled()

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Порог спама' }), { target: { value: '120' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Стоп-слова' }), { target: { value: 'казино\nказино\nкупить, пока дешево' } })
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить черновик' }))

    await waitFor(() => expect(automoderationApi.updateAutoModerationDraft).toHaveBeenCalledWith(
      activePolicy.siteId,
      draft.id,
      expect.objectContaining({
        expectedRevision: 4,
        preset: 'CUSTOM',
        reviewThreshold: 100,
        spamThreshold: 120,
        blockedWords: ['казино', 'купить, пока дешево'],
      }),
    ))
    expect(onDirtyChange).toHaveBeenCalledWith(true)
  })

  it('явно сохраняет изменённый черновик перед моделированием и показывает прогноз', async () => {
    render(<AutoModerationPolicyPanel siteId={activePolicy.siteId} moderationMode="PRE_MODERATION" />)
    await screen.findByRole('heading', { name: 'Политика автомодерации' })

    fireEvent.click(screen.getByRole('radio', { name: /Строгая/ }))
    const simulationInput = screen.getByRole('textbox', { name: 'Пример комментария для моделирования' })
    expect(simulationInput).toHaveAttribute('maxLength', '5000')
    fireEvent.change(simulationInput, { target: { value: 'казино и быстрый заработок' } })
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить и смоделировать' }))

    await waitFor(() => expect(automoderationApi.updateAutoModerationDraft).toHaveBeenCalled())
    await waitFor(() => expect(automoderationApi.simulateAutoModerationPolicy).toHaveBeenCalledWith(
      activePolicy.siteId,
      draft.id,
      'казино и быстрый заработок',
    ))
    expect(await screen.findByText('Только прогноз')).toBeInTheDocument()
    expect(screen.getByText(/спам-маркер/)).toBeInTheDocument()
  })

  it('блокирует редактор во время сохранения, чтобы ответ не затёр новые поля', async () => {
    let resolveUpdate: ((value: AutoModerationPolicy) => void) | undefined
    automoderationApi.updateAutoModerationDraft.mockReturnValue(new Promise((resolve) => {
      resolveUpdate = resolve
    }))
    render(<AutoModerationPolicyPanel siteId={activePolicy.siteId} moderationMode="PRE_MODERATION" />)
    await screen.findByRole('heading', { name: 'Политика автомодерации' })

    const strictPreset = screen.getByRole('radio', { name: /Строгая/ })
    fireEvent.click(strictPreset)
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить черновик' }))

    await waitFor(() => expect(strictPreset).toBeDisabled())
    expect(screen.getByRole('textbox', { name: 'Пример комментария для моделирования' })).toBeDisabled()
    resolveUpdate?.({ ...draft, preset: 'STRICT', revision: 5 })
    await waitFor(() => expect(strictPreset).not.toBeDisabled())
  })

  it('после публикации применяет authoritative response без повторного GET', async () => {
    automoderationApi.publishAutoModerationDraft.mockResolvedValue({
      ...draft,
      version: 4,
      state: 'PUBLISHED',
      active: true,
      publishedAt: '2026-07-12T11:00:00Z',
    })
    render(<AutoModerationPolicyPanel siteId={activePolicy.siteId} moderationMode="PRE_MODERATION" />)
    await screen.findByRole('heading', { name: 'Политика автомодерации' })

    fireEvent.click(screen.getByRole('button', { name: 'Опубликовать' }))
    const dialog = screen.getByRole('dialog', { name: 'Опубликовать политику?' })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Опубликовать' }))

    expect(await screen.findAllByText('Версия 4')).toHaveLength(2)
    expect(automoderationApi.getAutoModerationPolicies).toHaveBeenCalledTimes(1)
  })
})

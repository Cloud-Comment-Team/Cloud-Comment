import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Beaker,
  Check,
  Clock3,
  Eye,
  History,
  PencilLine,
  RotateCcw,
  Save,
  ShieldCheck,
  Trash2,
} from 'lucide-react'
import toast from 'react-hot-toast'

import {
  createAutoModerationDraft,
  deleteAutoModerationDraft,
  getAutoModerationPolicies,
  publishAutoModerationDraft,
  rollbackAutoModerationPolicy,
  simulateAutoModerationPolicy,
  updateAutoModerationDraft,
} from '../../api/automoderation'
import { getApiErrorMessage } from '../../api/auth'
import type {
  AutoModerationPoliciesResponse,
  AutoModerationPolicy,
  AutoModerationPolicyPreset,
  AutoModerationPolicyUpdateRequest,
  AutoModerationSimulationResponse,
  ModerationMode,
} from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { AsyncState } from '../common/AsyncState'
import { Badge } from '../common/Badge'
import { ActionBar, Dialog } from '../common/Workspace'
import {
  applyPreset,
  baselineStatus,
  cleanActionLabels,
  cleanStatus,
  decisionLabels,
  executionModeLabels,
  formatBlockedWords,
  linkActionLabels,
  parseBlockedWords,
  policyDraftEquals,
  policyPresetDescriptions,
  policyPresetLabels,
  signalLabel,
  statusLabels,
  toPolicyDraft,
  validatePolicyDraft,
} from './policyModel'

interface AutoModerationPolicyPanelProps {
  siteId: string
  moderationMode: ModerationMode
  onDirtyChange?: (dirty: boolean) => void
}

type ConflictAction = 'save' | 'publish' | 'rollback' | 'delete'

const presets: AutoModerationPolicyPreset[] = ['OPEN', 'BALANCED', 'STRICT', 'CUSTOM']

function AutoModerationPolicyPanel({ siteId, moderationMode, onDirtyChange }: AutoModerationPolicyPanelProps) {
  const [policies, setPolicies] = useState<AutoModerationPoliciesResponse | null>(null)
  const [draftForm, setDraftForm] = useState<AutoModerationPolicyUpdateRequest | null>(null)
  const [savedDraftForm, setSavedDraftForm] = useState<AutoModerationPolicyUpdateRequest | null>(null)
  const [blockedWordsInput, setBlockedWordsInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [conflictAction, setConflictAction] = useState<ConflictAction | null>(null)
  const [publishDialogOpen, setPublishDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [rollbackTarget, setRollbackTarget] = useState<AutoModerationPolicy | null>(null)
  const [pendingPreset, setPendingPreset] = useState<AutoModerationPolicyPreset | null>(null)
  const [simulationContent, setSimulationContent] = useState('')
  const [simulation, setSimulation] = useState<AutoModerationSimulationResponse | null>(null)
  const [simulationError, setSimulationError] = useState<string | null>(null)
  const [simulationLoading, setSimulationLoading] = useState(false)
  const reviewThresholdRef = useRef<HTMLInputElement>(null)
  const spamThresholdRef = useRef<HTMLInputElement>(null)
  const maxLinksRef = useRef<HTMLInputElement>(null)
  const blockedWordsRef = useRef<HTMLTextAreaElement>(null)

  const candidate = useMemo(() => draftForm ? {
    ...draftForm,
    blockedWords: parseBlockedWords(blockedWordsInput),
  } : null, [blockedWordsInput, draftForm])
  const dirty = Boolean(candidate && savedDraftForm && !policyDraftEquals(candidate, savedDraftForm))
  const interactionLocked = busy || simulationLoading

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  const applyPolicies = useCallback((next: AutoModerationPoliciesResponse) => {
    setPolicies(next)
    if (next.draft) {
      const nextForm = toPolicyDraft(next.draft)
      setDraftForm(nextForm)
      setSavedDraftForm(nextForm)
      setBlockedWordsInput(formatBlockedWords(next.draft.blockedWords))
    } else {
      setDraftForm(null)
      setSavedDraftForm(null)
      setBlockedWordsInput('')
    }
  }, [])

  const loadPolicies = useCallback(async (showLoading: boolean): Promise<boolean> => {
    if (showLoading) setLoading(true)
    setError(null)
    try {
      applyPolicies(await getAutoModerationPolicies(siteId))
      setConflictAction(null)
      return true
    } catch (loadError) {
      setError(getApiErrorMessage(loadError, 'Не удалось загрузить политики автомодерации.'))
      return false
    } finally {
      if (showLoading) setLoading(false)
    }
  }, [applyPolicies, siteId])

  function applyPublishedPolicy(published: AutoModerationPolicy) {
    setPolicies((current) => current ? {
      activePolicy: published,
      draft: null,
      versions: [
        published,
        ...current.versions
          .filter((version) => version.id !== published.id)
          .map((version) => ({ ...version, active: false })),
      ],
    } : current)
    setDraftForm(null)
    setSavedDraftForm(null)
    setBlockedWordsInput('')
    setSimulation(null)
    setConflictAction(null)
  }

  useEffect(() => {
    let cancelled = false
    void getAutoModerationPolicies(siteId)
      .then((next) => {
        if (!cancelled) applyPolicies(next)
      })
      .catch((loadError) => {
        if (!cancelled) setError(getApiErrorMessage(loadError, 'Не удалось загрузить политики автомодерации.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applyPolicies, siteId])

  function updateDraft(patch: Partial<AutoModerationPolicyUpdateRequest>) {
    setDraftForm((current) => current ? { ...current, ...patch } : current)
    setValidationError(null)
    setSimulation(null)
  }

  async function handleCreateDraft() {
    setBusy(true)
    try {
      const active = policies?.activePolicy
      const draft = await createAutoModerationDraft(
        siteId,
        active?.preset ?? 'BALANCED',
        active?.enabled ?? true,
        active?.executionMode ?? 'SHADOW',
      )
      const form = toPolicyDraft(draft)
      setPolicies((current) => current ? { ...current, draft } : current)
      setDraftForm(form)
      setSavedDraftForm(form)
      setBlockedWordsInput(formatBlockedWords(draft.blockedWords))
      toast.success('Черновик создан')
    } catch (createError) {
      if (isConflict(createError)) {
        setConflictAction('save')
      } else {
        toast.error(getApiErrorMessage(createError, 'Не удалось создать черновик.'))
      }
    } finally {
      setBusy(false)
    }
  }

  function focusInvalidField(field: ReturnType<typeof validatePolicyDraft>['field']) {
    if (field === 'reviewThreshold') reviewThresholdRef.current?.focus()
    if (field === 'spamThreshold') spamThresholdRef.current?.focus()
    if (field === 'maxLinks') maxLinksRef.current?.focus()
    if (field === 'blockedWords') blockedWordsRef.current?.focus()
  }

  async function persistDraft(showSuccess = true): Promise<AutoModerationPolicy | null> {
    if (!policies?.draft || !candidate) return null
    const validation = validatePolicyDraft(candidate)
    if (validation.message) {
      setValidationError(validation.message)
      focusInvalidField(validation.field)
      return null
    }

    setBusy(true)
    setValidationError(null)
    try {
      const saved = await updateAutoModerationDraft(siteId, policies.draft.id, candidate)
      const nextForm = toPolicyDraft(saved)
      setPolicies((current) => current ? { ...current, draft: saved } : current)
      setDraftForm(nextForm)
      setSavedDraftForm(nextForm)
      setBlockedWordsInput(formatBlockedWords(saved.blockedWords))
      if (showSuccess) toast.success('Черновик сохранён')
      return saved
    } catch (saveError) {
      if (isConflict(saveError)) {
        setConflictAction('save')
      } else {
        toast.error(getApiErrorMessage(saveError, 'Не удалось сохранить черновик.'))
      }
      return null
    } finally {
      setBusy(false)
    }
  }

  async function handleDeleteDraft() {
    if (!policies?.draft) return
    setBusy(true)
    try {
      await deleteAutoModerationDraft(siteId, policies.draft.id, policies.draft.revision)
      setDeleteDialogOpen(false)
      setPolicies((current) => current ? { ...current, draft: null } : current)
      setDraftForm(null)
      setSavedDraftForm(null)
      setBlockedWordsInput('')
      setSimulation(null)
      toast.success('Черновик удалён')
    } catch (deleteError) {
      if (isConflict(deleteError)) {
        setConflictAction('delete')
        setDeleteDialogOpen(false)
      } else {
        toast.error(getApiErrorMessage(deleteError, 'Не удалось удалить черновик.'))
      }
    } finally {
      setBusy(false)
    }
  }

  async function handleSimulate() {
    if (!policies?.draft) return
    const content = simulationContent.trim()
    if (!content) {
      setSimulation(null)
      setSimulationError('Введите пример комментария для моделирования.')
      return
    }
    let targetDraft = policies.draft
    if (dirty) {
      const saved = await persistDraft(false)
      if (!saved) return
      targetDraft = saved
    }
    setSimulationLoading(true)
    setSimulationError(null)
    try {
      setSimulation(await simulateAutoModerationPolicy(siteId, targetDraft.id, content))
    } catch (simulateError) {
      setSimulation(null)
      setSimulationError(getApiErrorMessage(simulateError, 'Не удалось смоделировать решение.'))
    } finally {
      setSimulationLoading(false)
    }
  }

  async function handlePublish() {
    if (!policies?.draft) return
    setBusy(true)
    try {
      const published = await publishAutoModerationDraft(
        siteId,
        policies.draft.id,
        policies.draft.revision,
        policies.activePolicy?.id ?? null,
      )
      setPublishDialogOpen(false)
      applyPublishedPolicy(published)
      toast.success('Политика опубликована')
    } catch (publishError) {
      setPublishDialogOpen(false)
      if (isConflict(publishError)) {
        setConflictAction('publish')
      } else {
        toast.error(getApiErrorMessage(publishError, 'Не удалось опубликовать политику.'))
      }
    } finally {
      setBusy(false)
    }
  }

  async function handleRollback() {
    if (!rollbackTarget) return
    setBusy(true)
    try {
      const published = await rollbackAutoModerationPolicy(siteId, rollbackTarget.id, policies?.activePolicy?.id ?? null)
      setRollbackTarget(null)
      applyPublishedPolicy(published)
      toast.success('Предыдущая конфигурация опубликована новой версией')
    } catch (rollbackError) {
      setRollbackTarget(null)
      if (isConflict(rollbackError)) {
        setConflictAction('rollback')
      } else {
        toast.error(getApiErrorMessage(rollbackError, 'Не удалось вернуть версию.'))
      }
    } finally {
      setBusy(false)
    }
  }

  function selectPreset(preset: AutoModerationPolicyPreset) {
    if (!draftForm || draftForm.preset === preset) return
    if (draftForm.preset === 'CUSTOM' && dirty && preset !== 'CUSTOM') {
      setPendingPreset(preset)
      return
    }
    setDraftForm(applyPreset(draftForm, preset))
    if (preset !== 'CUSTOM') setBlockedWordsInput('')
    setSimulation(null)
  }

  function confirmPresetChange() {
    if (!draftForm || !pendingPreset) return
    setDraftForm(applyPreset(draftForm, pendingPreset))
    setBlockedWordsInput('')
    setPendingPreset(null)
    setSimulation(null)
  }

  return (
    <section aria-labelledby="automoderation-policy-title" aria-busy={interactionLocked || undefined} className="mt-5 min-w-0 rounded-lg border p-4 md:p-5" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}>
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 id="automoderation-policy-title" className="font-semibold" style={{ color: 'var(--text-h)' }}>Политика автомодерации</h3>
            <p className="mt-1 max-w-3xl text-sm leading-6" style={{ color: 'var(--text)' }}>
              Версионируемые правила без внешнего AI. Сначала проверьте черновик в наблюдении, затем включайте применение.
            </p>
          </div>
        </div>
        {policies?.activePolicy && <PolicyBadges policy={policies.activePolicy} />}
      </div>

      <AsyncState loading={loading} error={null} empty={false}>
        {error && <div className="mt-4 rounded-lg border p-4" role="alert" style={{ borderColor: 'var(--danger)', backgroundColor: 'var(--danger-bg)' }}><p className="text-sm" style={{ color: 'var(--danger)' }}>{error}</p><button type="button" className="cc-button-secondary mt-3" onClick={() => void loadPolicies(true)}>Повторить</button></div>}
        {policies && (
          <div className="mt-5 space-y-5">
            {conflictAction && (
              <div className="rounded-lg border p-4" role="alert" style={{ borderColor: 'var(--warning)', backgroundColor: 'var(--warning-bg)' }}>
                <p className="font-semibold" style={{ color: 'var(--text-h)' }}>Политика изменилась в другой вкладке</p>
                <p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>
                  Локальные значения не отправлены повторно, чтобы не затереть более новую ревизию. Загрузите актуальное состояние и внесите изменения заново.
                </p>
                <button type="button" className="cc-button-secondary mt-3" onClick={() => void loadPolicies(false)}>Загрузить актуальное состояние</button>
              </div>
            )}

            {!policies.draft || !draftForm || !candidate ? (
              <div className="rounded-lg border p-5 text-center" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}>
                <PencilLine className="mx-auto h-7 w-7" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                <h4 className="mt-3 font-semibold" style={{ color: 'var(--text-h)' }}>Нет черновика</h4>
                <p className="mx-auto mt-1 max-w-xl text-sm" style={{ color: 'var(--text)' }}>
                  Создайте черновик из активной конфигурации. Публичное поведение не изменится до публикации.
                </p>
                <button type="button" className="cc-button-primary mt-4" disabled={busy} onClick={() => void handleCreateDraft()}>
                  <PencilLine className="h-4 w-4" aria-hidden="true" /> Создать черновик
                </button>
              </div>
            ) : (
              <>
                <fieldset className="contents" disabled={interactionLocked}>
                <ActionBar label="Действия с черновиком">
                  <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                    <Badge tone="warning">Черновик · ревизия {policies.draft.revision}</Badge>
                    {dirty && <span className="text-sm" style={{ color: 'var(--warning)' }}>Есть несохранённые изменения</span>}
                  </div>
                  <button type="button" className="cc-button-secondary" disabled={busy || !dirty} onClick={() => void persistDraft()}>
                    <Save className="h-4 w-4" aria-hidden="true" /> Сохранить черновик
                  </button>
                  <button type="button" className="cc-button-secondary" disabled={busy} onClick={() => setDeleteDialogOpen(true)}>
                    <Trash2 className="h-4 w-4" aria-hidden="true" /> Удалить
                  </button>
                  <button type="button" className="cc-button-primary" disabled={busy || dirty} onClick={() => setPublishDialogOpen(true)} title={dirty ? 'Сначала сохраните черновик' : undefined}>
                    <Check className="h-4 w-4" aria-hidden="true" /> Опубликовать
                  </button>
                </ActionBar>

                <fieldset>
                  <legend className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>Готовая политика</legend>
                  <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    {presets.map((preset) => (
                      <label key={preset} className="flex cursor-pointer gap-3 rounded-lg border p-4" style={{ borderColor: draftForm.preset === preset ? 'var(--accent)' : 'var(--border)', backgroundColor: 'var(--surface)' }}>
                        <input type="radio" name="automoderation-preset" value={preset} checked={draftForm.preset === preset} onChange={() => selectPreset(preset)} />
                        <span>
                          <span className="block text-sm font-semibold" style={{ color: 'var(--text-h)' }}>{policyPresetLabels[preset]}</span>
                          <span className="mt-1 block text-xs leading-5" style={{ color: 'var(--text)' }}>{policyPresetDescriptions[preset]}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                <div className="grid gap-4 lg:grid-cols-2">
                  <fieldset className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}>
                    <legend className="px-1 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>Состояние</legend>
                    <label className="flex items-start gap-3 text-sm">
                      <input type="checkbox" checked={draftForm.enabled} onChange={(event) => updateDraft({ enabled: event.target.checked })} />
                      <span><strong className="block" style={{ color: 'var(--text-h)' }}>Автомодерация включена</strong><span style={{ color: 'var(--text)' }}>Если выключить, решения и сигналы не вычисляются.</span></span>
                    </label>
                  </fieldset>
                  <fieldset className="rounded-lg border p-4" disabled={!draftForm.enabled} style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}>
                    <legend className="px-1 text-sm font-semibold" style={{ color: 'var(--text-h)' }}>Режим запуска</legend>
                    <div className="grid gap-3 sm:grid-cols-2">
                      {(['SHADOW', 'LIVE'] as const).map((mode) => (
                        <label key={mode} className="flex items-start gap-2 text-sm">
                          <input type="radio" name="automoderation-mode" value={mode} checked={draftForm.executionMode === mode} onChange={() => updateDraft({ executionMode: mode })} />
                          <span><strong className="block" style={{ color: 'var(--text-h)' }}>{executionModeLabels[mode]}</strong><span style={{ color: 'var(--text)' }}>{mode === 'SHADOW' ? 'Записывает прогноз, но не меняет статус.' : 'Применяет решение к новым комментариям.'}</span></span>
                        </label>
                      ))}
                    </div>
                  </fieldset>
                </div>

                {draftForm.preset === 'CUSTOM' && (
                  <CustomPolicyEditor
                    draft={draftForm}
                    blockedWordsInput={blockedWordsInput}
                    validationError={validationError}
                    reviewThresholdRef={reviewThresholdRef}
                    spamThresholdRef={spamThresholdRef}
                    maxLinksRef={maxLinksRef}
                    blockedWordsRef={blockedWordsRef}
                    onChange={updateDraft}
                    onBlockedWordsChange={(value) => {
                      setBlockedWordsInput(value)
                      setValidationError(null)
                      setSimulation(null)
                    }}
                  />
                )}

                {validationError && <p className="text-sm" role="alert" style={{ color: 'var(--danger)' }}>{validationError}</p>}

                <PolicyDecisionMatrix policy={candidate} moderationMode={moderationMode} />

                <PolicySimulation
                  content={simulationContent}
                  result={simulation}
                  error={simulationError}
                  loading={simulationLoading}
                  savesFirst={dirty}
                  onContentChange={(value) => {
                    setSimulationContent(value)
                    setSimulationError(null)
                  }}
                  onSimulate={() => void handleSimulate()}
                />
                </fieldset>
              </>
            )}

            <PolicyHistory
              versions={policies.versions}
              hasDraft={Boolean(policies.draft)}
              onRollback={setRollbackTarget}
            />
          </div>
        )}
      </AsyncState>

      <Dialog title="Опубликовать политику?" open={publishDialogOpen} onClose={() => setPublishDialogOpen(false)} actions={<><button type="button" className="cc-button-secondary" onClick={() => setPublishDialogOpen(false)}>Отмена</button><button type="button" className="cc-button-primary" disabled={busy} onClick={() => void handlePublish()}>Опубликовать</button></>}>
        <p className="text-sm" style={{ color: 'var(--text)' }}>
          Будет создана новая неизменяемая версия. {draftForm?.enabled && draftForm.executionMode === 'LIVE' ? 'Она начнёт менять статус новых комментариев сразу после публикации.' : draftForm?.enabled ? 'В режиме наблюдения публичные статусы не изменятся.' : 'Автомодерация будет выключена.'}
        </p>
      </Dialog>
      <Dialog title="Удалить черновик?" open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} actions={<><button type="button" className="cc-button-secondary" onClick={() => setDeleteDialogOpen(false)}>Отмена</button><button type="button" className="cc-button-danger" disabled={busy} onClick={() => void handleDeleteDraft()}>Удалить черновик</button></>}>
        <p className="text-sm" style={{ color: 'var(--text)' }}>Активная опубликованная версия не изменится.</p>
      </Dialog>
      <Dialog title="Вернуть эту версию?" open={rollbackTarget !== null} onClose={() => setRollbackTarget(null)} actions={<><button type="button" className="cc-button-secondary" onClick={() => setRollbackTarget(null)}>Отмена</button><button type="button" className="cc-button-primary" disabled={interactionLocked} onClick={() => void handleRollback()}>Создать новую версию</button></>}>
        <p className="text-sm" style={{ color: 'var(--text)' }}>
          Конфигурация версии {rollbackTarget?.version} будет скопирована в новую опубликованную версию. История останется неизменной.
        </p>
      </Dialog>
      <Dialog title="Сменить готовую политику?" open={pendingPreset !== null} onClose={() => setPendingPreset(null)} actions={<><button type="button" className="cc-button-secondary" onClick={() => setPendingPreset(null)}>Остаться на своей</button><button type="button" className="cc-button-primary" onClick={confirmPresetChange}>Сменить политику</button></>}>
        <p className="text-sm" style={{ color: 'var(--text)' }}>Несохранённые собственные пороги, правила ссылок и стоп-слова будут заменены значениями выбранной политики.</p>
      </Dialog>
    </section>
  )
}

function PolicyBadges({ policy }: { policy: AutoModerationPolicy }) {
  return <div className="flex flex-wrap items-center gap-2"><Badge tone={policy.enabled ? 'accent' : 'muted'}>{policy.enabled ? policyPresetLabels[policy.preset] : 'Выключена'}</Badge>{policy.enabled && <Badge tone={policy.executionMode === 'LIVE' ? 'success' : 'warning'}>{executionModeLabels[policy.executionMode]}</Badge>}{policy.version !== null && <Badge>Версия {policy.version}</Badge>}</div>
}

interface CustomPolicyEditorProps {
  draft: AutoModerationPolicyUpdateRequest
  blockedWordsInput: string
  validationError: string | null
  reviewThresholdRef: React.RefObject<HTMLInputElement | null>
  spamThresholdRef: React.RefObject<HTMLInputElement | null>
  maxLinksRef: React.RefObject<HTMLInputElement | null>
  blockedWordsRef: React.RefObject<HTMLTextAreaElement | null>
  onChange: (patch: Partial<AutoModerationPolicyUpdateRequest>) => void
  onBlockedWordsChange: (value: string) => void
}

function CustomPolicyEditor({ draft, blockedWordsInput, validationError, reviewThresholdRef, spamThresholdRef, maxLinksRef, blockedWordsRef, onChange, onBlockedWordsChange }: CustomPolicyEditorProps) {
  const describedBy = validationError ? 'automoderation-validation-error' : undefined
  return <fieldset className="rounded-lg border p-4 md:p-5" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}><legend className="px-1 font-semibold" style={{ color: 'var(--text-h)' }}>Собственные правила</legend><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
    <label className="block"><span className="mb-2 block text-sm font-medium">Порог проверки</span><input ref={reviewThresholdRef} className="cc-field" type="number" min={0} max={1000} step={1} value={draft.reviewThreshold} aria-describedby={describedBy} onChange={(event) => onChange({ reviewThreshold: Number(event.target.value) })} /></label>
    <label className="block"><span className="mb-2 block text-sm font-medium">Порог спама</span><input ref={spamThresholdRef} className="cc-field" type="number" min={1} max={1000} step={1} value={draft.spamThreshold} aria-describedby={describedBy} onChange={(event) => onChange({ spamThreshold: Number(event.target.value) })} /></label>
    <label className="block"><span className="mb-2 block text-sm font-medium">Чистый комментарий</span><select className="cc-field" value={draft.cleanAction} onChange={(event) => onChange({ cleanAction: event.target.value as AutoModerationPolicyUpdateRequest['cleanAction'] })}><option value="APPROVE">Автоматически одобрить</option><option value="FOLLOW_SITE_MODE">Следовать режиму сайта</option></select></label>
    <label className="block"><span className="mb-2 block text-sm font-medium">Если ссылок больше лимита</span><select className="cc-field" value={draft.linkAction} onChange={(event) => onChange({ linkAction: event.target.value as AutoModerationPolicyUpdateRequest['linkAction'] })}><option value="ALLOW">Разрешить</option><option value="REVIEW">Отправить на проверку</option><option value="SPAM">Отправить в спам</option></select></label>
    <label className="block"><span className="mb-2 block text-sm font-medium">Лимит ссылок</span><input ref={maxLinksRef} className="cc-field" type="number" min={0} max={20} step={1} value={draft.maxLinks} aria-describedby={describedBy} onChange={(event) => onChange({ maxLinks: Number(event.target.value) })} /></label>
    <label className="block md:col-span-2 xl:col-span-3"><span className="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm font-medium"><span>Стоп-слова</span><span style={{ color: 'var(--text)' }}>{parseBlockedWords(blockedWordsInput).length}/120</span></span><textarea ref={blockedWordsRef} className="cc-field min-h-28" maxLength={10_000} value={blockedWordsInput} aria-label="Стоп-слова" aria-describedby={describedBy} placeholder={'По одному слову или фразе на строку'} onChange={(event) => onBlockedWordsChange(event.target.value)} /></label>
  </div>{validationError && <span id="automoderation-validation-error" className="sr-only">{validationError}</span>}</fieldset>
}

function PolicyDecisionMatrix({ policy, moderationMode }: { policy: AutoModerationPolicyUpdateRequest; moderationMode: ModerationMode }) {
  const baseline = baselineStatus(moderationMode)
  const clean = cleanStatus(policy.cleanAction, moderationMode)
  const effective = (liveStatus: string) => policy.enabled && policy.executionMode === 'LIVE' ? liveStatus : statusLabels[baseline]
  return <section className="rounded-lg border p-4 md:p-5" aria-labelledby="policy-matrix-title" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}><div className="flex items-center gap-2"><Eye className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" /><h4 id="policy-matrix-title" className="font-semibold" style={{ color: 'var(--text-h)' }}>Что произойдёт с комментарием</h4></div><div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-sm"><thead><tr style={{ color: 'var(--text)' }}><th className="px-3 py-2" scope="col">Score</th><th className="px-3 py-2" scope="col">Решение</th><th className="px-3 py-2" scope="col">Фактический статус</th></tr></thead><tbody style={{ color: 'var(--text-h)' }}><tr className="border-t" style={{ borderColor: 'var(--border)' }}><td className="px-3 py-3">меньше {policy.reviewThreshold}</td><td className="px-3 py-3">Одобрить</td><td className="px-3 py-3">{effective(statusLabels[clean])}</td></tr><tr className="border-t" style={{ borderColor: 'var(--border)' }}><td className="px-3 py-3">от {policy.reviewThreshold} до {policy.spamThreshold - 1}</td><td className="px-3 py-3">На проверку</td><td className="px-3 py-3">{effective(statusLabels.PENDING)}</td></tr><tr className="border-t" style={{ borderColor: 'var(--border)' }}><td className="px-3 py-3">{policy.spamThreshold} и выше</td><td className="px-3 py-3">Спам</td><td className="px-3 py-3">{effective(statusLabels.SPAM)}</td></tr></tbody></table></div>{(!policy.enabled || policy.executionMode === 'SHADOW') && <p className="mt-3 text-xs leading-5" style={{ color: 'var(--text)' }}>{policy.enabled ? `Наблюдение сохранит прогноз, но статус останется «${statusLabels[baseline]}» по режиму сайта.` : 'Автомодерация выключена: решение и сигналы не вычисляются.'}</p>}</section>
}

interface PolicySimulationProps {
  content: string
  result: AutoModerationSimulationResponse | null
  error: string | null
  loading: boolean
  savesFirst: boolean
  onContentChange: (value: string) => void
  onSimulate: () => void
}

function PolicySimulation({ content, result, error, loading, savesFirst, onContentChange, onSimulate }: PolicySimulationProps) {
  return <section className="rounded-lg border p-4 md:p-5" aria-labelledby="policy-simulation-title" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface)' }}><div className="flex items-center gap-2"><Beaker className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" /><h4 id="policy-simulation-title" className="font-semibold" style={{ color: 'var(--text-h)' }}>Моделирование</h4></div><p className="mt-1 text-sm" style={{ color: 'var(--text)' }}>Текст используется только для ответа и не сохраняется в политике или обратной связи.</p><textarea className="cc-field mt-4 min-h-28" maxLength={5000} aria-label="Пример комментария для моделирования" aria-describedby="policy-simulation-limit" placeholder="Например: казино, быстрый заработок..." value={content} onChange={(event) => onContentChange(event.target.value)} /><span id="policy-simulation-limit" className="mt-1 block text-right text-xs" style={{ color: 'var(--text)' }}>{content.length}/5000</span><button type="button" className="cc-button-secondary mt-3" disabled={loading} aria-busy={loading || undefined} onClick={onSimulate}><Beaker className="h-4 w-4" aria-hidden="true" />{loading ? 'Моделируем…' : savesFirst ? 'Сохранить и смоделировать' : 'Смоделировать'}</button>{error && <p className="mt-3 text-sm" role="alert" style={{ color: 'var(--danger)' }}>{error}</p>}<div aria-live="polite">{result && <div className="mt-4 rounded-lg border p-4" style={{ borderColor: 'var(--border)', backgroundColor: 'var(--surface-muted)' }}><div className="flex flex-wrap items-center gap-2"><Badge tone={result.decision === 'APPROVE' ? 'success' : result.decision === 'REVIEW' ? 'warning' : 'danger'}>{decisionLabels[result.decision]}</Badge><strong className="text-sm" style={{ color: 'var(--text-h)' }}>Score {result.score}</strong><span className="text-sm" style={{ color: 'var(--text)' }}>Фактически: {statusLabels[result.effectiveStatus]}</span>{!result.applied && <Badge tone="muted">Только прогноз</Badge>}</div>{result.reason && <p className="mt-3 text-sm" style={{ color: 'var(--text-h)' }}>{result.reason}</p>}{result.signals.length > 0 ? <ul className="mt-3 grid gap-2 sm:grid-cols-2">{result.signals.map((signal, index) => <li key={`${signal.code}-${index}`} className="rounded-md border px-3 py-2 text-sm" style={{ borderColor: 'var(--border)' }}><strong style={{ color: 'var(--text-h)' }}>+{signal.score} {signalLabel(signal.code)}</strong>{signal.message && <span className="block" style={{ color: 'var(--text)' }}>{signal.message}</span>}</li>)}</ul> : <p className="mt-3 text-sm" style={{ color: 'var(--text)' }}>Сигналы не найдены.</p>}</div>}</div></section>
}

function PolicyHistory({ versions, hasDraft, onRollback }: { versions: AutoModerationPolicy[]; hasDraft: boolean; onRollback: (policy: AutoModerationPolicy) => void }) {
  return <section aria-labelledby="policy-history-title"><div className="flex items-center gap-2"><History className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" /><h4 id="policy-history-title" className="font-semibold" style={{ color: 'var(--text-h)' }}>История версий</h4></div>{versions.length === 0 ? <p className="mt-3 text-sm" style={{ color: 'var(--text)' }}>Опубликованных версий пока нет.</p> : <ol className="mt-3 space-y-2">{versions.map((version) => <li key={version.id}><details className="rounded-lg border p-4" style={{ borderColor: version.active ? 'var(--accent)' : 'var(--border)', backgroundColor: 'var(--surface)' }}><summary className="cursor-pointer list-none"><span className="flex flex-wrap items-center gap-2"><strong style={{ color: 'var(--text-h)' }}>Версия {version.version}</strong>{version.active && <Badge tone="success">Активна</Badge>}<Badge>{policyPresetLabels[version.preset]}</Badge><Badge tone={version.enabled && version.executionMode === 'LIVE' ? 'success' : version.enabled ? 'warning' : 'muted'}>{version.enabled ? executionModeLabels[version.executionMode] : 'Выключена'}</Badge>{version.publishedAt && <time className="ml-auto flex items-center gap-1 text-xs" dateTime={version.publishedAt} style={{ color: 'var(--text)' }}><Clock3 className="h-3.5 w-3.5" aria-hidden="true" />{formatDateTime(version.publishedAt)}</time>}</span></summary><dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4"><div><dt style={{ color: 'var(--text)' }}>Проверка</dt><dd className="font-semibold">от {version.reviewThreshold}</dd></div><div><dt style={{ color: 'var(--text)' }}>Спам</dt><dd className="font-semibold">от {version.spamThreshold}</dd></div><div><dt style={{ color: 'var(--text)' }}>Чистое сообщение</dt><dd className="font-semibold">{cleanActionLabels[version.cleanAction]}</dd></div><div><dt style={{ color: 'var(--text)' }}>Ссылки сверх лимита</dt><dd className="font-semibold">{linkActionLabels[version.linkAction]}</dd></div></dl>{!version.active && <button type="button" className="cc-button-secondary mt-4" disabled={hasDraft} title={hasDraft ? 'Сначала опубликуйте или удалите черновик' : undefined} onClick={() => onRollback(version)}><RotateCcw className="h-4 w-4" aria-hidden="true" /> Вернуть эту версию</button>}</details></li>)}</ol>}</section>
}

function isConflict(error: unknown): boolean {
  if (typeof error !== 'object' || error === null || !('response' in error)) return false
  const response = (error as { response?: { status?: number } }).response
  return response?.status === 409
}

export default AutoModerationPolicyPanel

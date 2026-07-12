import type {
  AutoModerationCleanAction,
  AutoModerationExecutionMode,
  AutoModerationLinkAction,
  AutoModerationPolicy,
  AutoModerationPolicyDecision,
  AutoModerationPolicyPreset,
  AutoModerationPolicyUpdateRequest,
  CommentStatus,
  ModerationMode,
} from '../../types/api'

export const policyPresetLabels: Record<AutoModerationPolicyPreset, string> = {
  OPEN: 'Открытая',
  BALANCED: 'Сбалансированная',
  STRICT: 'Строгая',
  CUSTOM: 'Своя',
}

export const policyPresetDescriptions: Record<AutoModerationPolicyPreset, string> = {
  OPEN: 'Только уверенные сигналы. Review от 70, spam от 130.',
  BALANCED: 'Рекомендуемый баланс качества и ручной нагрузки. Review от 45, spam от 90.',
  STRICT: 'Больше неоднозначных сообщений остаётся на проверке. Review от 25, spam от 85.',
  CUSTOM: 'Собственные пороги, правила ссылок и стоп-слова.',
}

export const executionModeLabels: Record<AutoModerationExecutionMode, string> = {
  SHADOW: 'Наблюдение',
  LIVE: 'Применять',
}

export const cleanActionLabels: Record<AutoModerationCleanAction, string> = {
  APPROVE: 'Автоматически одобрить',
  FOLLOW_SITE_MODE: 'Следовать режиму сайта',
}

export const linkActionLabels: Record<AutoModerationLinkAction, string> = {
  ALLOW: 'Разрешить',
  REVIEW: 'Отправить на проверку',
  SPAM: 'Отправить в спам',
}

export const decisionLabels: Record<AutoModerationPolicyDecision, string> = {
  APPROVE: 'Одобрить',
  REVIEW: 'На проверку',
  SPAM: 'Спам',
}

export const statusLabels: Record<CommentStatus, string> = {
  PENDING: 'На проверке',
  APPROVED: 'Опубликован',
  REJECTED: 'Отклонён',
  HIDDEN: 'Скрыт',
  SPAM: 'Спам',
}

const presetValues: Record<Exclude<AutoModerationPolicyPreset, 'CUSTOM'>, Pick<
  AutoModerationPolicyUpdateRequest,
  'reviewThreshold' | 'spamThreshold' | 'cleanAction' | 'linkAction' | 'maxLinks'
>> = {
  OPEN: {
    reviewThreshold: 70,
    spamThreshold: 130,
    cleanAction: 'APPROVE',
    linkAction: 'REVIEW',
    maxLinks: 2,
  },
  BALANCED: {
    reviewThreshold: 45,
    spamThreshold: 90,
    cleanAction: 'APPROVE',
    linkAction: 'REVIEW',
    maxLinks: 2,
  },
  STRICT: {
    reviewThreshold: 25,
    spamThreshold: 85,
    cleanAction: 'FOLLOW_SITE_MODE',
    linkAction: 'REVIEW',
    maxLinks: 0,
  },
}

export function toPolicyDraft(policy: AutoModerationPolicy): AutoModerationPolicyUpdateRequest {
  return {
    expectedRevision: policy.revision,
    enabled: policy.enabled,
    preset: policy.preset,
    executionMode: policy.executionMode,
    reviewThreshold: policy.reviewThreshold,
    spamThreshold: policy.spamThreshold,
    cleanAction: policy.cleanAction,
    linkAction: policy.linkAction,
    maxLinks: policy.maxLinks,
    blockedWords: [...policy.blockedWords],
  }
}

export function applyPreset(
  current: AutoModerationPolicyUpdateRequest,
  preset: AutoModerationPolicyPreset,
): AutoModerationPolicyUpdateRequest {
  if (preset === 'CUSTOM') {
    return { ...current, preset }
  }
  return {
    ...current,
    ...presetValues[preset],
    preset,
    blockedWords: [],
  }
}

export function formatBlockedWords(words: string[]): string {
  return words.join('\n')
}

export function parseBlockedWords(value: string): string[] {
  return Array.from(new Set(value
    .split(/\r?\n/)
    .map((word) => word.trim())
    .filter(Boolean)))
}

export function validatePolicyDraft(
  draft: AutoModerationPolicyUpdateRequest,
): { field: 'reviewThreshold' | 'spamThreshold' | 'maxLinks' | 'blockedWords' | null; message: string | null } {
  if (!Number.isInteger(draft.reviewThreshold) || draft.reviewThreshold < 0 || draft.reviewThreshold > 1000) {
    return { field: 'reviewThreshold', message: 'Порог проверки должен быть целым числом от 0 до 1000.' }
  }
  if (!Number.isInteger(draft.spamThreshold) || draft.spamThreshold < 1 || draft.spamThreshold > 1000) {
    return { field: 'spamThreshold', message: 'Порог спама должен быть целым числом от 1 до 1000.' }
  }
  if (draft.reviewThreshold >= draft.spamThreshold) {
    return { field: 'spamThreshold', message: 'Порог спама должен быть больше порога проверки.' }
  }
  if (!Number.isInteger(draft.maxLinks) || draft.maxLinks < 0 || draft.maxLinks > 20) {
    return { field: 'maxLinks', message: 'Лимит ссылок должен быть целым числом от 0 до 20.' }
  }
  if (draft.blockedWords.length > 120) {
    return { field: 'blockedWords', message: 'Можно указать не более 120 стоп-слов.' }
  }
  if (draft.blockedWords.some((word) => word.length > 80)) {
    return { field: 'blockedWords', message: 'Каждое стоп-слово должно быть не длиннее 80 символов.' }
  }
  return { field: null, message: null }
}

export function policyDraftEquals(
  left: AutoModerationPolicyUpdateRequest,
  right: AutoModerationPolicyUpdateRequest,
): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

export function baselineStatus(mode: ModerationMode): CommentStatus {
  return mode === 'PRE_MODERATION' ? 'PENDING' : 'APPROVED'
}

export function cleanStatus(
  cleanAction: AutoModerationCleanAction,
  moderationMode: ModerationMode,
): CommentStatus {
  return cleanAction === 'APPROVE' ? 'APPROVED' : baselineStatus(moderationMode)
}

const signalLabels: Record<string, string> = {
  AGGRESSIVE_CAPS: 'Много заглавных букв',
  BLOCKED_LINK: 'Ссылка запрещена политикой',
  CONTAINS_LINK: 'Комментарий содержит ссылку',
  CUSTOM_BLOCKED_WORD: 'Найдено стоп-слово',
  LINK_FLOOD: 'Слишком много ссылок',
  OBFUSCATION: 'Обнаружена маскировка текста',
  REPEATED_CHARACTERS: 'Много повторяющихся символов',
  SPAM_PHRASE: 'Найден спам-маркер',
  SUSPICIOUS_CONTACT: 'Подозрительный контакт',
  TOXICITY: 'Токсичный маркер',
}

export function signalLabel(code: string): string {
  return signalLabels[code] ?? code.replaceAll('_', ' ').toLocaleLowerCase('ru-RU')
}

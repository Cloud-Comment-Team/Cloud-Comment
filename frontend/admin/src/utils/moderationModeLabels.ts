import type { ModerationMode } from '../types/api'

export const moderationModeLabels: Record<ModerationMode, string> = {
  PRE_MODERATION: 'Пре-модерация',
  POST_MODERATION: 'Пост-модерация',
  DISABLED: 'Отключена',
}

import type { CommentStatus, ModerationActionType } from '../types/api'

const actionTargetStatus: Record<ModerationActionType, CommentStatus> = {
  APPROVE: 'APPROVED',
  REJECT: 'REJECTED',
  HIDE: 'HIDDEN',
  MARK_SPAM: 'SPAM',
  RESTORE: 'APPROVED',
}

const restoreSourceStatuses = new Set<CommentStatus>(['REJECTED', 'HIDDEN', 'SPAM'])

export function getAvailableModerationActions(status: CommentStatus): ModerationActionType[] {
  return (Object.entries(actionTargetStatus) as Array<[ModerationActionType, CommentStatus]>)
    .filter(([action, targetStatus]) => {
      if (targetStatus === status) {
        return false
      }
      if (action === 'RESTORE') {
        return restoreSourceStatuses.has(status)
      }
      if (action === 'APPROVE' && restoreSourceStatuses.has(status)) {
        return false
      }
      return true
    })
    .map(([action]) => action)
}

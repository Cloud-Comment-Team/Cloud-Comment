import type {
  AutoModerationFeedback,
  AutoModerationFeedbackType,
  Comment,
  BulkModerationRequest,
  BulkModerationResponse,
  ListCommentsParams,
  ModerationAction,
  ModerationActionRequest,
  ModerationCounts,
  PaginatedResponse,
  UpdateCommentFlagsRequest,
} from '../types/api'
import { apiClient } from './client'

export async function listComments(params: ListCommentsParams = {}): Promise<PaginatedResponse<Comment>> {
  const response = await apiClient.get<PaginatedResponse<Comment>>('/moderation/comments', {
    params,
    paramsSerializer: { indexes: null },
  })
  assertPaginatedCommentResponse(response.data)
  return response.data
}

export async function getModerationCounts(): Promise<ModerationCounts> {
  const response = await apiClient.get<ModerationCounts>('/moderation/counts')
  return response.data
}

export async function applyBulkModerationAction(request: BulkModerationRequest): Promise<BulkModerationResponse> {
  const response = await apiClient.post<BulkModerationResponse>('/moderation/comments/bulk-actions', request)
  return response.data
}

export async function undoModerationAction(actionId: string): Promise<ModerationAction> {
  const response = await apiClient.post<ModerationAction>(`/moderation/actions/${actionId}/undo`)
  return response.data
}

export async function getComment(commentId: string): Promise<Comment> {
  const response = await apiClient.get<Comment>(`/moderation/comments/${commentId}`)
  return response.data
}

export async function applyModerationAction(
  commentId: string,
  request: ModerationActionRequest,
): Promise<ModerationAction> {
  const response = await apiClient.post<ModerationAction>(`/moderation/comments/${commentId}/actions`, request)
  return response.data
}

export async function updateCommentFlags(commentId: string, request: UpdateCommentFlagsRequest): Promise<Comment> {
  const response = await apiClient.patch<Comment>(`/moderation/comments/${commentId}/flags`, request)
  return response.data
}

export async function setAutoModerationFeedback(
  commentId: string,
  type: AutoModerationFeedbackType,
): Promise<AutoModerationFeedback> {
  const response = await apiClient.put<AutoModerationFeedback>(
    `/moderation/comments/${commentId}/automoderation-feedback`,
    { type },
  )
  return response.data
}

export async function deleteAutoModerationFeedback(commentId: string): Promise<void> {
  await apiClient.delete(`/moderation/comments/${commentId}/automoderation-feedback`)
}

function assertPaginatedCommentResponse(value: PaginatedResponse<Comment>): void {
  if (!value || !Array.isArray(value.items)) {
    throw new Error('Некорректный ответ API со списком комментариев.')
  }
}

import type {
  Comment,
  ListCommentsParams,
  ModerationAction,
  ModerationActionRequest,
  PaginatedResponse,
  UpdateCommentFlagsRequest,
} from '../types/api'
import { apiClient } from './client'

export async function listComments(params: ListCommentsParams = {}): Promise<PaginatedResponse<Comment>> {
  const response = await apiClient.get<PaginatedResponse<Comment>>('/moderation/comments', { params })
  assertPaginatedCommentResponse(response.data)
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

function assertPaginatedCommentResponse(value: PaginatedResponse<Comment>): void {
  if (!value || !Array.isArray(value.items)) {
    throw new Error('Некорректный ответ API со списком комментариев.')
  }
}

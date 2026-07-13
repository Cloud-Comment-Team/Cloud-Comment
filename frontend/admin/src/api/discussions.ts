import type {
  CreateOwnerReplyRequest,
  CreateOwnerReplyResponse,
  DiscussionThread,
  DiscussionSummary,
  ListDiscussionsParams,
  PaginatedResponse,
} from '../types/api'
import { apiClient } from './client'

export async function listDiscussions(
  params: ListDiscussionsParams = {},
): Promise<PaginatedResponse<DiscussionSummary>> {
  const response = await apiClient.get<PaginatedResponse<DiscussionSummary>>('/discussions', { params })
  if (!response.data || !Array.isArray(response.data.items)) {
    throw new Error('Некорректный ответ API со списком обсуждений.')
  }
  return response.data
}

export async function getDiscussion(rootCommentId: string): Promise<DiscussionThread> {
  const response = await apiClient.get<DiscussionThread>(`/discussions/${rootCommentId}`)
  if (!response.data || !response.data.summary || !Array.isArray(response.data.messages)) {
    throw new Error('Некорректный ответ API с обсуждением.')
  }
  return response.data
}

export async function createOwnerReply(
  rootCommentId: string,
  request: CreateOwnerReplyRequest,
): Promise<CreateOwnerReplyResponse> {
  const response = await apiClient.post<CreateOwnerReplyResponse>(
    `/discussions/${rootCommentId}/replies`,
    request,
  )
  if (!response.data?.message || typeof response.data.created !== 'boolean') {
    throw new Error('Некорректный ответ API с ответом владельца.')
  }
  return response.data
}

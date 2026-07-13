import { apiClient } from './client'

export interface AccountDeletionRequest {
  id: string
  userId: string
  status: string
  createdAt: string
  expiresAt: string
}

export async function createAccountDeletionRequest(): Promise<AccountDeletionRequest> {
  const response = await apiClient.post<AccountDeletionRequest>('/account/deletion-requests')
  return response.data
}

export async function getCurrentAccountDeletionRequest(): Promise<AccountDeletionRequest | null> {
  try {
    const response = await apiClient.get<AccountDeletionRequest>('/account/deletion-requests/current')
    return response.data
  } catch (error) {
    if (isNotFoundError(error)) {
      return null
    }
    throw error
  }
}

export async function confirmAccountDeletion(token: string): Promise<void> {
  await apiClient.post('/account/deletion-confirmations', { token })
}

export async function exportPersonalData(): Promise<unknown> {
  const response = await apiClient.get<unknown>('/account/personal-data')
  return response.data
}

function isNotFoundError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: { status?: number } }).response?.status === 'number' &&
    (error as { response: { status: number } }).response.status === 404
  )
}

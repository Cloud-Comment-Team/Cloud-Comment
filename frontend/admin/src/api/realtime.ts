import { apiClient } from './client'

export interface RealtimeTicketResponse {
  ticket: string
  expiresAt: string
}

export async function createRealtimeTicket(): Promise<RealtimeTicketResponse> {
  const response = await apiClient.post<RealtimeTicketResponse>('/realtime/tickets')
  return response.data
}

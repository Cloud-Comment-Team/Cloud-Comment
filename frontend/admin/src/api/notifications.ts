import type { OwnerNotification, PaginatedResponse } from '../types/api'
import { apiClient } from './client'

export async function listNotifications(page = 1, pageSize = 20): Promise<PaginatedResponse<OwnerNotification>> {
  const response = await apiClient.get<PaginatedResponse<OwnerNotification>>('/notifications', {
    params: { page, pageSize },
  })
  return response.data
}

export async function getUnreadNotificationCount(): Promise<number> {
  const response = await apiClient.get<{ unreadCount: number }>('/notifications/unread-count')
  return response.data.unreadCount
}

export async function markNotificationRead(notificationId: string): Promise<OwnerNotification> {
  const response = await apiClient.patch<OwnerNotification>(`/notifications/${notificationId}/read`)
  return response.data
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post('/notifications/read-all')
}

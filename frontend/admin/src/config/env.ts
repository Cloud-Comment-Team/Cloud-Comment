const FALLBACK_API_BASE_URL = '/api'

export function getApiBaseUrl(
  envValue = import.meta.env.VITE_CLOUD_COMMENT_API_BASE_URL
): string {
  const normalizedValue = envValue?.trim()
  return normalizedValue || FALLBACK_API_BASE_URL
}

export const API_BASE_URL = getApiBaseUrl()

export function getRealtimeWebSocketUrl(ticket: string): string {
  const url = new URL(`${API_BASE_URL.replace(/\/$/, '')}/realtime/ws`, window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.searchParams.set('ticket', ticket)
  return url.toString()
}

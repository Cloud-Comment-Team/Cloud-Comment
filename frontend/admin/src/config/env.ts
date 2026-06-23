const FALLBACK_API_BASE_URL = '/api'

export function getApiBaseUrl(
  envValue = import.meta.env.VITE_CLOUD_COMMENT_API_BASE_URL
): string {
  const normalizedValue = envValue?.trim()
  return normalizedValue || FALLBACK_API_BASE_URL
}

export const API_BASE_URL = getApiBaseUrl()

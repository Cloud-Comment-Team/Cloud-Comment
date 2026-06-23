const FALLBACK_API_BASE_URL = "/api";

export function getDefaultApiBaseUrl(
  envValue = import.meta.env.VITE_CLOUD_COMMENT_API_BASE_URL
): string {
  const normalizedValue = envValue?.trim();
  return normalizedValue || FALLBACK_API_BASE_URL;
}

export const DEFAULT_API_BASE_URL = getDefaultApiBaseUrl();

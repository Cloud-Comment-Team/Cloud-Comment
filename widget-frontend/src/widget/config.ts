const FALLBACK_API_BASE_URL = "/api";
const WIDGET_AUTH_TOKEN_KEY_PREFIX = "cloud-comment.widget.authToken.v2";

export function getDefaultApiBaseUrl(
  envValue = import.meta.env.VITE_CLOUD_COMMENT_API_BASE_URL
): string {
  const normalizedValue = envValue?.trim();
  return normalizedValue || FALLBACK_API_BASE_URL;
}

export const DEFAULT_API_BASE_URL = getDefaultApiBaseUrl();

export function getFrameBaseUrl(
  apiBaseUrl: string,
  explicitValue?: string,
  envValue = import.meta.env.VITE_CLOUD_COMMENT_WIDGET_BASE_URL
): string {
  const candidate = explicitValue?.trim() || envValue?.trim();
  const apiOrigin = new URL(apiBaseUrl, window.location.href).origin;
  const resolved = new URL(candidate || apiOrigin, window.location.href);
  if (resolved.protocol !== "https:" && resolved.protocol !== "http:") {
    throw new Error("CloudComment frame URL must use HTTP(S)");
  }
  if (resolved.username || resolved.password) {
    throw new Error("CloudComment frame URL must not contain credentials");
  }
  resolved.search = "";
  resolved.hash = "";
  return resolved.toString().replace(/\/+$/u, "");
}

export function getWidgetAuthStorageKey(siteId: string, parentOrigin: string): string {
  return `${WIDGET_AUTH_TOKEN_KEY_PREFIX}:${siteId}:${parentOrigin}`;
}

const CONTEXT_STORAGE_PREFIX = "cloud-comment.widget.context.v1";
const AUTH_STORAGE_PREFIX = "cloud-comment.widget.authToken.v4";
const MAX_CONTEXT_TOKEN_LENGTH = 4096;

export type PersistedWidgetContext = {
  token: string;
  expiresAt: string;
};

export async function createContextStorageKey(
  siteId: string,
  parentOrigin: string,
  canonicalPageUrl: string
): Promise<string> {
  return `${CONTEXT_STORAGE_PREFIX}:${await stableScopeHash([
    "CLOUDCOMMENT_WIDGET_CONTEXT_STORAGE_V1",
    siteId,
    parentOrigin,
    canonicalPageUrl
  ])}`;
}

export async function createAuthStorageKey(
  siteId: string,
  parentOrigin: string
): Promise<string> {
  return `${AUTH_STORAGE_PREFIX}:${await stableScopeHash([
    "CLOUDCOMMENT_WIDGET_AUTH_STORAGE_V2",
    siteId,
    parentOrigin
  ])}`;
}

export function readStoredContextEnvelope(storageKey: string): PersistedWidgetContext | null {
  try {
    const serialized = window.sessionStorage.getItem(storageKey);
    if (!serialized) {
      return null;
    }
    const value: unknown = JSON.parse(serialized);
    if (!isPersistedContext(value)) {
      window.sessionStorage.removeItem(storageKey);
      return null;
    }
    return value;
  } catch {
    // Storage may be disabled by browser policy. The frame stays memory-only.
    return null;
  }
}

export function writePersistedContext(storageKey: string, value: PersistedWidgetContext): boolean {
  if (!isPersistedContext(value)) {
    return false;
  }
  try {
    window.sessionStorage.setItem(storageKey, JSON.stringify(value));
    return true;
  } catch {
    // A browser that blocks storage starts a new bootstrap after reload.
    return false;
  }
}

export function removePersistedValue(storageKey: string | null): void {
  if (!storageKey) {
    return;
  }
  try {
    window.sessionStorage.removeItem(storageKey);
  } catch {
    // Storage cleanup is best-effort when browser policy blocks storage.
  }
}

function isPersistedContext(value: unknown): value is PersistedWidgetContext {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  const keys = Object.keys(record);
  return keys.length === 2
    && typeof record.token === "string"
    && record.token.length >= 32
    && record.token.length <= MAX_CONTEXT_TOKEN_LENGTH
    && typeof record.expiresAt === "string"
    && record.expiresAt.length > 0
    && record.expiresAt.length <= 64
    && Number.isFinite(Date.parse(record.expiresAt));
}

async function stableScopeHash(parts: readonly string[]): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(parts.join("\n")));
  return toBase64Url(new Uint8Array(digest));
}

function toBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/u, "");
}

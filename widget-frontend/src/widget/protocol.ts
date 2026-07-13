export const WIDGET_PROTOCOL_VERSION = 1 as const;
export const WIDGET_CONTEXT_HEADER = "X-CloudComment-Widget-Context";
export const WIDGET_PAGE_URL_HEADER = "X-CloudComment-Page-Url";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const INSTANCE_PATTERN = /^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;
const BASE64URL_PATTERN = /^[A-Za-z0-9_-]+$/;

export type WidgetConnectMessage = {
  type: "cloud-comment:connect";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  siteId: string;
  apiOrigin: string;
  pageUrl: string;
  initialCommentId: string | null;
  theme: "auto" | "light" | "dark";
  fontFamily: string;
};

export type FrameReadyMessage = {
  type: "cloud-comment:frame-ready";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  publicKey: string;
};

export type FrameContextReusedMessage = {
  type: "cloud-comment:context-reused";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
};

export type FrameContextExpiredMessage = {
  type: "cloud-comment:context-expired";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
};

export type BootstrapTicketMessage = {
  type: "cloud-comment:bootstrap-ticket";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  ticket: string;
  expiresAt: string;
  canonicalPageUrl: string;
  publicKeyFingerprint: string;
};

export type HostDestroyMessage = {
  type: "cloud-comment:destroy";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
};

export type HostBootstrapErrorMessage = {
  type: "cloud-comment:bootstrap-error";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  code: "BOOTSTRAP_FAILED" | "BOOTSTRAP_TIMEOUT";
};

export type FrameResizeMessage = {
  type: "cloud-comment:resize";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  height: number;
};

export type FrameErrorMessage = {
  type: "cloud-comment:error";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
  code: string;
};

export type FrameDestroyedMessage = {
  type: "cloud-comment:destroyed";
  version: typeof WIDGET_PROTOCOL_VERSION;
  instanceId: string;
};

export type HostPortMessage = BootstrapTicketMessage | HostDestroyMessage | HostBootstrapErrorMessage;
export type FramePortMessage =
  | FrameReadyMessage
  | FrameContextReusedMessage
  | FrameContextExpiredMessage
  | FrameResizeMessage
  | FrameErrorMessage
  | FrameDestroyedMessage;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function isWidgetConnectMessage(value: unknown): value is WidgetConnectMessage {
  return isRecord(value)
    && hasOnlyKeys(value, [
      "type", "version", "instanceId", "siteId", "apiOrigin", "pageUrl", "initialCommentId", "theme", "fontFamily"
    ])
    && value.type === "cloud-comment:connect"
    && value.version === WIDGET_PROTOCOL_VERSION
    && typeof value.instanceId === "string" && INSTANCE_PATTERN.test(value.instanceId)
    && typeof value.siteId === "string" && UUID_PATTERN.test(value.siteId)
    && typeof value.apiOrigin === "string" && isHttpOrigin(value.apiOrigin)
    && typeof value.pageUrl === "string" && value.pageUrl.length > 0 && value.pageUrl.length <= 2048
    && (value.initialCommentId === null
      || (typeof value.initialCommentId === "string" && UUID_PATTERN.test(value.initialCommentId)))
    && (value.theme === "auto" || value.theme === "light" || value.theme === "dark")
    && typeof value.fontFamily === "string"
    && value.fontFamily.length > 0
    && value.fontFamily.length <= 256
    && !/[\u0000-\u001f\u007f]/u.test(value.fontFamily);
}

function isHttpOrigin(value: string): boolean {
  try {
    const url = new URL(value);
    return (url.protocol === "https:" || url.protocol === "http:")
      && !url.username
      && !url.password
      && url.origin === value;
  } catch {
    return false;
  }
}

export function isFramePortMessage(value: unknown, instanceId: string): value is FramePortMessage {
  if (!isRecord(value)
    || value.version !== WIDGET_PROTOCOL_VERSION
    || value.instanceId !== instanceId
    || typeof value.type !== "string") {
    return false;
  }
  if (value.type === "cloud-comment:frame-ready") {
    return hasOnlyKeys(value, ["type", "version", "instanceId", "publicKey"])
      && typeof value.publicKey === "string"
      && value.publicKey.length >= 80
      && value.publicKey.length <= 256
      && BASE64URL_PATTERN.test(value.publicKey);
  }
  if (value.type === "cloud-comment:context-reused") {
    return hasOnlyKeys(value, ["type", "version", "instanceId"]);
  }
  if (value.type === "cloud-comment:context-expired") {
    return hasOnlyKeys(value, ["type", "version", "instanceId"]);
  }
  if (value.type === "cloud-comment:destroyed") {
    return hasOnlyKeys(value, ["type", "version", "instanceId"]);
  }
  if (value.type === "cloud-comment:resize") {
    return hasOnlyKeys(value, ["type", "version", "instanceId", "height"])
      && typeof value.height === "number"
      && Number.isFinite(value.height)
      && value.height >= 0
      && value.height <= 10_000;
  }
  return value.type === "cloud-comment:error"
    && hasOnlyKeys(value, ["type", "version", "instanceId", "code"])
    && typeof value.code === "string"
    && /^[A-Z0-9_]{1,64}$/.test(value.code);
}

export function isHostPortMessage(value: unknown, instanceId: string): value is HostPortMessage {
  if (!isRecord(value)
    || value.version !== WIDGET_PROTOCOL_VERSION
    || value.instanceId !== instanceId
    || typeof value.type !== "string") {
    return false;
  }
  if (value.type === "cloud-comment:destroy") {
    return hasOnlyKeys(value, ["type", "version", "instanceId"]);
  }
  if (value.type === "cloud-comment:bootstrap-error") {
    return hasOnlyKeys(value, ["type", "version", "instanceId", "code"])
      && (value.code === "BOOTSTRAP_FAILED" || value.code === "BOOTSTRAP_TIMEOUT");
  }
  return value.type === "cloud-comment:bootstrap-ticket"
    && hasOnlyKeys(value, [
      "type", "version", "instanceId", "ticket", "expiresAt", "canonicalPageUrl", "publicKeyFingerprint"
    ])
    && typeof value.ticket === "string" && value.ticket.length >= 32 && value.ticket.length <= 256
    && BASE64URL_PATTERN.test(value.ticket)
    && typeof value.expiresAt === "string" && value.expiresAt.length <= 64 && Number.isFinite(Date.parse(value.expiresAt))
    && typeof value.canonicalPageUrl === "string"
    && value.canonicalPageUrl.length > 0 && value.canonicalPageUrl.length <= 2048
    && typeof value.publicKeyFingerprint === "string"
    && value.publicKeyFingerprint.length === 43
    && BASE64URL_PATTERN.test(value.publicKeyFingerprint);
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: readonly string[]): boolean {
  const keys = Object.keys(value);
  return keys.length === allowed.length && keys.every((key) => allowed.includes(key));
}

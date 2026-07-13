import { DEFAULT_API_BASE_URL, getFrameBaseUrl } from "./config";
import { resolvePageUrl } from "./pageUrl";
import {
  isFramePortMessage,
  WIDGET_PROTOCOL_VERSION,
  type BootstrapTicketMessage,
  type FrameReadyMessage,
  type WidgetConnectMessage
} from "./protocol";
import type {
  CloudCommentWidgetApi,
  CloudCommentWidgetInstance,
  CloudCommentWidgetOptions,
  WidgetTheme
} from "./types";

const DEFAULT_TARGET_ID = "cloud-comment-widget";
const MIN_FRAME_HEIGHT = 120;
const MAX_FRAME_HEIGHT = 5000;
const HANDSHAKE_TIMEOUT_MS = 8000;
const BOOTSTRAP_TIMEOUT_MS = 10000;
const DESTROY_ACK_TIMEOUT_MS = 500;

type ResolvedLoaderOptions = {
  siteId: string;
  frameBaseUrl: string;
  apiOrigin: string;
  pageUrl: string;
  target: string | HTMLElement;
  theme: WidgetTheme;
};

type BootstrapResponse = {
  ticket: string;
  expiresAt: string;
  canonicalPageUrl: string;
  publicKeyFingerprint: string;
};

function resolveTarget(target?: string | HTMLElement): HTMLElement {
  if (target instanceof HTMLElement) {
    return target;
  }
  if (typeof target === "string") {
    const foundTarget = document.querySelector<HTMLElement>(target);
    if (!foundTarget) {
      throw new Error(`CloudComment target was not found: ${target}`);
    }
    return foundTarget;
  }
  const existingTarget = document.getElementById(DEFAULT_TARGET_ID);
  if (existingTarget) {
    return existingTarget;
  }
  const createdTarget = document.createElement("div");
  createdTarget.id = DEFAULT_TARGET_ID;
  const currentScript = document.currentScript;
  if (currentScript?.parentNode) {
    currentScript.parentNode.insertBefore(createdTarget, currentScript.nextSibling);
  } else {
    document.body.append(createdTarget);
  }
  return createdTarget;
}

function normalizeOptions(options: CloudCommentWidgetOptions): ResolvedLoaderOptions {
  if (!options.siteId?.trim()) {
    throw new Error("CloudComment siteId is required");
  }
  const apiBaseUrl = options.apiBaseUrl ?? DEFAULT_API_BASE_URL;
  return {
    siteId: options.siteId.trim(),
    frameBaseUrl: getFrameBaseUrl(apiBaseUrl, options.frameBaseUrl),
    apiOrigin: new URL(apiBaseUrl, window.location.href).origin,
    pageUrl: resolvePageUrl(options.pageUrl, window.location.href),
    target: options.target ?? `#${DEFAULT_TARGET_ID}`,
    theme: normalizeTheme(options.theme)
  };
}

function normalizeTheme(theme?: string): WidgetTheme {
  return theme === "light" || theme === "dark" || theme === "auto" ? theme : "auto";
}

function init(options: CloudCommentWidgetOptions): CloudCommentWidgetInstance {
  const normalized = normalizeOptions(options);
  const target = resolveTarget(options.target);
  const frameOrigin = new URL(normalized.frameBaseUrl).origin;
  if (frameOrigin === normalized.apiOrigin) {
    return renderHostConfigurationError(target);
  }
  const instanceId = createInstanceId();
  const iframe = document.createElement("iframe");
  iframe.title = "Комментарии CloudComment";
  iframe.dataset.cloudCommentInstance = instanceId;
  iframe.src = `${normalized.frameBaseUrl}/api/public/sites/${encodeURIComponent(normalized.siteId)}/widget-frame`;
  iframe.referrerPolicy = "no-referrer";
  iframe.loading = "eager";
  iframe.style.display = "block";
  iframe.style.width = "100%";
  iframe.style.height = "320px";
  iframe.style.border = "0";
  iframe.style.colorScheme = normalized.theme === "auto" ? "light dark" : normalized.theme;

  iframe.setAttribute("sandbox", "allow-scripts allow-same-origin allow-popups");

  target.replaceChildren(iframe);
  let destroyed = false;
  let port: MessagePort | null = null;
  let bootstrapStarted = false;
  let handshakeTimer: number | null = null;
  let bootstrapTimer: number | null = null;
  let destroyAckTimer: number | null = null;
  let bootstrapAbort: AbortController | null = null;
  const finalizeDestroy = () => {
    clearTimer(destroyAckTimer);
    destroyAckTimer = null;
    port?.close();
    port = null;
    iframe.remove();
  };

  iframe.addEventListener("load", () => {
    if (destroyed || !iframe.contentWindow) {
      return;
    }
    bootstrapStarted = false;
    clearTimer(handshakeTimer);
    clearTimer(bootstrapTimer);
    bootstrapAbort?.abort();
    bootstrapAbort = null;
    port?.close();
    const channel = new MessageChannel();
    port = channel.port1;
    port.onmessage = (event) => {
      if (!isFramePortMessage(event.data, instanceId)) {
        return;
      }
      if (event.data.type === "cloud-comment:destroyed") {
        if (destroyed) {
          finalizeDestroy();
        }
      } else if (destroyed) {
        return;
      } else if (event.data.type === "cloud-comment:context-expired") {
        clearTimer(handshakeTimer);
        clearTimer(bootstrapTimer);
        bootstrapAbort?.abort();
        bootstrapAbort = null;
        port?.close();
        port = null;
        iframe.setAttribute("src", iframe.src);
      } else if (event.data.type === "cloud-comment:context-reused") {
        bootstrapStarted = true;
        clearTimer(handshakeTimer);
        handshakeTimer = null;
      } else if (event.data.type === "cloud-comment:frame-ready") {
        if (!bootstrapStarted) {
          bootstrapStarted = true;
          clearTimer(handshakeTimer);
          handshakeTimer = null;
          bootstrapAbort = new AbortController();
          const requestPort = port!;
          bootstrapTimer = window.setTimeout(() => {
            requestPort.postMessage(bootstrapError(instanceId, "BOOTSTRAP_TIMEOUT"));
            bootstrapAbort?.abort();
          }, BOOTSTRAP_TIMEOUT_MS);
          void bootstrapFrame(normalized, event.data, requestPort, bootstrapAbort.signal).finally(() => {
            clearTimer(bootstrapTimer);
            bootstrapTimer = null;
          });
        }
      } else if (event.data.type === "cloud-comment:resize") {
        const height = Math.max(MIN_FRAME_HEIGHT, Math.min(MAX_FRAME_HEIGHT, Math.ceil(event.data.height)));
        iframe.style.height = `${height}px`;
      }
    };
    port.start();
    handshakeTimer = window.setTimeout(() => {
      if (!bootstrapStarted && !destroyed) {
        port?.close();
        port = null;
        renderHostUnavailable(target, iframe);
      }
    }, HANDSHAKE_TIMEOUT_MS);
    const connectMessage: WidgetConnectMessage = {
      type: "cloud-comment:connect",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId,
      siteId: normalized.siteId,
      apiOrigin: normalized.apiOrigin,
      pageUrl: normalized.pageUrl,
      theme: normalized.theme,
      fontFamily: resolveHostFontFamily(target)
    };
    iframe.contentWindow.postMessage(connectMessage, frameOrigin, [channel.port2]);
  });

  return {
    destroy: () => {
      if (destroyed) {
        return;
      }
      destroyed = true;
      clearTimer(handshakeTimer);
      clearTimer(bootstrapTimer);
      bootstrapAbort?.abort();
      bootstrapAbort = null;
      iframe.style.display = "none";
      iframe.setAttribute("aria-hidden", "true");
      if (port) {
        port.postMessage({
          type: "cloud-comment:destroy",
          version: WIDGET_PROTOCOL_VERSION,
          instanceId
        });
        destroyAckTimer = window.setTimeout(finalizeDestroy, DESTROY_ACK_TIMEOUT_MS);
      } else {
        finalizeDestroy();
      }
    }
  };
}

function renderHostConfigurationError(target: HTMLElement): CloudCommentWidgetInstance {
  const status = document.createElement("p");
  status.setAttribute("role", "alert");
  status.textContent = "Нужен отдельный адрес виджета";
  target.replaceChildren(status);
  return {
    destroy: () => status.remove()
  };
}

async function bootstrapFrame(
  options: ResolvedLoaderOptions,
  ready: FrameReadyMessage,
  port: MessagePort,
  signal: AbortSignal
): Promise<void> {
  try {
    const response = await fetch(
      `${options.frameBaseUrl}/api/public/sites/${encodeURIComponent(options.siteId)}/widget-context/bootstrap`,
      {
        method: "POST",
        mode: "cors",
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer",
        signal,
        headers: { Accept: "application/json", "Content-Type": "application/json" },
        body: JSON.stringify({ publicKey: ready.publicKey, pageUrl: options.pageUrl })
      }
    );
    if (!response.ok) {
      throw new Error("bootstrap failed");
    }
    const body = await response.json() as Partial<BootstrapResponse>;
    if (!isBootstrapResponse(body)) {
      throw new Error("invalid bootstrap response");
    }
    const message: BootstrapTicketMessage = {
      type: "cloud-comment:bootstrap-ticket",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: ready.instanceId,
      ticket: body.ticket,
      expiresAt: body.expiresAt,
      canonicalPageUrl: body.canonicalPageUrl,
      publicKeyFingerprint: body.publicKeyFingerprint
    };
    port.postMessage(message);
  } catch {
    if (!signal.aborted) {
      port.postMessage(bootstrapError(ready.instanceId, "BOOTSTRAP_FAILED"));
    }
  }
}

function bootstrapError(
  instanceId: string,
  code: "BOOTSTRAP_FAILED" | "BOOTSTRAP_TIMEOUT"
) {
  return { type: "cloud-comment:bootstrap-error" as const, version: WIDGET_PROTOCOL_VERSION, instanceId, code };
}

function renderHostUnavailable(target: HTMLElement, iframe: HTMLIFrameElement): void {
  if (!iframe.isConnected) {
    return;
  }
  const status = document.createElement("p");
  status.setAttribute("role", "alert");
  status.textContent = "Комментарии недоступны";
  target.replaceChildren(status);
}

function clearTimer(timer: number | null): void {
  if (timer !== null) {
    window.clearTimeout(timer);
  }
}

function isBootstrapResponse(value: Partial<BootstrapResponse>): value is BootstrapResponse {
  return Object.keys(value).length === 4
    && typeof value.ticket === "string"
    && value.ticket.length >= 32 && value.ticket.length <= 256 && /^[A-Za-z0-9_-]+$/.test(value.ticket)
    && typeof value.expiresAt === "string"
    && value.expiresAt.length <= 64 && Number.isFinite(Date.parse(value.expiresAt))
    && typeof value.canonicalPageUrl === "string"
    && value.canonicalPageUrl.length > 0 && value.canonicalPageUrl.length <= 2048
    && typeof value.publicKeyFingerprint === "string"
    && value.publicKeyFingerprint.length === 43 && /^[A-Za-z0-9_-]+$/.test(value.publicKeyFingerprint);
}

function createInstanceId(): string {
  if (typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function resolveHostFontFamily(target: HTMLElement): string {
  const fontFamily = window.getComputedStyle(target).fontFamily.trim();
  return fontFamily.length > 0
    && fontFamily.length <= 256
    && !/[\u0000-\u001f\u007f]/u.test(fontFamily)
    ? fontFamily
    : "system-ui, sans-serif";
}

function autoInit(): CloudCommentWidgetInstance | null {
  const script = document.currentScript as HTMLScriptElement | null;
  const siteId = script?.dataset.siteId;
  if (!siteId) {
    return null;
  }
  return init({
    siteId,
    apiBaseUrl: script.dataset.apiBaseUrl,
    frameBaseUrl: script.dataset.frameBaseUrl,
    pageUrl: script.dataset.pageUrl,
    target: script.dataset.target,
    theme: normalizeTheme(script.dataset.theme)
  });
}

const api: CloudCommentWidgetApi = { init, autoInit };
window.CloudCommentWidget = api;
autoInit();

export { autoInit, init };

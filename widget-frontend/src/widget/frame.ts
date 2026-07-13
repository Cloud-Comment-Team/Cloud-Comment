import type { WidgetApiClient } from "./api";
import { generateWidgetFrameKey, signWidgetBootstrap } from "./frameCrypto";
import { contextRefreshDelay } from "./frameTime";
import {
  createAuthStorageKey,
  createContextStorageKey,
  readStoredContextEnvelope,
  removePersistedValue,
  writePersistedContext,
  type PersistedWidgetContext
} from "./frameStorage";
import {
  isHostPortMessage,
  WIDGET_CONTEXT_HEADER,
  isWidgetConnectMessage,
  WIDGET_PROTOCOL_VERSION,
  type BootstrapTicketMessage,
  type WidgetConnectMessage
} from "./protocol";
import { renderWidget } from "./render";
import type { CloudCommentWidgetInstance } from "./types";

type ExchangeResponse = {
  contextToken: string;
  expiresAt: string;
};

let connected = false;
let activeInstance: CloudCommentWidgetInstance | null = null;
let resizeObserver: ResizeObserver | null = null;
let activeLifecycleAbort: AbortController | null = null;
let activeContextExpiryTimer: number | null = null;

window.addEventListener("message", (event) => {
  if (connected
    || event.source !== parent
    || event.ports.length !== 1
    || !isWidgetConnectMessage(event.data)
    || !isWebOrigin(event.origin)) {
    return;
  }
  const expectedSiteId = siteIdFromFramePath(window.location.pathname);
  if (expectedSiteId && expectedSiteId !== event.data.siteId) {
    return;
  }
  if (!isPageForOrigin(event.data.pageUrl, event.origin)) {
    return;
  }
  if (event.data.apiOrigin === physicalFrameOrigin()) {
    connected = true;
    const port = event.ports[0];
    port.start();
    renderFatal("Нужен отдельный адрес виджета");
    port.postMessage(frameError(event.data.instanceId, "WIDGET_ORIGIN_REQUIRED"));
    port.close();
    return;
  }
  connected = true;
  startFrame(event.data, event.origin, event.ports[0]);
});

async function startFrame(connect: WidgetConnectMessage, parentOrigin: string, port: MessagePort): Promise<void> {
  document.documentElement.style.fontFamily = connect.fontFamily;
  document.documentElement.style.colorScheme = connect.theme === "auto" ? "light dark" : connect.theme;
  let destroyed = false;
  let bootstrapConsumed = false;
  let key: Awaited<ReturnType<typeof generateWidgetFrameKey>> | null = null;
  let contextStorageKey: string | null = null;
  let authStorageKey: string | null = null;
  const lifecycleAbort = new AbortController();
  activeLifecycleAbort?.abort();
  activeLifecycleAbort = lifecycleAbort;

  const clearPersistedSession = () => {
    removePersistedValue(authStorageKey);
    removePersistedValue(contextStorageKey);
  };
  const clearPersistedContext = () => {
    removePersistedValue(contextStorageKey);
  };
  const scheduleContextExpiry = (expiresAt: string, serverDate: string | null = null) => {
    clearTimer(activeContextExpiryTimer);
    const delay = contextRefreshDelay(expiresAt, serverDate);
    activeContextExpiryTimer = window.setTimeout(() => {
      activeContextExpiryTimer = null;
      if (destroyed) {
        return;
      }
      destroyed = true;
      clearPersistedContext();
      port.postMessage({
        type: "cloud-comment:context-expired",
        version: WIDGET_PROTOCOL_VERSION,
        instanceId: connect.instanceId
      });
      teardown(port);
    }, delay);
  };
  const destroyFrame = () => {
    if (destroyed) {
      return;
    }
    destroyed = true;
    lifecycleAbort.abort();
    clearPersistedContext();
    port.postMessage({
      type: "cloud-comment:destroyed",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId
    });
    teardown(port);
  };

  port.onmessage = (event) => {
    if (!isHostPortMessage(event.data, connect.instanceId)) {
      return;
    }
    if (event.data.type === "cloud-comment:destroy") {
      destroyFrame();
      return;
    }
    if (event.data.type === "cloud-comment:bootstrap-error") {
      bootstrapConsumed = true;
      renderFatal("Комментарии недоступны");
      return;
    }
    if (bootstrapConsumed || !key) {
      return;
    }
    bootstrapConsumed = true;
    void exchangeAndRender(
      connect,
      parentOrigin,
      event.data,
      key,
      port,
      lifecycleAbort.signal,
      contextStorageKey,
      (value) => {
        authStorageKey = value;
      },
      clearPersistedSession,
      scheduleContextExpiry
    ).catch(() => {
      if (destroyed || lifecycleAbort.signal.aborted) {
        return;
      }
      renderFatal("Виджет недоступен.");
      port.postMessage(frameError(connect.instanceId, "CONTEXT_EXCHANGE_FAILED"));
    });
  };
  port.start();

  try {
    contextStorageKey = await createContextStorageKey(connect.siteId, parentOrigin, connect.pageUrl);
    const persisted = readStoredContextEnvelope(contextStorageKey);
    if (persisted) {
      authStorageKey = await createAuthStorageKey(
        connect.siteId,
        parentOrigin
      );
      if (destroyed) {
        clearPersistedContext();
        return;
      }
      const validation = await validatePersistedContext(connect, persisted, lifecycleAbort.signal);
      if (destroyed) {
        clearPersistedContext();
        return;
      }
      if (validation.reusable) {
        bootstrapConsumed = true;
        scheduleContextExpiry(persisted.expiresAt, validation.serverDate);
        renderFrame(
          connect,
          parentOrigin,
          persisted.token,
          authStorageKey,
          contextStorageKey,
          clearPersistedSession,
          port
        );
        port.postMessage({
          type: "cloud-comment:context-reused",
          version: WIDGET_PROTOCOL_VERSION,
          instanceId: connect.instanceId
        });
        return;
      }
      clearPersistedContext();
    }

    key = await generateWidgetFrameKey();
    if (destroyed) {
      return;
    }
    port.postMessage({
      type: "cloud-comment:frame-ready",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      publicKey: key.publicKey
    });
  } catch {
    if (destroyed || lifecycleAbort.signal.aborted) {
      return;
    }
    renderFatal("Web Crypto недоступен.");
    port.postMessage(frameError(connect.instanceId, "CRYPTO_UNAVAILABLE"));
  }
}

async function exchangeAndRender(
  connect: WidgetConnectMessage,
  parentOrigin: string,
  bootstrap: BootstrapTicketMessage,
  key: Awaited<ReturnType<typeof generateWidgetFrameKey>>,
  port: MessagePort,
  signal: AbortSignal,
  contextStorageKey: string | null,
  setAuthStorageKey: (value: string) => void,
  clearPersistedSession: () => void,
  scheduleContextExpiry: (expiresAt: string, serverDate?: string | null) => void
): Promise<void> {
  if (bootstrap.publicKeyFingerprint !== key.fingerprint
    || !isPageForOrigin(bootstrap.canonicalPageUrl, parentOrigin)
    || bootstrap.canonicalPageUrl !== connect.pageUrl) {
    throw new Error("Bootstrap binding mismatch");
  }
  const proof = await signWidgetBootstrap(
    key.privateKey,
    connect.siteId,
    parentOrigin,
    bootstrap.canonicalPageUrl,
    key.fingerprint,
    bootstrap.ticket
  );
  const apiBaseUrl = `${physicalFrameOrigin()}/api`;
  const response = await fetch(
    `${apiBaseUrl}/public/sites/${encodeURIComponent(connect.siteId)}/widget-context/exchange`,
    {
      method: "POST",
      mode: "cors",
      credentials: "omit",
      cache: "no-store",
      referrerPolicy: "no-referrer",
      signal,
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ ticket: bootstrap.ticket, proof })
    }
  );
  if (!response.ok) {
    throw new Error("Context exchange failed");
  }
  const exchanged = await response.json() as Partial<ExchangeResponse>;
  if (typeof exchanged.contextToken !== "string"
    || exchanged.contextToken.length < 32
    || exchanged.contextToken.length > 4096
    || typeof exchanged.expiresAt !== "string"
    || !Number.isFinite(Date.parse(exchanged.expiresAt))) {
    throw new Error("Invalid context exchange response");
  }
  const authStorageKey = await createAuthStorageKey(
    connect.siteId,
    parentOrigin
  );
  if (signal.aborted) {
    return;
  }
  setAuthStorageKey(authStorageKey);
  if (contextStorageKey) {
    writePersistedContext(contextStorageKey, {
      token: exchanged.contextToken,
      expiresAt: exchanged.expiresAt
    });
  }
  scheduleContextExpiry(exchanged.expiresAt, response.headers.get("Date"));
  renderFrame(
    connect,
    parentOrigin,
    exchanged.contextToken,
    authStorageKey,
    contextStorageKey,
    clearPersistedSession,
    port,
    bootstrap.canonicalPageUrl
  );
}

async function validatePersistedContext(
  connect: WidgetConnectMessage,
  persisted: PersistedWidgetContext,
  signal: AbortSignal
): Promise<{ reusable: boolean; serverDate: string | null }> {
  try {
    const response = await fetch(
      `${physicalFrameOrigin()}/api/public/sites/${encodeURIComponent(connect.siteId)}/config`,
      {
        method: "GET",
        mode: "cors",
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer",
        signal,
        headers: {
          Accept: "application/json",
          [WIDGET_CONTEXT_HEADER]: persisted.token
        }
      }
    );
    return { reusable: response.ok, serverDate: response.headers.get("Date") };
  } catch {
    return { reusable: false, serverDate: null };
  }
}

function renderFrame(
  connect: WidgetConnectMessage,
  parentOrigin: string,
  contextToken: string,
  authStorageKey: string,
  contextStorageKey: string | null,
  clearPersistedSession: () => void,
  port: MessagePort,
  pageUrl = connect.pageUrl
): void {
  const root = ensureRoot();
  activeInstance?.destroy();
  activeInstance = renderWidget(root, {
    siteId: connect.siteId,
    apiBaseUrl: `${physicalFrameOrigin()}/api`,
    pageUrl,
    target: root,
    theme: connect.theme,
    contextToken,
    parentOrigin,
    authStorageKey,
    onAuthCleared: () => {
      removePersistedValue(contextStorageKey);
      clearPersistedSession();
    }
  });
  installLegalLinkPolicy(root);
  observeHeight(connect.instanceId, port);
}

function installLegalLinkPolicy(root: HTMLElement): void {
  (root.shadowRoot ?? root).addEventListener("click", (event) => {
    const link = event.composedPath().find((node): node is HTMLAnchorElement => node instanceof HTMLAnchorElement);
    if (!link) {
      return;
    }
    if (!isAllowedFrameLegalUrl(link.href)) {
      event.preventDefault();
      return;
    }
    link.target = "_blank";
    link.rel = "noopener noreferrer";
  });
}

function isAllowedFrameLegalUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.origin === physicalFrameOrigin()
      && (url.protocol === "https:" || url.protocol === "http:")
      && url.pathname.startsWith("/legal/")
      && !url.username
      && !url.password;
  } catch {
    return false;
  }
}

function observeHeight(instanceId: string, port: MessagePort): void {
  resizeObserver?.disconnect();
  const report = () => {
    const height = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight);
    port.postMessage({
      type: "cloud-comment:resize",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId,
      height
    });
  };
  resizeObserver = new ResizeObserver(report);
  resizeObserver.observe(document.documentElement);
  resizeObserver.observe(document.body);
  report();
}

function teardown(port: MessagePort): void {
  activeLifecycleAbort?.abort();
  activeLifecycleAbort = null;
  clearTimer(activeContextExpiryTimer);
  activeContextExpiryTimer = null;
  resizeObserver?.disconnect();
  resizeObserver = null;
  activeInstance?.destroy();
  activeInstance = null;
  port.close();
  document.body.replaceChildren();
}

function clearTimer(timer: number | null): void {
  if (timer !== null) {
    window.clearTimeout(timer);
  }
}

function ensureRoot(): HTMLElement {
  const existing = document.getElementById("cloud-comment-frame-root");
  if (existing) {
    return existing;
  }
  const root = document.createElement("div");
  root.id = "cloud-comment-frame-root";
  document.body.replaceChildren(root);
  return root;
}

function renderFatal(message: string): void {
  const status = document.createElement("p");
  status.setAttribute("role", "alert");
  status.textContent = message;
  status.style.cssText = "margin:0;padding:16px;font:14px/1.5 system-ui,sans-serif;color:#b91c1c";
  document.body.replaceChildren(status);
}

function physicalFrameOrigin(): string {
  const url = new URL(window.location.href);
  if (url.protocol !== "https:" && url.protocol !== "http:") {
    throw new Error("Unsupported widget frame origin");
  }
  return url.origin;
}

function isWebOrigin(origin: string): boolean {
  try {
    const url = new URL(origin);
    return (url.protocol === "https:" || url.protocol === "http:") && url.origin === origin;
  } catch {
    return false;
  }
}

function isPageForOrigin(pageUrl: string, origin: string): boolean {
  try {
    const url = new URL(pageUrl);
    return url.origin === origin && !url.hash;
  } catch {
    return false;
  }
}

function siteIdFromFramePath(pathname: string): string | null {
  const match = pathname.match(/\/api\/public\/sites\/([0-9a-f-]{36})\/widget-frame\/?$/i);
  return match?.[1] ?? null;
}

function frameError(instanceId: string, code: string) {
  return {
    type: "cloud-comment:error" as const,
    version: WIDGET_PROTOCOL_VERSION,
    instanceId,
    code
  };
}

function previewInit(api: WidgetApiClient): CloudCommentWidgetInstance {
  if (!window.location.pathname.endsWith("/widget-preview.html")) {
    throw new Error();
  }
  const root = document.getElementById("cloud-comment-preview") ?? ensureRoot();
  return renderWidget(root, {
    siteId: "00000000-0000-0000-0000-000000000001",
    apiBaseUrl: "https://preview.invalid/api",
    pageUrl: `${window.location.origin}/widget-preview`,
    target: root,
    theme: "auto",
    parentOrigin: window.location.origin
  }, api);
}

window.CloudCommentWidgetFrame = { previewInit };

// @vitest-environment jsdom

import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { WIDGET_CONTEXT_HEADER, WIDGET_PROTOCOL_VERSION } from "./protocol";

const siteId = "00000000-0000-0000-0000-000000000001";
const instanceId = "00000000-0000-0000-0000-000000000099";

class TestPort {
  onmessage: ((event: MessageEvent) => void) | null = null;
  sent: unknown[] = [];
  postMessage(data: unknown): void { this.sent.push(data); }
  start(): void {}
  close(): void {}
}

class TestResizeObserver {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe(): void { this.callback([], this as unknown as ResizeObserver); }
  unobserve(): void {}
  disconnect(): void {}
}

beforeEach(() => {
  document.body.replaceChildren();
  window.sessionStorage.clear();
  vi.resetModules();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("frame отвергает чужой source/origin, подписывает browser origin и атомарно принимает один ticket", async () => {
  document.body.replaceChildren(document.createElement("div"));
  const signedPayloads: string[] = [];
  const subtle = {
    generateKey: vi.fn(async () => ({
      privateKey: { extractable: false },
      publicKey: { extractable: true }
    })),
    exportKey: vi.fn(async () => new Uint8Array(91).buffer),
    digest: vi.fn(async () => new Uint8Array(32).buffer),
    sign: vi.fn(async (_algorithm: unknown, _key: unknown, data: BufferSource) => {
      const view = data instanceof ArrayBuffer
        ? new Uint8Array(data)
        : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
      signedPayloads.push(new TextDecoder().decode(view));
      return new Uint8Array(64).buffer;
    })
  };
  vi.stubGlobal("crypto", { subtle, randomUUID: () => instanceId, getRandomValues: (value: Uint8Array) => value });
  vi.stubGlobal("ResizeObserver", TestResizeObserver);
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    if (url.endsWith("/widget-context/exchange")) {
      return json({ contextToken: "isolated-frame-context".padEnd(43, "x"), expiresAt: "2099-01-01T00:00:00Z" });
    }
    expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe("isolated-frame-context".padEnd(43, "x"));
    if (url.endsWith("/config")) {
      return json({ siteId, moderationMode: "POST_MODERATION", style: { theme: "LIGHT" } });
    }
    if (url.includes("/pages/comments")) {
      return json({ items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 });
    }
    if (url.endsWith("/privacy/consent-requirements")) {
      return json({
        privacyPolicyVersion: "v1",
        termsVersion: "v1",
        privacyPolicyUrl: "/legal/privacy-policy.html",
        termsUrl: "/legal/terms.html",
        personalDataNoticeUrl: "/legal/personal-data-notice.html",
        dataExportInfoUrl: "/legal/personal-data-notice.html#data-export"
      });
    }
    return json({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  await import("./frame");

  const wrongPort = new TestPort();
  dispatchConnect(wrongPort, { source: {}, origin: "https://site.example" });
  dispatchConnect(wrongPort, { source: window.parent, origin: "https://evil.example", pageUrl: "https://site.example/article" });
  expect(wrongPort.sent).toHaveLength(0);

  const port = new TestPort();
  dispatchConnect(port, { source: window.parent, origin: "https://site.example" });
  await vi.waitFor(() => expect(port.sent).toHaveLength(1));
  expect(port.sent[0]).toMatchObject({ type: "cloud-comment:frame-ready", instanceId });
  expect(document.documentElement.style.fontFamily).toBe('"Host Sans", Arial, sans-serif');
  expect(document.documentElement.style.colorScheme).toBe("light");

  const ticketMessage = {
    type: "cloud-comment:bootstrap-ticket",
    version: WIDGET_PROTOCOL_VERSION,
    instanceId,
    ticket: "T".repeat(43),
    expiresAt: "2099-01-01T00:00:00Z",
    canonicalPageUrl: "https://site.example/article",
    publicKeyFingerprint: "A".repeat(43)
  };
  port.onmessage?.(new MessageEvent("message", { data: ticketMessage }));
  port.onmessage?.(new MessageEvent("message", { data: ticketMessage }));

  await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("/config"))).toBe(true));
  expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/widget-context/exchange"))).toHaveLength(1);
  expect(signedPayloads).toEqual([[
    "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1",
    siteId,
    "https://site.example",
    "https://site.example/article",
    "A".repeat(43),
    "T".repeat(43)
  ].join("\n")]);
  expect(JSON.stringify(port.sent)).not.toMatch(/proof|contextToken|Bearer|password/);
});

it("отклоняет неканонический connect, если authoritative backend URL отличается", async () => {
  const signedPayloads: string[] = [];
  const subtle = {
    generateKey: vi.fn(async () => ({
      privateKey: { extractable: false },
      publicKey: { extractable: true }
    })),
    exportKey: vi.fn(async () => new Uint8Array(91).buffer),
    digest: vi.fn(async () => new Uint8Array(32).buffer),
    sign: vi.fn(async (_algorithm: unknown, _key: unknown, data: BufferSource) => {
      const view = data instanceof ArrayBuffer
        ? new Uint8Array(data)
        : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
      signedPayloads.push(new TextDecoder().decode(view));
      return new Uint8Array(64).buffer;
    })
  };
  vi.stubGlobal("crypto", { subtle, randomUUID: () => instanceId, getRandomValues: (value: Uint8Array) => value });
  vi.stubGlobal("ResizeObserver", TestResizeObserver);
  const fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("fetch", fetchMock);
  await import("./frame");

  const port = new TestPort();
  dispatchConnect(port, {
    source: window.parent,
    origin: "https://example.test",
    pageUrl: "HTTPS://Example.TEST:443"
  });
  await vi.waitFor(() => expect(port.sent).toHaveLength(1));
  port.onmessage?.(new MessageEvent("message", { data: {
    type: "cloud-comment:bootstrap-ticket",
    version: WIDGET_PROTOCOL_VERSION,
    instanceId,
    ticket: "T".repeat(43),
    expiresAt: "2099-01-01T00:00:00Z",
    canonicalPageUrl: "https://example.test/",
    publicKeyFingerprint: "A".repeat(43)
  } }));

  await vi.waitFor(() => expect(port.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:error",
    code: "CONTEXT_EXCHANGE_FAILED"
  })));
  expect(fetchMock).not.toHaveBeenCalled();
  expect(signedPayloads).toEqual([]);
});

it("отклоняет connect, если frame загружен с API origin", async () => {
  const fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("fetch", fetchMock);
  await import("./frame");

  const port = new TestPort();
  dispatchConnect(port, {
    source: window.parent,
    origin: "https://site.example",
    apiOrigin: window.location.origin
  });

  expect(port.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:error",
    instanceId,
    code: "WIDGET_ORIGIN_REQUIRED"
  }));
  expect(port.sent).not.toContainEqual(expect.objectContaining({ type: "cloud-comment:frame-ready" }));
  expect(document.querySelector("[role='alert']")?.textContent)
    .toBe("Нужен отдельный адрес виджета");
  expect(fetchMock).not.toHaveBeenCalled();
});

it.each([
  ["отставании", -5 * 60_000],
  ["опережении", 5 * 60_000]
])("обновляет frame context заранее при %s клиентских часов, сохраняя site bearer", async (_label, clientOffset) => {
  const contextToken = "short-lived-frame-context".padEnd(43, "x");
  const commenterBearer = "site-scoped-commenter-bearer";
  const browserNow = Date.now.bind(Date);
  const serverNow = Math.floor(browserNow() / 1000) * 1000;
  vi.spyOn(Date, "now").mockImplementation(() => browserNow() + clientOffset);
  const subtle = {
    generateKey: vi.fn(async () => ({
      privateKey: { extractable: false },
      publicKey: { extractable: true }
    })),
    exportKey: vi.fn(async () => new Uint8Array(91).buffer),
    digest: vi.fn(async () => new Uint8Array(32).buffer),
    sign: vi.fn(async () => new Uint8Array(64).buffer)
  };
  vi.stubGlobal("crypto", { subtle, randomUUID: () => instanceId, getRandomValues: (value: Uint8Array) => value });
  vi.stubGlobal("ResizeObserver", TestResizeObserver);
  const { createAuthStorageKey, createContextStorageKey } = await import("./frameStorage");
  const authStorageKey = await createAuthStorageKey(siteId, "https://site.example");
  const contextStorageKey = await createContextStorageKey(
    siteId,
    "https://site.example",
    "https://site.example/article"
  );
  window.sessionStorage.setItem(authStorageKey, commenterBearer);
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    if (url.endsWith("/widget-context/exchange")) {
      return json({
        contextToken,
        expiresAt: new Date(serverNow + 30_250).toISOString()
      }, 200, { Date: new Date(serverNow).toUTCString() });
    }
    expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe(contextToken);
    if (url.endsWith("/config")) {
      return json({ siteId, moderationMode: "POST_MODERATION", style: { theme: "LIGHT" } });
    }
    if (url.includes("/pages/comments")) {
      return json({ items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 });
    }
    if (url.endsWith("/privacy/consent-requirements")) {
      return json({
        privacyPolicyVersion: "v1",
        termsVersion: "v1",
        privacyPolicyUrl: "/legal/privacy-policy.html",
        termsUrl: "/legal/terms.html",
        personalDataNoticeUrl: "/legal/personal-data-notice.html",
        dataExportInfoUrl: "/legal/personal-data-notice.html#data-export"
      });
    }
    if (url.endsWith("/auth/me")) {
      expect(new Headers(init?.headers).get("Authorization")).toBe(`Bearer ${commenterBearer}`);
      return json({
        id: "00000000-0000-0000-0000-000000000002",
        email: "commenter@example.test",
        roles: ["COMMENTER"],
        createdAt: "2026-07-13T00:00:00Z",
        updatedAt: "2026-07-13T00:00:00Z"
      });
    }
    return json({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  await import("./frame");

  const port = new TestPort();
  dispatchConnect(port, { source: window.parent, origin: "https://site.example" });
  await vi.waitFor(() => expect(port.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:frame-ready"
  })));
  port.onmessage?.(new MessageEvent("message", { data: {
    type: "cloud-comment:bootstrap-ticket",
    version: WIDGET_PROTOCOL_VERSION,
    instanceId,
    ticket: "T".repeat(43),
    expiresAt: new Date(serverNow + 120_000).toISOString(),
    canonicalPageUrl: "https://site.example/article",
    publicKeyFingerprint: "A".repeat(43)
  } }));

  await vi.waitFor(() => expect(window.sessionStorage.getItem(contextStorageKey)).not.toBeNull());
  await vi.waitFor(() => expect(port.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:context-expired",
    instanceId
  })));
  expect(window.sessionStorage.getItem(contextStorageKey)).toBeNull();
  expect(window.sessionStorage.getItem(authStorageKey)).toBe(commenterBearer);
});

it("dedicated frame после reload переиспользует context и bearer, а destroy сохраняет site bearer", async () => {
  const contextToken = "persisted-frame-context".padEnd(43, "x");
  const commenterBearer = "persisted-commenter-bearer";
  const subtle = {
    digest: vi.fn(async () => new Uint8Array(32).buffer),
    generateKey: vi.fn(async () => {
      throw new Error("Новый bootstrap не должен запускаться при валидном persisted context");
    })
  };
  vi.stubGlobal("crypto", {
    subtle,
    randomUUID: () => instanceId,
    getRandomValues: (value: Uint8Array) => value
  });
  vi.stubGlobal("ResizeObserver", TestResizeObserver);
  const { createAuthStorageKey, createContextStorageKey } = await import("./frameStorage");
  const contextStorageKey = await createContextStorageKey(siteId, "https://site.example", "https://site.example/article");
  const authStorageKey = await createAuthStorageKey(
    siteId,
    "https://site.example"
  );
  window.sessionStorage.setItem(contextStorageKey, JSON.stringify({
    token: contextToken,
    expiresAt: "2099-01-01T00:00:00Z"
  }));
  window.sessionStorage.setItem(authStorageKey, commenterBearer);

  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe(contextToken);
    if (url.endsWith("/config")) {
      return json({ siteId, moderationMode: "POST_MODERATION", style: { theme: "LIGHT" } });
    }
    if (url.includes("/pages/comments")) {
      return json({ items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0 });
    }
    if (url.endsWith("/privacy/consent-requirements")) {
      return json({
        privacyPolicyVersion: "v1",
        termsVersion: "v1",
        privacyPolicyUrl: "/legal/privacy-policy.html",
        termsUrl: "/legal/terms.html",
        personalDataNoticeUrl: "/legal/personal-data-notice.html",
        dataExportInfoUrl: "/legal/personal-data-notice.html#data-export"
      });
    }
    if (url.endsWith("/auth/me")) {
      expect(new Headers(init?.headers).get("Authorization")).toBe(`Bearer ${commenterBearer}`);
      return json({
        id: "00000000-0000-0000-0000-000000000002",
        email: "commenter@example.test",
        roles: ["COMMENTER"],
        createdAt: "2026-07-13T00:00:00Z",
        updatedAt: "2026-07-13T00:00:00Z"
      });
    }
    return json({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);

  await import("./frame");
  const firstPort = new TestPort();
  dispatchConnect(firstPort, { source: window.parent, origin: "https://site.example" });
  await vi.waitFor(() => expect(firstPort.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:context-reused",
    instanceId
  })));
  await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/auth/me"))).toHaveLength(1));
  expect(window.sessionStorage.getItem(contextStorageKey)).not.toBeNull();
  expect(window.sessionStorage.getItem(authStorageKey)).toBe(commenterBearer);
  const firstRoot = document.getElementById("cloud-comment-frame-root")?.shadowRoot;
  firstRoot?.querySelector<HTMLButtonElement>("[data-profile-action='toggle']")?.click();
  const legalLink = firstRoot?.querySelector<HTMLAnchorElement>(".cloud-comment__account-links a");
  expect(legalLink?.target).toBe("_blank");
  expect(legalLink?.rel).toBe("noopener noreferrer");
  const safeClick = new MouseEvent("click", { bubbles: true, cancelable: true, composed: true });
  legalLink?.dispatchEvent(safeClick);
  expect(safeClick.defaultPrevented).toBe(false);
  if (legalLink) {
    legalLink.href = "https://evil.example/legal/privacy-policy.html";
  }
  const unsafeClick = new MouseEvent("click", { bubbles: true, cancelable: true, composed: true });
  legalLink?.dispatchEvent(unsafeClick);
  expect(unsafeClick.defaultPrevented).toBe(true);

  vi.resetModules();
  await import("./frame");
  const reloadedPort = new TestPort();
  dispatchConnect(reloadedPort, { source: window.parent, origin: "https://site.example" });
  await vi.waitFor(() => expect(reloadedPort.sent).toContainEqual(expect.objectContaining({
    type: "cloud-comment:context-reused",
    instanceId
  })));
  await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/auth/me"))).toHaveLength(2));

  expect(fetchMock.mock.calls.some(([url]) => String(url).includes("widget-context/exchange"))).toBe(false);
  expect(subtle.generateKey).not.toHaveBeenCalled();
  reloadedPort.onmessage?.(new MessageEvent("message", { data: {
    type: "cloud-comment:destroy",
    version: WIDGET_PROTOCOL_VERSION,
    instanceId
  } }));
  expect(window.sessionStorage.getItem(contextStorageKey)).toBeNull();
  expect(window.sessionStorage.getItem(authStorageKey)).toBe(commenterBearer);
});

function dispatchConnect(
  port: TestPort,
  overrides: { source: unknown; origin: string; apiOrigin?: string; pageUrl?: string }
): void {
  const event = new MessageEvent("message", {
    data: {
      type: "cloud-comment:connect",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId,
      siteId,
      apiOrigin: overrides.apiOrigin ?? "https://api.example",
      pageUrl: overrides.pageUrl ?? "https://site.example/article",
      initialCommentId: null,
      theme: "light",
      fontFamily: '"Host Sans", Arial, sans-serif'
    },
    origin: overrides.origin,
    ports: [port as unknown as MessagePort]
  });
  Object.defineProperty(event, "source", { value: overrides.source });
  window.dispatchEvent(event);
}

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers }
  });
}

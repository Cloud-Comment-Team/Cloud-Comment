// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { init } from "./index";
import { WIDGET_PROTOCOL_VERSION } from "./protocol";

const siteId = "00000000-0000-0000-0000-000000000001";

class TestPort {
  onmessage: ((event: MessageEvent) => void) | null = null;
  peer: TestPort | null = null;
  closed = false;

  postMessage(data: unknown): void {
    if (!this.closed) {
      this.peer?.onmessage?.(new MessageEvent("message", { data }));
    }
  }

  start(): void {}
  close(): void { this.closed = true; }
}

class TestMessageChannel {
  port1: TestPort;
  port2: TestPort;

  constructor() {
    this.port1 = new TestPort();
    this.port2 = new TestPort();
    this.port1.peer = this.port2;
    this.port2.peer = this.port1;
  }
}

beforeEach(() => {
  document.body.replaceChildren();
  window.localStorage.clear();
  window.sessionStorage.clear();
  vi.stubGlobal("MessageChannel", TestMessageChannel);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("тонкий host loader", () => {
  it("не размещает auth UI/credential на host и связывает bootstrap с canonical pageUrl", async () => {
    localStorage.setItem("cloud-comment.admin.authToken", "admin-secret");
    localStorage.setItem("cloud-comment.widget.authToken", "old-widget-secret");
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      ticket: "T".repeat(43),
      expiresAt: "2099-01-01T00:00:00Z",
      canonicalPageUrl: "https://site.example/article?q=ok",
      publicKeyFingerprint: "F".repeat(43)
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const target = document.createElement("div");
    target.style.fontFamily = '"Brand Sans", Arial, sans-serif';
    document.body.append(target);

    const instance = init({
      siteId,
      frameBaseUrl: "https://widget.example",
      apiBaseUrl: "https://admin.example/api",
      pageUrl: "https://site.example/article?utm_source=mail&q=ok#cloud-comment-00000000-0000-0000-0000-000000000042",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));

    expect(iframe.src).toBe(`https://widget.example/frame.html#site=${siteId}`);
    expect(iframe.src).not.toContain("article");
    expect(iframe.src).not.toContain("token");
    expect(iframe.getAttribute("sandbox")).toBe("allow-scripts allow-same-origin allow-popups");
    expect(iframe.getAttribute("sandbox")).not.toMatch(/allow-(?:downloads|forms|popups-to-escape-sandbox|top-navigation|storage-access)/);
    expect(target.querySelector("form")).toBeNull();
    expect(localStorage.getItem("cloud-comment.admin.authToken")).toBe("admin-secret");
    expect(localStorage.getItem("cloud-comment.widget.authToken")).toBe("old-widget-secret");

    const postCalls = postMessage.mock.calls as unknown as Array<[unknown, string, TestPort[]]>;
    const connect = postCalls[0][0] as {
      instanceId: string;
      apiOrigin: string;
      pageUrl: string;
      initialCommentId: string | null;
      fontFamily: string;
    };
    expect(postCalls[0][1]).toBe("https://widget.example");
    expect(connect.apiOrigin).toBe("https://admin.example");
    expect(connect.pageUrl).toBe("https://site.example/article?q=ok");
    expect(connect.initialCommentId).toBe("00000000-0000-0000-0000-000000000042");
    expect(connect.fontFamily).toBe('"Brand Sans", Arial, sans-serif');
    const framePort = postCalls[0][2][0];
    const hostMessages: unknown[] = [];
    framePort.onmessage = (event) => hostMessages.push(event.data);
    framePort.postMessage({
      type: "cloud-comment:frame-ready",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      publicKey: "A".repeat(122)
    });

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const request = fetchMock.mock.calls[0];
    expect(request[0]).toBe(`https://admin.example/api/public/sites/${siteId}/widget-context/bootstrap`);
    expect(JSON.parse(String(request[1]?.body))).toEqual({
      publicKey: "A".repeat(122),
      pageUrl: "https://site.example/article?q=ok"
    });
    await vi.waitFor(() => expect(hostMessages).toHaveLength(1));
    expect(hostMessages[0]).toMatchObject({
      type: "cloud-comment:bootstrap-ticket",
      ticket: "T".repeat(43),
      canonicalPageUrl: "https://site.example/article?q=ok"
    });
    expect(JSON.stringify(hostMessages)).not.toMatch(/proof|contextToken|Bearer|password/);

    framePort.postMessage({
      type: "cloud-comment:resize",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      height: 777.2
    });
    expect(iframe.style.height).toBe("778px");
    instance.destroy();
    expect(iframe.style.display).toBe("none");
    expect(hostMessages[hostMessages.length - 1]).toMatchObject({
      type: "cloud-comment:destroy",
      instanceId: connect.instanceId
    });
    framePort.postMessage({
      type: "cloud-comment:destroyed",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId
    });
    expect(target.querySelector("iframe")).toBeNull();
  });

  it("передаёт в frame канонический explicit pageUrl без path и default port", () => {
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      frameBaseUrl: "https://widget.example",
      pageUrl: "HTTPS://Example.TEST:443",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));

    const connect = postMessage.mock.calls[0][0] as { pageUrl: string };
    expect(connect.pageUrl).toBe("https://example.test/");
  });

  it("не создаёт iframe, когда widget origin совпадает с API origin", () => {
    const target = document.createElement("div");
    document.body.append(target);
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const instance = init({
      siteId,
      apiBaseUrl: `${window.location.origin}/api`,
      frameBaseUrl: window.location.origin,
      pageUrl: `${window.location.origin}/article#x`,
      target
    });

    expect(target.querySelector("iframe")).toBeNull();
    expect(target.querySelector("[role='alert']")?.textContent)
      .toBe("Нужен отдельный адрес виджета");
    expect(fetchMock).not.toHaveBeenCalled();
    instance.destroy();
    expect(target.childNodes).toHaveLength(0);
  });

  it("запрещает использовать production admin/API origin как frame fallback", () => {
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://team13.st.ifbest.org/api",
      frameBaseUrl: "https://team13.st.ifbest.org",
      pageUrl: "https://card.ifbest.org/article",
      target
    });
    expect(target.querySelector("iframe")).toBeNull();
    expect(target.querySelector("[role='alert']")).not.toBeNull();
  });

  it("держит несколько экземпляров на независимых MessageChannel", () => {
    const first = document.createElement("div");
    const second = document.createElement("div");
    document.body.append(first, second);
    init({ siteId, frameBaseUrl: "https://widget.example", pageUrl: "https://site.example/a", target: first });
    init({ siteId, frameBaseUrl: "https://widget.example", pageUrl: "https://site.example/b", target: second });
    const frames = document.querySelectorAll("iframe");
    expect(frames).toHaveLength(2);
    expect(frames[0].dataset.cloudCommentInstance).not.toBe(frames[1].dataset.cloudCommentInstance);
  });

  it("поздний destroy ack старого instance не удаляет немедленно созданный новый iframe", () => {
    const target = document.createElement("div");
    document.body.append(target);
    const first = init({ siteId, frameBaseUrl: "https://widget.example", pageUrl: "https://site.example/a", target });
    const firstIframe = target.querySelector("iframe")!;
    const firstPostMessage = vi.spyOn(firstIframe.contentWindow!, "postMessage");
    firstIframe.dispatchEvent(new Event("load"));
    const firstCall = firstPostMessage.mock.calls[0] as unknown as [unknown, string, TestPort[]];
    const firstConnect = firstCall[0] as { instanceId: string };
    const firstFramePort = firstCall[2][0];

    first.destroy();
    init({ siteId, frameBaseUrl: "https://widget.example", pageUrl: "https://site.example/b", target });
    const replacement = target.querySelector("iframe")!;
    expect(replacement).not.toBe(firstIframe);
    expect(replacement.src).toBe(`https://widget.example/frame.html#site=${siteId}`);

    firstFramePort.postMessage({
      type: "cloud-comment:destroyed",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: firstConnect.instanceId
    });
    expect(target.querySelector("iframe")).toBe(replacement);
  });

  it("показывает bounded bootstrap error при network 500 и не очищает iframe в пустоту", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>(async () => new Response("{}", { status: 500 })));
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: "https://site.example/article",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));
    const calls = postMessage.mock.calls as unknown as Array<[unknown, string, TestPort[]]>;
    const connect = calls[0][0] as { instanceId: string };
    const framePort = calls[0][2][0];
    const messages: unknown[] = [];
    framePort.onmessage = (event) => messages.push(event.data);
    framePort.postMessage({
      type: "cloud-comment:frame-ready",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      publicKey: "A".repeat(122)
    });

    await vi.waitFor(() => expect(messages).toContainEqual(expect.objectContaining({
      type: "cloud-comment:bootstrap-error",
      code: "BOOTSTRAP_FAILED"
    })));
    expect(target.querySelector("iframe")).toBe(iframe);
  });

  it("завершает handshake timeout доступным alert", async () => {
    vi.useFakeTimers();
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: `${window.location.origin}/article`,
      target
    });
    const iframe = target.querySelector("iframe")!;
    vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));

    await vi.advanceTimersByTimeAsync(8000);

    expect(target.querySelector("iframe")).toBeNull();
    expect(target.querySelector("[role='alert']")?.textContent).toBe("Комментарии недоступны");
  });

  it("не запускает новый bootstrap после подтверждённого reuse контекста", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: "https://site.example/article",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));
    const calls = postMessage.mock.calls as unknown as Array<[unknown, string, TestPort[]]>;
    const connect = calls[0][0] as { instanceId: string };
    const framePort = calls[0][2][0];

    framePort.postMessage({
      type: "cloud-comment:context-reused",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId
    });
    await vi.advanceTimersByTimeAsync(10_000);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(target.querySelector("iframe")).toBe(iframe);
    expect(target.querySelector("[role='alert']")).toBeNull();
  });

  it("перезагружает только свой iframe после сигнала об истечении context", () => {
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: "https://site.example/article",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));
    const call = postMessage.mock.calls[0] as unknown as [unknown, string, TestPort[]];
    const connect = call[0] as { instanceId: string };
    const framePort = call[2][0];
    const setAttribute = vi.spyOn(iframe, "setAttribute");

    framePort.postMessage({
      type: "cloud-comment:context-expired",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId
    });

    expect(setAttribute).toHaveBeenCalledWith("src", iframe.src);
    expect(target.querySelector("iframe")).toBe(iframe);
  });

  it("прерывает зависший bootstrap по таймауту и сообщает frame ограниченную ошибку", async () => {
    vi.useFakeTimers();
    let requestSignal: AbortSignal | undefined;
    const fetchMock = vi.fn<typeof fetch>(async (_input, init) => new Promise<Response>((_resolve, reject) => {
      requestSignal = init?.signal ?? undefined;
      requestSignal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
    }));
    vi.stubGlobal("fetch", fetchMock);
    const target = document.createElement("div");
    document.body.append(target);
    init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: "https://site.example/article",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));
    const calls = postMessage.mock.calls as unknown as Array<[unknown, string, TestPort[]]>;
    const connect = calls[0][0] as { instanceId: string };
    const framePort = calls[0][2][0];
    const messages: unknown[] = [];
    framePort.onmessage = (event) => messages.push(event.data);
    framePort.postMessage({
      type: "cloud-comment:frame-ready",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      publicKey: "A".repeat(122)
    });
    await Promise.resolve();

    expect(fetchMock).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(requestSignal?.aborted).toBe(true);
    expect(messages).toContainEqual(expect.objectContaining({
      type: "cloud-comment:bootstrap-error",
      code: "BOOTSTRAP_TIMEOUT"
    }));
    expect(target.querySelector("iframe")).toBe(iframe);
  });

  it("abort'ит pending bootstrap при destroy и не отправляет позднюю ошибку", async () => {
    let requestSignal: AbortSignal | undefined;
    const fetchMock = vi.fn<typeof fetch>(async (_input, init) => new Promise<Response>((_resolve, reject) => {
      requestSignal = init?.signal ?? undefined;
      requestSignal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
    }));
    vi.stubGlobal("fetch", fetchMock);
    const target = document.createElement("div");
    document.body.append(target);
    const instance = init({
      siteId,
      apiBaseUrl: "https://admin.example/api",
      frameBaseUrl: "https://widget.example",
      pageUrl: "https://site.example/article",
      target
    });
    const iframe = target.querySelector("iframe")!;
    const postMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
    iframe.dispatchEvent(new Event("load"));
    const calls = postMessage.mock.calls as unknown as Array<[unknown, string, TestPort[]]>;
    const connect = calls[0][0] as { instanceId: string };
    const framePort = calls[0][2][0];
    const messages: unknown[] = [];
    framePort.onmessage = (event) => messages.push(event.data);
    framePort.postMessage({
      type: "cloud-comment:frame-ready",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId,
      publicKey: "A".repeat(122)
    });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());

    instance.destroy();
    await Promise.resolve();

    expect(requestSignal?.aborted).toBe(true);
    expect(messages.filter((message) => JSON.stringify(message).includes("bootstrap-error"))).toHaveLength(0);
    expect(messages).toContainEqual(expect.objectContaining({ type: "cloud-comment:destroy" }));
    framePort.postMessage({
      type: "cloud-comment:destroyed",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: connect.instanceId
    });
  });
});

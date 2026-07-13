import { afterEach, describe, expect, it, vi } from "vitest";

import { createWidgetApiClient } from "./api";
import { WIDGET_CONTEXT_HEADER, WIDGET_PAGE_URL_HEADER } from "./protocol";

const siteId = "00000000-0000-0000-0000-000000000001";
const pageUrl = "https://site.example/article";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("изолированный transport виджета", () => {
  it("не создаёт публичный API transport без widget context", () => {
    expect(() => createWidgetApiClient("https://widget.example/api", siteId, pageUrl, ""))
      .toThrow("CloudComment frame context is required");
  });

  it("передаёт context на каждом site API и фиксированный pageUrl на comment API", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      items: [], page: 1, pageSize: 20, totalItems: 0, totalPages: 0
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const api = createWidgetApiClient("https://widget.example/api", siteId, pageUrl, "frame-context");

    await api.listComments("PINNED_FIRST", 1, 20, "commenter-bearer");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain(`/public/sites/${siteId}/pages/comments`);
    expect(init).toMatchObject({ credentials: "omit", cache: "no-store", mode: "cors" });
    expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe("frame-context");
    expect(new Headers(init?.headers).get(WIDGET_PAGE_URL_HEADER)).toBe(pageUrl);
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer commenter-bearer");
  });

  it("получает consent только через site-scoped alias с context", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      privacyPolicyVersion: "v1",
      termsVersion: "v1",
      privacyPolicyUrl: "/legal/privacy-policy.html",
      termsUrl: "/legal/terms.html",
      personalDataNoticeUrl: "/legal/personal-data-notice.html",
      dataExportInfoUrl: "/legal/personal-data-notice.html#data-export"
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const api = createWidgetApiClient("https://widget.example/api", siteId, pageUrl, "frame-context");

    await api.getConsentRequirements();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`https://widget.example/api/public/sites/${siteId}/privacy/consent-requirements`);
    expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe("frame-context");
  });

  it("выполняет account-операции только через site-scoped context", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      requestId: "00000000-0000-0000-0000-000000000002",
      expiresAt: "2026-07-14T00:00:00Z"
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const api = createWidgetApiClient("https://widget.example/api", siteId, pageUrl, "frame-context");

    await api.requestAccountDeletion("commenter-bearer");
    await api.exportPersonalData("commenter-bearer");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `https://widget.example/api/public/sites/${siteId}/account/deletion-requests`,
      `https://widget.example/api/public/sites/${siteId}/account/personal-data`
    ]);
    for (const [, init] of fetchMock.mock.calls) {
      expect(new Headers(init?.headers).get(WIDGET_CONTEXT_HEADER)).toBe("frame-context");
      expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer commenter-bearer");
      expect(new Headers(init?.headers).get(WIDGET_PAGE_URL_HEADER)).toBeNull();
    }
  });
});

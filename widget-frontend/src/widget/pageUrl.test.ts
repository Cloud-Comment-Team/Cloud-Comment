import { describe, expect, it } from "vitest";

import { resolvePageUrl } from "./pageUrl";

describe("pageUrl виджета", () => {
  it("удаляет fragment у URL текущей страницы и сохраняет query", () => {
    expect(resolvePageUrl(undefined, "https://site.example/article?tab=comments#reply-42"))
      .toBe("https://site.example/article?tab=comments");
  });

  it("удаляет fragment у явно переданного pageUrl", () => {
    expect(resolvePageUrl(
      "https://site.example/article?mode=compact#comments",
      "https://fallback.example/"
    )).toBe("https://site.example/article?mode=compact");
  });

  it("не принимает закодированную решётку за fragment", () => {
    expect(resolvePageUrl(
      "https://site.example/search?q=%23comments",
      "https://fallback.example/"
    )).toBe("https://site.example/search?q=%23comments");
  });
});

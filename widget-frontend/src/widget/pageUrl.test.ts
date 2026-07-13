import { describe, expect, it } from "vitest";

import { resolveInitialCommentId, resolvePageUrl } from "./pageUrl";

describe("pageUrl виджета", () => {
  it("удаляет fragment и приватные параметры у URL текущей страницы", () => {
    expect(resolvePageUrl(undefined, "https://site.example/article?tab=comments&utm_source=mail#reply-42"))
      .toBe("https://site.example/article?tab=comments");
  });

  it("применяет ту же политику к явно переданному pageUrl", () => {
    expect(resolvePageUrl(
      "https://site.example/article?mode=compact&SESSION_ID=secret#comments",
      "https://fallback.example/"
    )).toBe("https://site.example/article?mode=compact");
  });

  it("не принимает закодированную решётку за fragment", () => {
    expect(resolvePageUrl(
      "https://site.example/search?q=%23comments",
      "https://fallback.example/"
    )).toBe("https://site.example/search?q=%23comments");
  });

  it("распознаёт регистр и percent-encoded имена tracking и sensitive параметров", () => {
    expect(resolvePageUrl(
      "https://site.example/article?%75tm_source=mail&FBCLID=click&api%5Fkey=secret&csrf%5Ftoken=secret&tab=comments",
      "https://fallback.example/"
    )).toBe("https://site.example/article?tab=comments");
  });

  it("сохраняет raw encoding, порядок и повторы функциональных параметров", () => {
    expect(resolvePageUrl(
      "https://site.example/search?q=hello+world&&q=hello%20world&next=%2Ffeed&anchor=%23item&utm_medium=email&tab=all&",
      "https://fallback.example/"
    )).toBe("https://site.example/search?q=hello+world&&q=hello%20world&next=%2Ffeed&anchor=%23item&tab=all&");
  });

  it("удаляет пустой query вместе с вопросительным знаком", () => {
    expect(resolvePageUrl("https://site.example/article?#comments", "https://fallback.example/"))
      .toBe("https://site.example/article");
  });

  it("канонизирует origin как backend: регистр, default port и корневой path", () => {
    expect(resolvePageUrl("HTTPS://Example.TEST:443", "https://fallback.example/"))
      .toBe("https://example.test/");
    expect(resolvePageUrl("http://Example.TEST:80?tab=comments", "https://fallback.example/"))
      .toBe("http://example.test/?tab=comments");
    expect(resolvePageUrl("https://Example.TEST:8443", "https://fallback.example/"))
      .toBe("https://example.test:8443/");
  });

  it("удаляет только зафиксированные tracking и sensitive имена", () => {
    expect(resolvePageUrl(
      "https://site.example/article?campaign=summer&tokenize=true&custom_token=secret&mc_eid=mail&gbraid=a&wbraid=b&srsltid=c&gad_source=d&gad_campaignid=e&client_secret=secret&secret=value&signature=s&sig=s&otp=1&jsessionid=3&phpsessid=4&x-amz-signature=aws&x-goog-credential=goog&x-amazing=keep&filter=recent",
      "https://fallback.example/"
    )).toBe("https://site.example/article?campaign=summer&tokenize=true&x-amazing=keep&filter=recent");
  });

  it("сохраняет неоднозначные functional параметры code, ticket и sid", () => {
    expect(resolvePageUrl(
      "https://site.example/callback?code=flow-a&ticket=thread-a&sid=section-a",
      "https://fallback.example/"
    )).toBe("https://site.example/callback?code=flow-a&ticket=thread-a&sid=section-a");
    expect(resolvePageUrl(
      "https://site.example/callback?code=flow-b&ticket=thread-b&sid=section-b",
      "https://fallback.example/"
    )).not.toBe("https://site.example/callback?code=flow-a&ticket=thread-a&sid=section-a");
  });

  it("экранирует malformed percent, не меняя valid escapes", () => {
    expect(resolvePageUrl("https://site.example/search?q=100%", "https://fallback.example/"))
      .toBe("https://site.example/search?q=100%25");
    expect(resolvePageUrl("https://site.example/search?%ZZ=x", "https://fallback.example/"))
      .toBe("https://site.example/search?%25ZZ=x");
    expect(resolvePageUrl("https://site.example/search?next=%2Ffeed", "https://fallback.example/"))
      .toBe("https://site.example/search?next=%2Ffeed");
  });

  it("извлекает только безопасный UUID permalink из fragment", () => {
    expect(resolveInitialCommentId(
      "https://site.example/article#cloud-comment-00000000-0000-0000-0000-000000000042",
      "https://fallback.example/"
    )).toBe("00000000-0000-0000-0000-000000000042");
    expect(resolveInitialCommentId("https://site.example/article#comments", "https://fallback.example/"))
      .toBeNull();
  });
});

// @vitest-environment jsdom

import { expect, it } from "vitest";

import { getFrameBaseUrl } from "./config";

it("сохраняет Pages base path и удаляет query, fragment и завершающий slash", () => {
  expect(getFrameBaseUrl(
    "https://api.example/api",
    undefined,
    "https://cloud-comment-team.github.io/Cloud-Comment/?source=deploy#frame"
  )).toBe("https://cloud-comment-team.github.io/Cloud-Comment");
});

it("отклоняет frame URL с credentials", () => {
  expect(() => getFrameBaseUrl(
    "https://api.example/api",
    "https://user:password@widget.example/frame"
  )).toThrow("must not contain credentials");
});

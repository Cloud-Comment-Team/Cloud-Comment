import { afterEach, describe, expect, it, vi } from "vitest";

import { contextRefreshDelay } from "./frameTime";

describe("contextRefreshDelay", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it.each([-5 * 60_000, 5 * 60_000])(
    "использует серверное время при смещении клиентских часов на %i мс",
    (clientOffset) => {
      const serverNow = Date.parse("2026-07-13T10:00:00Z");
      vi.useFakeTimers();
      vi.setSystemTime(serverNow + clientOffset);

      expect(contextRefreshDelay(
        new Date(serverNow + 30_250).toISOString(),
        new Date(serverNow).toUTCString()
      )).toBe(250);
    }
  );

  it("сохраняет тридцатисекундный запас при отстающих часах и отсутствии Date", () => {
    const serverNow = Date.parse("2026-07-13T10:00:00Z");
    vi.useFakeTimers();
    vi.setSystemTime(serverNow - 5 * 60_000);

    expect(contextRefreshDelay(
      new Date(serverNow + 2 * 60 * 60_000).toISOString(),
      null
    )).toBe(7_170_000);
  });
});

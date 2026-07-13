const CONTEXT_REBOOT_MARGIN_MS = 30_000;
const MAX_CONTEXT_REFRESH_DELAY_MS = 7_170_000;

export function contextRefreshDelay(expiresAt: string, serverDate: string | null): number {
  const serverNowMillis = Date.parse(serverDate ?? "");
  return Math.min(MAX_CONTEXT_REFRESH_DELAY_MS, Math.max(
    0,
    Date.parse(expiresAt)
      - (Number.isNaN(serverNowMillis) ? Date.now() : serverNowMillis)
      - CONTEXT_REBOOT_MARGIN_MS
  ));
}

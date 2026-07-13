// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createAuthStorageKey,
  createContextStorageKey,
  readStoredContextEnvelope,
  removePersistedValue,
  writePersistedContext
} from "./frameStorage";

const siteId = "00000000-0000-0000-0000-000000000001";
const parentOrigin = "https://site.example";
const pageA = "https://site.example/article-a";

beforeEach(() => window.sessionStorage.clear());
afterEach(() => vi.restoreAllMocks());

describe("изолированное хранилище widget frame", () => {
  it("делит context по страницам, а bearer — только по site и parent origin", async () => {
    const contextKeyA = await createContextStorageKey(siteId, parentOrigin, pageA);
    const contextKeyB = await createContextStorageKey(siteId, parentOrigin, `${parentOrigin}/article-b`);
    const authKeyA = await createAuthStorageKey(siteId, parentOrigin);
    const authKeySameScope = await createAuthStorageKey(siteId, parentOrigin);
    const authKeyOtherSite = await createAuthStorageKey("00000000-0000-0000-0000-000000000002", parentOrigin);
    const authKeyOtherOrigin = await createAuthStorageKey(siteId, "https://other.example");

    expect(contextKeyA).not.toBe(contextKeyB);
    expect(authKeyA).toBe(authKeySameScope);
    expect(authKeyA).not.toBe(authKeyOtherSite);
    expect(authKeyA).not.toBe(authKeyOtherOrigin);
    expect(contextKeyA).not.toContain(pageA);
    expect(authKeyA).not.toContain(parentOrigin);
  });

  it("сохраняет строгий envelope без решений по локальным часам и удаляет его явно", async () => {
    const storageKey = await createContextStorageKey(siteId, parentOrigin, pageA);
    const value = { token: "C".repeat(43), expiresAt: "2099-01-01T00:00:00Z" };

    expect(writePersistedContext(storageKey, value)).toBe(true);
    expect(readStoredContextEnvelope(storageKey)).toEqual(value);
    removePersistedValue(storageKey);
    expect(readStoredContextEnvelope(storageKey)).toBeNull();
  });

  it("оставляет проверку срока backend, но не принимает расширенный посторонними полями envelope", async () => {
    const storageKey = await createContextStorageKey(siteId, parentOrigin, pageA);
    window.sessionStorage.setItem(storageKey, JSON.stringify({
      token: "C".repeat(43),
      expiresAt: "2020-01-01T00:00:00Z"
    }));
    expect(readStoredContextEnvelope(storageKey)).toEqual({
      token: "C".repeat(43),
      expiresAt: "2020-01-01T00:00:00Z"
    });

    window.sessionStorage.setItem(storageKey, JSON.stringify({
      token: "C".repeat(43),
      expiresAt: "2099-01-01T00:00:00Z",
      attackerControlled: true
    }));
    expect(readStoredContextEnvelope(storageKey)).toBeNull();
  });

  it("остаётся memory-only, когда политика браузера блокирует sessionStorage", async () => {
    const storageKey = await createContextStorageKey(siteId, parentOrigin, pageA);
    vi.spyOn(window, "sessionStorage", "get").mockImplementation(() => {
      throw new DOMException("Storage blocked", "SecurityError");
    });

    expect(readStoredContextEnvelope(storageKey)).toBeNull();
    expect(writePersistedContext(storageKey, {
      token: "C".repeat(43),
      expiresAt: "2099-01-01T00:00:00Z"
    })).toBe(false);
  });
});

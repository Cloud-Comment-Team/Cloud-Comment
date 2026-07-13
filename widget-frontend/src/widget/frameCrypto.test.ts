import { describe, expect, it } from "vitest";

import { generateWidgetFrameKey, signWidgetBootstrap } from "./frameCrypto";

describe("proof bootstrap-контекста", () => {
  it("создаёт неэкспортируемый private key и raw P1363 ECDSA proof над точными canonical bytes", async () => {
    const key = await generateWidgetFrameKey();
    expect(key.privateKey.extractable).toBe(false);
    expect(key.publicKey).not.toContain("=");

    const canonical = [
      "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1",
      "00000000-0000-0000-0000-000000000001",
      "https://site.example",
      "https://site.example/article",
      key.fingerprint,
      "one-time-ticket"
    ].join("\n");
    const proof = await signWidgetBootstrap(
      key.privateKey,
      "00000000-0000-0000-0000-000000000001",
      "https://site.example",
      "https://site.example/article",
      key.fingerprint,
      "one-time-ticket"
    );
    const signature = fromBase64Url(proof);
    expect(signature).toHaveLength(64);

    const publicKey = await crypto.subtle.importKey(
      "spki",
      key.publicKeySpki.buffer as ArrayBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"]
    );
    await expect(crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      publicKey,
      signature.buffer as ArrayBuffer,
      new TextEncoder().encode(canonical).buffer as ArrayBuffer
    )).resolves.toBe(true);
  });
});

function fromBase64Url(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
}

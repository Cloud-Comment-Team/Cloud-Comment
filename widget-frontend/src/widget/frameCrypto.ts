const BOOTSTRAP_PREFIX = "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1";

export type WidgetFrameKey = {
  privateKey: CryptoKey;
  publicKeySpki: Uint8Array;
  publicKey: string;
  fingerprint: string;
};

export async function generateWidgetFrameKey(): Promise<WidgetFrameKey> {
  const keyPair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign", "verify"]
  );
  const publicKeySpki = new Uint8Array(await crypto.subtle.exportKey("spki", keyPair.publicKey));
  const fingerprintBytes = new Uint8Array(await crypto.subtle.digest("SHA-256", publicKeySpki));
  return {
    privateKey: keyPair.privateKey,
    publicKeySpki,
    publicKey: toBase64Url(publicKeySpki),
    fingerprint: toBase64Url(fingerprintBytes)
  };
}

export async function signWidgetBootstrap(
  privateKey: CryptoKey,
  siteId: string,
  parentOrigin: string,
  canonicalPageUrl: string,
  publicKeyFingerprint: string,
  ticket: string
): Promise<string> {
  const canonical = [
    BOOTSTRAP_PREFIX,
    siteId,
    parentOrigin,
    canonicalPageUrl,
    publicKeyFingerprint,
    ticket
  ].join("\n");
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    privateKey,
    new TextEncoder().encode(canonical)
  );
  return toBase64Url(new Uint8Array(signature));
}

export function toBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

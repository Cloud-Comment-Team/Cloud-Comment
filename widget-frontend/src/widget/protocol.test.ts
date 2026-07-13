import { describe, expect, it } from "vitest";

import { isFramePortMessage, isWidgetConnectMessage, WIDGET_PROTOCOL_VERSION } from "./protocol";

const validConnect = {
  type: "cloud-comment:connect",
  version: WIDGET_PROTOCOL_VERSION,
  instanceId: "00000000-0000-0000-0000-000000000099",
  siteId: "00000000-0000-0000-0000-000000000001",
  apiOrigin: "https://api.example",
  pageUrl: "https://site.example/article",
  theme: "auto",
  fontFamily: '"Brand Sans", Arial, sans-serif'
};

describe("strict widget protocol", () => {
  it("принимает только ограниченный fontFamily snapshot без управляющих символов", () => {
    expect(isWidgetConnectMessage(validConnect)).toBe(true);
    expect(isWidgetConnectMessage({ ...validConnect, fontFamily: "A".repeat(257) })).toBe(false);
    expect(isWidgetConnectMessage({ ...validConnect, fontFamily: "Arial\nbody { display:none }" })).toBe(false);
  });

  it("отвергает расширение connect произвольными presentation-полями", () => {
    expect(isWidgetConnectMessage({ ...validConnect, cssText: "display:none" })).toBe(false);
  });

  it("принимает только точный HTTP(S) apiOrigin", () => {
    expect(isWidgetConnectMessage({ ...validConnect, apiOrigin: "https://API.example" })).toBe(false);
    expect(isWidgetConnectMessage({ ...validConnect, apiOrigin: "https://api.example/" })).toBe(false);
    expect(isWidgetConnectMessage({ ...validConnect, apiOrigin: "null" })).toBe(false);
  });

  it("принимает строгий сигнал истечения context", () => {
    const message = {
      type: "cloud-comment:context-expired",
      version: WIDGET_PROTOCOL_VERSION,
      instanceId: validConnect.instanceId
    };
    expect(isFramePortMessage(message, validConnect.instanceId)).toBe(true);
    expect(isFramePortMessage({ ...message, contextToken: "secret" }, validConnect.instanceId)).toBe(false);
  });
});

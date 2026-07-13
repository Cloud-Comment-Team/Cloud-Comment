const TRACKING_PARAMETER_NAMES = new Set([
  "fbclid",
  "gclid",
  "dclid",
  "msclkid",
  "yclid",
  "twclid",
  "ttclid",
  "igshid",
  "li_fat_id",
  "_ga",
  "_gl",
  "mc_cid",
  "mc_eid",
  "gbraid",
  "wbraid",
  "srsltid",
  "gad_source",
  "gad_campaignid"
]);

const SENSITIVE_PARAMETER_NAMES = new Set([
  "access_token",
  "id_token",
  "refresh_token",
  "auth_token",
  "authorization",
  "api_key",
  "apikey",
  "jwt",
  "password",
  "session",
  "session_id",
  "sessionid",
  "token",
  "client_secret",
  "secret",
  "signature",
  "sig",
  "otp",
  "jsessionid",
  "phpsessid"
]);

const SENSITIVE_PARAMETER_PREFIXES = ["x-amz-", "x-goog-"];

function escapeInvalidPercents(value: string): string {
  return value.replace(/%(?![0-9a-f]{2})/gi, "%25");
}

function decodeParameterName(rawName: string): string {
  try {
    return decodeURIComponent(rawName);
  } catch {
    return rawName.replace(/%([0-9a-f]{2})/gi, (_, hex: string) =>
      String.fromCharCode(Number.parseInt(hex, 16))
    );
  }
}

function isPrivateParameter(rawParameter: string): boolean {
  const separator = rawParameter.indexOf("=");
  const rawName = separator === -1 ? rawParameter : rawParameter.slice(0, separator);
  const name = decodeParameterName(rawName).toLowerCase();

  return name.startsWith("utm_")
    || TRACKING_PARAMETER_NAMES.has(name)
    || SENSITIVE_PARAMETER_NAMES.has(name)
    || SENSITIVE_PARAMETER_PREFIXES.some((prefix) => name.startsWith(prefix))
    || name.endsWith("_token");
}

export function resolvePageUrl(explicitPageUrl: string | undefined, currentPageUrl: string): string {
  const pageUrl = explicitPageUrl ?? currentPageUrl;
  const fragmentStart = pageUrl.indexOf("#");
  const withoutFragment = fragmentStart === -1 ? pageUrl : pageUrl.slice(0, fragmentStart);
  const percentSafeUrl = escapeInvalidPercents(withoutFragment);
  const queryStart = percentSafeUrl.indexOf("?");
  if (queryStart === -1) {
    return percentSafeUrl;
  }

  const rawQuery = percentSafeUrl.slice(queryStart + 1);
  if (rawQuery.length === 0) {
    return percentSafeUrl.slice(0, queryStart);
  }
  const canonicalQuery = rawQuery
    .split("&")
    .filter((parameter) => parameter.length === 0 || !isPrivateParameter(parameter))
    .join("&");

  return canonicalQuery.length === 0
    ? percentSafeUrl.slice(0, queryStart)
    : `${percentSafeUrl.slice(0, queryStart)}?${canonicalQuery}`;
}

const PRIVATE_PARAMETER_PATTERN = /^(?:utm_|x-amz-|x-goog-|(?:fbclid|gclid|dclid|msclkid|yclid|twclid|ttclid|igshid|li_fat_id|_ga|_gl|mc_cid|mc_eid|gbraid|wbraid|srsltid|gad_source|gad_campaignid|authorization|api_key|apikey|jwt|password|session|session_id|sessionid|token|client_secret|secret|signature|sig|otp|jsessionid|phpsessid)$|.*_token$)/i;
const MAX_PAGE_URL_LENGTH = 2048;
const MAX_ORIGIN_LENGTH = 255;
const VALID_PROTOCOLS = new Set(["http:", "https:"]);
const DOMAIN_PATTERN = /^(?:localhost|(?=.{1,255}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63})$/;
const IPV4_PATTERN = /^(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
const COMMENT_FRAGMENT_PATTERN = /^#cloud-comment-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

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

  return PRIVATE_PARAMETER_PATTERN.test(name);
}

export function resolvePageUrl(explicitPageUrl: string | undefined, currentPageUrl: string): string {
  const pageUrl = (explicitPageUrl ?? currentPageUrl).trim();
  const fragmentStart = pageUrl.indexOf("#");
  const withoutFragment = fragmentStart === -1 ? pageUrl : pageUrl.slice(0, fragmentStart);
  const percentSafeUrl = escapeInvalidPercents(withoutFragment);
  if (!withoutFragment
    || pageUrl.length > MAX_PAGE_URL_LENGTH
    || percentSafeUrl.length > MAX_PAGE_URL_LENGTH
    || /\s/u.test(withoutFragment)) {
    throw new Error("Invalid CloudComment page URL");
  }

  let parsed: URL;
  try {
    parsed = new URL(percentSafeUrl);
  } catch {
    throw new Error("Invalid CloudComment page URL");
  }
  if (!VALID_PROTOCOLS.has(parsed.protocol)
    || parsed.username
    || parsed.password
    || parsed.origin.length > MAX_ORIGIN_LENGTH
    || (!DOMAIN_PATTERN.test(parsed.hostname) && !IPV4_PATTERN.test(parsed.hostname))) {
    throw new Error("Invalid CloudComment page URL");
  }

  const queryStart = percentSafeUrl.indexOf("?");
  const pathEnd = queryStart === -1 ? percentSafeUrl.length : queryStart;
  const authorityStart = percentSafeUrl.indexOf("://") + 3;
  const pathStart = percentSafeUrl.indexOf("/", authorityStart);
  const rawPath = pathStart === -1 || pathStart >= pathEnd
    ? "/"
    : percentSafeUrl.slice(pathStart, pathEnd);
  const canonicalBase = `${parsed.origin}${rawPath}`;
  if (queryStart === -1) {
    return canonicalBase;
  }

  const rawQuery = percentSafeUrl.slice(queryStart + 1);
  if (rawQuery.length === 0) {
    return canonicalBase;
  }
  const canonicalQuery = rawQuery
    .split("&")
    .filter((parameter) => parameter.length === 0 || !isPrivateParameter(parameter))
    .join("&");

  return canonicalQuery.length === 0
    ? canonicalBase
    : `${canonicalBase}?${canonicalQuery}`;
}

export function resolveInitialCommentId(explicitPageUrl: string | undefined, currentPageUrl: string): string | null {
  try {
    return new URL(explicitPageUrl ?? currentPageUrl).hash.match(COMMENT_FRAGMENT_PATTERN)?.[1]?.toLowerCase() ?? null;
  } catch {
    return null;
  }
}

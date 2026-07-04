interface ParsedOriginsInput {
  origins: string[]
  error: string | null
}

const DOMAIN_PATTERN = /^(localhost|(?=.{1,255}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63})$/
const IPV4_PATTERN = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/
const WHITESPACE_PATTERN = /\s/

export function parseAllowedOriginsInput(value: string): ParsedOriginsInput {
  const rawOrigins = value
    .split(/[\n,]/)
    .map((origin) => origin.trim())
    .filter(Boolean)

  if (rawOrigins.length === 0) {
    return { origins: [], error: 'Укажите хотя бы один allowed origin.' }
  }

  const normalizedOrigins: string[] = []
  for (const origin of rawOrigins) {
    const normalizedOrigin = normalizeOrigin(origin)
    if (!normalizedOrigin) {
      return {
        origins: [],
        error: `Allowed origin должен быть http/https origin без пути: ${origin}`,
      }
    }
    normalizedOrigins.push(normalizedOrigin)
  }

  return { origins: [...new Set(normalizedOrigins)], error: null }
}

export function formatOriginsInput(origins: string[]): string {
  return origins.join('\n')
}

export function normalizeDomainInput(value: string): string | null {
  const domain = value.trim().toLowerCase()
  if (
    !domain ||
    domain.length > 255 ||
    domain.includes('://') ||
    /[/?#]/.test(domain) ||
    WHITESPACE_PATTERN.test(domain) ||
    !DOMAIN_PATTERN.test(domain)
  ) {
    return null
  }
  return domain
}

function normalizeOrigin(value: string): string | null {
  const trimmed = value.trim()
  if (trimmed.length > 255 || WHITESPACE_PATTERN.test(trimmed) || !/^https?:\/\/[^/?#]+$/i.test(trimmed)) {
    return null
  }

  try {
    const url = new URL(trimmed)
    if (!['http:', 'https:'].includes(url.protocol)) {
      return null
    }
    if (!isValidOriginHost(url.hostname)) {
      return null
    }
    if (url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
      return null
    }
    return `${url.protocol}//${url.host.toLowerCase()}`
  } catch {
    return null
  }
}

function isValidOriginHost(host: string): boolean {
  const normalizedHost = host.toLowerCase()
  return DOMAIN_PATTERN.test(normalizedHost) || IPV4_PATTERN.test(normalizedHost)
}

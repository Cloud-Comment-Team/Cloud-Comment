interface ParsedOriginsInput {
  origins: string[]
  error: string | null
}

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
  if (!domain || /[\s/?#:@]/.test(domain)) {
    return null
  }
  return domain
}

function normalizeOrigin(value: string): string | null {
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol)) {
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

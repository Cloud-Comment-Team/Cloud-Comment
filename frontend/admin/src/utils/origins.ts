export function parseOriginsInput(value: string): string[] {
  const origins = value
    .split(/[\n,]/)
    .map((origin) => origin.trim())
    .filter(Boolean)

  return [...new Set(origins)]
}

export function formatOriginsInput(origins: string[]): string {
  return origins.join('\n')
}

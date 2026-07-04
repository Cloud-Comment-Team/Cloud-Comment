export type ElementBounds = {
  top: number
  left: number
  width: number
  height: number
  viewportHeight?: number
}

export type NavigationIntent = {
  route: string
  fromPath: string
  rect: ElementBounds
}

export type RevealMetrics = {
  x: number
  y: number
  radius: number
}

export const FLOW_INTENT_TTL_MS = 900

const NAVIGATION_ORDER = ['/', '/sites', '/moderation', '/account']

export function navigationFamily(pathname: string) {
  if (pathname === '/') {
    return '/'
  }

  if (pathname.startsWith('/sites')) {
    return '/sites'
  }

  if (pathname.startsWith('/moderation') || pathname.startsWith('/comments')) {
    return '/moderation'
  }

  if (pathname.startsWith('/account')) {
    return '/account'
  }

  return pathname
}

export function routeDirection(fromRoute: string, toRoute: string) {
  const fromIndex = NAVIGATION_ORDER.indexOf(navigationFamily(fromRoute))
  const toIndex = NAVIGATION_ORDER.indexOf(navigationFamily(toRoute))

  if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) {
    return 0
  }

  return toIndex > fromIndex ? 1 : -1
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

export function getRevealMetrics(intent: NavigationIntent | null, bounds: ElementBounds | null, direction: number): RevealMetrics {
  const width = bounds?.width ?? 1200
  const height = bounds?.height ?? 720

  if (!intent || !bounds) {
    const x = direction < 0 ? width : 0
    const y = 68

    return {
      x,
      y,
      radius: Math.hypot(Math.max(x, width - x), Math.max(y, height - y)) + 120,
    }
  }

  const sourceX = intent.rect.left + intent.rect.width / 2 - bounds.left
  const sourceY = intent.rect.top + intent.rect.height / 2 - bounds.top
  const x = clamp(sourceX, -40, width + 40)
  const y = clamp(sourceY, 24, Math.max(24, height - 24))

  return {
    x,
    y,
    radius: Math.hypot(Math.max(x, width - x), Math.max(y, height - y)) + 140,
  }
}

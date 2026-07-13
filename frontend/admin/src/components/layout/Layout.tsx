import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { ScrollRestoration, useLocation, useOutlet } from 'react-router-dom'
import { AnimatePresence, useReducedMotion } from 'framer-motion'
import { Toaster } from 'react-hot-toast'

import Header from './Header'
import Sidebar from './Sidebar'
import { RealtimeNotifications } from '../notifications/RealtimeNotifications'
import { RealtimeProvider } from '../realtime/RealtimeProvider'
import { PageTransition } from './RouteTransition'
import {
  FLOW_INTENT_TTL_MS,
  navigationFamily,
  routeDirection,
  type NavigationIntent,
} from './routeTransitionModel'

const desktopMediaQuery = '(min-width: 1024px)'

function subscribeToDesktopViewport(onChange: () => void) {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return () => undefined
  }

  const media = window.matchMedia(desktopMediaQuery)
  media.addEventListener('change', onChange)
  return () => media.removeEventListener('change', onChange)
}

function getDesktopViewportSnapshot() {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia(desktopMediaQuery).matches
}

const Layout = () => {
  const outlet = useOutlet()
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [navigationIntent, setNavigationIntent] = useState<NavigationIntent | null>(null)
  const mainRef = useRef<HTMLDivElement>(null)
  const intentTimeoutRef = useRef<number | null>(null)
  const reducedMotion = useReducedMotion() ?? false
  const isDesktop = useSyncExternalStore(subscribeToDesktopViewport, getDesktopViewportSnapshot, () => false)
  const direction =
    navigationIntent && navigationFamily(navigationIntent.route) === navigationFamily(location.pathname)
      ? routeDirection(navigationIntent.fromPath, location.pathname)
      : 0

  const captureNavigationIntent = useCallback(
    (route: string, element: HTMLElement) => {
      if (route === location.pathname) {
        return
      }

      const rect = element.getBoundingClientRect()
      const nextIntent: NavigationIntent = {
        route,
        fromPath: location.pathname,
        rect: {
          top: rect.top,
          left: rect.left,
          width: rect.width,
          height: rect.height,
        },
      }

      if (intentTimeoutRef.current) {
        window.clearTimeout(intentTimeoutRef.current)
      }

      setNavigationIntent(nextIntent)
      intentTimeoutRef.current = window.setTimeout(() => {
        setNavigationIntent((current) => (current === nextIntent ? null : current))
        intentTimeoutRef.current = null
      }, FLOW_INTENT_TTL_MS)
    },
    [location.pathname],
  )

  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true })
  }, [location.pathname])

  useEffect(
    () => () => {
      if (intentTimeoutRef.current) {
        window.clearTimeout(intentTimeoutRef.current)
      }
    },
    [],
  )

  return (
    <div className="min-h-screen" style={{ backgroundColor: 'var(--bg)' }}>
      <RealtimeProvider>
        <Toaster
          position="top-right"
          toastOptions={{
            style: {
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              color: 'var(--text-h)',
            },
          }}
        />
        <Sidebar
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          onNavigationIntent={captureNavigationIntent}
          actions={isDesktop ? <RealtimeNotifications /> : undefined}
          showThemeToggle={isDesktop}
        />
        <div className="flex min-h-screen flex-col lg:pl-56">
          {!isDesktop && (
            <Header
              onMenuClick={() => setSidebarOpen(true)}
              actions={<RealtimeNotifications />}
            />
          )}
          <main className="flex-1 px-4 py-5 md:px-6 lg:px-8 lg:py-7">
            <div ref={mainRef} tabIndex={-1} className="cc-main-focus mx-auto grid w-full max-w-[90rem] outline-none">
              <AnimatePresence initial={false} mode="popLayout">
                <PageTransition
                  key={location.pathname}
                  locationKey={location.pathname}
                  direction={direction}
                  reducedMotion={reducedMotion}
                >
                  {outlet}
                </PageTransition>
              </AnimatePresence>
            </div>
          </main>
        </div>
        <ScrollRestoration />
      </RealtimeProvider>
    </div>
  )
}

export default Layout

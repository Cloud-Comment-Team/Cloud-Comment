import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useLocation, useOutlet } from 'react-router-dom'
import { AnimatePresence, useReducedMotion } from 'framer-motion'
import { Toaster } from 'react-hot-toast'

import Header from './Header'
import Sidebar from './Sidebar'
import { RealtimeNotifications } from '../notifications/RealtimeNotifications'
import { useAuthStore } from '../../store'
import { PageTransition, RouteFlowOverlay } from './RouteTransition'
import {
  FLOW_INTENT_TTL_MS,
  navigationFamily,
  routeDirection,
  type ElementBounds,
  type NavigationIntent,
} from './routeTransitionModel'

const Layout = () => {
  const outlet = useOutlet()
  const location = useLocation()
  const token = useAuthStore((state) => state.token)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [navigationIntent, setNavigationIntent] = useState<NavigationIntent | null>(null)
  const [mainBounds, setMainBounds] = useState<ElementBounds | null>(null)
  const mainRef = useRef<HTMLDivElement>(null)
  const intentTimeoutRef = useRef<number | null>(null)
  const reducedMotion = useReducedMotion() ?? false
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

  useLayoutEffect(() => {
    const measureMain = () => {
      const rect = mainRef.current?.getBoundingClientRect()

      if (!rect) {
        return
      }

      setMainBounds({
        top: rect.top,
        left: rect.left,
        width: rect.width,
        height: rect.height,
        viewportHeight: window.innerHeight,
      })
    }

    measureMain()
    window.addEventListener('resize', measureMain)

    return () => window.removeEventListener('resize', measureMain)
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
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} onNavigationIntent={captureNavigationIntent} />
      <div className="flex min-h-screen flex-col lg:pl-64">
        <Header onMenuClick={() => setSidebarOpen(true)} />
        <RealtimeNotifications key={token ?? 'guest'} />
        <main className="flex-1 px-4 py-5 md:px-6 lg:px-8 lg:py-7">
          <div ref={mainRef} className="mx-auto grid w-full max-w-7xl">
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
      <RouteFlowOverlay
        intent={navigationIntent}
        bounds={mainBounds}
        locationKey={location.key}
        reducedMotion={reducedMotion}
      />
    </div>
  )
}

export default Layout

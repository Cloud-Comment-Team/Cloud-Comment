import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { ThemeContext, type ThemeMode, type ThemeRevealOrigin } from './ThemeContext'

type ViewTransitionHandle = {
  ready: Promise<void>
}

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => ViewTransitionHandle
}

const THEME_STORAGE_KEY = 'cloud-comment.admin.theme'

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark'
}

function getSystemTheme(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'light'
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getStoredTheme(): ThemeMode | null {
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isThemeMode(storedTheme) ? storedTheme : null
  } catch {
    return null
  }
}

function getInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'light'
  }

  return getStoredTheme() ?? getSystemTheme()
}

function applyTheme(theme: ThemeMode) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

function persistTheme(theme: ThemeMode) {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Theme still applies for the current session when storage is unavailable.
  }
}

function shouldReduceMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function revealRadius({ x, y }: ThemeRevealOrigin): number {
  return Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
}

function animateThemeReveal(origin: ThemeRevealOrigin) {
  const radius = revealRadius(origin)
  const clipPath = [`circle(0px at ${origin.x}px ${origin.y}px)`, `circle(${radius}px at ${origin.x}px ${origin.y}px)`]
  const options: KeyframeAnimationOptions & { pseudoElement?: string } = {
    duration: 620,
    easing: 'cubic-bezier(.2,.85,.25,1)',
    pseudoElement: '::view-transition-new(root)',
  }

  document.documentElement.animate({ clipPath }, options)
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(getInitialTheme)

  const commitTheme = useCallback((nextTheme: ThemeMode) => {
    applyTheme(nextTheme)
    persistTheme(nextTheme)
    setThemeState(nextTheme)
  }, [])

  const setTheme = useCallback(
    (nextTheme: ThemeMode, origin?: ThemeRevealOrigin) => {
      if (nextTheme === theme) {
        return
      }

      const transitionDocument = document as ViewTransitionDocument
      if (!origin || shouldReduceMotion() || !transitionDocument.startViewTransition) {
        commitTheme(nextTheme)
        return
      }

      const transition = transitionDocument.startViewTransition(() => commitTheme(nextTheme))
      transition.ready.then(() => animateThemeReveal(origin)).catch(() => undefined)
    },
    [commitTheme, theme],
  )

  const toggleTheme = useCallback(
    (origin?: ThemeRevealOrigin) => setTheme(theme === 'dark' ? 'light' : 'dark', origin),
    [setTheme, theme],
  )

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const listener = () => {
      if (!getStoredTheme()) {
        setThemeState(getSystemTheme())
      }
    }

    mediaQuery.addEventListener('change', listener)
    return () => mediaQuery.removeEventListener('change', listener)
  }, [])

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [setTheme, theme, toggleTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

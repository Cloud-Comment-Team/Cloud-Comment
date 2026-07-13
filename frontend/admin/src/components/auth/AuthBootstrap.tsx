import { useEffect, useRef, type ReactNode } from 'react'

import { clearCsrfCredentials } from '../../api/csrf'
import { subscribeSessionEvents } from '../../auth/sessionEvents'
import { useAuthStore } from '../../store'

const LEGACY_AUTH_TOKEN_KEY = 'cloud-comment.admin.authToken'

export function AuthBootstrap({ children }: { children: ReactNode }) {
  const checkAuth = useAuthStore((state) => state.checkAuth)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const status = useAuthStore((state) => state.status)
  const startedRef = useRef(false)
  const focusRecheckRef = useRef<Promise<void> | null>(null)

  useEffect(() => {
    if (startedRef.current) {
      return
    }

    startedRef.current = true
    try {
      window.localStorage.removeItem(LEGACY_AUTH_TOKEN_KEY)
    } catch {
      // Cookie-сессия должна работать и при недоступном Web Storage.
    }
    void checkAuth()
  }, [checkAuth])

  useEffect(() => subscribeSessionEvents((event) => {
    clearCsrfCredentials()
    if (event === 'expired') {
      clearAuth()
      return
    }

    void checkAuth({ force: true })
  }), [checkAuth, clearAuth])

  useEffect(() => {
    const revalidate = () => {
      if (status === 'checking' || focusRecheckRef.current) {
        return
      }

      const request = checkAuth({ force: true, silent: true })
      focusRecheckRef.current = request
      void request.finally(() => {
        if (focusRecheckRef.current === request) {
          focusRecheckRef.current = null
        }
      })
    }
    const revalidateWhenVisible = () => {
      if (document.visibilityState === 'visible') {
        revalidate()
      }
    }

    window.addEventListener('focus', revalidate)
    document.addEventListener('visibilitychange', revalidateWhenVisible)
    return () => {
      window.removeEventListener('focus', revalidate)
      document.removeEventListener('visibilitychange', revalidateWhenVisible)
    }
  }, [checkAuth, status])

  if (status === 'checking') {
    return (
      <div className="flex min-h-screen items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
        <p aria-live="polite" role="status" style={{ color: 'var(--text)' }}>Проверяем доступ…</p>
      </div>
    )
  }

  return children
}

import { useEffect, useState } from 'react'

export function RouteFallback() {
  const [visible, setVisible] = useState(false)
  useEffect(() => {
    const timeout = window.setTimeout(() => setVisible(true), 160)
    return () => window.clearTimeout(timeout)
  }, [])

  return (
    <div className="min-h-48" role={visible ? 'status' : undefined} aria-live="polite">
      {visible && <div className="cc-card space-y-4 p-5" aria-label="Загружаем раздел">
        <div className="h-5 w-40 animate-pulse rounded bg-[var(--muted-bg)]" />
        <div className="h-24 animate-pulse rounded bg-[var(--surface-muted)]" />
        <span className="sr-only">Загружаем раздел…</span>
      </div>}
    </div>
  )
}

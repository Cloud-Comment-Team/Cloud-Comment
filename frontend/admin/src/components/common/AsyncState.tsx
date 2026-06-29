import type { ReactNode } from 'react'
import { AlertCircle, Inbox, Loader2 } from 'lucide-react'

interface AsyncStateProps {
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyMessage?: string
  children: ReactNode
}

export function AsyncState({
  loading = false,
  error = null,
  empty = false,
  emptyMessage = 'Нет данных для отображения',
  children,
}: AsyncStateProps) {
  if (loading) {
    return (
      <div
        className="cc-card flex items-center justify-center gap-3 py-14"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
      >
        <Loader2 className="h-6 w-6 animate-spin" style={{ color: 'var(--accent)' }} aria-hidden="true" />
        <span style={{ color: 'var(--text)' }}>Загрузка...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex items-start gap-3 rounded-lg border px-4 py-4 text-left shadow-sm"
        style={{ borderColor: 'var(--danger)', backgroundColor: 'var(--danger-bg)' }}
      >
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--danger)' }} aria-hidden="true" />
        <p className="text-sm" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      </div>
    )
  }

  if (empty) {
    return (
      <div
        className="cc-card flex flex-col items-center justify-center gap-3 px-4 py-14 text-center"
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
      >
        <Inbox className="h-8 w-8" style={{ color: 'var(--text)' }} aria-hidden="true" />
        <p style={{ color: 'var(--text)' }}>{emptyMessage}</p>
      </div>
    )
  }

  return <>{children}</>
}

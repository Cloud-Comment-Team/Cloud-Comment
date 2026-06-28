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
      <div className="flex items-center justify-center gap-3 rounded-xl border py-16" style={{ borderColor: 'var(--border)' }}>
        <Loader2 className="h-6 w-6 animate-spin" style={{ color: 'var(--accent)' }} aria-hidden="true" />
        <span style={{ color: 'var(--text)' }}>Загрузка...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div
        className="flex items-start gap-3 rounded-xl border px-4 py-4 text-left"
        style={{ borderColor: '#ff7875', backgroundColor: 'rgba(255, 120, 117, 0.08)' }}
      >
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" style={{ color: '#a8071a' }} aria-hidden="true" />
        <p className="text-sm" style={{ color: '#a8071a' }}>
          {error}
        </p>
      </div>
    )
  }

  if (empty) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border py-16" style={{ borderColor: 'var(--border)' }}>
        <Inbox className="h-8 w-8" style={{ color: 'var(--text)' }} aria-hidden="true" />
        <p style={{ color: 'var(--text)' }}>{emptyMessage}</p>
      </div>
    )
  }

  return <>{children}</>
}

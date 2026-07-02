import type { ReactNode } from 'react'

type BadgeTone = 'accent' | 'success' | 'warning' | 'danger' | 'muted'

interface BadgeProps {
  children: ReactNode
  tone?: BadgeTone
}

const badgeColors: Record<BadgeTone, { backgroundColor: string; color: string }> = {
  accent: { backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' },
  success: { backgroundColor: 'var(--success-bg)', color: 'var(--success)' },
  warning: { backgroundColor: 'var(--warning-bg)', color: 'var(--warning)' },
  danger: { backgroundColor: 'var(--danger-bg)', color: 'var(--danger)' },
  muted: { backgroundColor: 'var(--muted-bg)', color: 'var(--text)' },
}

export function Badge({ children, tone = 'muted' }: BadgeProps) {
  return (
    <span className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold" style={badgeColors[tone]}>
      {children}
    </span>
  )
}

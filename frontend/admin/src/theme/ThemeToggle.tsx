import { Moon, Sun } from 'lucide-react'
import type { ButtonHTMLAttributes } from 'react'

import { useTheme } from './useTheme'

type ThemeToggleProps = {
  compact?: boolean
} & Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'onClick' | 'type'>

export function ThemeToggle({ compact = false, className = '', ...props }: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const Icon = isDark ? Sun : Moon
  const label = isDark ? 'Включить светлую тему' : 'Включить тёмную тему'

  return (
    <button
      {...props}
      type="button"
      aria-label={label}
      title={label}
      className={`inline-flex h-10 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition hover:-translate-y-0.5 hover:shadow-sm ${compact ? 'w-10 px-0' : ''} ${className}`}
      style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
      onClick={(event) => {
        const rect = event.currentTarget.getBoundingClientRect()
        toggleTheme({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 })
      }}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      {!compact && <span>{isDark ? 'Светлая' : 'Тёмная'}</span>}
    </button>
  )
}

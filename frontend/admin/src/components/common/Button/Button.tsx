import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'danger'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  icon?: ReactNode
  busy?: boolean
}

export default function Button({ variant = 'secondary', icon, busy = false, children, disabled, ...props }: ButtonProps) {
  return (
    <button className={`cc-button-${variant}`} disabled={disabled || busy} aria-busy={busy || undefined} {...props}>
      {icon}
      {busy ? 'Подождите…' : children}
    </button>
  )
}

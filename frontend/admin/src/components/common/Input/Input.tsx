import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { forwardRef, useId } from 'react'
import type { InputHTMLAttributes, ReactNode } from 'react'

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'> {
  label: string
  icon?: ReactNode
  rightAction?: ReactNode
  error?: string
  hint?: string
  success?: string
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ id, label, icon, rightAction, error, hint, success, className = '', style, ...inputProps }, ref) => {
    const generatedId = useId()
    const reducedMotion = useReducedMotion()
    const inputId = id ?? generatedId
    const helperId = `${inputId}-helper`
    const helperText = error ?? success ?? hint
    const describedBy = [inputProps['aria-describedby'], helperId].filter(Boolean).join(' ') || undefined
    const helperColor = error ? '#a8071a' : success ? '#237804' : 'var(--text)'
    const borderColor = error ? '#ff7875' : success ? '#95de64' : 'var(--border)'
    const helperMotion = reducedMotion
      ? {}
      : {
          initial: { opacity: 0, y: -4 },
          animate: { opacity: 1, y: 0 },
          exit: { opacity: 0, y: -4 },
          transition: { duration: 0.14 },
        }

    return (
      <label className="block text-left" htmlFor={inputId}>
        <span className="mb-2 block text-sm font-medium" style={{ color: 'var(--text-h)' }}>
          {label}
        </span>
        <span className="relative block">
          {icon && (
            <span
              className="pointer-events-none absolute left-3 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center"
              style={{ color: error ? '#a8071a' : success ? '#237804' : 'var(--text)' }}
            >
              {icon}
            </span>
          )}
          <input
            {...inputProps}
            aria-describedby={describedBy}
            aria-invalid={error ? true : undefined}
            className={`w-full rounded-lg border py-3 outline-none transition focus:ring-2 ${icon ? 'pl-10' : 'pl-4'} ${rightAction ? 'pr-12' : 'pr-4'} ${className}`}
            id={inputId}
            ref={ref}
            style={{
              backgroundColor: 'var(--code-bg)',
              borderColor,
              color: 'var(--text-h)',
              ...style,
            }}
          />
          {rightAction}
        </span>
        <span className="mt-2 block min-h-5 text-sm leading-5" id={helperId}>
          <AnimatePresence initial={false} mode="wait">
            {helperText && (
              <motion.span key={`${helperColor}-${helperText}`} className="block" style={{ color: helperColor }} {...helperMotion}>
                {helperText}
              </motion.span>
            )}
          </AnimatePresence>
        </span>
      </label>
    )
  },
)

Input.displayName = 'Input'

export default Input

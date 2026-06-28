import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import type { ReactNode } from 'react'

interface AuthFormShellProps {
  icon: ReactNode
  title: string
  description: string
  serverError?: string | null
  children: ReactNode
  footer: ReactNode
}

export function AuthFormShell({
  icon,
  title,
  description,
  serverError,
  children,
  footer,
}: AuthFormShellProps) {
  const reducedMotion = useReducedMotion()

  return (
    <main className="min-h-screen px-4 py-10 flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
      <motion.section
        animate={reducedMotion ? undefined : { opacity: 1, y: 0 }}
        className="w-full max-w-md"
        initial={reducedMotion ? false : { opacity: 0, y: 8 }}
        transition={reducedMotion ? undefined : { duration: 0.22, ease: 'easeOut' }}
      >
        <header className="mb-8 text-left">
          <div
            className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
          >
            {icon}
          </div>
          <h1
            className="mb-2 font-semibold"
            style={{ color: 'var(--text-h)', fontSize: '1.875rem', lineHeight: '2.25rem', letterSpacing: 0, margin: '0 0 0.5rem' }}
          >
            {title}
          </h1>
          <p className="text-base" style={{ color: 'var(--text)' }}>
            {description}
          </p>
        </header>

        <AnimatePresence initial={false}>
          {serverError && (
            <motion.div
              animate={reducedMotion ? undefined : { opacity: 1, y: 0 }}
              className="mb-5 rounded-lg border px-4 py-3 text-left text-sm"
              exit={reducedMotion ? undefined : { opacity: 0, y: -4 }}
              initial={reducedMotion ? false : { opacity: 0, y: -4 }}
              role="alert"
              style={{ backgroundColor: '#fff1f0', borderColor: '#ffccc7', color: '#a8071a' }}
              transition={reducedMotion ? undefined : { duration: 0.16 }}
            >
              {serverError}
            </motion.div>
          )}
        </AnimatePresence>

        {children}

        <div className="mt-6 text-center text-sm" style={{ color: 'var(--text)' }}>
          {footer}
        </div>
      </motion.section>
    </main>
  )
}

interface AuthSubmitButtonProps {
  isSubmitting: boolean
  submittingLabel: string
  children: ReactNode
  disabled?: boolean
}

export function AuthSubmitButton({
  isSubmitting,
  submittingLabel,
  children,
  disabled,
}: AuthSubmitButtonProps) {
  const reducedMotion = useReducedMotion()
  const buttonMotion = reducedMotion
    ? {}
    : {
        whileHover: { y: disabled || isSubmitting ? 0 : -1 },
        whileTap: { scale: disabled || isSubmitting ? 1 : 0.99 },
        transition: { duration: 0.14 },
      }

  return (
    <motion.button
      className="w-full rounded-lg px-4 py-3 font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-60"
      disabled={disabled || isSubmitting}
      style={{ backgroundColor: 'var(--accent)' }}
      type="submit"
      {...buttonMotion}
    >
      {isSubmitting ? submittingLabel : children}
    </motion.button>
  )
}

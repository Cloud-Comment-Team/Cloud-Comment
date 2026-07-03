import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import type { ReactNode } from 'react'

import { BrandLogo, BrandMark } from '../brand/BrandLogo'
import { ThemeToggle } from '../../theme'

interface AuthFormShellProps {
  title: string
  description: string
  serverError?: string | null
  children: ReactNode
  footer: ReactNode
}

export function AuthFormShell({
  title,
  description,
  serverError,
  children,
  footer,
}: AuthFormShellProps) {
  const reducedMotion = useReducedMotion()

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-8" style={{ backgroundColor: 'var(--bg)' }}>
      <div className="fixed right-4 top-4 z-10">
        <ThemeToggle compact />
      </div>
      <motion.section
        animate={reducedMotion ? undefined : { opacity: 1, y: 0 }}
        className="grid w-full max-w-5xl overflow-hidden rounded-lg border shadow-2xl lg:grid-cols-[1fr_1.05fr]"
        initial={reducedMotion ? false : { opacity: 0, y: 8 }}
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        transition={reducedMotion ? undefined : { duration: 0.22, ease: 'easeOut' }}
      >
        <div className="hidden min-h-[520px] flex-col justify-between p-8 lg:flex" style={{ backgroundColor: 'var(--surface-strong)', color: 'var(--text-inverse)' }}>
          <div>
            <div className="mb-8 inline-flex rounded-lg bg-white p-3 shadow-xl">
              <BrandLogo className="h-auto w-56" />
            </div>
            <h2 className="max-w-sm text-3xl font-bold leading-tight">
              Центр управления комментариями для всех ваших сайтов
            </h2>
            <p className="mt-4 max-w-sm text-sm leading-6" style={{ color: 'rgba(248,250,252,0.74)' }}>
              Создавайте проекты, подключайте embed-код и модерируйте обсуждения без переключения между сервисами.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-3 text-sm">
            {['Сайты', 'Виджет', 'Модерация'].map((item) => (
              <span key={item} className="rounded-lg border px-3 py-2 text-center" style={{ borderColor: 'rgba(255,255,255,0.18)', color: 'rgba(248,250,252,0.82)' }}>
                {item}
              </span>
            ))}
          </div>
        </div>

        <div className="p-6 sm:p-8 lg:p-10">
          <header className="mb-8 text-left">
            <div
              className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-lg lg:hidden"
              style={{ backgroundColor: 'var(--surface)', color: 'var(--accent)' }}
            >
              <BrandMark className="h-12 w-12" />
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
                style={{ backgroundColor: 'var(--danger-bg)', borderColor: 'var(--danger)', color: 'var(--danger)' }}
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
      className="w-full rounded-lg px-4 py-3 font-semibold transition disabled:cursor-not-allowed disabled:opacity-60"
      disabled={disabled || isSubmitting}
      style={{ backgroundColor: 'var(--accent)', color: 'var(--accent-contrast)' }}
      type="submit"
      {...buttonMotion}
    >
      {isSubmitting ? submittingLabel : children}
    </motion.button>
  )
}

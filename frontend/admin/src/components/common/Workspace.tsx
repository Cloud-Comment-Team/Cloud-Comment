import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode }) {
  return (
    <header className="cc-page-heading">
      <div>
        {eyebrow && <p className="cc-eyebrow">{eyebrow}</p>}
        <h1 className="cc-title">{title}</h1>
        {description && <p className="cc-subtitle">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

export function ActionBar({ children, label = 'Действия' }: { children: ReactNode; label?: string }) {
  return <section className="cc-action-bar" aria-label={label}>{children}</section>
}

export function FilterBar({ children }: { children: ReactNode }) {
  return <form className="cc-filter-bar" role="search">{children}</form>
}

export function DataRow({ children, compact = false }: { children: ReactNode; compact?: boolean }) {
  return <div className="cc-data-row" data-density={compact ? 'compact' : 'default'}>{children}</div>
}

export function Drawer({ title, open, onClose, children }: { title: string; open: boolean; onClose: () => void; children: ReactNode }) {
  if (!open) return null
  return (
    <aside className="cc-drawer" role="dialog" aria-modal="true" aria-labelledby="cc-drawer-title">
      <div className="mb-5 flex items-center justify-between gap-3">
        <h2 id="cc-drawer-title" className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>{title}</h2>
        <button type="button" className="cc-button-secondary !p-2" onClick={onClose} aria-label="Закрыть панель"><X className="h-4 w-4" /></button>
      </div>
      {children}
    </aside>
  )
}

export function Dialog({ title, open, onClose, children, actions }: { title: string; open: boolean; onClose: () => void; children: ReactNode; actions?: ReactNode }) {
  const dialogRef = useRef<HTMLElement>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!open) return

    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const appRoot = document.getElementById('root')
    const rootWasInert = appRoot?.hasAttribute('inert') ?? false
    appRoot?.setAttribute('inert', '')
    const dialog = dialogRef.current
    const focusableSelector = [
      'a[href]',
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',')
    const focusableElements = () => Array.from(
      dialog?.querySelectorAll<HTMLElement>(focusableSelector) ?? [],
    ).filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true')

    const firstFocusable = focusableElements()[0]
    ;(firstFocusable ?? dialog)?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !dialog) return

      const elements = focusableElements()
      if (elements.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }
      const first = elements[0]
      const last = elements[elements.length - 1]
      if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      if (appRoot && !rootWasInert) appRoot.removeAttribute('inert')
      previousFocus?.focus()
    }
  }, [open])

  if (!open) return null
  return createPortal(
    <>
      <button
        className="fixed inset-0 z-40 cursor-default bg-slate-950/40"
        onClick={onClose}
        aria-label="Закрыть диалог"
        tabIndex={-1}
      />
      <section ref={dialogRef} className="cc-dialog" role="dialog" aria-modal="true" aria-labelledby="cc-dialog-title" tabIndex={-1}>
        <h2 id="cc-dialog-title" className="text-lg font-semibold" style={{ color: 'var(--text-h)' }}>{title}</h2>
        <div className="my-4">{children}</div>
        {actions && <div className="flex justify-end gap-2">{actions}</div>}
      </section>
    </>,
    document.body,
  )
}

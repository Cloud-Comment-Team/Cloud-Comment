import { Menu } from 'lucide-react'
import type { ReactNode } from 'react'

import { BrandMark } from '../brand/BrandLogo'
import { ThemeToggle } from '../../theme'

interface HeaderProps {
  onMenuClick: () => void
  actions?: ReactNode
}

const Header = ({ onMenuClick, actions }: HeaderProps) => {
  return (
    <header
      className="sticky top-0 z-30 border-b"
      style={{ backgroundColor: 'var(--surface-translucent)', borderColor: 'var(--border)' }}
    >
      <div className="flex min-h-16 items-center justify-between gap-4 px-4">
        <button
          type="button"
          className="inline-flex h-10 w-10 items-center justify-center rounded-lg border transition lg:hidden"
          style={{ borderColor: 'var(--border)', color: 'var(--text-h)' }}
          aria-label="Открыть меню"
          onClick={onMenuClick}
        >
          <Menu className="h-5 w-5" aria-hidden="true" />
        </button>

        <div className="flex min-w-0 flex-1 items-center gap-2 text-left lg:pl-2">
          <BrandMark className="h-8 w-8 rounded-md shadow-sm lg:hidden" />
          <p className="text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
            <span className="lg:hidden">CloudComment</span><span className="hidden lg:inline">Панель владельца</span>
          </p>
        </div>
        {actions}
        <ThemeToggle compact />
      </div>
    </header>
  )
}

export default Header

import { Menu } from 'lucide-react'

interface HeaderProps {
  onMenuClick: () => void
}

const Header = ({ onMenuClick }: HeaderProps) => {
  return (
    <header
      className="sticky top-0 z-30 border-b backdrop-blur"
      style={{ backgroundColor: 'var(--surface-translucent)', borderColor: 'var(--border)' }}
    >
      <div className="flex h-14 items-center justify-between gap-4 px-4 md:px-6">
        <button
          type="button"
          className="inline-flex h-10 w-10 items-center justify-center rounded-lg border transition hover:opacity-80 lg:hidden"
          style={{ borderColor: 'var(--border)', color: 'var(--text-h)' }}
          aria-label="Открыть меню"
          onClick={onMenuClick}
        >
          <Menu className="h-5 w-5" aria-hidden="true" />
        </button>

        <div className="min-w-0 text-left">
          <p className="text-sm font-medium" style={{ color: 'var(--text-h)' }}>
            CloudComment Admin
          </p>
          <p className="hidden text-xs md:block" style={{ color: 'var(--text)' }}>
            Сайты, embed-код и модерация комментариев
          </p>
        </div>

        <div className="hidden text-right text-xs sm:block" style={{ color: 'var(--text)' }}>
          MVP workspace
        </div>
      </div>
    </header>
  )
}

export default Header

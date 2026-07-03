import { NavLink, useNavigate } from 'react-router-dom'
import { Globe, LayoutDashboard, LogOut, Settings, Shield, X } from 'lucide-react'

import { BrandMark } from '../brand/BrandLogo'
import { useAuthStore } from '../../store'
import { ThemeToggle } from '../../theme'

const navigation = [
  { name: 'Дашборд', href: '/', icon: LayoutDashboard },
  { name: 'Сайты', href: '/sites', icon: Globe },
  { name: 'Модерация', href: '/moderation', icon: Shield },
  { name: 'Аккаунт', href: '/account', icon: Settings },
]

interface SidebarProps {
  isOpen: boolean
  onClose: () => void
}

const Sidebar = ({ isOpen, onClose }: SidebarProps) => {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const initials = user?.email?.slice(0, 2).toUpperCase() ?? 'AD'

  async function handleLogout() {
    await logout()
    onClose()
    navigate('/login', { replace: true })
  }

  return (
    <>
      {isOpen && (
        <button
          type="button"
          className="fixed inset-0 z-40 bg-slate-950/45 backdrop-blur-sm lg:hidden"
          aria-label="Закрыть меню"
          onClick={onClose}
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 transform border-r shadow-2xl transition-transform duration-200 ease-in-out lg:translate-x-0 lg:shadow-none ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
        style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
        aria-label="Основная навигация"
      >
        <div className="flex h-full flex-col">
          <div className="flex min-h-16 items-center gap-3 border-b px-4" style={{ borderColor: 'var(--border)' }}>
            <BrandMark className="h-10 w-10 shadow-sm" />
            <div className="min-w-0 flex-1 text-left">
              <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-h)' }}>
                CloudComment
              </p>
              <p className="truncate text-xs" style={{ color: 'var(--text)' }}>
                Панель владельца
              </p>
            </div>
            <button
              type="button"
              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border transition hover:opacity-80 lg:hidden"
              style={{ borderColor: 'var(--border)', color: 'var(--text-h)' }}
              aria-label="Закрыть меню"
              onClick={onClose}
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
            {navigation.map((item) => (
              <NavLink
                key={item.name}
                to={item.href}
                end={item.href === '/'}
                onClick={onClose}
                className="group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition hover:-translate-y-0.5 hover:shadow-sm"
                style={({ isActive }) => ({
                  color: isActive ? 'var(--accent)' : 'var(--text)',
                  backgroundColor: isActive ? 'var(--accent-bg)' : 'transparent',
                  border: isActive ? '1px solid var(--accent-border)' : '1px solid transparent',
                  fontWeight: isActive ? '600' : '500',
                })}
              >
                <item.icon className="h-4 w-4" aria-hidden="true" />
                <span>{item.name}</span>
              </NavLink>
            ))}
          </nav>

          <div className="space-y-3 border-t p-3" style={{ borderColor: 'var(--border)' }}>
            <div
              className="flex items-center gap-3 rounded-lg border p-3"
              style={{ backgroundColor: 'var(--surface-muted)', borderColor: 'var(--border)' }}
            >
              <div
                className="flex h-9 w-9 items-center justify-center rounded-full"
                style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
              >
                <span className="text-xs font-semibold">{initials}</span>
              </div>
              <div className="min-w-0 flex-1 text-left">
                <p className="truncate text-sm font-medium" style={{ color: 'var(--text-h)' }}>
                  Владелец
                </p>
                <p className="truncate text-xs" style={{ color: 'var(--text)' }}>
                  {user?.email ?? 'Аккаунт'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => void handleLogout()}
                className="flex min-w-0 flex-1 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition hover:opacity-80"
                style={{ color: 'var(--text)' }}
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Выйти
              </button>
              <ThemeToggle compact />
            </div>
          </div>
        </div>
      </aside>
    </>
  )
}

export default Sidebar

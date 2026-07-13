import { NavLink, useNavigate } from 'react-router-dom'
import { Globe, LayoutDashboard, LogOut, MessageSquareText, Shield, X } from 'lucide-react'
import toast from 'react-hot-toast'
import type { ReactNode } from 'react'

import { BrandMark } from '../brand/BrandLogo'
import { useAuthStore } from '../../store'
import { ThemeToggle } from '../../theme'
import { preloadRoute } from '../../routes/lazyPages'

const navigation = [
  { name: 'Дашборд', href: '/', icon: LayoutDashboard },
  { name: 'Сайты', href: '/sites', icon: Globe },
  { name: 'Обсуждения', href: '/comments', icon: MessageSquareText },
  { name: 'К разбору', href: '/moderation', icon: Shield },
]

interface SidebarProps {
  isOpen: boolean
  onClose: () => void
  onNavigationIntent: (route: string, element: HTMLElement) => void
  actions?: ReactNode
  showThemeToggle: boolean
}

const Sidebar = ({ isOpen, onClose, onNavigationIntent, actions, showThemeToggle }: SidebarProps) => {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const initials = user?.email?.slice(0, 2).toUpperCase() ?? 'AD'

  async function handleLogout() {
    try {
      await logout()
      onClose()
      navigate('/login', { replace: true })
    } catch {
      toast.error('Не удалось завершить сессию. Проверьте соединение и попробуйте ещё раз.')
    }
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
        className={`fixed inset-y-0 left-0 z-50 w-56 transform border-r shadow-2xl transition-transform duration-200 ease-in-out lg:translate-x-0 lg:shadow-none ${
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

          <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-5">
            {navigation.map((item) => (
              <NavLink
                key={item.name}
                to={item.href}
                end={item.href === '/'}
                onPointerDown={(event) => onNavigationIntent(item.href, event.currentTarget)}
                onPointerEnter={() => void preloadRoute(item.href)}
                onFocus={() => void preloadRoute(item.href)}
                onClick={(event) => {
                  onNavigationIntent(item.href, event.currentTarget)
                  onClose()
                }}
                className="relative flex items-center gap-3 rounded-lg border-l-2 px-3 py-2.5 text-sm transition-colors"
                style={({ isActive }) => ({
                  color: isActive ? 'var(--accent)' : 'var(--text)',
                  fontWeight: isActive ? '600' : '500',
                  backgroundColor: isActive ? 'var(--accent-bg)' : 'transparent',
                  borderLeftColor: isActive ? 'var(--accent)' : 'transparent',
                })}
              >
                <item.icon className="h-4 w-4" aria-hidden="true" />
                <span>{item.name}</span>
              </NavLink>
            ))}
          </nav>

          <div className="space-y-3 border-t p-3" style={{ borderColor: 'var(--border)' }}>
            <NavLink
              to="/account"
              onPointerDown={(event) => onNavigationIntent('/account', event.currentTarget)}
              onPointerEnter={() => void preloadRoute('/account')}
              onFocus={() => void preloadRoute('/account')}
              onClick={(event) => {
                onNavigationIntent('/account', event.currentTarget)
                onClose()
              }}
              className="flex items-center gap-3 rounded-lg border p-3 text-left transition-colors"
              style={({ isActive }) => ({
                borderColor: isActive ? 'var(--accent-border)' : 'var(--border)',
                backgroundColor: isActive ? 'var(--accent-bg)' : 'transparent',
              })}
              aria-label="Открыть настройки аккаунта"
            >
              {() => (
                <>
                  <div
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
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
                </>
              )}
            </NavLink>

            <div className="flex items-center gap-2 border-t pt-3" style={{ borderColor: 'var(--border)' }}>
              <button
                type="button"
                onClick={() => void handleLogout()}
                className="flex min-w-0 flex-1 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition hover:opacity-80"
                style={{ color: 'var(--text)' }}
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Выйти
              </button>
              {actions}
              {showThemeToggle && <ThemeToggle compact />}
            </div>
          </div>
        </div>
      </aside>
    </>
  )
}

export default Sidebar

import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Globe, LayoutDashboard, LogOut, MessageSquare, Settings, Shield, BarChart } from 'lucide-react';

import { useAuthStore } from '../../store';

const navigation = [
  { name: 'Дашборд', href: '/', icon: LayoutDashboard },
  { name: 'Сайты', href: '/sites', icon: Globe },
  { name: 'Комментарии', href: '/comments', icon: MessageSquare },
  { name: 'Модерация', href: '/moderation', icon: Shield },
  { name: 'Статистика', href: '/statistics', icon: BarChart },
  { name: 'Настройки', href: '/settings', icon: Settings },
];

const Sidebar: React.FC = () => {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();

  const initials = user?.email?.slice(0, 2).toUpperCase() ?? 'AD';

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside 
      className="fixed inset-y-0 left-0 z-50 w-64 border-r transform -translate-x-full lg:translate-x-0 transition-transform duration-200 ease-in-out"
      style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}
    >
      <div className="flex flex-col h-full">
        <div className="flex items-center gap-3 px-6 h-16 border-b" style={{ borderColor: 'var(--border)' }}>
          <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: 'var(--accent)' }}>
            <MessageSquare className="w-5 h-5 text-white" />
          </div>
          <span className="font-bold text-lg" style={{ color: 'var(--text-h)' }}>CommentAdmin</span>
        </div>

        <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
          {navigation.map((item) => (
            <NavLink
              key={item.name}
              to={item.href}
              end={item.href === '/'}
              className="flex items-center gap-3 px-4 py-3 rounded-lg transition-all hover:opacity-80"
              style={({ isActive }) => ({
                color: isActive ? 'var(--accent)' : 'var(--text)',
                backgroundColor: isActive ? 'var(--accent-bg)' : 'transparent',
                fontWeight: isActive ? '500' : 'normal'
              })}
            >
              <item.icon className="w-5 h-5" />
              <span>{item.name}</span>
            </NavLink>
          ))}
        </nav>

        <div className="p-4 border-t space-y-3" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full flex items-center justify-center" 
              style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
            >
              <span className="text-sm font-semibold">{initials}</span>
            </div>
            <div className="flex-1 min-w-0 text-left">
              <p className="text-sm font-medium truncate" style={{ color: 'var(--text-h)' }}>Владелец</p>
              <p className="text-xs truncate" style={{ color: 'var(--text)' }}>{user?.email ?? '—'}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm transition hover:opacity-80"
            style={{ color: 'var(--text)' }}
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Выйти
          </button>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;

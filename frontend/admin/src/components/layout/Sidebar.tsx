import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, MessageSquare, Users, Settings, Shield, BarChart } from 'lucide-react';

const navigation = [
  { name: 'Дашборд', href: '/', icon: LayoutDashboard },
  { name: 'Комментарии', href: '/comments', icon: MessageSquare },
  { name: 'Пользователи', href: '/users', icon: Users },
  { name: 'Модерация', href: '/moderation', icon: Shield },
  { name: 'Статистика', href: '/statistics', icon: BarChart },
  { name: 'Настройки', href: '/settings', icon: Settings },
];

const Sidebar: React.FC = () => {
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

        <div className="p-4 border-t" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full flex items-center justify-center" 
              style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
            >
              <span className="text-sm font-semibold">AD</span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate" style={{ color: 'var(--text-h)' }}>Admin User</p>
              <p className="text-xs truncate" style={{ color: 'var(--text)' }}>admin@example.com</p>
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
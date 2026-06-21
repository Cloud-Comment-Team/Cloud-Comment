import React from 'react';
import { Bell, Search, Menu } from 'lucide-react';

const Header: React.FC = () => {
  return (
    <header 
      className="sticky top-0 z-40 border-b"
      style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}
    >
      <div className="flex items-center justify-between px-4 md:px-6 h-16">
        <div className="flex items-center gap-4">
          <button className="lg:hidden p-2 rounded-lg transition hover:opacity-80">
            <Menu className="w-5 h-5" style={{ color: 'var(--text)' }} />
          </button>
          
          <div className="relative hidden md:block">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4" style={{ color: 'var(--text)' }} />
            <input
              type="text"
              placeholder="Поиск..."
              className="w-80 pl-10 pr-4 py-2 rounded-lg outline-none transition border"
              style={{ 
                backgroundColor: 'var(--code-bg)',
                borderColor: 'var(--border)',
                color: 'var(--text-h)'
              }}
            />
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button className="p-2 rounded-lg transition relative hover:opacity-80">
            <Bell className="w-5 h-5" style={{ color: 'var(--text)' }} />
            <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
          </button>
          
          <div className="w-px h-8" style={{ backgroundColor: 'var(--border)' }}></div>
          
          <button className="flex items-center gap-2 px-3 py-2 rounded-lg transition hover:opacity-80">
            <div className="w-8 h-8 rounded-full flex items-center justify-center" 
              style={{ backgroundColor: 'var(--accent-bg)', color: 'var(--accent)' }}
            >
              <span className="text-sm font-semibold">AD</span>
            </div>
          </button>
        </div>
      </div>
    </header>
  );
};

export default Header;
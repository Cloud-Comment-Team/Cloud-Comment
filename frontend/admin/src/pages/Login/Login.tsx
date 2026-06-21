import React from 'react';

const Login: React.FC = () => {
  return (
    <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
      <div className="w-full max-w-md p-8 rounded-xl border" style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}>
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>CommentAdmin</h1>
          <p className="mt-2" style={{ color: 'var(--text)' }}>Вход в панель управления</p>
        </div>

        <form className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--text-h)' }}>
              Email
            </label>
            <input
              type="email"
              className="w-full px-4 py-2 rounded-lg border outline-none transition"
              style={{ 
                backgroundColor: 'var(--code-bg)',
                borderColor: 'var(--border)',
                color: 'var(--text-h)'
              }}
              placeholder="admin@example.com"
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--text-h)' }}>
              Пароль
            </label>
            <input
              type="password"
              className="w-full px-4 py-2 rounded-lg border outline-none transition"
              style={{ 
                backgroundColor: 'var(--code-bg)',
                borderColor: 'var(--border)',
                color: 'var(--text-h)'
              }}
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            className="w-full py-2 px-4 rounded-lg font-medium transition hover:opacity-90"
            style={{ backgroundColor: 'var(--accent)', color: 'white' }}
          >
            Войти
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;
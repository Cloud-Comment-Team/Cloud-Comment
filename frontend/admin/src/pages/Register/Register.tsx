import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Mail, Lock, Eye, EyeOff, UserPlus } from 'lucide-react';
import { apiClient } from '../../api/client';

const Register: React.FC = () => {
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (formData.password !== formData.confirmPassword) {
      setError('Пароли не совпадают');
      return;
    }

    if (formData.password.length < 6) {
      setError('Пароль должен быть не менее 6 символов');
      return;
    }

    setLoading(true);

    try {
      await apiClient.post('/auth/register', {
        email: formData.email,
        password: formData.password,
      });

      navigate('/login');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка регистрации');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4" style={{ backgroundColor: '#fcf8fb' }}>
      <div
        className="w-full max-w-md p-8 rounded-2xl shadow-xl"
        style={{
          backgroundColor: 'rgba(255,255,255,0.7)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(255,255,255,0.3)'
        }}
      >
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-3 mb-3">
            <div
              className="w-12 h-12 rounded-xl flex items-center justify-center shadow-lg"
              style={{ background: 'linear-gradient(135deg, #0058bc, #60cdff)' }}
            >
              <UserPlus className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-2xl font-extrabold" style={{ color: '#1b1b1d' }}>
              Cloud<span style={{ color: '#0058bc' }}>Comment</span>
            </h1>
          </div>
          <p className="text-sm" style={{ color: '#414755' }}>Создайте аккаунт</p>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-lg text-sm" style={{ backgroundColor: '#ffdad6', color: '#ba1a1a' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1.5" style={{ color: '#1b1b1d' }}>Email</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5" style={{ color: '#717786' }} />
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="w-full pl-10 pr-4 py-3 rounded-xl border outline-none transition-all focus:ring-2"
                style={{
                  backgroundColor: '#f6f3f5',
                  borderColor: '#c1c6d7',
                  color: '#1b1b1d'
                }}
                placeholder="admin@example.com"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1.5" style={{ color: '#1b1b1d' }}>Пароль</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5" style={{ color: '#717786' }} />
              <input
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={formData.password}
                onChange={handleChange}
                className="w-full pl-10 pr-10 py-3 rounded-xl border outline-none transition-all focus:ring-2"
                style={{
                  backgroundColor: '#f6f3f5',
                  borderColor: '#c1c6d7',
                  color: '#1b1b1d'
                }}
                placeholder="••••••••"
                required
                minLength={6}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 hover:opacity-80"
                style={{ color: '#717786' }}
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1.5" style={{ color: '#1b1b1d' }}>Подтверждение пароля</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5" style={{ color: '#717786' }} />
              <input
                type={showPassword ? 'text' : 'password'}
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleChange}
                className="w-full pl-10 pr-4 py-3 rounded-xl border outline-none transition-all focus:ring-2"
                style={{
                  backgroundColor: '#f6f3f5',
                  borderColor: '#c1c6d7',
                  color: '#1b1b1d'
                }}
                placeholder="••••••••"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 rounded-xl font-bold text-white shadow-lg transition-all hover:brightness-110 active:scale-95 disabled:opacity-50"
            style={{ background: 'linear-gradient(135deg, #0058bc, #60cdff)' }}
          >
            {loading ? 'Регистрация...' : 'Зарегистрироваться'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm" style={{ color: '#414755' }}>
          Уже есть аккаунт?{' '}
          <Link to="/login" style={{ color: '#0058bc' }} className="font-semibold hover:underline">
            Войти
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
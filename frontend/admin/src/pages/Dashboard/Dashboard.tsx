import React from 'react';
import { MessageSquare, Users, Clock, AlertTriangle } from 'lucide-react';

const StatCard: React.FC<{ icon: React.ReactNode; label: string; value: string | number }> = ({
  icon,
  label,
  value
}) => (
  <div className="rounded-xl p-6 border" 
    style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}
  >
    <div className="flex items-center justify-between mb-4">
      <div className="p-3 rounded-lg" style={{ backgroundColor: 'var(--accent-bg)' }}>
        {icon}
      </div>
      <span className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>{value}</span>
    </div>
    <p className="text-sm" style={{ color: 'var(--text)' }}>{label}</p>
  </div>
);

const Dashboard: React.FC = () => {
  const stats = [
    {
      icon: <MessageSquare className="w-6 h-6" style={{ color: 'var(--accent)' }} />,
      label: 'Всего комментариев',
      value: '1,234'
    },
    {
      icon: <Users className="w-6 h-6" style={{ color: 'var(--accent)' }} />,
      label: 'Активных пользователей',
      value: '456'
    },
    {
      icon: <Clock className="w-6 h-6" style={{ color: 'var(--accent)' }} />,
      label: 'На модерации',
      value: '23'
    },
    {
      icon: <AlertTriangle className="w-6 h-6" style={{ color: 'var(--accent)' }} />,
      label: 'Требуют внимания',
      value: '5'
    }
  ];

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>Дашборд</h1>
        <p className="mt-1" style={{ color: 'var(--text)' }}>Общая статистика вашего сервиса комментариев</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat, index) => (
          <StatCard key={index} {...stat} />
        ))}
      </div>
    </div>
  );
};

export default Dashboard;
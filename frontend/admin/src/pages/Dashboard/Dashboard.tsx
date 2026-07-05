import { Link } from 'react-router-dom'
import { Globe, ShieldCheck } from 'lucide-react'

import { OwnerAnalyticsPanel } from '../../components/analytics/OwnerAnalyticsPanel'

const Dashboard = () => {
  return (
    <div className="cc-page">
      <div className="cc-page-heading">
        <div>
          <p className="cc-eyebrow">Обзор</p>
          <h1 className="cc-title">Панель владельца сайта</h1>
          <p className="cc-subtitle">
            Управляйте проектами, embed-кодом, модерацией и аналитикой комментариев из одного места.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link to="/sites/new" className="cc-button-primary">
            <Globe className="h-4 w-4" aria-hidden="true" />
            Создать сайт
          </Link>
          <Link to="/moderation" className="cc-button-secondary">
            <ShieldCheck className="h-4 w-4" aria-hidden="true" />
            Модерация
          </Link>
        </div>
      </div>

      <OwnerAnalyticsPanel />
    </div>
  )
}

export default Dashboard

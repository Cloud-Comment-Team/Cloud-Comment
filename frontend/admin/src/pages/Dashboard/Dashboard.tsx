import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, Clock, Globe, MessageSquare } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { listComments } from '../../api/moderation'
import { listSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'

interface DashboardStats {
  sites: number
  pending: number
  approved: number
  rejected: number
}

const StatCard = ({
  icon,
  label,
  value,
  href,
}: {
  icon: ReactNode
  label: string
  value: number | string
  href?: string
}) => {
  const content = (
    <div
      className="rounded-xl border p-6 transition hover:opacity-95"
      style={{ backgroundColor: 'var(--bg)', borderColor: 'var(--border)' }}
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="rounded-lg p-3" style={{ backgroundColor: 'var(--accent-bg)' }}>
          {icon}
        </div>
        <span className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          {value}
        </span>
      </div>
      <p className="text-sm" style={{ color: 'var(--text)' }}>
        {label}
      </p>
    </div>
  )

  if (href) {
    return <Link to={href}>{content}</Link>
  }

  return content
}

const Dashboard = () => {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadStats() {
      setLoading(true)
      setError(null)

      try {
        const [sitesResponse, pendingResponse, approvedResponse, rejectedResponse] = await Promise.all([
          listSites({ page: 1, pageSize: 1 }),
          listComments({ status: 'PENDING', page: 1, pageSize: 1 }),
          listComments({ status: 'APPROVED', page: 1, pageSize: 1 }),
          listComments({ status: 'REJECTED', page: 1, pageSize: 1 }),
        ])

        if (!cancelled) {
          setStats({
            sites: sitesResponse.totalItems,
            pending: pendingResponse.totalItems,
            approved: approvedResponse.totalItems,
            rejected: rejectedResponse.totalItems,
          })
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить статистику дашборда.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadStats()

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="text-left">
      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          Дашборд
        </h1>
        <p className="mt-1" style={{ color: 'var(--text)' }}>
          Краткий обзор сайтов и статусов комментариев
        </p>
      </div>

      <AsyncState loading={loading} error={error} empty={false}>
        {stats && (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              icon={<Globe className="h-6 w-6" style={{ color: 'var(--accent)' }} aria-hidden="true" />}
              label="Сайтов"
              value={stats.sites}
              href="/sites"
            />
            <StatCard
              icon={<Clock className="h-6 w-6" style={{ color: 'var(--accent)' }} aria-hidden="true" />}
              label="На модерации"
              value={stats.pending}
              href="/moderation"
            />
            <StatCard
              icon={<MessageSquare className="h-6 w-6" style={{ color: 'var(--accent)' }} aria-hidden="true" />}
              label="Одобрено"
              value={stats.approved}
              href="/moderation"
            />
            <StatCard
              icon={<AlertTriangle className="h-6 w-6" style={{ color: 'var(--accent)' }} aria-hidden="true" />}
              label="Отклонено"
              value={stats.rejected}
              href="/moderation"
            />
          </div>
        )}
      </AsyncState>
    </div>
  )
}

export default Dashboard

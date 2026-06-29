import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, Clock, Globe, MessageSquare, ShieldCheck } from 'lucide-react'
import { motion, useReducedMotion } from 'framer-motion'

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
  tone = 'accent',
}: {
  icon: ReactNode
  label: string
  value: number | string
  href?: string
  tone?: 'accent' | 'success' | 'warning' | 'danger'
}) => {
  const color = `var(--${tone})`
  const backgroundColor = `var(--${tone}-bg)`
  const reducedMotion = useReducedMotion()

  const content = (
    <motion.div
      className="cc-card p-5 transition"
      style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
      whileHover={reducedMotion ? undefined : { y: href ? -3 : 0 }}
      transition={reducedMotion ? undefined : { duration: 0.16 }}
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="rounded-lg p-3" style={{ backgroundColor, color }}>
          {icon}
        </div>
        <span className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
          {value}
        </span>
      </div>
      <p className="text-sm" style={{ color: 'var(--text)' }}>
        {label}
      </p>
    </motion.div>
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
    <div className="cc-page">
      <div className="cc-page-heading">
        <div>
          <p className="cc-eyebrow">Обзор</p>
          <h1 className="cc-title">Панель владельца сайта</h1>
          <p className="cc-subtitle">
            Управляйте проектами, embed-кодом и модерацией комментариев из одного места.
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

      <AsyncState loading={loading} error={error} empty={false}>
        {stats && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              icon={<Globe className="h-6 w-6" aria-hidden="true" />}
              label="Сайтов"
              value={stats.sites}
              href="/sites"
            />
            <StatCard
              icon={<Clock className="h-6 w-6" aria-hidden="true" />}
              label="На модерации"
              value={stats.pending}
              href="/moderation"
              tone="warning"
            />
            <StatCard
              icon={<MessageSquare className="h-6 w-6" aria-hidden="true" />}
              label="Одобрено"
              value={stats.approved}
              href="/moderation"
              tone="success"
            />
            <StatCard
              icon={<AlertTriangle className="h-6 w-6" aria-hidden="true" />}
              label="Отклонено"
              value={stats.rejected}
              href="/moderation"
              tone="danger"
            />
          </div>
        )}
      </AsyncState>
    </div>
  )
}

export default Dashboard

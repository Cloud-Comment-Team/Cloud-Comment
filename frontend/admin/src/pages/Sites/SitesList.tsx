import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Globe, Plus } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { listSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import { PageHeader } from '../../components/common/Workspace'
import type { Site } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { moderationModeLabels } from '../../utils/moderationModeLabels'

const SitesList = () => {
  const [sites, setSites] = useState<Site[]>([])
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadSites() {
      setLoading(true)
      setError(null)

      try {
        const response = await listSites({ page, pageSize: 20 })
        if (cancelled) {
          return
        }
        setSites(response.items)
        setTotalPages(response.totalPages)
        setTotalItems(response.totalItems)
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить список сайтов.'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadSites()

    return () => {
      cancelled = true
    }
  }, [page])

  return (
    <div className="cc-page">
      <PageHeader
        eyebrow="Проекты"
        title="Сайты"
        description="Управляйте проектами, доменами и настройками виджета комментариев"
        actions={<Link to="/sites/new" className="cc-button-primary">
          <Plus className="h-4 w-4" aria-hidden="true" />
          Создать сайт
        </Link>}
      />

      <AsyncState
        loading={loading}
        error={error}
        empty={!loading && !error && sites.length === 0}
        emptyMessage="У вас пока нет сайтов. Создайте первый проект, чтобы подключить виджет."
      >
        <div className="space-y-4">
          <div className="grid gap-3 md:hidden">
            {sites.map((site) => (
              <Link key={site.id} to={`/sites/${site.id}`} className="cc-card block p-4">
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold" style={{ color: 'var(--text-h)' }}>
                      {site.name}
                    </p>
                    <p className="mt-1 truncate text-sm" style={{ color: 'var(--text)' }}>
                      {site.domain}
                    </p>
                  </div>
                  <ArrowRight className="h-4 w-4 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone="accent">{moderationModeLabels[site.moderationMode]}</Badge>
                  <Badge tone={site.isActive ? 'success' : 'danger'}>
                    {site.isActive ? 'Активен' : 'Деактивирован'}
                  </Badge>
                </div>
              </Link>
            ))}
          </div>

          <div className="cc-table-shell hidden md:block">
            <table className="w-full min-w-[760px] divide-y text-sm" style={{ borderColor: 'var(--border)' }}>
              <thead style={{ backgroundColor: 'var(--surface-muted)' }}>
                <tr>
                  <th className="px-4 py-3 text-left font-medium" style={{ color: 'var(--text-h)' }}>
                    Название
                  </th>
                  <th className="px-4 py-3 text-left font-medium" style={{ color: 'var(--text-h)' }}>
                    Домен
                  </th>
                  <th className="px-4 py-3 text-left font-medium" style={{ color: 'var(--text-h)' }}>
                    Модерация
                  </th>
                  <th className="px-4 py-3 text-left font-medium" style={{ color: 'var(--text-h)' }}>
                    Статус
                  </th>
                  <th className="px-4 py-3 text-left font-medium" style={{ color: 'var(--text-h)' }}>
                    Создан
                  </th>
                </tr>
              </thead>
              <tbody>
                {sites.map((site) => (
                  <tr key={site.id} className="border-t transition hover:bg-[var(--surface-muted)]" style={{ borderColor: 'var(--border)' }}>
                    <td className="px-4 py-3">
                      <Link
                        to={`/sites/${site.id}`}
                        className="inline-flex items-center gap-2 font-semibold hover:underline"
                        style={{ color: 'var(--text-h)' }}
                      >
                        <Globe className="h-4 w-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                        {site.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3" style={{ color: 'var(--text)' }}>
                      {site.domain}
                    </td>
                    <td className="px-4 py-3" style={{ color: 'var(--text)' }}>
                      {moderationModeLabels[site.moderationMode]}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={site.isActive ? 'success' : 'danger'}>
                        {site.isActive ? 'Активен' : 'Деактивирован'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3" style={{ color: 'var(--text)' }}>
                      {formatDateTime(site.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <PaginationControls page={page} totalPages={totalPages} totalItems={totalItems} onPageChange={setPage} />
        </div>
      </AsyncState>
    </div>
  )
}

export default SitesList

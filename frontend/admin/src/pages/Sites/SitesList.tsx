import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Globe, Plus } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { listSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
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
    <div className="text-left">
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-h)' }}>
            Сайты
          </h1>
          <p className="mt-1" style={{ color: 'var(--text)' }}>
            Управляйте проектами, доменами и настройками виджета комментариев
          </p>
        </div>
        <Link
          to="/sites/new"
          className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition hover:opacity-90"
          style={{ backgroundColor: 'var(--accent)' }}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          Создать сайт
        </Link>
      </div>

      <AsyncState
        loading={loading}
        error={error}
        empty={!loading && !error && sites.length === 0}
        emptyMessage="У вас пока нет сайтов. Создайте первый проект, чтобы подключить виджет."
      >
        <div className="space-y-4">
          <div
            className="overflow-x-auto rounded-lg border"
            style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)' }}
          >
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
                  <tr key={site.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                    <td className="px-4 py-3">
                      <Link
                        to={`/sites/${site.id}`}
                        className="inline-flex items-center gap-2 font-medium hover:underline"
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

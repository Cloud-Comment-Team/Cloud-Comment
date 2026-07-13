import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Plus } from 'lucide-react'

import { getApiErrorMessage } from '../../api/auth'
import { listSites } from '../../api/sites'
import { AsyncState } from '../../components/common/AsyncState'
import { Badge } from '../../components/common/Badge'
import { PaginationControls } from '../../components/common/PaginationControls'
import { PageHeader } from '../../components/common/Workspace'
import type { Site } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { moderationModeLabels } from '../../utils/moderationModeLabels'

const projectPluralRules = new Intl.PluralRules('ru-RU')

function projectLabel(count: number) {
  const forms: Record<Intl.LDMLPluralRule, string> = {
    zero: 'проектов',
    one: 'проект',
    two: 'проекта',
    few: 'проекта',
    many: 'проектов',
    other: 'проектов',
  }
  return forms[projectPluralRules.select(count)]
}

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
          <section className="cc-section overflow-hidden" aria-label="Список сайтов">
            <div className="cc-section-header">
              <div>
                <h2 className="cc-section-title">Подключённые сайты</h2>
                <p className="cc-section-description">{totalItems} {projectLabel(totalItems)} в CloudComment</p>
              </div>
            </div>
            <div className="cc-list-heading md:grid-cols-[minmax(12rem,1.4fr)_minmax(9rem,0.8fr)_8rem_10rem_1.5rem]">
              <span>Сайт</span>
              <span>Модерация</span>
              <span>Статус</span>
              <span>Создан</span>
              <span className="sr-only">Открыть</span>
            </div>
            {sites.map((site) => (
              <Link
                key={site.id}
                to={`/sites/${site.id}`}
                className="cc-list-row md:grid-cols-[minmax(12rem,1.4fr)_minmax(9rem,0.8fr)_8rem_10rem_1.5rem]"
              >
                <div className="min-w-0">
                  <p className="truncate font-semibold" style={{ color: 'var(--text-h)' }}>{site.name}</p>
                  <p className="mt-0.5 truncate text-sm" style={{ color: 'var(--text)' }}>{site.domain}</p>
                </div>
                <div className="flex items-center justify-between gap-3 md:contents">
                  <span className="text-sm" style={{ color: 'var(--text)' }}>{moderationModeLabels[site.moderationMode]}</span>
                  <Badge tone={site.isActive ? 'success' : 'danger'}>{site.isActive ? 'Активен' : 'Выключен'}</Badge>
                  <span className="hidden text-sm md:inline" style={{ color: 'var(--text)' }}>{formatDateTime(site.createdAt)}</span>
                  <ArrowRight className="h-4 w-4 justify-self-end" style={{ color: 'var(--text)' }} aria-hidden="true" />
                </div>
              </Link>
            ))}
          </section>

          <PaginationControls page={page} totalPages={totalPages} totalItems={totalItems} onPageChange={setPage} />
        </div>
      </AsyncState>
    </div>
  )
}

export default SitesList

import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, CheckCircle2, CircleAlert, Clock3, Globe2, MessageSquareText, ShieldAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

import { getApiErrorMessage } from '../../api/auth'
import { listDiscussions } from '../../api/discussions'
import { getModerationCounts } from '../../api/moderation'
import { getInstallationStatus, listSites } from '../../api/sites'
import { PageHeader } from '../../components/common/Workspace'
import type { DiscussionSummary, ModerationCounts, Site, SiteInstallationStatus } from '../../types/api'
import { formatDateTime } from '../../utils/formatDate'
import { buildTodayModel } from './todayModel'

interface TodayData {
  sites: Site[]
  installationStatuses: Map<string, SiteInstallationStatus>
  moderation: ModerationCounts | null
  discussions: DiscussionSummary[]
}

const EMPTY_DATA: TodayData = {
  sites: [],
  installationStatuses: new Map(),
  moderation: null,
  discussions: [],
}

function installationCopy(status: SiteInstallationStatus) {
  if (status.status === 'REJECTED') return 'Последний запрос отклонён: проверьте разрешённый origin.'
  if (status.status === 'STALE') return 'Виджет давно не обращался к CloudComment.'
  if (status.status === 'NEVER_SEEN') return 'Виджет ещё не обнаружен на сайте.'
  return 'Установка работает.'
}

function discussionPage(item: DiscussionSummary) {
  if (item.pageTitle?.trim()) return item.pageTitle
  try {
    const url = new URL(item.pageUrl)
    return `${url.hostname}${url.pathname === '/' ? '' : url.pathname}`
  } catch {
    return item.pageUrl
  }
}

const Dashboard = () => {
  const [data, setData] = useState<TodayData>(EMPTY_DATA)
  const [loading, setLoading] = useState(true)
  const [errors, setErrors] = useState<string[]>([])

  useEffect(() => {
    let cancelled = false

    async function loadToday() {
      const [sitesResult, moderationResult, discussionsResult] = await Promise.allSettled([
        listSites({ page: 1, pageSize: 100 }),
        getModerationCounts(),
        listDiscussions({ page: 1, pageSize: 5 }),
      ])
      if (cancelled) return

      const sites = sitesResult.status === 'fulfilled' ? sitesResult.value.items : []
      const statusResults = await Promise.allSettled(sites.map((site) => getInstallationStatus(site.id)))
      if (cancelled) return

      const installationStatuses = new Map<string, SiteInstallationStatus>()
      statusResults.forEach((result, index) => {
        if (result.status === 'fulfilled') installationStatuses.set(sites[index].id, result.value)
      })

      const nextErrors: string[] = []
      if (sitesResult.status === 'rejected') nextErrors.push(getApiErrorMessage(sitesResult.reason, 'Не удалось проверить сайты.'))
      if (moderationResult.status === 'rejected') nextErrors.push(getApiErrorMessage(moderationResult.reason, 'Не удалось проверить очередь.'))
      if (discussionsResult.status === 'rejected') nextErrors.push(getApiErrorMessage(discussionsResult.reason, 'Не удалось загрузить обсуждения.'))
      if (statusResults.some((result) => result.status === 'rejected')) nextErrors.push('Часть статусов установки временно недоступна.')

      setData({
        sites,
        installationStatuses,
        moderation: moderationResult.status === 'fulfilled' ? moderationResult.value : null,
        discussions: discussionsResult.status === 'fulfilled' ? discussionsResult.value.items : [],
      })
      setErrors(nextErrors)
      setLoading(false)
    }

    void loadToday()
    return () => { cancelled = true }
  }, [])

  const model = useMemo(() => buildTodayModel(data), [data])
  const noSites = !loading && data.sites.length === 0 && !errors.some((message) => message.includes('сайт'))

  return (
    <div className="cc-page">
      <PageHeader
        title="Сегодня"
        description="Сначала то, что требует решения. Подробные показатели находятся в аналитике."
        actions={noSites ? undefined : <Link to="/sites/new" className="cc-button-primary"><Globe2 className="h-4 w-4" />Подключить сайт</Link>}
      />

      {loading && (
        <div className="grid gap-4 lg:grid-cols-3" role="status" aria-label="Загрузка рабочего состояния">
          {[1, 2, 3].map((item) => <div key={item} className="cc-card h-40 animate-pulse" />)}
        </div>
      )}

      {noSites && (
        <section className="cc-card cc-density-spacious flex flex-col items-start gap-5">
          <div className="cc-icon-well"><Globe2 className="h-5 w-5" /></div>
          <div>
            <h2 className="cc-section-title">Подключите первый сайт</h2>
            <p className="cc-supporting mt-2 max-w-xl">Создайте сайт, скопируйте код установки и дождитесь автоматической проверки первого запроса виджета.</p>
          </div>
          <Link to="/sites/new" className="cc-button-primary">Подключить сайт <ArrowRight className="h-4 w-4" /></Link>
        </section>
      )}

      {!loading && !noSites && (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(22rem,.95fr)]">
          <div className="space-y-4">
            {model.installationProblems.length > 0 ? (
              <section className="cc-card overflow-hidden" aria-labelledby="today-installation-title">
                <div className="cc-section-heading"><div><p className="cc-eyebrow">Требует действия</p><h2 id="today-installation-title" className="cc-section-title">Установка сайтов</h2></div><CircleAlert className="h-5 w-5" style={{ color: 'var(--status-rejected-accent)' }} /></div>
                <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
                  {model.installationProblems.slice(0, 5).map(({ site, status }) => (
                    <Link key={site.id} to={`/sites/${site.id}?section=installation`} className="cc-today-row group">
                      <div className="min-w-0"><strong className="block truncate" style={{ color: 'var(--text-h)' }}>{site.name}</strong><span className="mt-1 block text-sm" style={{ color: 'var(--text)' }}>{installationCopy(status)}</span></div>
                      <div className="shrink-0 text-right"><span className="text-xs" style={{ color: 'var(--text)' }}>{status.lastRejectedAt || status.lastSuccessfulAt ? formatDateTime(status.lastRejectedAt ?? status.lastSuccessfulAt!) : 'с момента создания'}</span><ArrowRight className="ml-auto mt-2 h-4 w-4 transition-transform group-hover:translate-x-1" /></div>
                    </Link>
                  ))}
                </div>
              </section>
            ) : (
              <section className="cc-card cc-density-comfortable flex items-start gap-4">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--status-approved-accent)' }} />
                <div><h2 className="cc-section-title">Сайты на связи</h2><p className="cc-supporting mt-1">Сейчас нет проблем установки, требующих вашего действия.</p></div>
              </section>
            )}

            <section className="cc-card cc-density-comfortable" aria-labelledby="today-queue-title">
              <div className="flex items-start justify-between gap-4">
                <div className="flex min-w-0 items-start gap-3"><ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" style={{ color: model.queueSize ? 'var(--status-pending-accent)' : 'var(--text)' }} /><div><h2 id="today-queue-title" className="cc-section-title">К разбору</h2><p className="cc-supporting mt-1">{model.queueSize ? `${model.queueSize} сообщений ждут решения.` : 'Очередь разобрана.'}</p></div></div>
                <Link to="/moderation" className={model.queueSize ? 'cc-button-primary' : 'cc-button-secondary'}>{model.queueSize ? 'Разобрать' : 'Открыть'}<ArrowRight className="h-4 w-4" /></Link>
              </div>
            </section>
          </div>

          <section className="cc-card overflow-hidden" aria-labelledby="today-discussions-title">
            <div className="cc-section-heading"><div><p className="cc-eyebrow">Последняя активность</p><h2 id="today-discussions-title" className="cc-section-title">Обсуждения</h2></div><MessageSquareText className="h-5 w-5" style={{ color: 'var(--text)' }} /></div>
            {model.discussions.length === 0 ? <p className="cc-density-comfortable cc-supporting">Новых обсуждений пока нет.</p> : (
              <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
                {model.discussions.map((discussion) => (
                  <Link key={discussion.rootCommentId} to={`/comments?discussion=${discussion.rootCommentId}`} className="cc-today-row group">
                    <div className="min-w-0"><span className="block truncate text-xs font-semibold" style={{ color: 'var(--text)' }}>{discussion.siteName}</span><strong className="mt-1 block truncate" style={{ color: 'var(--text-h)' }}>{discussionPage(discussion)}</strong><span className="mt-1 block truncate text-sm" style={{ color: 'var(--text)' }}>{discussion.lastAuthor.displayName}: {discussion.lastMessage}</span></div>
                    <div className="shrink-0 text-right"><Clock3 className="ml-auto h-4 w-4" /><time className="mt-1 block text-xs" style={{ color: 'var(--text)' }}>{formatDateTime(discussion.lastActivityAt)}</time></div>
                  </Link>
                ))}
              </div>
            )}
            <div className="border-t px-4 py-3" style={{ borderColor: 'var(--border)' }}><Link to="/comments" className="text-sm font-semibold" style={{ color: 'var(--accent)' }}>Все обсуждения</Link></div>
          </section>
        </div>
      )}

      {errors.length > 0 && <section className="cc-inline-error" role="status"><div><strong>Часть данных не обновилась</strong>{errors.map((error) => <p key={error} className="mt-1 text-sm">{error}</p>)}</div></section>}
    </div>
  )
}

export default Dashboard

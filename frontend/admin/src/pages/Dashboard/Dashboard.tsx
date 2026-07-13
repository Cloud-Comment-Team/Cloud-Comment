import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, CheckCircle2, CircleAlert, Clock3, Globe2, MessageSquareText, Plus, ShieldAlert } from 'lucide-react'
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

function installationLabel(status: SiteInstallationStatus) {
  if (status.status === 'REJECTED') return 'Домен отклонён'
  if (status.status === 'STALE') return 'Нет связи'
  if (status.status === 'NEVER_SEEN') return 'Не подключён'
  return 'Работает'
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
    <div className="cc-page cc-today-page">
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
        <div className="cc-today-layout">
          <div className="cc-today-main">
            <section className="cc-today-panel" aria-labelledby="today-installation-title">
              <header className="cc-today-panel-heading">
                <div>
                  <div className="cc-today-heading-line">
                    <h2 id="today-installation-title">Требует внимания</h2>
                    {model.installationProblems.length > 0 && <span className="cc-today-count">{model.installationProblems.length}</span>}
                  </div>
                  <p>{model.installationProblems.length > 0 ? 'Исправьте подключение — после этого комментарии появятся на сайтах.' : 'Все сайты подключены и отвечают.'}</p>
                </div>
                {model.installationProblems.length > 0
                  ? <CircleAlert className="h-5 w-5" style={{ color: 'var(--status-rejected-accent)' }} />
                  : <CheckCircle2 className="h-5 w-5" style={{ color: 'var(--status-approved-accent)' }} />}
              </header>

              {model.installationProblems.length > 0 ? (
                <div className="cc-today-list">
                  {model.installationProblems.slice(0, 5).map(({ site, status }) => (
                    <Link key={site.id} to={`/sites/${site.id}?section=installation`} className="cc-today-attention-row group">
                      <span className="cc-today-row-icon" aria-hidden="true"><Globe2 className="h-4 w-4" /></span>
                      <span className="min-w-0">
                        <span className="cc-today-row-title">{site.name}</span>
                        <span className="cc-today-row-copy">{installationCopy(status)}</span>
                      </span>
                      <span className="cc-today-row-aside">
                        <span className="cc-today-status">{installationLabel(status)}</span>
                        <span className="cc-today-row-time">{status.lastRejectedAt || status.lastSuccessfulAt ? formatDateTime(status.lastRejectedAt ?? status.lastSuccessfulAt!) : 'Ещё не проверялся'}</span>
                      </span>
                      <ArrowRight className="h-4 w-4 shrink-0 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                    </Link>
                  ))}
                </div>
              ) : (
                <div className="cc-today-calm">Новых проблем с установкой нет.</div>
              )}
            </section>

            <section className="cc-today-panel" aria-labelledby="today-discussions-title">
              <header className="cc-today-panel-heading">
                <div>
                  <h2 id="today-discussions-title">Последние обсуждения</h2>
                  <p>Новые реплики со всех подключённых сайтов.</p>
                </div>
                <MessageSquareText className="h-5 w-5" style={{ color: 'var(--text)' }} />
              </header>
              {model.discussions.length === 0 ? <div className="cc-today-calm">Новых обсуждений пока нет.</div> : (
                <div className="cc-today-list">
                  {model.discussions.map((discussion) => (
                    <Link key={discussion.rootCommentId} to={`/comments?discussion=${discussion.rootCommentId}`} className="cc-today-discussion-row group">
                      <span className="cc-today-row-icon" aria-hidden="true"><MessageSquareText className="h-4 w-4" /></span>
                      <span className="min-w-0">
                        <span className="cc-today-discussion-meta">{discussion.siteName}</span>
                        <span className="cc-today-row-title">{discussionPage(discussion)}</span>
                        <span className="cc-today-row-copy truncate">{discussion.lastAuthor.displayName}: {discussion.lastMessage}</span>
                      </span>
                      <span className="cc-today-row-aside">
                        <Clock3 className="ml-auto h-4 w-4" aria-hidden="true" />
                        <time className="cc-today-row-time">{formatDateTime(discussion.lastActivityAt)}</time>
                      </span>
                      <ArrowRight className="h-4 w-4 shrink-0 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                    </Link>
                  ))}
                </div>
              )}
              <footer className="cc-today-panel-footer"><Link to="/comments">Открыть все обсуждения <ArrowRight className="h-4 w-4" /></Link></footer>
            </section>
          </div>

          <aside className="cc-today-sidebar" aria-label="Быстрые действия">
            <section className={model.queueSize ? 'cc-today-queue is-active' : 'cc-today-queue'} aria-labelledby="today-queue-title">
              <span className="cc-today-queue-icon"><ShieldAlert className="h-5 w-5" /></span>
              <div>
                <p className="cc-today-kicker">Модерация</p>
                <h2 id="today-queue-title">{model.queueSize ? `${model.queueSize} ${model.queueSize === 1 ? 'сообщение' : 'сообщений'} к разбору` : 'Очередь разобрана'}</h2>
                <p>{model.queueSize ? 'Проверьте жалобы и подозрительные комментарии.' : 'Сейчас решений от вас не требуется.'}</p>
              </div>
              <Link to="/moderation" className={model.queueSize ? 'cc-button-primary' : 'cc-button-secondary'}>{model.queueSize ? 'Перейти к разбору' : 'Открыть очередь'}<ArrowRight className="h-4 w-4" /></Link>
            </section>

            <section className="cc-today-shortcuts">
              <h2>Быстрые действия</h2>
              <Link to="/sites/new"><Plus className="h-4 w-4" />Подключить ещё один сайт<ArrowRight className="ml-auto h-4 w-4" /></Link>
              <Link to="/sites"><Globe2 className="h-4 w-4" />Управление сайтами<ArrowRight className="ml-auto h-4 w-4" /></Link>
              <Link to="/analytics"><Clock3 className="h-4 w-4" />Посмотреть аналитику<ArrowRight className="ml-auto h-4 w-4" /></Link>
            </section>
          </aside>
        </div>
      )}

      {errors.length > 0 && <section className="cc-inline-error" role="status"><div><strong>Часть данных не обновилась</strong>{errors.map((error) => <p key={error} className="mt-1 text-sm">{error}</p>)}</div></section>}
    </div>
  )
}

export default Dashboard

import type { DiscussionSummary, ModerationCounts, Site, SiteInstallationStatus } from '../../types/api'

export interface InstallationProblem {
  site: Site
  status: SiteInstallationStatus
}

export interface TodayInput {
  sites: Site[]
  installationStatuses: Map<string, SiteInstallationStatus>
  moderation: ModerationCounts | null
  discussions: DiscussionSummary[]
}

export interface TodayModel {
  installationProblems: InstallationProblem[]
  queueSize: number
  discussions: DiscussionSummary[]
  calm: boolean
}

export function buildTodayModel(input: TodayInput): TodayModel {
  const installationProblems = input.sites
    .map((site) => ({ site, status: input.installationStatuses.get(site.id) }))
    .filter((item): item is InstallationProblem => Boolean(item.status && item.status.status !== 'HEALTHY'))
    .sort((left, right) => installationRank(left.status) - installationRank(right.status))

  const queueSize = input.moderation
    ? (input.moderation.statuses.PENDING ?? 0) + (input.moderation.statuses.SPAM ?? 0)
    : 0
  const discussions = [...input.discussions]
    .sort((left, right) => right.lastActivityAt.localeCompare(left.lastActivityAt))
    .slice(0, 5)

  return {
    installationProblems,
    queueSize,
    discussions,
    calm: input.sites.length > 0 && installationProblems.length === 0 && queueSize === 0,
  }
}

function installationRank(status: SiteInstallationStatus): number {
  if (status.status === 'REJECTED') return 0
  if (status.status === 'NEVER_SEEN') return 1
  if (status.status === 'STALE') return 2
  return 3
}

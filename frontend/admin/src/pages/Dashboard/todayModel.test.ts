import { describe, expect, it } from 'vitest'

import type { DiscussionSummary, ModerationCounts, Site, SiteInstallationStatus } from '../../types/api'
import { buildTodayModel } from './todayModel'

const site = (id: string): Site => ({ id, name: id, domain: `${id}.example`, isActive: true } as Site)
const status = (value: SiteInstallationStatus['status']): SiteInstallationStatus => ({ status: value } as SiteInstallationStatus)
const moderation = (pending: number, spam: number): ModerationCounts => ({
  requiringDecision: pending + spam,
  statuses: { PENDING: pending, SPAM: spam, APPROVED: 0, REJECTED: 0, HIDDEN: 0 },
})

describe('buildTodayModel', () => {
  it('puts rejected installation ahead of stale and never seen sites', () => {
    const model = buildTodayModel({
      sites: [site('stale'), site('new'), site('rejected')],
      installationStatuses: new Map([
        ['stale', status('STALE')],
        ['new', status('NEVER_SEEN')],
        ['rejected', status('REJECTED')],
      ]),
      moderation: moderation(0, 0),
      discussions: [],
    })

    expect(model.installationProblems.map((item) => item.site.id)).toEqual(['rejected', 'new', 'stale'])
  })

  it('reports a calm workspace only when sites are healthy and the queue is empty', () => {
    const model = buildTodayModel({
      sites: [site('healthy')],
      installationStatuses: new Map([['healthy', status('HEALTHY')]]),
      moderation: moderation(0, 0),
      discussions: [{ lastActivityAt: '2026-07-13T12:00:00Z' } as DiscussionSummary],
    })

    expect(model.calm).toBe(true)
    expect(model.queueSize).toBe(0)
  })
})

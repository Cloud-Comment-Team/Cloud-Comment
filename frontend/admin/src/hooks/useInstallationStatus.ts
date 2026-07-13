import { useCallback, useEffect, useRef, useState } from 'react'

import { getApiErrorMessage } from '../api/auth'
import { getInstallationStatus } from '../api/sites'
import type { SiteInstallationStatus } from '../types/api'

export const INSTALLATION_POLL_INTERVAL_MS = 2_000
export const INSTALLATION_POLL_WINDOW_MS = 60_000

type RefreshResult =
  | { ok: true; status: SiteInstallationStatus }
  | { ok: false; error: string }

interface InstallationLoadState {
  siteId: string
  status: SiteInstallationStatus | null
  error: string | null
  loading: boolean
  refreshing: boolean
}

const isTerminalStatus = (status: SiteInstallationStatus | null) => (
  status?.status === 'HEALTHY' || status?.status === 'REJECTED'
)

export const useInstallationStatus = (siteId: string, pollingEnabled: boolean) => {
  const [loadState, setLoadState] = useState<InstallationLoadState>({
    siteId,
    status: null,
    error: null,
    loading: true,
    refreshing: false,
  })
  const [pollingPhase, setPollingPhase] = useState<'idle' | 'polling' | 'paused' | 'timed-out'>('idle')
  const [pollingCycle, setPollingCycle] = useState(0)
  const requestIdRef = useRef(0)
  const statusRef = useRef<SiteInstallationStatus | null>(null)
  const pollingElapsedMsRef = useRef(0)
  const pollingSiteIdRef = useRef(siteId)
  const previousPollingEnabledRef = useRef(false)

  const refresh = useCallback(async (
    retainStatusOnError = false,
    restartPollingOnNonTerminal = false,
  ): Promise<RefreshResult> => {
    const requestId = ++requestIdRef.current
    setLoadState((current) => ({
      siteId,
      status: current.siteId === siteId ? current.status : null,
      error: null,
      loading: current.siteId !== siteId || current.loading,
      refreshing: true,
    }))

    try {
      const nextStatus = await getInstallationStatus(siteId)
      if (requestId === requestIdRef.current) {
        statusRef.current = nextStatus
        setLoadState({ siteId, status: nextStatus, error: null, loading: false, refreshing: false })
        if (restartPollingOnNonTerminal && !isTerminalStatus(nextStatus)) {
          pollingElapsedMsRef.current = 0
          setPollingCycle((cycle) => cycle + 1)
        }
      }
      return { ok: true, status: nextStatus }
    } catch (refreshError) {
      const message = getApiErrorMessage(refreshError, 'Не удалось обновить диагностику установки.')
      if (requestId === requestIdRef.current) {
        const retainedStatus = retainStatusOnError ? statusRef.current : null
        statusRef.current = retainedStatus
        setLoadState({ siteId, status: retainedStatus, error: message, loading: false, refreshing: false })
      }
      return { ok: false, error: message }
    }
  }, [siteId])

  useEffect(() => {
    pollingElapsedMsRef.current = 0
    pollingSiteIdRef.current = siteId
    statusRef.current = null
    const initialRequestTimer = window.setTimeout(() => void refresh(), 0)

    return () => {
      window.clearTimeout(initialRequestTimer)
      requestIdRef.current += 1
    }
  }, [refresh, siteId])

  const currentStatus = loadState.siteId === siteId ? loadState.status : null
  const currentError = loadState.siteId === siteId ? loadState.error : null
  const currentLoading = loadState.siteId === siteId ? loadState.loading : true
  const currentRefreshing = loadState.siteId === siteId ? loadState.refreshing : false
  const terminal = isTerminalStatus(currentStatus)

  useEffect(() => {
    if (!pollingEnabled) {
      previousPollingEnabledRef.current = false
      pollingElapsedMsRef.current = 0
      return
    }

    if (!previousPollingEnabledRef.current || pollingSiteIdRef.current !== siteId) {
      previousPollingEnabledRef.current = true
      pollingSiteIdRef.current = siteId
      pollingElapsedMsRef.current = 0
    }

    if (isTerminalStatus(statusRef.current)) {
      return
    }

    let cancelled = false
    let timerId: number | undefined

    const clearTimer = () => {
      if (timerId !== undefined) {
        window.clearTimeout(timerId)
        timerId = undefined
      }
    }

    const stop = (timedOut = false) => {
      clearTimer()
      setPollingPhase(timedOut ? 'timed-out' : 'idle')
    }

    const schedule = () => {
      clearTimer()
      if (cancelled || isTerminalStatus(statusRef.current)) {
        stop()
        return
      }
      if (document.hidden) {
        setPollingPhase('paused')
        return
      }
      if (pollingElapsedMsRef.current >= INSTALLATION_POLL_WINDOW_MS) {
        stop(true)
        return
      }

      setPollingPhase('polling')
      timerId = window.setTimeout(async () => {
        timerId = undefined
        pollingElapsedMsRef.current += INSTALLATION_POLL_INTERVAL_MS
        const result = await refresh(true)
        if (cancelled || (result.ok && isTerminalStatus(result.status))) {
          stop()
          return
        }
        schedule()
      }, INSTALLATION_POLL_INTERVAL_MS)
    }

    const handleVisibilityChange = () => {
      if (document.hidden) {
        clearTimer()
        setPollingPhase('paused')
      } else {
        schedule()
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    schedule()

    return () => {
      cancelled = true
      clearTimer()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [pollingCycle, pollingEnabled, refresh, siteId, currentStatus?.status])

  return {
    status: currentStatus,
    error: currentError,
    loading: currentLoading,
    refreshing: currentRefreshing,
    polling: pollingEnabled && !terminal && pollingPhase === 'polling',
    pollingPaused: pollingEnabled && !terminal && pollingPhase === 'paused',
    pollingTimedOut: pollingEnabled && !terminal && pollingPhase === 'timed-out',
    refresh,
  }
}

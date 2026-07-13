import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getInstallationStatus } from '../api/sites'
import type { SiteInstallationStatus } from '../types/api'
import {
  INSTALLATION_POLL_INTERVAL_MS,
  INSTALLATION_POLL_WINDOW_MS,
  useInstallationStatus,
} from './useInstallationStatus'

vi.mock('../api/sites', () => ({
  getInstallationStatus: vi.fn(),
}))

const mockedGetInstallationStatus = vi.mocked(getInstallationStatus)
const originalHiddenDescriptor = Object.getOwnPropertyDescriptor(document, 'hidden')

const installationStatus = (
  status: SiteInstallationStatus['status'],
): SiteInstallationStatus => ({
  status,
  reason: status === 'HEALTHY'
    ? 'RECENT_SUCCESS'
    : status === 'REJECTED'
      ? 'ORIGIN_REJECTED'
      : status === 'STALE'
        ? 'SUCCESS_STALE'
        : 'WIDGET_NOT_SEEN',
  siteCreated: true,
  originConfigured: true,
  widgetSeen: status !== 'NEVER_SEEN',
  firstCommentReceived: status === 'HEALTHY',
  lastSuccessfulOrigin: status === 'HEALTHY' ? 'https://example.test' : null,
  lastSuccessfulAt: status === 'HEALTHY' ? '2026-07-13T12:00:00Z' : null,
  lastRejectedOrigin: status === 'REJECTED' ? 'https://wrong.example' : null,
  lastRejectedAt: status === 'REJECTED' ? '2026-07-13T12:00:00Z' : null,
})

const Harness = ({ pollingEnabled = true }: { pollingEnabled?: boolean }) => {
  const result = useInstallationStatus('00000000-0000-0000-0000-000000000176', pollingEnabled)
  return (
    <output>
      <span data-testid="status">{result.status?.status ?? 'LOADING'}</span>
      <span data-testid="polling">{String(result.polling)}</span>
      <span data-testid="paused">{String(result.pollingPaused)}</span>
      <span data-testid="timed-out">{String(result.pollingTimedOut)}</span>
      <button type="button" onClick={() => void result.refresh(false, true)}>Проверить снова</button>
    </output>
  )
}

const flushPromises = async () => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0)
  })
}

describe('useInstallationStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockedGetInstallationStatus.mockReset()
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (originalHiddenDescriptor) {
      Object.defineProperty(document, 'hidden', originalHiddenDescriptor)
    }
  })

  it('обновляет состояние каждые две секунды и останавливается на HEALTHY', async () => {
    mockedGetInstallationStatus
      .mockResolvedValueOnce(installationStatus('NEVER_SEEN'))
      .mockResolvedValueOnce(installationStatus('STALE'))
      .mockResolvedValueOnce(installationStatus('HEALTHY'))

    render(<Harness />)
    await flushPromises()
    expect(screen.getByTestId('status')).toHaveTextContent('NEVER_SEEN')

    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS))
    expect(screen.getByTestId('status')).toHaveTextContent('STALE')

    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS))
    expect(screen.getByTestId('status')).toHaveTextContent('HEALTHY')
    expect(screen.getByTestId('polling')).toHaveTextContent('false')

    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS * 5))
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(3)
  })

  it('не продолжает polling после REJECTED', async () => {
    mockedGetInstallationStatus.mockResolvedValue(installationStatus('REJECTED'))

    render(<Harness />)
    await flushPromises()
    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS * 5))

    expect(screen.getByTestId('status')).toHaveTextContent('REJECTED')
    expect(screen.getByTestId('polling')).toHaveTextContent('false')
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(1)
  })

  it('приостанавливается в скрытой вкладке и продолжает после возвращения', async () => {
    mockedGetInstallationStatus.mockResolvedValue(installationStatus('NEVER_SEEN'))

    render(<Harness />)
    await flushPromises()
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    act(() => document.dispatchEvent(new Event('visibilitychange')))

    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS * 5))
    expect(screen.getByTestId('paused')).toHaveTextContent('true')
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(1)

    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_INTERVAL_MS))

    expect(screen.getByTestId('paused')).toHaveTextContent('false')
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(2)
  })

  it('завершает bounded polling через минуту', async () => {
    mockedGetInstallationStatus.mockResolvedValue(installationStatus('NEVER_SEEN'))

    render(<Harness />)
    await flushPromises()
    await act(async () => vi.advanceTimersByTimeAsync(INSTALLATION_POLL_WINDOW_MS))

    expect(screen.getByTestId('polling')).toHaveTextContent('false')
    expect(screen.getByTestId('timed-out')).toHaveTextContent('true')
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(31)

    fireEvent.click(screen.getByRole('button', { name: 'Проверить снова' }))
    await flushPromises()
    expect(screen.getByTestId('polling')).toHaveTextContent('true')
    expect(screen.getByTestId('timed-out')).toHaveTextContent('false')
    expect(mockedGetInstallationStatus).toHaveBeenCalledTimes(32)
  })
})

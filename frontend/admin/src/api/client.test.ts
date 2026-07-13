import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './client'
import { logout } from './auth'
import { subscribeSessionEvents, type SessionEventType } from '../auth/sessionEvents'
import {
  authBootstrapClient,
  clearCsrfCredentials,
  getCsrfCredentials,
} from './csrf'

const originalApiAdapter = apiClient.defaults.adapter
const originalBootstrapAdapter = authBootstrapClient.defaults.adapter

function response<T>(config: InternalAxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return {
    config,
    data,
    headers: {},
    status,
    statusText: status === 200 ? 'OK' : 'Forbidden',
  }
}

afterEach(() => {
  apiClient.defaults.adapter = originalApiAdapter
  authBootstrapClient.defaults.adapter = originalBootstrapAdapter
  clearCsrfCredentials()
  vi.restoreAllMocks()
})

describe('apiClient cookie и CSRF contract', () => {
  it('дедуплицирует параллельную загрузку CSRF-реквизитов', async () => {
    let resolveRequest: ((value: AxiosResponse) => void) | undefined
    let requestConfig: InternalAxiosRequestConfig | undefined
    const adapter = vi.fn((config: InternalAxiosRequestConfig) => new Promise<AxiosResponse>((resolve) => {
      requestConfig = config
      resolveRequest = resolve
    }))
    authBootstrapClient.defaults.adapter = adapter as AxiosAdapter

    const first = getCsrfCredentials()
    const second = getCsrfCredentials()
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(1))

    if (!requestConfig || !resolveRequest) {
      throw new Error('CSRF-запрос не был создан')
    }
    resolveRequest(response(requestConfig, { headerName: 'X-CSRF-TOKEN', token: 'csrf-one' }))

    await expect(Promise.all([first, second])).resolves.toEqual([
      { headerName: 'X-CSRF-TOKEN', token: 'csrf-one' },
      { headerName: 'X-CSRF-TOKEN', token: 'csrf-one' },
    ])
  })

  it('не добавляет bearer и запрашивает CSRF только для изменяющего запроса', async () => {
    const bootstrapAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      headerName: 'X-CSRF-TOKEN',
      token: 'csrf-token',
    })) as AxiosAdapter
    const apiAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      authorization: config.headers.get('Authorization'),
      csrf: config.headers.get('X-CSRF-TOKEN'),
    })) as AxiosAdapter
    authBootstrapClient.defaults.adapter = bootstrapAdapter
    apiClient.defaults.adapter = apiAdapter

    const readResponse = await apiClient.get('/auth/me')
    const writeResponse = await apiClient.post('/realtime/tickets')

    expect(readResponse.data).toEqual({ authorization: undefined, csrf: undefined })
    expect(writeResponse.data).toEqual({ authorization: undefined, csrf: 'csrf-token' })
    expect(bootstrapAdapter).toHaveBeenCalledTimes(1)
    expect(apiClient.defaults.withCredentials).toBe(true)
    expect(authBootstrapClient.defaults.withCredentials).toBe(true)
  })

  it('один раз обновляет CSRF и безопасно повторяет отклонённый запрос', async () => {
    const csrfTokens = ['expired-csrf', 'fresh-csrf']
    const bootstrapAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      headerName: 'X-CSRF-TOKEN',
      token: csrfTokens.shift(),
    })) as AxiosAdapter
    let attempts = 0
    const apiAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      attempts += 1
      if (attempts === 1) {
        const forbidden = response(config, { error: { code: 'INVALID_CSRF_TOKEN' } }, 403)
        throw new AxiosError('Invalid CSRF token', 'ERR_BAD_REQUEST', config, undefined, forbidden)
      }

      return response(config, { csrf: config.headers.get('X-CSRF-TOKEN') })
    }) as AxiosAdapter
    authBootstrapClient.defaults.adapter = bootstrapAdapter
    apiClient.defaults.adapter = apiAdapter

    await expect(apiClient.patch('/sites/site-id', { name: 'Новое название' })).resolves.toMatchObject({
      data: { csrf: 'fresh-csrf' },
    })
    expect(apiAdapter).toHaveBeenCalledTimes(2)
    expect(bootstrapAdapter).toHaveBeenCalledTimes(2)
  })

  it('после logout заново получает CSRF для следующей сессии', async () => {
    let csrfIndex = 0
    const bootstrapAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      headerName: 'X-CSRF-TOKEN',
      token: `csrf-${++csrfIndex}`,
    })) as AxiosAdapter
    const apiAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      csrf: config.headers.get('X-CSRF-TOKEN'),
    })) as AxiosAdapter
    authBootstrapClient.defaults.adapter = bootstrapAdapter
    apiClient.defaults.adapter = apiAdapter

    await apiClient.post('/sites', { name: 'Первая сессия' })
    await logout()
    const nextSessionResponse = await apiClient.post('/sites', { name: 'Следующая сессия' })

    expect(nextSessionResponse.data).toEqual({ csrf: 'csrf-2' })
    expect(bootstrapAdapter).toHaveBeenCalledTimes(2)
  })

  it('не завершает cookie-сессию из-за неверного пароля, но реагирует на INVALID_SESSION', async () => {
    const sessionEvents: SessionEventType[] = []
    const unsubscribe = subscribeSessionEvents((event) => sessionEvents.push(event))
    let errorCode = 'INVALID_CREDENTIALS'
    apiClient.defaults.adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      const unauthorized = response(config, { error: { code: errorCode } }, 401)
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, unauthorized)
    }) as AxiosAdapter

    await expect(apiClient.get('/auth/login-attempt')).rejects.toBeInstanceOf(AxiosError)
    expect(sessionEvents).toEqual([])

    errorCode = 'INVALID_SESSION'
    await expect(apiClient.get('/auth/me')).rejects.toBeInstanceOf(AxiosError)
    expect(sessionEvents).toEqual(['expired'])
    unsubscribe()
  })
})

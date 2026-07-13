import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { notifySessionExpired } from '../auth/sessionEvents'
import { API_BASE_URL } from '../config/env'
import { getCsrfCredentials } from './csrf'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _csrfRetry?: boolean
}

const SAFE_METHODS = new Set(['get', 'head', 'options'])

function isUnsafeRequest(config: Pick<InternalAxiosRequestConfig, 'method'>): boolean {
  return !SAFE_METHODS.has((config.method ?? 'get').toLowerCase())
}

function getErrorCode(error: AxiosError): unknown {
  if (typeof error.response?.data !== 'object' || error.response.data === null) {
    return null
  }

  const response = error.response.data as {
    code?: unknown
    error?: { code?: unknown }
  }
  return response.code ?? response.error?.code
}

function isInvalidCsrfToken(error: AxiosError): boolean {
  return error.response?.status === 403 && getErrorCode(error) === 'INVALID_CSRF_TOKEN'
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

apiClient.interceptors.request.use(async (config) => {
  if (isUnsafeRequest(config)) {
    const csrf = await getCsrfCredentials()
    config.headers.set(csrf.headerName, csrf.token)
  }

  return config
})

apiClient.interceptors.response.use(undefined, async (error: unknown) => {
  if (!axios.isAxiosError(error) || !error.config) {
    return Promise.reject(error)
  }

  if (error.response?.status === 401 && getErrorCode(error) === 'INVALID_SESSION') {
    notifySessionExpired()
  }

  const config = error.config as RetryableRequestConfig
  if (!isUnsafeRequest(config) || config._csrfRetry || !isInvalidCsrfToken(error)) {
    return Promise.reject(error)
  }

  // INVALID_CSRF_TOKEN отклоняется до выполнения операции, поэтому один повтор
  // после обновления реквизитов не дублирует изменяющий запрос.
  config._csrfRetry = true
  const csrf = await getCsrfCredentials(true)
  config.headers.set(csrf.headerName, csrf.token)
  return apiClient.request(config)
})

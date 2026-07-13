import axios from 'axios'

import { API_BASE_URL } from '../config/env'

export interface CsrfCredentials {
  headerName: string
  token: string
}

export const authBootstrapClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

let credentials: CsrfCredentials | null = null
let credentialsPromise: Promise<CsrfCredentials> | null = null

function isCsrfCredentials(value: unknown): value is CsrfCredentials {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<CsrfCredentials>
  return typeof candidate.headerName === 'string'
    && candidate.headerName.trim().length > 0
    && typeof candidate.token === 'string'
    && candidate.token.trim().length > 0
}

export function clearCsrfCredentials(): void {
  credentials = null
}

export async function getCsrfCredentials(forceRefresh = false): Promise<CsrfCredentials> {
  if (!forceRefresh && credentials) {
    return credentials
  }

  if (credentialsPromise) {
    return credentialsPromise
  }

  if (forceRefresh) {
    credentials = null
  }

  credentialsPromise = authBootstrapClient
    .get<unknown>('/auth/csrf')
    .then((response) => {
      if (!isCsrfCredentials(response.data)) {
        throw new Error('Сервер вернул некорректные CSRF-реквизиты.')
      }

      credentials = {
        headerName: response.data.headerName.trim(),
        token: response.data.token,
      }
      return credentials
    })
    .finally(() => {
      credentialsPromise = null
    })

  return credentialsPromise
}

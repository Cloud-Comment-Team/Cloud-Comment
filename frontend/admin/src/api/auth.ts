import { apiClient } from './client'
import { clearCsrfCredentials } from './csrf'
import type { AuthUser, LoginResponse } from '../types'

export interface LoginCredentials {
  email: string
  password: string
}

export interface RegisterCredentials {
  email: string
  password: string
  acceptedPrivacyPolicy: boolean
  acceptedTerms: boolean
  privacyPolicyVersion: string
  termsVersion: string
}

export type RegisterResponse = AuthUser

interface ApiErrorResponse {
  error?: {
    code?: string
    message?: string
    fields?: Array<{
      field: string
      message: string
    }>
  }
}

export async function login(credentials: LoginCredentials): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', credentials)
  return response.data
}

export async function register(credentials: RegisterCredentials): Promise<RegisterResponse> {
  const response = await apiClient.post<RegisterResponse>('/auth/register', credentials)
  return response.data
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await apiClient.get<AuthUser>('/auth/me')
  return response.data
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post('/auth/logout')
  } finally {
    clearCsrfCredentials()
  }
}

export function getApiErrorCode(error: unknown): string | null {
  if (!isApiError(error)) {
    return null
  }
  return error.response?.data?.error?.code ?? null
}

export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (!isApiError(error)) {
    return fallbackMessage
  }

  const data = error.response?.data

  if (data?.error?.fields?.length) {
    return data.error.fields.map((field) => field.message).join(' ')
  }

  return data?.error?.message || fallbackMessage
}

function isApiError(error: unknown): error is { response?: { data?: ApiErrorResponse } } {
  return typeof error === 'object' && error !== null && 'response' in error
}

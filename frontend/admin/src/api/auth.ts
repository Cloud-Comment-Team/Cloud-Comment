import { apiClient } from './client'
import type { AuthUser, LoginResponse } from '../types'

interface LoginCredentials {
  email: string
  password: string
}

export async function login(credentials: LoginCredentials): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', credentials)
  return response.data
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response = await apiClient.get<AuthUser>('/auth/me')
  return response.data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

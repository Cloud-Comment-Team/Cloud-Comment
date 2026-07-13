import { StrictMode } from 'react'
import { render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'

import type { AuthUser } from '../../types'

const authApi = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../../api/auth', () => authApi)

import { useAuthStore } from '../../store'
import { notifySessionExpired } from '../../auth/sessionEvents'
import { AuthBootstrap } from './AuthBootstrap'

const user: AuthUser = {
  id: '00000000-0000-0000-0000-000000000167',
  email: 'owner@example.test',
  roles: ['OWNER'],
  createdAt: '2026-07-13T00:00:00Z',
  updatedAt: '2026-07-13T00:00:00Z',
}

beforeEach(() => {
  authApi.getCurrentUser.mockReset()
  useAuthStore.setState({ user: null, status: 'checking' })
  window.localStorage.clear()
})

it('проверяет cookie-сессию один раз и удаляет старый localStorage-токен', async () => {
  authApi.getCurrentUser.mockResolvedValue(user)
  window.localStorage.setItem('cloud-comment.admin.authToken', 'legacy-bearer')

  render(
    <StrictMode>
      <AuthBootstrap>
        <p>Панель готова</p>
      </AuthBootstrap>
    </StrictMode>,
  )

  expect(window.localStorage.getItem('cloud-comment.admin.authToken')).toBeNull()
  expect(await screen.findByText('Панель готова')).toBeVisible()
  expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1)
})

it('очищает локальную auth-модель при истечении HttpOnly-сессии', async () => {
  authApi.getCurrentUser.mockResolvedValue(user)
  render(
    <AuthBootstrap>
      <p>Панель готова</p>
    </AuthBootstrap>,
  )
  expect(await screen.findByText('Панель готова')).toBeVisible()

  notifySessionExpired()

  expect(useAuthStore.getState()).toMatchObject({ user: null, status: 'unauthenticated' })
})

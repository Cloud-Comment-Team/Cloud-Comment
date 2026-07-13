import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthUser, LoginResponse } from '../types'

const authApi = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../api/auth', () => authApi)

import { useAuthStore } from './index'

const user: AuthUser = {
  id: '00000000-0000-0000-0000-000000000167',
  email: 'owner@example.test',
  roles: ['OWNER'],
  createdAt: '2026-07-13T00:00:00Z',
  updatedAt: '2026-07-13T00:00:00Z',
}

beforeEach(() => {
  authApi.getCurrentUser.mockReset()
  authApi.login.mockReset()
  authApi.logout.mockReset()
  useAuthStore.setState({ user: null, status: 'checking' })
})

describe('useAuthStore cookie session', () => {
  it('дедуплицирует проверку серверной сессии и сохраняет только пользователя', async () => {
    authApi.getCurrentUser.mockResolvedValue(user)

    await Promise.all([
      useAuthStore.getState().checkAuth(),
      useAuthStore.getState().checkAuth(),
    ])

    expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1)
    expect(useAuthStore.getState()).toMatchObject({ user, status: 'authenticated' })
    expect(useAuthStore.getState()).not.toHaveProperty('token')
  })

  it('после account switch дожидается новой проверки и не восстанавливает устаревшего пользователя', async () => {
    const nextUser: AuthUser = { ...user, id: '00000000-0000-0000-0000-000000000168', email: 'new-owner@example.test' }
    let resolveStaleCheck: ((value: AuthUser) => void) | undefined
    authApi.getCurrentUser
      .mockImplementationOnce(() => new Promise<AuthUser>((resolve) => {
        resolveStaleCheck = resolve
      }))
      .mockResolvedValueOnce(nextUser)
    useAuthStore.setState({ user, status: 'authenticated' })

    const staleCheck = useAuthStore.getState().checkAuth({ silent: true })
    const forcedCheck = useAuthStore.getState().checkAuth({ force: true })
    expect(useAuthStore.getState()).toMatchObject({ user: null, status: 'checking' })

    resolveStaleCheck?.(user)
    await Promise.all([staleCheck, forcedCheck])

    expect(authApi.getCurrentUser).toHaveBeenCalledTimes(2)
    expect(useAuthStore.getState()).toMatchObject({ user: nextUser, status: 'authenticated' })
  })

  it('считает отсутствующую или отозванную cookie-сессию неаутентифицированной', async () => {
    authApi.getCurrentUser.mockRejectedValue(new Error('Unauthorized'))

    await useAuthStore.getState().checkAuth()

    expect(useAuthStore.getState()).toMatchObject({ user: null, status: 'unauthenticated' })
  })

  it('входит и выходит без сохранения bearer-токена', async () => {
    const loginResponse: LoginResponse = {
      expiresAt: '2026-07-14T00:00:00Z',
      user,
    }
    authApi.login.mockResolvedValue(loginResponse)
    authApi.logout.mockResolvedValue(undefined)

    await useAuthStore.getState().login({ email: user.email, password: 'Password123!' })
    expect(useAuthStore.getState()).toMatchObject({ user, status: 'authenticated' })
    expect(useAuthStore.getState()).not.toHaveProperty('token')

    await useAuthStore.getState().logout()
    expect(useAuthStore.getState()).toMatchObject({ user: null, status: 'unauthenticated' })
  })

  it('не изображает выход, если сервер не отозвал HttpOnly-сессию', async () => {
    useAuthStore.setState({ user, status: 'authenticated' })
    authApi.logout.mockRejectedValue(new Error('Network error'))

    await expect(useAuthStore.getState().logout()).rejects.toThrow('Network error')

    expect(useAuthStore.getState()).toMatchObject({ user, status: 'authenticated' })
  })

  it('локально очищает состояние после удаления аккаунта', () => {
    useAuthStore.setState({ user, status: 'authenticated' })

    useAuthStore.getState().clearAuth()

    expect(useAuthStore.getState()).toMatchObject({ user: null, status: 'unauthenticated' })
  })
})

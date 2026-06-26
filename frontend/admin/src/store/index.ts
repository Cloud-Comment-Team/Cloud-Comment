import { create } from 'zustand'

import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'
import { getStoredAuthToken, removeStoredAuthToken, storeAuthToken } from '../auth/tokenStorage'
import type { AuthUser } from '../types'

type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated'

interface LoginCredentials {
  email: string
  password: string
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  status: AuthStatus
  login: (credentials: LoginCredentials) => Promise<void>
  checkAuth: () => Promise<void>
  logout: () => Promise<void>
}

const initialToken = getStoredAuthToken()

export const useAuthStore = create<AuthState>((set, get) => ({
  token: initialToken,
  user: null,
  status: initialToken ? 'checking' : 'unauthenticated',

  async login(credentials) {
    const response = await loginRequest(credentials)

    storeAuthToken(response.token)
    set({
      token: response.token,
      user: response.user,
      status: 'authenticated',
    })
  },

  async checkAuth() {
    const token = get().token ?? getStoredAuthToken()

    if (!token) {
      removeStoredAuthToken()
      set({ token: null, user: null, status: 'unauthenticated' })
      return
    }

    set({ token, status: 'checking' })

    try {
      const user = await getCurrentUser()
      set({ token, user, status: 'authenticated' })
    } catch {
      removeStoredAuthToken()
      set({ token: null, user: null, status: 'unauthenticated' })
    }
  },

  async logout() {
    try {
      await logoutRequest()
    } finally {
      removeStoredAuthToken()
      set({ token: null, user: null, status: 'unauthenticated' })
    }
  },
}))

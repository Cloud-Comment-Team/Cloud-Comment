import { create } from 'zustand'

import { getApiErrorCode, getCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'
import { notifySessionChanged, notifySessionExpired } from '../auth/sessionEvents'
import type { AuthUser } from '../types'

type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated'

interface LoginCredentials {
  email: string
  password: string
}

interface AuthCheckOptions {
  force?: boolean
  silent?: boolean
}

interface AuthState {
  user: AuthUser | null
  status: AuthStatus
  login: (credentials: LoginCredentials) => Promise<void>
  checkAuth: (options?: AuthCheckOptions) => Promise<void>
  logout: () => Promise<void>
  clearAuth: () => void
}

let authCheckPromise: Promise<void> | null = null
let queuedAuthCheckPromise: Promise<void> | null = null
let authRevision = 0
let completedAuthRevision = -1

export const useAuthStore = create<AuthState>((set, get) => {
  const startAuthCheck = (silent: boolean): Promise<void> => {
    const requestRevision = authRevision
    const request = getCurrentUser()
      .then((user) => {
        if (requestRevision === authRevision) {
          set({ user, status: 'authenticated' })
        }
      })
      .catch((error: unknown) => {
        if (requestRevision !== authRevision) {
          return
        }
        if (silent && get().status !== 'checking' && getApiErrorCode(error) !== 'INVALID_SESSION') {
          return
        }
        set({ user: null, status: 'unauthenticated' })
      })
      .finally(() => {
        completedAuthRevision = Math.max(completedAuthRevision, requestRevision)
        if (authCheckPromise === request) {
          authCheckPromise = null
        }
      })

    authCheckPromise = request
    return request
  }

  const queueForcedAuthCheck = (silent: boolean): Promise<void> => {
    if (!queuedAuthCheckPromise) {
      const pendingCheck = authCheckPromise
      queuedAuthCheckPromise = (async () => {
        if (pendingCheck) {
          await pendingCheck
        }
        while (completedAuthRevision < authRevision) {
          await startAuthCheck(silent)
        }
      })().finally(() => {
        queuedAuthCheckPromise = null
      })
    }

    return queuedAuthCheckPromise
  }

  return {
    user: null,
    status: 'checking',

    async login(credentials) {
      const response = await loginRequest(credentials)

      authRevision += 1
      set({
        user: response.user,
        status: 'authenticated',
      })
      notifySessionChanged()
    },

    async checkAuth(options) {
      const force = options?.force === true
      if (force) {
        authRevision += 1
      }

      if (!options?.silent) {
        set({ user: null, status: 'checking' })
      } else if (get().status === 'checking') {
        set({ status: 'checking' })
      }

      if (queuedAuthCheckPromise) {
        return queuedAuthCheckPromise
      }

      if (authCheckPromise) {
        return force ? queueForcedAuthCheck(options?.silent === true) : authCheckPromise
      }

      return startAuthCheck(options?.silent === true)
    },

    async logout() {
      await logoutRequest()
      authRevision += 1
      set({ user: null, status: 'unauthenticated' })
      notifySessionExpired()
    },

    clearAuth() {
      authRevision += 1
      set({ user: null, status: 'unauthenticated' })
    },
  }
})

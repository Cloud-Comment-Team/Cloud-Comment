import { createContext } from 'react'

import type { RealtimeEvent } from '../../types/api'

export type RealtimeConnectionStatus = 'connecting' | 'connected' | 'disconnected'

export type RealtimeListener = (event: RealtimeEvent) => void

export interface RealtimeContextValue {
  connectionStatus: RealtimeConnectionStatus
  subscribe: (listener: RealtimeListener) => () => void
}

export const RealtimeContext = createContext<RealtimeContextValue | null>(null)

import { useContext, useEffect, useRef } from 'react'

import type { RealtimeEvent } from '../../types/api'
import { RealtimeContext, type RealtimeConnectionStatus, type RealtimeContextValue } from './realtimeContext'

export function useRealtimeConnectionStatus(): RealtimeConnectionStatus {
  return useRealtimeContext().connectionStatus
}

export function useRealtimeEvent(handler: (event: RealtimeEvent) => void) {
  const { subscribe } = useRealtimeContext()
  const handlerRef = useRef(handler)

  useEffect(() => {
    handlerRef.current = handler
  }, [handler])

  useEffect(() => subscribe((event) => handlerRef.current(event)), [subscribe])
}

function useRealtimeContext(): RealtimeContextValue {
  const context = useContext(RealtimeContext)
  if (!context) {
    throw new Error('RealtimeProvider is required')
  }
  return context
}

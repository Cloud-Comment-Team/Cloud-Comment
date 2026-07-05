import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { getRealtimeWebSocketUrl } from '../../config/env'
import { useAuthStore } from '../../store'
import type { RealtimeEvent } from '../../types/api'
import { RealtimeContext, type RealtimeConnectionStatus, type RealtimeListener } from './realtimeContext'

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  const authStatus = useAuthStore((state) => state.status)
  const [connectionStatus, setConnectionStatus] = useState<RealtimeConnectionStatus>('disconnected')
  const listenersRef = useRef(new Set<RealtimeListener>())
  const reconnectTimerRef = useRef<number | null>(null)
  const shouldReconnectRef = useRef(true)

  const subscribe = useCallback((listener: RealtimeListener) => {
    listenersRef.current.add(listener)
    return () => {
      listenersRef.current.delete(listener)
    }
  }, [])

  useEffect(() => {
    if (authStatus !== 'authenticated' || !token) {
      return
    }

    let websocket: WebSocket | null = null
    let reconnectAttempt = 0
    shouldReconnectRef.current = true

    const clearReconnectTimer = () => {
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
    }

    const dispatchEvent = (rawMessage: string) => {
      let event: RealtimeEvent
      try {
        event = JSON.parse(rawMessage) as RealtimeEvent
      } catch {
        return
      }

      if (!event || typeof event.type !== 'string') {
        return
      }

      listenersRef.current.forEach((listener) => listener(event))
    }

    const connect = () => {
      clearReconnectTimer()
      setConnectionStatus('connecting')
      websocket = new WebSocket(getRealtimeWebSocketUrl(token))

      websocket.addEventListener('open', () => {
        reconnectAttempt = 0
        setConnectionStatus('connected')
      })

      websocket.addEventListener('message', (event) => {
        dispatchEvent(event.data)
      })

      websocket.addEventListener('close', () => {
        setConnectionStatus('disconnected')
        if (!shouldReconnectRef.current) {
          return
        }

        reconnectAttempt += 1
        const delay = Math.min(1000 * 2 ** reconnectAttempt, 10000)
        reconnectTimerRef.current = window.setTimeout(connect, delay)
      })

      websocket.addEventListener('error', () => {
        websocket?.close()
      })
    }

    connect()

    return () => {
      shouldReconnectRef.current = false
      clearReconnectTimer()
      websocket?.close()
    }
  }, [authStatus, token])

  const value = useMemo(
    () => ({
      connectionStatus,
      subscribe,
    }),
    [connectionStatus, subscribe],
  )

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

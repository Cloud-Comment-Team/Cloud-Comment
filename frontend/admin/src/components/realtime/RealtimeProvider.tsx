import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { createRealtimeTicket } from '../../api/realtime'
import { getRealtimeWebSocketUrl } from '../../config/env'
import { useAuthStore } from '../../store'
import type { RealtimeEvent } from '../../types/api'
import { RealtimeContext, type RealtimeConnectionStatus, type RealtimeListener } from './realtimeContext'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasStringFields(value: Record<string, unknown>, fields: string[]): boolean {
  return fields.every((field) => typeof value[field] === 'string')
}

function isRealtimeEvent(value: unknown): value is RealtimeEvent {
  if (!isRecord(value) || !isRecord(value.payload)) {
    return false
  }
  if (value.sentAt !== undefined && typeof value.sentAt !== 'string') {
    return false
  }

  if (value.type === 'comment.created') {
    return hasStringFields(value.payload, [
      'commentId',
      'siteId',
      'siteName',
      'pageId',
      'pageUrl',
      'contentPreview',
      'status',
      'createdAt',
    ])
  }

  if (value.type === 'comment.moderation_action_applied') {
    return hasStringFields(value.payload, [
      'siteId',
      'pageId',
      'commentId',
      'action',
      'fromStatus',
      'toStatus',
      'moderatorId',
      'createdAt',
    ])
  }

  return false
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  const authStatus = useAuthStore((state) => state.status)
  const [connectionStatus, setConnectionStatus] = useState<RealtimeConnectionStatus>('disconnected')
  const listenersRef = useRef(new Set<RealtimeListener>())
  const reconnectTimerRef = useRef<number | null>(null)

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
    let disposed = false

    const clearReconnectTimer = () => {
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
    }

    const dispatchEvent = (rawMessage: string) => {
      let event: unknown
      try {
        event = JSON.parse(rawMessage)
      } catch {
        return
      }

      if (!isRealtimeEvent(event)) {
        return
      }

      listenersRef.current.forEach((listener) => listener(event))
    }

    const scheduleReconnect = () => {
      if (disposed) {
        return
      }

      reconnectAttempt += 1
      const delay = Math.min(1000 * 2 ** reconnectAttempt, 10000)
      reconnectTimerRef.current = window.setTimeout(() => void connect(), delay)
    }

    const connect = async () => {
      clearReconnectTimer()
      setConnectionStatus('connecting')
      try {
        const { ticket } = await createRealtimeTicket()
        if (disposed) {
          return
        }
        websocket = new WebSocket(getRealtimeWebSocketUrl(ticket))
      } catch {
        setConnectionStatus('disconnected')
        scheduleReconnect()
        return
      }

      websocket.addEventListener('open', () => {
        reconnectAttempt = 0
        setConnectionStatus('connected')
      })

      websocket.addEventListener('message', (event) => {
        dispatchEvent(event.data)
      })

      websocket.addEventListener('close', () => {
        setConnectionStatus('disconnected')
        scheduleReconnect()
      })

      websocket.addEventListener('error', () => {
        websocket?.close()
      })
    }

    void connect()

    return () => {
      disposed = true
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

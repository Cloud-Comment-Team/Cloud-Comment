export type SessionEventType = 'changed' | 'expired'

type SessionEventListener = (type: SessionEventType) => void

interface SessionMessage {
  kind: 'cloud-comment-admin-session'
  id: string
  sourceId: string
  type: SessionEventType
}

const CHANNEL_NAME = 'cloud-comment.admin.session'
const STORAGE_EVENT_KEY = 'cloud-comment.admin.session-event'
const listeners = new Set<SessionEventListener>()
const sourceId = typeof crypto !== 'undefined' && 'randomUUID' in crypto
  ? crypto.randomUUID()
  : `${Date.now()}-${Math.random()}`

let channel: BroadcastChannel | null = null
let storageListenerInstalled = false

function isSessionMessage(value: unknown): value is SessionMessage {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const message = value as Partial<SessionMessage>
  return message.kind === 'cloud-comment-admin-session'
    && typeof message.id === 'string'
    && typeof message.sourceId === 'string'
    && (message.type === 'changed' || message.type === 'expired')
}

function dispatch(type: SessionEventType): void {
  listeners.forEach((listener) => {
    try {
      listener(type)
    } catch {
      // Одна сломанная подписка не должна блокировать остальные вкладки.
    }
  })
}

function receive(message: unknown): void {
  if (!isSessionMessage(message) || message.sourceId === sourceId) {
    return
  }
  dispatch(message.type)
}

function onStorage(event: StorageEvent): void {
  if (event.key !== STORAGE_EVENT_KEY || !event.newValue) {
    return
  }

  try {
    receive(JSON.parse(event.newValue))
  } catch {
    // Повреждённое или постороннее storage-событие не влияет на сессию.
  }
}

function ensureTransport(): void {
  if (typeof window === 'undefined') {
    return
  }

  if ('BroadcastChannel' in window) {
    try {
      if (!channel) {
        channel = new window.BroadcastChannel(CHANNEL_NAME)
        channel.addEventListener('message', (event) => receive(event.data))
      }
      return
    } catch {
      channel = null
    }
  }

  if (!storageListenerInstalled) {
    window.addEventListener('storage', onStorage)
    storageListenerInstalled = true
  }
}

function publish(type: SessionEventType): void {
  ensureTransport()
  if (typeof window === 'undefined') {
    return
  }
  const message: SessionMessage = {
    kind: 'cloud-comment-admin-session',
    id: typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
    sourceId,
    type,
  }

  if (channel) {
    try {
      channel.postMessage(message)
      return
    } catch {
      channel.close()
      channel = null
    }
  }

  try {
    window.localStorage.setItem(STORAGE_EVENT_KEY, JSON.stringify(message))
    window.localStorage.removeItem(STORAGE_EVENT_KEY)
  } catch {
    // Синхронизация между вкладками — best effort; сессионные данные не сохраняются.
  }
}

export function notifySessionChanged(): void {
  publish('changed')
}

export function notifySessionExpired(): void {
  dispatch('expired')
  publish('expired')
}

export function subscribeSessionEvents(listener: SessionEventListener): () => void {
  ensureTransport()
  listeners.add(listener)
  return () => listeners.delete(listener)
}

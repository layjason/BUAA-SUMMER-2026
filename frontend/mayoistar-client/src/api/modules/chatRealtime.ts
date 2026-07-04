/**
 * 聊天 WebSocket 客户端
 *
 * Mock 模式走内存事件总线；真实模式连接 `/chat/ws/messages`。
 */
import { API_BASE_URL, USE_MOCK } from '@/api/config'
import {
  connectMockChatRealtime,
  subscribeMockChatRealtime,
  type ChatRealtimeEvent,
} from '@/mock/chatRealtimeBus'

export type { ChatRealtimeEvent }

export type ChatRealtimeHandler = (event: ChatRealtimeEvent) => void

function toWebSocketUrl(baseUrl: string): string {
  const normalized = baseUrl.replace(/\/+$/, '')
  const wsBase = normalized.replace(/^http/i, 'ws')
  return `${wsBase}/chat/ws/messages`
}

/**
 * 建立聊天实时连接
 *
 * @returns disconnect 断开并取消订阅
 */
export function connectChatRealtime(
  userId: string,
  token: string | null,
  onEvent: ChatRealtimeHandler,
): () => void {
  const numericUserId = Number(userId)
  if (!userId || Number.isNaN(numericUserId)) {
    return () => {}
  }

  if (USE_MOCK) {
    const releaseConnection = connectMockChatRealtime(numericUserId)
    const unsubscribe = subscribeMockChatRealtime(numericUserId, onEvent)
    return () => {
      unsubscribe()
      releaseConnection()
    }
  }

  const url = toWebSocketUrl(API_BASE_URL)
  const socket = uni.connectSocket({
    url,
    header: token ? { Authorization: `Bearer ${token}` } : {},
    complete: () => {},
  })

  socket.onMessage((res) => {
    try {
      const raw = typeof res.data === 'string' ? res.data : String(res.data)
      const event = JSON.parse(raw) as ChatRealtimeEvent
      onEvent(event)
    } catch {
      // 忽略无法解析的帧
    }
  })

  return () => {
    socket.close({})
  }
}

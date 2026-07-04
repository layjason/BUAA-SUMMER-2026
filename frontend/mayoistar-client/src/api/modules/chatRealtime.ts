/**
 * 聊天 / 社交实时连接模块
 *
 * Mock 模式走内存事件总线；真实模式通过 STOMP 连接 `/chat/ws/messages`。
 */
import { API_BASE_URL, USE_MOCK } from '@/api/config'
import {
  connectStompRealtime,
  type ChatRealtimeEvent,
  type FriendRequest,
} from '@/api/stomp/connectStomp'
import { toStompWebSocketUrl } from '@/api/stomp/uniStompSocket'
import {
  connectMockChatRealtime,
  subscribeMockChatRealtime,
  subscribeMockSocialRealtime,
} from '@/mock/chatRealtimeBus'

export type { ChatRealtimeEvent, FriendRequest }

export type ChatRealtimeHandler = (event: ChatRealtimeEvent) => void
export type SocialRealtimeHandler = (request: FriendRequest) => void

export interface RealtimeConnectionHandlers {
  onChatEvent: ChatRealtimeHandler
  onSocialEvent?: SocialRealtimeHandler
}

function normalizeHandlers(
  handlers: RealtimeConnectionHandlers | ChatRealtimeHandler,
): RealtimeConnectionHandlers {
  return typeof handlers === 'function' ? { onChatEvent: handlers } : handlers
}

/**
 * 建立实时连接（mock 总线或 STOMP WebSocket）。
 *
 * @returns disconnect 断开并取消订阅
 */
export function connectChatRealtime(
  userId: string,
  token: string | null,
  handlers: RealtimeConnectionHandlers | ChatRealtimeHandler,
): () => void {
  const resolved = normalizeHandlers(handlers)
  const numericUserId = Number(userId)
  if (!userId || Number.isNaN(numericUserId)) {
    return () => {}
  }

  if (USE_MOCK) {
    const releaseConnection = connectMockChatRealtime(numericUserId)
    const unsubscribeChat = subscribeMockChatRealtime(numericUserId, resolved.onChatEvent)
    const unsubscribeSocial = resolved.onSocialEvent
      ? subscribeMockSocialRealtime(numericUserId, resolved.onSocialEvent)
      : () => {}

    return () => {
      unsubscribeChat()
      unsubscribeSocial()
      releaseConnection()
    }
  }

  const wsUrl = toStompWebSocketUrl(API_BASE_URL)
  return connectStompRealtime(wsUrl, token, resolved)
}

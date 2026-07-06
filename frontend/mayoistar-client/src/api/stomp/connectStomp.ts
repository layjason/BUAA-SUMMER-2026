/**
 * STOMP over WebSocket 客户端，对齐后端 Spring 消息代理约定。
 */
import { Client } from '@stomp/stompjs'
import type { components } from '@/api/types/schema'
import { createStompWebSocket, type StompWebSocketHeaders } from './uniStompSocket'

export type ChatRealtimeEvent = components['schemas']['Chat.ChatRealtimeEvent']
export type FriendRequest = components['schemas']['Social.FriendRequest']

/** 与后端 WebSocketNotificationService 中的目的地一致 */
export const STOMP_CHAT_EVENTS_DEST = '/user/queue/chat-events'
export const STOMP_SOCIAL_EVENTS_DEST = '/user/queue/social-events'

export interface StompRealtimeHandlers {
  onChatEvent: (event: ChatRealtimeEvent) => void
  onSocialEvent?: (request: FriendRequest) => void
}

/**
 * 解析 STOMP MESSAGE body 为 JSON 对象。
 */
export function parseStompMessageBody<T>(body: string): T | null {
  try {
    return JSON.parse(body) as T
  } catch {
    return null
  }
}

/**
 * 构建实时连接鉴权头。
 *
 * 前置条件：token 来自认证 Store，可能为空或已被清除。
 * 后置条件：有 token 时返回 Bearer 头；无 token 时返回 undefined。
 * 不变量：HTTP WebSocket 握手头与 STOMP CONNECT 头使用同一份 token。
 */
export function buildStompAuthHeaders(token: string | null): StompWebSocketHeaders | undefined {
  const trimmed = token?.trim()
  return trimmed ? { Authorization: `Bearer ${trimmed}` } : undefined
}

/**
 * 建立 STOMP 实时连接并订阅聊天/社交私有队列。
 *
 * @returns disconnect 断开连接并释放订阅
 */
export function connectStompRealtime(
  wsUrl: string,
  token: string | null,
  handlers: StompRealtimeHandlers,
): () => void {
  const authHeaders = buildStompAuthHeaders(token)
  const client = new Client({
    webSocketFactory: () => createStompWebSocket(wsUrl, authHeaders),
    connectHeaders: authHeaders ?? {},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(STOMP_CHAT_EVENTS_DEST, (message) => {
        const event = parseStompMessageBody<ChatRealtimeEvent>(message.body)
        if (event) handlers.onChatEvent(event)
      })

      if (handlers.onSocialEvent) {
        client.subscribe(STOMP_SOCIAL_EVENTS_DEST, (message) => {
          const request = parseStompMessageBody<FriendRequest>(message.body)
          if (request) handlers.onSocialEvent!(request)
        })
      }
    },
  })

  client.activate()

  return () => {
    void client.deactivate()
  }
}

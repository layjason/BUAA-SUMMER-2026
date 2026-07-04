/**
 * Mock 实时事件总线
 *
 * 模拟 STOMP 订阅语义：在线用户分别接收 chat-events 与 social-events 推送。
 */
import type { components } from '@/api/types/schema'

export type ChatRealtimeEvent = components['schemas']['Chat.ChatRealtimeEvent']
export type FriendRequest = components['schemas']['Social.FriendRequest']

type ChatListener = (event: ChatRealtimeEvent) => void
type SocialListener = (request: FriendRequest) => void

const chatListenersByUser = new Map<number, Set<ChatListener>>()
const socialListenersByUser = new Map<number, Set<SocialListener>>()
const connectionCounts = new Map<number, number>()

/**
 * 建立 mock 连接（引用计数，支持多页面/组件复用）。
 */
export function connectMockChatRealtime(userId: number): () => void {
  connectionCounts.set(userId, (connectionCounts.get(userId) ?? 0) + 1)
  return () => {
    const next = (connectionCounts.get(userId) ?? 1) - 1
    if (next <= 0) {
      connectionCounts.delete(userId)
    } else {
      connectionCounts.set(userId, next)
    }
  }
}

/**
 * 订阅指定用户的聊天实时事件（对齐 /user/queue/chat-events）。
 */
export function subscribeMockChatRealtime(userId: number, listener: ChatListener): () => void {
  let set = chatListenersByUser.get(userId)
  if (!set) {
    set = new Set()
    chatListenersByUser.set(userId, set)
  }
  set.add(listener)
  return () => {
    set!.delete(listener)
    if (set!.size === 0) {
      chatListenersByUser.delete(userId)
    }
  }
}

/**
 * 订阅指定用户的社交实时事件（对齐 /user/queue/social-events）。
 */
export function subscribeMockSocialRealtime(userId: number, listener: SocialListener): () => void {
  let set = socialListenersByUser.get(userId)
  if (!set) {
    set = new Set()
    socialListenersByUser.set(userId, set)
  }
  set.add(listener)
  return () => {
    set!.delete(listener)
    if (set!.size === 0) {
      socialListenersByUser.delete(userId)
    }
  }
}

/**
 * workflow 向在线用户投递聊天事件。
 */
export function deliverChatRealtimeEvent(userId: number, event: ChatRealtimeEvent): void {
  if (!connectionCounts.has(userId)) return
  const set = chatListenersByUser.get(userId)
  if (!set) return
  for (const listener of set) {
    listener(event)
  }
}

/**
 * workflow 向在线用户投递好友申请事件。
 */
export function deliverSocialRealtimeEvent(userId: number, request: FriendRequest): void {
  if (!connectionCounts.has(userId)) return
  const set = socialListenersByUser.get(userId)
  if (!set) return
  for (const listener of set) {
    listener(request)
  }
}

/** 测试辅助：重置总线状态 */
export function resetMockChatRealtimeBus(): void {
  chatListenersByUser.clear()
  socialListenersByUser.clear()
  connectionCounts.clear()
}

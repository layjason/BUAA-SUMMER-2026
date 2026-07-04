/**
 * Mock 聊天 WebSocket 事件总线
 *
 * 模拟 `/chat/ws/messages` 长连接：workflow 在状态变更后向在线订阅者投递
 * ChatRealtimeEvent，客户端通过 subscribe 接收，与真实 WebSocket 帧格式一致。
 */
import type { components } from '@/api/types/schema'

export type ChatRealtimeEvent = components['schemas']['Chat.ChatRealtimeEvent']

type Listener = (event: ChatRealtimeEvent) => void

const listenersByUser = new Map<number, Set<Listener>>()
const connectionCounts = new Map<number, number>()

/** 建立 mock 连接（引用计数，支持多页面/组件复用） */
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

/** 订阅指定用户的实时事件 */
export function subscribeMockChatRealtime(userId: number, listener: Listener): () => void {
  let set = listenersByUser.get(userId)
  if (!set) {
    set = new Set()
    listenersByUser.set(userId, set)
  }
  set.add(listener)
  return () => {
    set!.delete(listener)
    if (set!.size === 0) {
      listenersByUser.delete(userId)
    }
  }
}

/** workflow 向在线用户投递事件（对齐后端 WebSocket 推送语义） */
export function deliverChatRealtimeEvent(userId: number, event: ChatRealtimeEvent): void {
  if (!connectionCounts.has(userId)) return
  const set = listenersByUser.get(userId)
  if (!set) return
  for (const listener of set) {
    listener(event)
  }
}

/** 测试辅助：重置总线状态 */
export function resetMockChatRealtimeBus(): void {
  listenersByUser.clear()
  connectionCounts.clear()
}

/**
 * 聊天 / 社交实时事件 composable
 *
 * 在页面生命周期内维持 STOMP（或 mock 总线）连接。
 */
import { onUnmounted, ref } from 'vue'
import {
  connectChatRealtime,
  type ChatRealtimeEvent,
  type FriendRequest,
  type SocialRealtimeHandler,
} from '@/api/modules/chatRealtime'
import { useAuthStore } from '@/stores/auth'

/**
 * 订阅聊天与可选的社交实时事件。
 */
export function useChatRealtime(
  onChatEvent: (event: ChatRealtimeEvent) => void,
  onSocialEvent?: SocialRealtimeHandler,
) {
  const connected = ref(false)
  let disconnect: (() => void) | null = null

  /** 建立实时连接 */
  function connect() {
    const authStore = useAuthStore()
    const userId = authStore.userId
    if (!userId) return

    disconnect?.()
    disconnect = connectChatRealtime(userId, authStore.getAccessToken(), {
      onChatEvent,
      onSocialEvent,
    })
    connected.value = true
  }

  /** 关闭实时连接 */
  function close() {
    disconnect?.()
    disconnect = null
    connected.value = false
  }

  onUnmounted(close)

  return { connected, connect, close }
}

export type { ChatRealtimeEvent, FriendRequest }

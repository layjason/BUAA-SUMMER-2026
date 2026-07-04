/**
 * 聊天实时事件 composable
 *
 * 在页面/组件生命周期内维持 WebSocket（或 mock 总线）连接。
 */
import { onUnmounted, ref } from 'vue'
import { connectChatRealtime, type ChatRealtimeEvent } from '@/api/modules/chatRealtime'
import { useAuthStore } from '@/stores/auth'

export function useChatRealtime(onEvent: (event: ChatRealtimeEvent) => void) {
  const connected = ref(false)
  let disconnect: (() => void) | null = null

  function connect() {
    const authStore = useAuthStore()
    const userId = authStore.userId
    if (!userId) return

    disconnect?.()
    disconnect = connectChatRealtime(userId, authStore.getAccessToken(), onEvent)
    connected.value = true
  }

  function close() {
    disconnect?.()
    disconnect = null
    connected.value = false
  }

  onUnmounted(close)

  return { connected, connect, close }
}

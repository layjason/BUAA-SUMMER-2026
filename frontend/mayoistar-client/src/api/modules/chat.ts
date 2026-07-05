/**
 * 聊天消息 API 模块
 *
 * 封装会话列表、消息收发、撤回、转发、已读标记、图片上传等接口。
 */
import { get, post, upload } from '@/api/request'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']
type SendMessageRequest = components['schemas']['Chat.SendMessageRequest']
type ForwardMessageRequest = components['schemas']['Chat.ForwardMessageRequest']

/** 获取会话列表 */
export function getConversations() {
  return get('/chat/conversations')
}

/** 获取会话消息列表 */
export function getMessages(conversationId: string, page: number, pageSize: number) {
  return get('/chat/conversations/{conversationId}/messages', {
    path: { conversationId },
    query: { page, pageSize },
  })
}

/** 发送消息到会话 */
export function sendMessage(
  conversationId: string,
  text: string,
  kind: SendMessageRequest['kind'] = 'text',
) {
  const body: SendMessageRequest = { text, kind }
  return post('/chat/conversations/{conversationId}/messages', {
    path: { conversationId },
    body,
  })
}

/** 撤回消息 */
export function recallMessage(messageId: string) {
  return post('/chat/messages/{messageId}/recall', {
    path: { messageId },
  })
}

/** 转发消息到另一个会话 */
export function forwardMessage(messageId: string, targetConversationId: string) {
  const body: ForwardMessageRequest = { targetConversationIds: [targetConversationId] }
  return post('/chat/messages/{messageId}/forward', {
    path: { messageId },
    body,
  })
}

/** 标记消息为已读 */
export function markMessagesRead(messageIds: string[]) {
  return post('/chat/messages/read', {
    body: { messageIds },
  })
}

/** 上传聊天图片 */
export function uploadChatImage(filePath: string): Promise<MediaFile> {
  return upload<MediaFile>('/chat/media/images', filePath)
}

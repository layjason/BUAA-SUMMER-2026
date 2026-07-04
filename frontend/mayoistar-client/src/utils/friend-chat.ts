/**
 * 好友单聊会话解析工具
 *
 * OpenAPI 暂无「按好友 userId 查询会话」接口，客户端通过会话列表 + 好友资料组合定位。
 */
import { getConversations } from '@/api/modules/chat'
import { getFriends, getUserProfile } from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type FriendItem = components['schemas']['Social.FriendItem']

function unwrapItems<T>(result: unknown): T[] {
  return Array.isArray(result) ? result : (((result as Record<string, unknown>).items as T[]) ?? [])
}

/**
 * 根据好友 userId 解析对应单聊 conversationId
 */
export async function resolveFriendConversationId(targetUserId: string): Promise<string | null> {
  const [profile, convResult, friendsResult] = await Promise.all([
    getUserProfile(targetUserId),
    getConversations(),
    getFriends().catch(() => []),
  ])

  const conversations = unwrapItems<ConversationSummary>(convResult)
  const friends = unwrapItems<FriendItem>(friendsResult)
  const friend = friends.find((f) => f.userId === targetUserId)

  const titles = new Set<string>()
  if (friend?.remark?.trim()) titles.add(friend.remark.trim())
  if (profile.nickname) titles.add(profile.nickname)

  const match = conversations.find((c) => c.kind === 'friend' && titles.has(c.title))
  return match?.conversationId ?? null
}

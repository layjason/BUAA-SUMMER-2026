import { getMessages } from '@/api/modules/chat'
import { getUserProfile } from '@/api/modules/social'
import { toAvatarAbsoluteUrl } from '@/utils/avatar-display'
import type { components } from '@/api/types/schema'

type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type TeamProfile = components['schemas']['Social.TeamProfile']
type FriendItem = components['schemas']['Social.FriendItem']

function unwrapItems<T>(result: unknown): T[] {
  if (Array.isArray(result)) return result
  return ((result as Record<string, unknown>).items as T[]) ?? []
}

/** 从 MediaFile 提取头像展示 URL（同步绝对路径，供列表轻量展示） */
export function avatarSignedUrl(media?: components['schemas']['MediaFile']): string {
  return toAvatarAbsoluteUrl(media?.signedUrl)
}

function friendDisplayName(friend: FriendItem): string {
  return friend.remark?.trim() || friend.nickname
}

function findFriendByTitle(friends: FriendItem[], title: string): FriendItem | undefined {
  const normalized = title.trim()
  if (!normalized) return undefined
  return friends.find((f) => f.nickname === normalized || f.remark?.trim() === normalized)
}

async function resolvePeerUserIdByMessages(
  conversationId: string,
  currentUserId: string,
): Promise<string | null> {
  try {
    const msgResult = await getMessages(conversationId, 1, 30)
    const items = unwrapItems<{ senderId: string }>(msgResult)
    const peerId = items.map((item) => item.senderId).find((id) => id && id !== currentUserId)
    return peerId ?? null
  } catch {
    return null
  }
}

async function resolveFriendPeerMap(
  conversations: ConversationSummary[],
  currentUserId: string,
): Promise<Map<string, string>> {
  const peerByConvId = new Map<string, string>()
  const needsResolve = conversations.filter((conv) => conv.kind === 'friend' && !conv.title?.trim())

  await Promise.all(
    needsResolve.map(async (conv) => {
      const peerId = await resolvePeerUserIdByMessages(conv.conversationId, currentUserId)
      if (peerId) peerByConvId.set(conv.conversationId, peerId)
    }),
  )

  return peerByConvId
}

function enrichTeamConversation(
  conv: ConversationSummary,
  teamByChatId: Map<string, TeamProfile>,
): ConversationSummary {
  if (conv.kind !== 'team') return conv
  const team = teamByChatId.get(conv.conversationId)
  if (!team) return conv
  return {
    ...conv,
    title: `${team.name} (${team.memberCount})`,
    avatar: avatarSignedUrl(conv.avatar) ? conv.avatar : team.avatar,
  }
}

function enrichFriendConversation(
  conv: ConversationSummary,
  friends: FriendItem[],
  peerByConvId: Map<string, string>,
  profileNameByUserId: Map<string, string>,
): ConversationSummary {
  if (conv.kind !== 'friend') return conv

  const peerId = peerByConvId.get(conv.conversationId)
  const friend =
    (peerId ? friends.find((f) => f.userId === peerId) : undefined) ??
    findFriendByTitle(friends, conv.title ?? '')

  let title = conv.title?.trim() || ''
  if (friend) {
    title = title || friendDisplayName(friend)
  } else if (peerId) {
    title = profileNameByUserId.get(peerId) ?? title
  }

  let avatar = conv.avatar
  if (!avatarSignedUrl(avatar) && friend?.avatar) {
    avatar = friend.avatar
  }

  return {
    ...conv,
    title,
    avatar,
  }
}

/** 用好友/小队资料补全会话列表缺失的头像与标题 */
export async function enrichConversationSummaries(
  conversations: ConversationSummary[],
  teams: TeamProfile[],
  friends: FriendItem[] = [],
  currentUserId = '',
): Promise<ConversationSummary[]> {
  const teamByChatId = new Map(teams.map((team) => [team.chatId, team]))
  const peerByConvId = currentUserId
    ? await resolveFriendPeerMap(conversations, currentUserId)
    : new Map<string, string>()

  const profileNameByUserId = new Map<string, string>()
  await Promise.all(
    [...new Set(peerByConvId.values())].map(async (peerId) => {
      if (friends.some((friend) => friend.userId === peerId)) return
      try {
        const profile = await getUserProfile(peerId)
        if (profile.nickname) {
          profileNameByUserId.set(peerId, profile.nickname)
        }
      } catch {
        /* 忽略单条资料失败 */
      }
    }),
  )

  return conversations.map((conv) => {
    const teamEnriched = enrichTeamConversation(conv, teamByChatId)
    return enrichFriendConversation(teamEnriched, friends, peerByConvId, profileNameByUserId)
  })
}

/** 会话列表展示用标题（空标题时给出兜底文案） */
export function conversationDisplayTitle(conv: ConversationSummary): string {
  const title = conv.title?.trim()
  if (title) return title
  return conv.kind === 'team' ? '小队群聊' : '好友'
}

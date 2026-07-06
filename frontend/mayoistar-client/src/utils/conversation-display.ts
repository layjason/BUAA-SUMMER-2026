import type { components } from '@/api/types/schema'

type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type TeamProfile = components['schemas']['Social.TeamProfile']
type FriendItem = components['schemas']['Social.FriendItem']

/** 从 MediaFile 提取头像展示 URL */
export function avatarSignedUrl(media?: components['schemas']['MediaFile']): string {
  return media?.signedUrl ?? ''
}

/** 用好友/小队资料补全会话列表缺失的头像与标题 */
export function enrichConversationSummaries(
  conversations: ConversationSummary[],
  teams: TeamProfile[],
  friends: FriendItem[] = [],
): ConversationSummary[] {
  const teamByChatId = new Map(teams.map((team) => [team.chatId, team]))

  return conversations.map((conv) => {
    if (conv.kind === 'team') {
      const team = teamByChatId.get(conv.conversationId)
      if (!team) return conv
      return {
        ...conv,
        title: `${team.name} (${team.memberCount})`,
        avatar: avatarSignedUrl(conv.avatar) ? conv.avatar : team.avatar,
      }
    }

    if (!avatarSignedUrl(conv.avatar)) {
      const friend = friends.find(
        (f) =>
          conv.title === f.nickname ||
          conv.title === f.remark?.trim() ||
          conv.title === (f.remark?.trim() || f.nickname),
      )
      if (friend?.avatar) {
        return { ...conv, avatar: friend.avatar }
      }
    }

    return conv
  })
}

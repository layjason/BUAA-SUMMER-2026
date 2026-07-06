/**
 * 转发目标列表拼装
 *
 * 组合会话列表与小队资料，供多选转发弹窗展示头像与小队人数。
 */
import { getConversations } from '@/api/modules/chat'
import { listMyTeams } from '@/api/modules/teams'
import { getFriends } from '@/api/modules/social'
import { enrichConversationSummaries, avatarSignedUrl } from '@/utils/conversation-display'
import { extractPageItems } from '@/utils/page-result'
import type { components } from '@/api/types/schema'

type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type TeamProfile = components['schemas']['Social.TeamProfile']

export interface ForwardTargetItem {
  conversationId: string
  kind: 'friend' | 'team'
  title: string
  avatarUrl?: string
  /** 仅小队群聊展示 */
  memberCount?: number
}

/**
 * 拉取可转发目标（排除当前会话）
 *
 * 前置条件：调用方已登录
 * 后置条件：返回带头像的会话项；小队附带 memberCount（若可解析）
 */
export async function fetchForwardTargets(
  excludeConversationId: string,
): Promise<ForwardTargetItem[]> {
  const [convResult, teamsResult, friendsResult] = await Promise.all([
    getConversations(),
    listMyTeams(1, 100).catch(() => []),
    getFriends(1, 100).catch(() => []),
  ])

  const teams = extractPageItems<TeamProfile>(teamsResult)
  const friends = extractPageItems<components['schemas']['Social.FriendItem']>(friendsResult)
  const conversations = enrichConversationSummaries(
    extractPageItems<ConversationSummary>(convResult),
    teams,
    friends,
  )
  const memberCountByChatId = new Map(teams.map((team) => [team.chatId, team.memberCount] as const))

  return conversations
    .filter((conv) => conv.conversationId !== excludeConversationId)
    .map((conv) => ({
      conversationId: conv.conversationId,
      kind: conv.kind,
      title: conv.title || (conv.kind === 'team' ? '小队群聊' : '好友'),
      avatarUrl: avatarSignedUrl(conv.avatar),
      memberCount: conv.kind === 'team' ? memberCountByChatId.get(conv.conversationId) : undefined,
    }))
}

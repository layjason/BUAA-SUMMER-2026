/**
 * B2 关系态拼装：并行拉取关注/粉丝/好友/黑名单/好友申请，推导主页操作态。
 */
import {
  getFollows,
  getFollowers,
  getFriends,
  getBlacklist,
  getSentFriendRequests,
  getReceivedFriendRequests,
} from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type FollowItem = components['schemas']['Social.FollowItem']
type FriendItem = components['schemas']['Social.FriendItem']
type BlacklistItem = components['schemas']['Social.BlacklistItem']
type FriendRequest = components['schemas']['Social.FriendRequest']

export type FriendRequestPending = 'none' | 'sent' | 'received'

/** 列表行「加好友」按钮可呈现的关系态 */
export type FriendListActionState =
  'self' | 'friend' | 'blocked' | 'pending_sent' | 'pending_received' | 'add'

/** 批量关系索引，供参与者/同伴列表一次性加载后本地判断 */
export interface BulkSocialRelationContext {
  friendIds: Set<string>
  blacklistIds: Set<string>
  sentPendingIds: Set<string>
  receivedPendingIds: Set<string>
}

export interface SocialRelationState {
  isFriend: boolean
  isFollowing: boolean
  isFollowedBy: boolean
  isMutual: boolean
  blockedByMe: boolean
  friendRequestPending: FriendRequestPending
  incomingRequestId?: string
}

function unwrapItems<T>(result: unknown): T[] {
  if (Array.isArray(result)) return result as T[]
  return ((result as Record<string, unknown>).items as T[]) ?? []
}

/** 拉取与目标用户相关的社交关系态（用于个人主页按钮展示） */
export async function fetchSocialRelationState(targetUserId: string): Promise<SocialRelationState> {
  const [
    followsResult,
    followersResult,
    friendsResult,
    blacklistResult,
    sentResult,
    receivedResult,
  ] = await Promise.all([
    getFollows(),
    getFollowers(),
    getFriends(),
    getBlacklist(),
    getSentFriendRequests('pending'),
    getReceivedFriendRequests('pending'),
  ])

  const follows = unwrapItems<FollowItem>(followsResult)
  const followers = unwrapItems<FollowItem>(followersResult)
  const friends = unwrapItems<FriendItem>(friendsResult)
  const blacklist = unwrapItems<BlacklistItem>(blacklistResult)
  const sentPending = unwrapItems<FriendRequest>(sentResult)
  const receivedPending = unwrapItems<FriendRequest>(receivedResult)

  const followEntry = follows.find((f) => f.userId === targetUserId)
  const isFollowing = Boolean(followEntry)
  const isFollowedBy = followers.some((f) => f.userId === targetUserId)
  const isMutual = isFollowing && isFollowedBy
  const isFriend = friends.some((f) => f.userId === targetUserId)
  const blockedByMe = blacklist.some((b) => b.userId === targetUserId)

  let friendRequestPending: FriendRequestPending = 'none'
  let incomingRequestId: string | undefined

  if (sentPending.some((r) => r.targetUserId === targetUserId)) {
    friendRequestPending = 'sent'
  } else {
    const incoming = receivedPending.find((r) => r.requesterId === targetUserId)
    if (incoming) {
      friendRequestPending = 'received'
      incomingRequestId = incoming.requestId
    }
  }

  return {
    isFriend,
    isFollowing,
    isFollowedBy,
    isMutual: followEntry?.mutual ?? isMutual,
    blockedByMe,
    friendRequestPending,
    incomingRequestId,
  }
}

/**
 * 并行拉取好友/黑名单/待处理申请，构建列表页关系索引
 *
 * 前置条件：调用方已登录
 * 后置条件：返回可用于 O(1) 查询的 Set 索引
 */
export async function fetchBulkSocialRelationContext(): Promise<BulkSocialRelationContext> {
  const [friendsResult, blacklistResult, sentResult, receivedResult] = await Promise.all([
    getFriends(),
    getBlacklist(),
    getSentFriendRequests('pending'),
    getReceivedFriendRequests('pending'),
  ])

  const friends = unwrapItems<FriendItem>(friendsResult)
  const blacklist = unwrapItems<BlacklistItem>(blacklistResult)
  const sentPending = unwrapItems<FriendRequest>(sentResult)
  const receivedPending = unwrapItems<FriendRequest>(receivedResult)

  return {
    friendIds: new Set(friends.map((f) => f.userId)),
    blacklistIds: new Set(blacklist.map((b) => b.userId)),
    sentPendingIds: new Set(
      sentPending.map((r) => r.targetUserId).filter((id): id is string => Boolean(id)),
    ),
    receivedPendingIds: new Set(
      receivedPending.map((r) => r.requesterId).filter((id): id is string => Boolean(id)),
    ),
  }
}

/**
 * 根据批量关系索引解析列表行操作态
 */
export function resolveFriendListActionState(
  ctx: BulkSocialRelationContext,
  userId: string,
  currentUserId: string,
): FriendListActionState {
  if (userId === currentUserId) return 'self'
  if (ctx.friendIds.has(userId)) return 'friend'
  if (ctx.blacklistIds.has(userId)) return 'blocked'
  if (ctx.sentPendingIds.has(userId)) return 'pending_sent'
  if (ctx.receivedPendingIds.has(userId)) return 'pending_received'
  return 'add'
}

/** 将列表行操作态映射为展示文案 */
export function friendListActionLabel(state: FriendListActionState): string {
  const labels: Record<FriendListActionState, string> = {
    self: '',
    friend: '已是好友',
    blocked: '已屏蔽',
    pending_sent: '等待确认',
    pending_received: '待你确认',
    add: '加好友',
  }
  return labels[state]
}

/** 列表行操作态是否允许点击发起加好友 */
export function canTapFriendListAction(state: FriendListActionState): boolean {
  return state === 'add' || state === 'pending_received'
}

/**
 * 社交 API 模块
 *
 * 封装好友管理、好友请求、关注、取关、用户公开资料等接口。
 */
import { get, post, patch, del } from '@/api/request'
import type { components } from '@/api/types/schema'

type FriendRequestCreate = components['schemas']['Social.FriendRequestCreate']
type FriendRequestDecision = components['schemas']['Social.FriendRequestDecision']

/** 获取好友列表 */
export function getFriends() {
  return get('/social/friends')
}

/** 获取收到的好友请求列表 */
export function getReceivedFriendRequests() {
  return get('/social/friend-requests/received')
}

/** 获取已发送的好友请求列表 */
export function getSentFriendRequests() {
  return get('/social/friend-requests/sent')
}

/** 发送好友请求 */
export function sendFriendRequest(targetUserId: string, message?: string) {
  const body: FriendRequestCreate = {
    targetUserId,
    message,
    source: 'profile',
  }
  return post('/social/friend-requests', {
    body,
  })
}

/** 处理好友请求（接受或拒绝） */
export function handleFriendRequest(requestId: string, accept: boolean) {
  const body: FriendRequestDecision = { accepted: accept }
  return post('/social/friend-requests/{requestId}/decision', {
    path: { requestId },
    body,
  })
}

/** 移除好友 */
export function removeFriend(userId: string) {
  return del('/social/friends/{userId}', {
    path: { userId },
  })
}

/** 更新好友备注 */
export function updateFriendRemark(userId: string, remark: string) {
  return patch('/social/friends/{userId}', {
    path: { userId },
    body: { remark },
  })
}

/** 关注用户 */
export function followUser(targetUserId: string) {
  return post('/social/follows/{targetUserId}', {
    path: { targetUserId },
  })
}

/** 取消关注用户 */
export function unfollowUser(targetUserId: string) {
  return del('/social/follows/{targetUserId}', {
    path: { targetUserId },
  })
}

/** 获取关注列表 */
export function getFollows() {
  return get('/social/follows')
}

/** 获取粉丝列表 */
export function getFollowers() {
  return get('/social/followers')
}

/** 获取用户公开资料 */
export function getUserProfile(userId: string) {
  return get('/social/profiles/{userId}', {
    path: { userId },
  })
}

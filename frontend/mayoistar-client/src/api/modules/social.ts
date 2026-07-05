/**
 * 社交 API 模块
 *
 * 封装好友管理、好友请求、关注、取关、用户公开资料、个人二维码等接口。
 */
import { get, post, patch, del } from '@/api/request'
import type { components } from '@/api/types/schema'

type FriendRequestCreate = components['schemas']['Social.FriendRequestCreate']
type FriendRequestDecision = components['schemas']['Social.FriendRequestDecision']
type QrCodeScanRequest = components['schemas']['Social.QrCodeScanRequest']

/** 获取好友列表（支持按昵称 keyword 筛选） */
export function getFriends(page = 1, pageSize = 50, keyword?: string) {
  return get('/social/friends', {
    query: { page, pageSize, keyword },
  })
}

/** 获取收到的好友请求列表 */
export function getReceivedFriendRequests(
  status?: components['schemas']['Social.FriendRequestStatus'],
) {
  return get('/social/friend-requests/received', {
    query: status ? { status } : undefined,
  })
}

/** 获取已发送的好友请求列表 */
export function getSentFriendRequests(
  status?: components['schemas']['Social.FriendRequestStatus'],
) {
  return get('/social/friend-requests/sent', {
    query: status ? { status } : undefined,
  })
}

/** 发送好友请求 */
export function sendFriendRequest(
  targetUserId: string,
  message?: string,
  source?: FriendRequestCreate['source'],
) {
  const body: FriendRequestCreate = {
    targetUserId,
    message,
    source: source ?? 'profile',
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

/** 更新好友备注与分组标签 */
export function updateFriendRemark(userId: string, remark?: string, groupTags?: string[]) {
  const body: Record<string, unknown> = {}
  if (remark !== undefined) body.remark = remark
  if (groupTags !== undefined) body.groupTags = groupTags
  return patch('/social/friends/{userId}', {
    path: { userId },
    body,
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
export function getFollows(page = 1, pageSize = 50) {
  return get('/social/follows', {
    query: { page, pageSize },
  })
}

/** 获取粉丝列表 */
export function getFollowers(page = 1, pageSize = 50) {
  return get('/social/followers', {
    query: { page, pageSize },
  })
}

/** 获取用户公开资料 */
export function getUserProfile(userId: string) {
  return get('/social/profiles/{userId}', {
    path: { userId },
  })
}

/** 获取黑名单列表 */
export function getBlacklist(page = 1, pageSize = 20) {
  return get('/social/blacklist', {
    query: { page, pageSize },
  })
}

/** 屏蔽用户 */
export function blockUser(targetUserId: string) {
  return post('/social/blacklist/{targetUserId}', {
    path: { targetUserId },
  })
}

/** 取消屏蔽用户 */
export function unblockUser(targetUserId: string) {
  return del('/social/blacklist/{targetUserId}', {
    path: { targetUserId },
  })
}

/** 扫描个人二维码并发好友申请（source=qrCode） */
export function scanPersonalQrCode(token: string, message?: string) {
  const body: QrCodeScanRequest = { token, message }
  return post('/social/qr-code/scan', { body })
}

type ReportCreateRequest = components['schemas']['Social.ReportCreateRequest']

/** 提交举报 */
export function createReport(
  targetType: ReportCreateRequest['targetType'],
  targetId: string,
  reason: string,
) {
  const body: ReportCreateRequest = { targetType, targetId, reason }
  return post('/social/reports', { body })
}

/** 查看我提交的举报 */
export function getMyReports(
  page = 1,
  pageSize = 20,
  status?: components['schemas']['Social.ReportStatus'],
) {
  return get('/social/reports', {
    query: { page, pageSize, ...(status ? { status } : {}) },
  })
}

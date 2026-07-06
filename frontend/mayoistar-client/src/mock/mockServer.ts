/**
 * 有状态 Mock Server
 *
 * 接收 HTTP 方法 + 路径 + 请求体，路由到对应的 workflow 函数，
 * 返回 MockApiResponse。作为 client.ts 的 mockHandler 注入。
 *
 * 前置条件：mockDb 已通过 initMockDb() 初始化
 * 后置条件：返回标准 { code, message, data } 响应
 * 不变量：未匹配的路由返回 null，由调用方决定是否走真实 HTTP
 */
import type { MockApiResponse } from './types'
import type {
  ActivityFeedTab,
  ActivityReviewRequest,
  ActivityRuntimeStatus,
  ActivitySearchQuery,
  ActivitySummaryPostRequest,
  CheckInRequest,
  FriendRequestCreate,
  ReportCreateRequest,
  ReportStatus,
  JoinTeamRequestBody,
  MockDraftUpsertInput,
  QualificationSubmitRequest,
  RegisterActivityRequest,
  SendMessageRequest,
  TeamCreateRequest,
  TeamUpdateRequest,
  TeamMemberRole,
  UpdateMerchantProfileRequest,
} from './schema-types'
import { persistMockDb, resetMockDb } from './database'
import { MockBusinessError } from './workflow'
import {
  login,
  changePassword,
  register,
  getFeed,
  getActivityDetail,
  getParticipationState,
  registerForActivity,
  cancelRegistration,
  confirmWaitlist,
  createDraft,
  updateDraft,
  submitActivity,
  generateCheckInQrCode,
  checkIn,
  sendFriendRequest,
  handleFriendRequest,
  sendMessage,
  recallMessage,
  createTeam,
  updateTeam,
  joinTeam,
  searchActivities,
  getMapActivities,
  getMyActivities,
  getMyDrafts,
  getDraft,
  getMyRegistrations,
  getParticipants,
  getCheckIns,
  exportCheckInsCsv,
  createReview,
  createSummary,
  listActivityReviews,
  getMyActivityReview,
  listActivitySummaries,
  getMyActivitySummary,
  cloneActivity,
  createDraftFromTemplate,
  getUserProfile,
  updateUserProfile,
  checkNicknameAvailability,
  getFriends,
  getReceivedFriendRequests,
  getSentFriendRequests,
  removeFriend,
  updateFriendRemark,
  followUser,
  unfollowUser,
  getFollows,
  getFollowers,
  blockUser,
  unblockUser,
  getBlacklist,
  createReport,
  listMyReports,
  getPersonalQrCodePng,
  scanPersonalQrCode,
  listConversations,
  getMessages,
  listMyTeams,
  getTeamDetail,
  searchTeams,
  leaveTeam,
  dissolveTeam,
  getTeamMembers,
  getTeamJoinRequests,
  handleJoinRequest,
  updateMemberRole,
  markMessagesRead,
  forwardMessage,
  getInterestTags,
  getTemplates,
  getMerchantProfile,
  updateMerchantProfile,
  submitMerchantQualification,
  getCurrentUserId,
  listTeamActivities,
  createTeamActivity,
  getTeamActivity,
} from './workflow'
import {
  publishAnnouncement,
  listAnnouncements,
  updateAnnouncement,
  deleteAnnouncement,
  markAnnouncementRead,
  createPoll,
  listPolls,
  getPoll,
  votePoll,
  uploadTeamFile,
  listTeamFiles,
  deleteTeamFiles,
  uploadTeamAlbumImage,
  listTeamAlbumImages,
  deleteTeamAlbumImages,
  getTeamPointRanks,
} from './teamFeatures'
import { MOCK_IMAGE_BASE_URL } from '@/config/env'
import type { DownloadFileResult } from '@/api/request'

/* ---- 辅助函数 ---- */

/**
 * 从 URL 路径中提取路径参数
 *
 * 前置条件：pattern 为带 {param} 占位符的路径模板
 * 后置条件：返回匹配到的参数键值对，未匹配返回 null
 */
function matchPath(pattern: string, actualPath: string): Record<string, string> | null {
  const patternParts = pattern.split('/')
  const actualParts = actualPath.split('/')
  if (patternParts.length !== actualParts.length) return null

  const params: Record<string, string> = {}
  for (let i = 0; i < patternParts.length; i++) {
    if (patternParts[i].startsWith('{') && patternParts[i].endsWith('}')) {
      const key = patternParts[i].slice(1, -1)
      params[key] = actualParts[i]
    } else if (patternParts[i] !== actualParts[i]) {
      return null
    }
  }
  return params
}

/** 解析查询字符串为键值对 */
function parseQuery(queryString: string): Record<string, string> {
  const params: Record<string, string> = {}
  if (!queryString) return params
  for (const part of queryString.split('&')) {
    const [key, value] = part.split('=')
    if (key) params[decodeURIComponent(key)] = decodeURIComponent(value ?? '')
  }
  return params
}

/** 构造成功响应 */
function ok<T>(data: T): MockApiResponse<T> {
  return { code: 200, message: 'For Super Earth!', data }
}

/** 构造业务错误响应 */
function err(code: number, message: string): MockApiResponse<null> {
  return { code, message, data: null }
}

/** 模拟上传响应（与 OpenAPI MediaFile 对齐） */
function mockUploadResponse(usage: string = 'activityImage'): Record<string, unknown> {
  const now = new Date().toISOString()
  const mediaId = `media_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const accessUrl = `${MOCK_IMAGE_BASE_URL}/seed/${encodeURIComponent(mediaId)}/400/300`
  return {
    mediaId,
    fileName: `${mediaId}.jpg`,
    contentType: 'image/jpeg',
    sizeBytes: 50000,
    usage,
    signedUrl: accessUrl,
    uploadedAt: now,
  }
}

/* ---- 路由表 ---- */

type RouteHandler = (
  pathParams: Record<string, string>,
  query: Record<string, string>,
  body: Record<string, unknown>,
) => MockApiResponse

interface Route {
  method: string
  pattern: string
  handler: RouteHandler
}

const routes: Route[] = [
  /* ===== 认证 ===== */
  {
    method: 'POST',
    pattern: '/identity/auth/login',
    handler: (_p, _q, body) => {
      const result = login(body.email as string, body.password as string)
      // login 成功时设置当前用户（login 内部已调用 setCurrentUserId）
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/identity/auth/register/personal',
    handler: (_p, _q, body) => {
      const result = register(
        body.email as string,
        body.password as string,
        body.nickname as string,
        'personal',
      )
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/identity/auth/register/merchant',
    handler: (_p, _q, body) => {
      const result = register(
        body.email as string,
        body.password as string,
        body.nickname as string,
        'merchant',
      )
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/identity/auth/logout',
    handler: () => ok(null),
  },
  {
    method: 'POST',
    pattern: '/identity/auth/refresh',
    handler: () =>
      ok({
        accessToken: 'mock-refreshed-access-token',
        refreshToken: 'mock-refreshed-refresh-token',
      }),
  },
  {
    method: 'POST',
    pattern: '/identity/auth/activate',
    handler: () => ok(null),
  },
  {
    method: 'POST',
    pattern: '/identity/auth/activation-email',
    handler: () => ok(null),
  },
  {
    method: 'POST',
    pattern: '/identity/auth/password-reset-email',
    handler: () => ok(null),
  },
  {
    method: 'POST',
    pattern: '/identity/auth/password-reset',
    handler: () => ok(null),
  },
  {
    method: 'POST',
    pattern: '/identity/me/password',
    handler: (_p, _q, body) =>
      ok(
        changePassword(getCurrentUserId(), body.oldPassword as string, body.newPassword as string),
      ),
  },

  /* ===== 个人资料 ===== */
  {
    method: 'GET',
    pattern: '/identity/me/profile',
    handler: () => ok(getUserProfile(getCurrentUserId())),
  },
  {
    method: 'PATCH',
    pattern: '/identity/me/profile',
    handler: (_p, _q, body) => ok(updateUserProfile(getCurrentUserId(), body)),
  },
  {
    method: 'GET',
    pattern: '/identity/me/merchant-profile',
    handler: () => ok(getMerchantProfile(getCurrentUserId())),
  },
  {
    method: 'PATCH',
    pattern: '/identity/me/merchant-profile',
    handler: (_p, _q, body) =>
      ok(updateMerchantProfile(getCurrentUserId(), body as UpdateMerchantProfileRequest)),
  },
  {
    method: 'POST',
    pattern: '/identity/me/merchant-qualification',
    handler: (_p, _q, body) =>
      ok(submitMerchantQualification(getCurrentUserId(), body as QualificationSubmitRequest)),
  },
  {
    method: 'GET',
    pattern: '/identity/interest-tags',
    handler: () => ok(getInterestTags()),
  },
  {
    method: 'GET',
    pattern: '/identity/nicknames/availability',
    handler: (_p, query) => ok(checkNicknameAvailability(query.nickname ?? '')),
  },

  /* ===== 活动 Feed / 搜索 / 地图 ===== */
  {
    method: 'GET',
    pattern: '/activities/feed',
    handler: (_p, query) => {
      const tab = (query.tab ?? 'recommended') as ActivityFeedTab
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '10', 10)
      const filters: ActivitySearchQuery = {}
      if (query.keyword) filters.keyword = query.keyword
      if (query.city) filters.city = query.city
      if (query.startAtFrom) filters.startAtFrom = query.startAtFrom
      if (query.startAtTo) filters.startAtTo = query.startAtTo
      if (query.minFee !== undefined) filters.minFee = parseFloat(query.minFee)
      if (query.maxFee !== undefined) filters.maxFee = parseFloat(query.maxFee)
      if (query.longitude !== undefined) filters.longitude = parseFloat(query.longitude)
      if (query.latitude !== undefined) filters.latitude = parseFloat(query.latitude)
      if (query.distanceMeters !== undefined) {
        filters.distanceMeters = parseFloat(query.distanceMeters)
      }
      if (query.activityTypes) {
        filters.activityTypes = query.activityTypes
          .split(',')
          .map((type) => type.trim())
          .filter(Boolean)
      }
      return ok(getFeed(tab, page, pageSize, filters))
    },
  },
  {
    method: 'GET',
    pattern: '/activities/search',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '10', 10)
      const filters: ActivitySearchQuery = {}
      if (query.keyword) filters.keyword = query.keyword
      if (query.city) filters.city = query.city
      if (query.startAtFrom) filters.startAtFrom = query.startAtFrom
      if (query.startAtTo) filters.startAtTo = query.startAtTo
      if (query.runtimeStatus) {
        filters.runtimeStatus = query.runtimeStatus as ActivityRuntimeStatus
      }
      if (query.minFee !== undefined) filters.minFee = parseFloat(query.minFee)
      if (query.maxFee !== undefined) filters.maxFee = parseFloat(query.maxFee)
      if (query.longitude !== undefined) filters.longitude = parseFloat(query.longitude)
      if (query.latitude !== undefined) filters.latitude = parseFloat(query.latitude)
      if (query.distanceMeters !== undefined) {
        filters.distanceMeters = parseFloat(query.distanceMeters)
      }
      if (query.activityTypes) {
        filters.activityTypes = query.activityTypes
          .split(',')
          .map((type) => type.trim())
          .filter(Boolean)
      }
      return ok(searchActivities(filters, page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/activities/map',
    handler: (_p, query) => {
      const lng = parseFloat(query.longitude ?? '116.4')
      const lat = parseFloat(query.latitude ?? '39.9')
      const dist = parseFloat(query.distanceMeters ?? '10000')
      const filters: ActivitySearchQuery = {}
      if (query.keyword) filters.keyword = query.keyword
      if (query.city) filters.city = query.city
      if (query.startAtFrom) filters.startAtFrom = query.startAtFrom
      if (query.startAtTo) filters.startAtTo = query.startAtTo
      if (query.minFee !== undefined) filters.minFee = parseFloat(query.minFee)
      if (query.maxFee !== undefined) filters.maxFee = parseFloat(query.maxFee)
      if (query.activityTypes) {
        filters.activityTypes = query.activityTypes
          .split(',')
          .map((type) => type.trim())
          .filter(Boolean)
      }
      return ok(getMapActivities(lng, lat, dist, filters))
    },
  },

  /* ===== 我的活动 / 草稿 ===== */
  {
    method: 'GET',
    pattern: '/activities/mine',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '100', 10)
      return ok(getMyActivities(getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/activities/drafts',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '100', 10)
      return ok(getMyDrafts(getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/activities/drafts',
    handler: (_p, _q, body) => ok(createDraft(getCurrentUserId(), body as MockDraftUpsertInput)),
  },

  /* ===== 我的报名 ===== */
  {
    method: 'GET',
    pattern: '/activities/registrations/mine',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '10', 10)
      return ok(getMyRegistrations(getCurrentUserId(), page, pageSize))
    },
  },

  /* ===== 模板 ===== */
  {
    method: 'GET',
    pattern: '/activities/templates',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '100', 10)
      return ok(getTemplates(page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/activities/templates/{templateId}/drafts',
    handler: (params) =>
      ok(createDraftFromTemplate(parseInt(params.templateId, 10), getCurrentUserId())),
  },

  /* ===== 活动克隆 ===== */
  {
    method: 'POST',
    pattern: '/activities/{activityId}/clone',
    handler: (params) => ok(cloneActivity(parseInt(params.activityId, 10), getCurrentUserId())),
  },

  /* ===== 媒体上传（mock） ===== */
  {
    method: 'POST',
    pattern: '/activities/media/images',
    handler: () => ok(mockUploadResponse('activityImage')),
  },
  {
    method: 'POST',
    pattern: '/activities/media/review-images',
    handler: () => ok(mockUploadResponse('reviewImage')),
  },
  {
    method: 'POST',
    pattern: '/identity/media/avatar',
    handler: () => ok(mockUploadResponse('avatar')),
  },
  {
    method: 'POST',
    pattern: '/identity/media/license',
    handler: () => ok(mockUploadResponse('merchantLicense')),
  },

  /* ===== 活动详情 / 提交 / 参与状态 ===== */
  {
    method: 'GET',
    pattern: '/activities/drafts/{draftId}',
    handler: (params) => ok(getDraft(parseInt(params.draftId, 10))),
  },
  {
    method: 'PATCH',
    pattern: '/activities/drafts/{draftId}',
    handler: (params, _q, body) =>
      ok(updateDraft(parseInt(params.draftId, 10), body as MockDraftUpsertInput)),
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/submit',
    handler: (params) => ok(submitActivity(parseInt(params.activityId, 10))),
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/participation-state',
    handler: (params) =>
      ok(getParticipationState(parseInt(params.activityId, 10), getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}',
    handler: (params) => ok(getActivityDetail(parseInt(params.activityId, 10))),
  },

  /* ===== 报名 / 取消 / 候补 ===== */
  {
    method: 'POST',
    pattern: '/activities/{activityId}/registrations',
    handler: (params, _q, body) => {
      const result = registerForActivity(
        parseInt(params.activityId, 10),
        getCurrentUserId(),
        body as RegisterActivityRequest,
      )
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/registrations/cancel',
    handler: (params) => {
      const result = cancelRegistration(parseInt(params.activityId, 10), getCurrentUserId())
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/waiting-confirmations',
    handler: (params, _q, body) => {
      if (body.confirmed === false) {
        const result = cancelRegistration(parseInt(params.activityId, 10), getCurrentUserId())
        return ok(result)
      }
      const result = confirmWaitlist(parseInt(params.activityId, 10), getCurrentUserId())
      return ok(result)
    },
  },

  /* ===== 参与者 / 签到 ===== */
  {
    method: 'GET',
    pattern: '/activities/{activityId}/participants',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '10', 10)
      return ok(getParticipants(parseInt(params.activityId, 10), page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/check-in-qrcode',
    handler: (params) => ok(generateCheckInQrCode(parseInt(params.activityId, 10))),
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/check-ins',
    handler: (params, _q, body) => {
      const result = checkIn(parseInt(params.activityId, 10), getCurrentUserId(), {
        qrCodeToken: (body.qrCodeToken as string) ?? (body.code as string),
        currentLocation: body.currentLocation as CheckInRequest['currentLocation'],
      })
      return ok(result)
    },
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/check-ins',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '100', 10)
      return ok(getCheckIns(parseInt(params.activityId, 10), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/check-ins/export',
    handler: (params) => ok(exportCheckInsCsv(parseInt(params.activityId, 10))),
  },

  /* ===== 评价 / 总结 ===== */
  {
    method: 'GET',
    pattern: '/activities/{activityId}/reviews/mine',
    handler: (params) =>
      ok(getMyActivityReview(parseInt(params.activityId, 10), getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/reviews',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listActivityReviews(parseInt(params.activityId, 10), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/summaries/mine',
    handler: (params) =>
      ok(getMyActivitySummary(parseInt(params.activityId, 10), getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/activities/{activityId}/summaries',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listActivitySummaries(parseInt(params.activityId, 10), page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/reviews',
    handler: (params, _q, body) =>
      ok(
        createReview(
          parseInt(params.activityId, 10),
          getCurrentUserId(),
          body as ActivityReviewRequest,
        ),
      ),
  },
  {
    method: 'POST',
    pattern: '/activities/{activityId}/summaries',
    handler: (params, _q, body) =>
      ok(
        createSummary(
          parseInt(params.activityId, 10),
          getCurrentUserId(),
          body as ActivitySummaryPostRequest,
        ),
      ),
  },

  /* ===== 社交 ===== */
  {
    method: 'GET',
    pattern: '/social/friends',
    handler: (_p, query) => {
      const keyword = query.keyword as string | undefined
      return ok(getFriends(getCurrentUserId(), keyword))
    },
  },
  {
    method: 'POST',
    pattern: '/social/qr-code/scan',
    handler: (_p, _q, body) => {
      const token = (body.token as string) ?? ''
      const message = body.message as string | undefined
      return ok(scanPersonalQrCode(getCurrentUserId(), token, message))
    },
  },
  {
    method: 'GET',
    pattern: '/social/friend-requests/received',
    handler: () => ok(getReceivedFriendRequests(getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/social/friend-requests/sent',
    handler: () => ok(getSentFriendRequests(getCurrentUserId())),
  },
  {
    method: 'POST',
    pattern: '/social/friend-requests',
    handler: (_p, _q, body) => {
      const result = sendFriendRequest(getCurrentUserId(), body as FriendRequestCreate)
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/social/friend-requests/{requestId}/decision',
    handler: (params, _q, body) => {
      const result = handleFriendRequest(parseInt(params.requestId, 10), body.accepted as boolean)
      return ok(result)
    },
  },
  {
    method: 'DELETE',
    pattern: '/social/friends/{userId}',
    handler: (params) => {
      removeFriend(getCurrentUserId(), parseInt(params.userId, 10))
      return ok(null)
    },
  },
  {
    method: 'PATCH',
    pattern: '/social/friends/{userId}',
    handler: (params, _q, body) => {
      const result = updateFriendRemark(
        getCurrentUserId(),
        parseInt(params.userId, 10),
        body.remark as string | undefined,
        body.groupTags as string[] | undefined,
      )
      return ok(result)
    },
  },
  {
    method: 'GET',
    pattern: '/social/follows',
    handler: () => ok(getFollows(getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/social/followers',
    handler: () => ok(getFollowers(getCurrentUserId())),
  },
  {
    method: 'POST',
    pattern: '/social/follows/{targetUserId}',
    handler: (params) => {
      return ok(followUser(getCurrentUserId(), parseInt(params.targetUserId, 10)))
    },
  },
  {
    method: 'DELETE',
    pattern: '/social/follows/{targetUserId}',
    handler: (params) => {
      return ok(unfollowUser(getCurrentUserId(), parseInt(params.targetUserId, 10)))
    },
  },
  {
    method: 'GET',
    pattern: '/social/blacklist',
    handler: () => ok(getBlacklist(getCurrentUserId())),
  },
  {
    method: 'POST',
    pattern: '/social/blacklist/{targetUserId}',
    handler: (params) => {
      blockUser(getCurrentUserId(), parseInt(params.targetUserId, 10))
      return ok(null)
    },
  },
  {
    method: 'DELETE',
    pattern: '/social/blacklist/{targetUserId}',
    handler: (params) => {
      unblockUser(getCurrentUserId(), parseInt(params.targetUserId, 10))
      return ok(null)
    },
  },
  {
    method: 'GET',
    pattern: '/social/profiles/{userId}',
    handler: (params) => ok(getUserProfile(parseInt(params.userId, 10))),
  },
  {
    method: 'POST',
    pattern: '/social/reports',
    handler: (_p, _q, body) => {
      return ok(createReport(getCurrentUserId(), body as ReportCreateRequest))
    },
  },
  {
    method: 'GET',
    pattern: '/social/reports',
    handler: (_p, query) => {
      const status = query.status as ReportStatus | undefined
      const page = parseInt((query.page as string) ?? '1', 10)
      const pageSize = parseInt((query.pageSize as string) ?? '20', 10)
      return ok(listMyReports(getCurrentUserId(), status, page, pageSize))
    },
  },

  /* ===== 小队 ===== */
  {
    method: 'GET',
    pattern: '/social/teams/mine',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listMyTeams(getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/social/teams',
    handler: (_p, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      const tags = query.tags
        ? Array.isArray(query.tags)
          ? query.tags
          : String(query.tags).split(',')
        : undefined
      return ok(searchTeams(query.keyword as string | undefined, tags, page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/social/teams',
    handler: (_p, _q, body) => ok(createTeam(getCurrentUserId(), body as TeamCreateRequest)),
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}',
    handler: (params) => ok(getTeamDetail(parseInt(params.teamId, 10))),
  },
  {
    method: 'PATCH',
    pattern: '/social/teams/{teamId}',
    handler: (params, _q, body) =>
      ok(updateTeam(parseInt(params.teamId, 10), getCurrentUserId(), body as TeamUpdateRequest)),
  },
  {
    method: 'POST',
    pattern: '/social/teams/{teamId}/join',
    handler: (params, _q, body) => {
      const result = joinTeam(
        parseInt(params.teamId, 10),
        getCurrentUserId(),
        body as JoinTeamRequestBody,
      )
      return ok(result)
    },
  },
  {
    method: 'DELETE',
    pattern: '/social/teams/{teamId}',
    handler: (params) => {
      dissolveTeam(parseInt(params.teamId, 10), getCurrentUserId())
      return ok(null)
    },
  },
  {
    method: 'POST',
    pattern: '/social/teams/{teamId}/leave',
    handler: (params) => {
      leaveTeam(parseInt(params.teamId, 10), getCurrentUserId())
      return ok(null)
    },
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}/members',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(getTeamMembers(parseInt(params.teamId, 10), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}/join-requests',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(getTeamJoinRequests(parseInt(params.teamId, 10), page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/social/teams/{teamId}/join-requests/{requestId}/decision',
    handler: (params, _q, body) => {
      const result = handleJoinRequest(parseInt(params.requestId, 10), body.accepted as boolean)
      return ok(result)
    },
  },
  {
    method: 'PATCH',
    pattern: '/social/teams/{teamId}/members/{memberId}/role',
    handler: (params, _q, body) => {
      const result = updateMemberRole(
        parseInt(params.teamId, 10),
        getCurrentUserId(),
        parseInt(params.memberId, 10),
        body.role as TeamMemberRole,
      )
      return ok(result)
    },
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}/points',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(getTeamPointRanks(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}/activities',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listTeamActivities(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'POST',
    pattern: '/social/teams/{teamId}/activities',
    handler: (params, _q, body) =>
      ok(
        createTeamActivity(
          parseInt(params.teamId, 10),
          getCurrentUserId(),
          body as import('./schema-types').ActivityUpsertRequest,
        ),
      ),
  },
  {
    method: 'GET',
    pattern: '/social/teams/{teamId}/activities/{activityId}',
    handler: (params) =>
      ok(
        getTeamActivity(
          parseInt(params.teamId, 10),
          parseInt(params.activityId, 10),
          getCurrentUserId(),
        ),
      ),
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/announcements',
    handler: (params, _q, body) =>
      ok(
        publishAnnouncement(
          parseInt(params.teamId, 10),
          getCurrentUserId(),
          (body as { content: string }).content,
        ),
      ),
  },
  {
    method: 'GET',
    pattern: '/chat/teams/{teamId}/announcements',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listAnnouncements(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'PUT',
    pattern: '/chat/teams/{teamId}/announcements/{announcementId}',
    handler: (params, _q, body) =>
      ok(
        updateAnnouncement(
          parseInt(params.teamId, 10),
          parseInt(params.announcementId, 10),
          getCurrentUserId(),
          (body as { content: string }).content,
        ),
      ),
  },
  {
    method: 'DELETE',
    pattern: '/chat/teams/{teamId}/announcements/{announcementId}',
    handler: (params) => {
      deleteAnnouncement(
        parseInt(params.teamId, 10),
        parseInt(params.announcementId, 10),
        getCurrentUserId(),
      )
      return ok(null)
    },
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/announcements/{announcementId}/read',
    handler: (params) =>
      ok(
        markAnnouncementRead(
          parseInt(params.teamId, 10),
          parseInt(params.announcementId, 10),
          getCurrentUserId(),
        ),
      ),
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/polls',
    handler: (params, _q, body) => {
      const payload = body as { title: string; options: string[]; deadline?: string }
      return ok(
        createPoll(
          parseInt(params.teamId, 10),
          getCurrentUserId(),
          payload.title,
          payload.options,
          payload.deadline,
        ),
      )
    },
  },
  {
    method: 'GET',
    pattern: '/chat/teams/{teamId}/polls',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listPolls(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/chat/teams/{teamId}/polls/{pollId}',
    handler: (params) =>
      ok(getPoll(parseInt(params.teamId, 10), parseInt(params.pollId, 10), getCurrentUserId())),
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/polls/{pollId}/votes',
    handler: (params, _q, body) =>
      ok(
        votePoll(
          parseInt(params.teamId, 10),
          parseInt(params.pollId, 10),
          getCurrentUserId(),
          parseInt((body as { optionId: string }).optionId, 10),
        ),
      ),
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/files',
    handler: (params) => ok(uploadTeamFile(parseInt(params.teamId, 10), getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/chat/teams/{teamId}/files',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(listTeamFiles(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'DELETE',
    pattern: '/chat/teams/{teamId}/files',
    handler: (params, _q, body) => {
      deleteTeamFiles(
        parseInt(params.teamId, 10),
        getCurrentUserId(),
        (body as { mediaIds: string[] }).mediaIds,
      )
      return ok(null)
    },
  },
  {
    method: 'POST',
    pattern: '/chat/teams/{teamId}/album-images',
    handler: (params) => ok(uploadTeamAlbumImage(parseInt(params.teamId, 10), getCurrentUserId())),
  },
  {
    method: 'GET',
    pattern: '/chat/teams/{teamId}/album-images',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(
        listTeamAlbumImages(parseInt(params.teamId, 10), getCurrentUserId(), page, pageSize),
      )
    },
  },
  {
    method: 'DELETE',
    pattern: '/chat/teams/{teamId}/album-images',
    handler: (params, _q, body) => {
      deleteTeamAlbumImages(
        parseInt(params.teamId, 10),
        getCurrentUserId(),
        (body as { mediaIds: string[] }).mediaIds,
      )
      return ok(null)
    },
  },

  /* ===== 聊天 ===== */
  {
    method: 'POST',
    pattern: '/chat/media/images',
    handler: () => ok(mockUploadResponse('chatImage')),
  },
  {
    method: 'GET',
    pattern: '/chat/conversations',
    handler: (_p, query) => {
      const page = parseInt((query.page as string) ?? '1', 10)
      const pageSize = parseInt((query.pageSize as string) ?? '20', 10)
      return ok(listConversations(getCurrentUserId(), page, pageSize))
    },
  },
  {
    method: 'GET',
    pattern: '/chat/conversations/{conversationId}/messages',
    handler: (params, query) => {
      const page = parseInt(query.page ?? '1', 10)
      const pageSize = parseInt(query.pageSize ?? '20', 10)
      return ok(
        getMessages(parseInt(params.conversationId, 10), getCurrentUserId(), page, pageSize),
      )
    },
  },
  {
    method: 'POST',
    pattern: '/chat/conversations/{conversationId}/messages',
    handler: (params, _q, body) =>
      ok(
        sendMessage(
          parseInt(params.conversationId, 10),
          getCurrentUserId(),
          body as SendMessageRequest,
        ),
      ),
  },
  {
    method: 'POST',
    pattern: '/chat/messages/{messageId}/recall',
    handler: (params) => {
      const result = recallMessage(parseInt(params.messageId, 10), getCurrentUserId())
      return ok(result)
    },
  },
  {
    method: 'POST',
    pattern: '/chat/messages/read',
    handler: (_p, _q, body) => {
      const messageIds = (body.messageIds as string[]).map((id) => parseInt(id, 10))
      return ok(markMessagesRead(messageIds, getCurrentUserId()))
    },
  },
  {
    method: 'POST',
    pattern: '/chat/messages/{messageId}/forward',
    handler: (params, _q, body) => {
      const targetIds = (body.targetConversationIds as string[]).map((id) => parseInt(id, 10))
      const forwarded = forwardMessage(
        parseInt(params.messageId, 10),
        getCurrentUserId(),
        targetIds,
      )
      return ok(forwarded)
    },
  },

  /* ===== AI ===== */
  {
    method: 'POST',
    pattern: '/ai/activity-plans',
    handler: (_p, _q, body) =>
      ok({
        status: 'succeeded',
        title: `AI 建议：${(body.topic as string) ?? '趣味活动'}`,
        introduction:
          '这是一个由 AI 生成的活动方案。包含了活动的基本信息、流程安排和注意事项。你可以根据实际情况进行修改。',
        safetyNotice: '请注意活动安全，遵守场地规则，随身携带个人物品。如遇紧急情况请联系组织者。',
        tags: ['趣味', '社交', '休闲'],
        suggestedCapacity: 20,
        suggestedRegistrationDeadline: new Date(Date.now() + 3 * 86400000).toISOString(),
      }),
  },
  {
    method: 'POST',
    pattern: '/ai/image-classifications',
    handler: (_p, _q, body) => {
      const mediaIds = (body.mediaIds as string[]) ?? []
      return ok({
        status: 'succeeded',
        items: mediaIds.map((id) => ({
          mediaId: id,
          suggestedTags: ['活动现场', '合影'],
          confidence: 0.85,
        })),
      })
    },
  },

  /* ===== 重置演示数据 ===== */
  {
    method: 'POST',
    pattern: '/mock/reset',
    handler: () => {
      resetMockDb()
      return ok({ message: '演示数据已重置' })
    },
  },
]

/* ---- 公开接口 ---- */

/**
 * Mock 请求处理入口
 *
 * 匹配路由表中的路径，调用对应 handler；
 * 未匹配返回 null，由 client.ts 走真实 HTTP。
 *
 * @param method HTTP 方法（GET/POST/PUT/PATCH/DELETE）
 * @param fullPath 完整路径，可能包含查询字符串
 * @param body 请求体（POST/PUT/PATCH）
 * @returns Mock 响应 Promise，或 null 表示未匹配
 */
/** Mock 二进制 GET（如个人二维码 PNG） */
export function handleMockBinaryRequest(path: string): Promise<ArrayBuffer> | null {
  const basePath = path.split('?')[0]
  if (basePath === '/social/qr-code') {
    return Promise.resolve(getPersonalQrCodePng(getCurrentUserId()))
  }
  return null
}

/**
 * Mock 文件下载（如签到 CSV）。
 *
 * 前置条件：path 为后端下载接口路径。
 * 后置条件：匹配时返回可交给下载调用方处理的临时文件路径。
 * 不变量：只模拟文件下载，不返回 APIResponse JSON 包装。
 */
export function handleMockDownloadRequest(path: string): Promise<DownloadFileResult> | null {
  const basePath = path.split('?')[0]
  const params = matchPath('/activities/{activityId}/check-ins/export', basePath)
  if (!params) return null

  const csv = exportCheckInsCsv(parseInt(params.activityId, 10))
  const encodedCsv = encodeURIComponent(csv)
  return Promise.resolve({
    tempFilePath: `data:text/csv;charset=utf-8,${encodedCsv}`,
    statusCode: 200,
  })
}

export function handleMockRequest(
  method: string,
  fullPath: string,
  body?: unknown,
): Promise<MockApiResponse> | null {
  // 分离路径和查询字符串
  const [basePath, queryString] = fullPath.split('?')
  const query = parseQuery(queryString ?? '')
  const bodyObj = (body as Record<string, unknown>) ?? {}
  const upperMethod = method.toUpperCase()

  // 遍历路由表查找匹配
  for (const route of routes) {
    if (route.method !== upperMethod) continue
    const pathParams = matchPath(route.pattern, basePath)
    if (pathParams !== null) {
      try {
        const result = route.handler(pathParams, query, bodyObj)
        // 每次写操作后持久化
        if (upperMethod !== 'GET') {
          persistMockDb()
        }
        return Promise.resolve(result)
      } catch (e) {
        if (e instanceof MockBusinessError) {
          return Promise.resolve(err(e.code, e.message))
        }
        const message = e instanceof Error ? e.message : 'Mock 处理异常'
        return Promise.resolve(err(90000, message))
      }
    }
  }

  // 未匹配 → 返回 null（走真实 HTTP）
  return null
}

/**
 * Mock 层 OpenAPI 契约类型
 *
 * 从 schema.d.ts 派生，供 mock/workflow 返回值与 OpenAPI 保持一致。
 * 新增 mock 响应类型时请优先在此声明，再在 workflow 中引用。
 */
import type { components } from '@/api/types/schema'
import type { MockDraft } from './types'

/** OpenAPI 组件 schema 别名 */
export type Schema = components['schemas']

/* ---- 通用 ---- */

export type MediaFile = Schema['MediaFile']
export type LocationInfo = Schema['LocationInfo']
export type EmptyData = Schema['EmptyData']

/* ---- 活动 ---- */

export type ActivitySummary = Schema['Activities.ActivitySummary']
export type ActivityDetail = Schema['Activities.ActivityDetail']
export type ActivityMapPoint = Schema['Activities.ActivityMapPoint']
export type ActivityParticipant = Schema['Activities.ActivityParticipant']
export type ActivityParticipationState = Schema['Activities.ActivityParticipationState']
export type ActivityReview = Schema['Activities.ActivityReview']
export type ActivityReviewListItem = Schema['Activities.ActivityReviewListItem']
export type MyActivityReviewResult = Schema['Activities.MyActivityReviewResult']
export type MyActivitySummaryResult = Schema['Activities.MyActivitySummaryResult']
export type ActivitySummaryPost = Schema['Activities.ActivitySummaryPost']
export type ActivityTemplate = Schema['Activities.ActivityTemplate']
export type ActivityDraftDetail = Schema['Activities.ActivityDraftDetail']
export type ActivityDraftSummary = Schema['Activities.ActivityDraftSummary']
export type ActivityDraftUpsertRequest = Schema['Activities.ActivityDraftUpsertRequest']
export type RegisteredActivitySummary = Schema['Activities.RegisteredActivitySummary']
export type RegistrationResult = Schema['Activities.RegistrationResult']
export type CheckInRecord = Schema['Activities.CheckInRecord']
export type CheckInQrCode = Schema['Activities.CheckInQrCode']
export type ActivityFeedTab = Schema['Activities.ActivityFeedTab']
export type ActivityRuntimeStatus = Schema['Activities.ActivityRuntimeStatus']
export type RegisterActivityRequest = Schema['Activities.RegisterActivityRequest']
export type CheckInRequest = Schema['Activities.CheckInRequest']
export type ActivityReviewRequest = Schema['Activities.ActivityReviewRequest']
export type ActivitySummaryPostRequest = Schema['Activities.ActivitySummaryPostRequest']

/**
 * 活动搜索筛选（对齐 OpenAPI ActivityOperations_searchActivities query）。
 *
 * runtimeStatus 为 mock 内部扩展字段，OpenAPI 搜索 query 不含此项。
 */
export type ActivitySearchQuery = {
  keyword?: string
  activityTypes?: string[]
  city?: string
  minFee?: number
  maxFee?: number
  startAtFrom?: string
  startAtTo?: string
  longitude?: number
  latitude?: number
  distanceMeters?: number
  runtimeStatus?: ActivityRuntimeStatus
}

/**
 * 草稿写入输入：OpenAPI Upsert 字段 + mock 内部兼容字段。
 *
 * 前置条件：来自 API 模块或页面表单
 * 后置条件：normalizeDraftInput 可将其转为 mock 库存储结构
 */
export type MockDraftUpsertInput = ActivityDraftUpsertRequest & {
  startTime?: string
  endTime?: string
  fee?: number
  images?: string[]
  coverUrl?: string
  sourceType?: MockDraft['sourceType']
}

/* ---- 身份 ---- */

export type LoginResult = Schema['Identity.LoginResult']
export type PublicUserProfile = Schema['Identity.PublicUserProfile']
export type NicknameAvailability = Schema['Identity.NicknameAvailability']
export type MerchantProfile = Schema['Identity.MerchantProfile']
export type InterestTagItem = Schema['Identity.InterestTagItem']
export type UpdatePersonalProfileRequest = Schema['Identity.UpdatePersonalProfileRequest']
export type UpdateMerchantProfileRequest = Schema['Identity.UpdateMerchantProfileRequest']
export type QualificationSubmitRequest = Schema['Identity.QualificationSubmitRequest']
export type UserKind = Schema['Identity.UserKind']

/* ---- 社交 ---- */

export type FriendItem = Schema['Social.FriendItem']
export type FriendRequest = Schema['Social.FriendRequest']
export type TeamProfile = Schema['Social.TeamProfile']
export type TeamCreateRequest = Schema['Social.TeamCreateRequest']
export type TeamJoinRequest = Schema['Social.TeamJoinRequest']
export type JoinTeamRequestBody = Schema['Social.JoinTeamRequest']
export type FriendRequestSource = Schema['Social.FriendRequestSource']
export type FriendRequestCreate = Schema['Social.FriendRequestCreate']

/* ---- 聊天 ---- */

export type ChatMessage = Schema['Chat.ChatMessage']
export type ConversationSummary = Schema['Chat.ConversationSummary']
export type SendMessageRequest = Schema['Chat.SendMessageRequest']
export type MessageKind = Schema['Chat.MessageKind']

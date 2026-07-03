/**
 * 活动 API 模块
 *
 * 封装活动列表、搜索、详情、参与人、评价、摘要、模板、克隆等接口。
 */
import { get, post, patch, upload } from '@/api/request'
import type { components } from '@/api/types/schema'

export type {
  ActivityDetail,
  ActivityDraftDetail,
  ActivityDraftSummary,
  ActivityParticipationState,
  ActivityReviewListItem,
  ActivitySummariesPage,
  ActivityReviewsPage,
  ActivitySummary,
  ActivitySummaryPost,
  ActivityTemplate,
  CheckInQrCode,
  MediaFile,
  MyActivityReviewResult,
  MyActivitySummaryResult,
  RegistrationResult,
} from '@/api/types/activity-schemas'

import type { MediaFile } from '@/api/types/activity-schemas'
type ActivityFeedTab = components['schemas']['Activities.ActivityFeedTab']
type ActivityDraftUpsertRequest = components['schemas']['Activities.ActivityDraftUpsertRequest']
type ActivitySummaryPostRequest = components['schemas']['Activities.ActivitySummaryPostRequest']
type ActivityReviewRequest = components['schemas']['Activities.ActivityReviewRequest']

/** 搜索活动查询参数（与 OpenAPI ActivityOperations_searchActivities 对齐） */
export type SearchActivitiesParams = {
  keyword?: string
  page: number
  pageSize: number
  activityTypes?: string[]
  city?: string
  startAtFrom?: string
  startAtTo?: string
  minFee?: number
  maxFee?: number
}

/** 获取活动信息流（首页 Feed） */
export function getFeed(tab: ActivityFeedTab, page: number, pageSize: number) {
  return get('/activities/feed', {
    query: { tab, page, pageSize },
  })
}

/** 搜索活动 */
export function searchActivities(params: SearchActivitiesParams) {
  return get('/activities/search', {
    query: params,
  })
}

/** 获取地图范围内的活动列表 */
export function getMapActivities(longitude: number, latitude: number, distanceMeters: number) {
  return get('/activities/map', {
    query: { longitude, latitude, distanceMeters },
  })
}

/** 获取活动详情 */
export function getActivityDetail(activityId: string) {
  return get('/activities/{activityId}', {
    path: { activityId },
  })
}

/** 获取当前用户相对于指定活动的参与状态 */
export function getParticipationState(activityId: string) {
  return get('/activities/{activityId}/participation-state', {
    path: { activityId },
  })
}

/** 获取我创建的活动列表 */
export function getMyActivities(page?: number, pageSize?: number) {
  return get('/activities/mine', {
    query: { page: page ?? 1, pageSize: pageSize ?? 100 },
  })
}

/** 获取活动参与者列表 */
export function getParticipants(activityId: string, page: number, pageSize: number) {
  return get('/activities/{activityId}/participants', {
    path: { activityId },
    query: { page, pageSize },
  })
}

/** 批量上传活动图片，返回所有上传结果 */
export function uploadActivityImages(filePaths: string[]): Promise<MediaFile[]> {
  return Promise.all(filePaths.map((fp) => upload<MediaFile>('/activities/media/images', fp)))
}

/** 批量上传评价图片，返回所有上传结果 */
export function uploadReviewImages(filePaths: string[]): Promise<MediaFile[]> {
  return Promise.all(
    filePaths.map((fp) => upload<MediaFile>('/activities/media/review-images', fp)),
  )
}

/** 克隆历史活动为新草稿 */
export function cloneActivity(activityId: string) {
  return post('/activities/{activityId}/clone', {
    path: { activityId },
  })
}

/** 获取活动模板列表 */
export function getTemplates() {
  return get('/activities/templates')
}

/** 从模板创建活动草稿 */
export function createDraftFromTemplate(templateId: string) {
  return post('/activities/templates/{templateId}/drafts', {
    path: { templateId },
  })
}

/** 获取当前用户的草稿列表 */
export function getDrafts() {
  return get('/activities/drafts')
}

/** 获取指定草稿详情 */
export function getDraft(activityId: string) {
  return get('/activities/drafts/{activityId}', {
    path: { activityId },
  })
}

/** 创建新草稿 */
export function createDraft(data: ActivityDraftUpsertRequest) {
  return post('/activities/drafts', {
    body: data,
  })
}

/** 更新已有草稿 */
export function updateDraft(activityId: string, data: ActivityDraftUpsertRequest) {
  return patch('/activities/drafts/{activityId}', {
    path: { activityId },
    body: data,
  })
}

/** 提交草稿审核 */
export function submitDraft(activityId: string) {
  return post('/activities/{activityId}/submit', {
    path: { activityId },
  })
}

/** 获取活动图文总结列表 */
export function getActivitySummaries(activityId: string, page?: number, pageSize?: number) {
  return get('/activities/{activityId}/summaries', {
    path: { activityId },
    query: { page: page ?? 1, pageSize: pageSize ?? 20 },
  })
}

/** 获取当前用户对指定活动发布的总结 */
export function getMyActivitySummary(activityId: string) {
  return get('/activities/{activityId}/summaries/mine', {
    path: { activityId },
  })
}

/** 发布活动图文总结 */
export function createActivitySummary(activityId: string, data: ActivitySummaryPostRequest) {
  return post('/activities/{activityId}/summaries', {
    path: { activityId },
    body: data,
  })
}

/** 获取活动评价列表 */
export function getActivityReviews(activityId: string, page?: number, pageSize?: number) {
  return get('/activities/{activityId}/reviews', {
    path: { activityId },
    query: { page: page ?? 1, pageSize: pageSize ?? 20 },
  })
}

/** 获取当前用户对指定活动的评价 */
export function getMyActivityReview(activityId: string) {
  return get('/activities/{activityId}/reviews/mine', {
    path: { activityId },
  })
}

/** 提交活动评价 */
export function createActivityReview(activityId: string, data: ActivityReviewRequest) {
  return post('/activities/{activityId}/reviews', {
    path: { activityId },
    body: data,
  })
}

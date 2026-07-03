/**
 * 活动域 OpenAPI 类型
 *
 * 从 schema.d.ts 派生，供页面与组件引用，避免手写 interface 与内联 as 断言。
 */
import type { PageResult } from '@/api/types'
import type { components } from '@/api/types/schema'

type Schema = components['schemas']

export type MediaFile = Schema['MediaFile']
export type ActivityDetail = Schema['Activities.ActivityDetail']
export type ActivityParticipationState = Schema['Activities.ActivityParticipationState']
export type ActivitySummary = Schema['Activities.ActivitySummary']
export type ActivityDraftSummary = Schema['Activities.ActivityDraftSummary']
export type ActivityDraftDetail = Schema['Activities.ActivityDraftDetail']
export type ActivityTemplate = Schema['Activities.ActivityTemplate']
export type ActivitySummaryPost = Schema['Activities.ActivitySummaryPost']
export type ActivityReviewListItem = Schema['Activities.ActivityReviewListItem']
export type MyActivityReviewResult = Schema['Activities.MyActivityReviewResult']
export type MyActivitySummaryResult = Schema['Activities.MyActivitySummaryResult']
export type CheckInQrCode = Schema['Activities.CheckInQrCode']
export type RegistrationResult = Schema['Activities.RegistrationResult']

/** 活动总结列表分页响应 */
export type ActivitySummariesPage = PageResult<ActivitySummaryPost>

/** 活动评价列表分页响应 */
export type ActivityReviewsPage = PageResult<ActivityReviewListItem>

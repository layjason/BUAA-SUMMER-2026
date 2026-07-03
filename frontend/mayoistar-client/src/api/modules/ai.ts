/**
 * AI 功能 API 模块
 *
 * 封装 AI 活动计划生成、图片分类等智能功能接口。
 */
import { post } from '@/api/request'
import type { components } from '@/api/types/schema'

export type { ActivityPlanningResult, ImageClassificationResult } from '@/api/types/ai-schemas'

type ActivityPlanningRequest = components['schemas']['Ai.ActivityPlanningRequest']

/** 使用 AI 生成活动计划 */
export function generateActivityPlan(params: ActivityPlanningRequest) {
  return post('/ai/activity-plans', {
    body: params,
  })
}

/** 使用 AI 对图片进行分类 */
export function classifyImages(mediaIds: string[]) {
  return post('/ai/image-classifications', {
    body: { mediaIds },
  })
}

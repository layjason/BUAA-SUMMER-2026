/**
 * AI 功能 API 模块
 *
 * 封装 AI 活动计划生成、图片分类等智能功能接口。
 */
import { get, post } from '@/api/request'
import type { components } from '@/api/types/schema'

export type {
  ActivityPlanningResult,
  ImageClassificationResult,
  ImageClassificationSubmitResponse,
} from '@/api/types/ai-schemas'

type ActivityPlanningRequest = components['schemas']['Ai.ActivityPlanningRequest']
type ImageClassificationTaskQueryResponse =
  components['schemas']['Ai.ImageClassificationTaskQueryResponse']

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

/** 查询 AI 图片分类任务结果 */
export function getImageClassificationTaskResult(taskId: string) {
  return get('/ai/image-classifications/{taskId}', {
    path: { taskId },
  })
}

/**
 * 提交图片分类任务并轮询最终结果。
 *
 * 前置条件：mediaIds 来自已上传的活动图片。
 * 后置条件：任务成功时返回包含 items 的分类结果；失败、超时或本地等待超时则抛出错误。
 * 不变量：轮询只访问 OpenAPI 已定义的任务查询接口。
 */
export async function classifyImagesAndWait(
  mediaIds: string[],
  options: { intervalMs?: number; maxAttempts?: number } = {},
): Promise<ImageClassificationTaskQueryResponse> {
  if (mediaIds.length === 0) {
    return { status: 'succeeded', items: [] }
  }

  const intervalMs = options.intervalMs ?? 1500
  const maxAttempts = options.maxAttempts ?? 20
  const submitted = await classifyImages(mediaIds)

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const result = await getImageClassificationTaskResult(submitted.taskId)

    if (result.status === 'succeeded') return result
    if (result.status === 'failed' || result.status === 'timeout') {
      throw new Error(result.errorMessage || '图片分类任务失败')
    }

    await new Promise<void>((resolve) => setTimeout(resolve, intervalMs))
  }

  throw new Error('图片分类任务等待超时')
}

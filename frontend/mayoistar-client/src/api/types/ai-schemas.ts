/**
 * AI 域 OpenAPI 类型
 *
 * 从 schema.d.ts 派生，供页面与组件引用。
 */
import type { components } from '@/api/types/schema'

type Schema = components['schemas']

export type ActivityPlanningResult = Schema['Ai.ActivityPlanningResult']
export type ImageClassificationSubmitResponse = Schema['Ai.ImageClassificationSubmitResponse']
export type ImageClassificationTaskQueryResponse = Schema['Ai.ImageClassificationTaskQueryResponse']
export type ImageClassificationItem = Schema['Ai.ImageClassificationItem']

/**
 * 图片分类查询结果（兼容别名）
 *
 * api-spec 已改为异步任务模式，旧同步类型 ImageClassificationResult 已移除。
 * summary.vue 等调用方仍引用此别名，待活动模块改为提交后轮询 taskId。
 */
export type ImageClassificationResult = ImageClassificationTaskQueryResponse

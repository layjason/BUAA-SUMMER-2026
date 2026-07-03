/**
 * AI 域 OpenAPI 类型
 *
 * 从 schema.d.ts 派生，供页面与组件引用。
 */
import type { components } from '@/api/types/schema'

type Schema = components['schemas']

export type ActivityPlanningResult = Schema['Ai.ActivityPlanningResult']
export type ImageClassificationResult = Schema['Ai.ImageClassificationResult']

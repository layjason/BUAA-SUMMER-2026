import { describe, expect, it } from 'vitest'
import type { ActivityDetail, ActivityReviewListItem } from '@/api/types/activity-schemas'
import type { components } from '@/api/types/schema'

type Schema = components['schemas']

/**
 * 编译期校验：activity-schemas 必须与 schema.d.ts 同源。
 */
describe('activity-schemas 与 OpenAPI 对齐', () => {
  it('ActivityDetail 应映射 schema 定义', () => {
    const detail: ActivityDetail = {} as ActivityDetail
    const schemaDetail: Schema['Activities.ActivityDetail'] = detail
    expect(schemaDetail).toBe(detail)
  })

  it('ActivityReviewListItem 应包含 nickname 字段', () => {
    const item: ActivityReviewListItem = {
      reviewId: '1',
      activityId: '7',
      userId: '10001',
      nickname: '测试用户',
      rating: 5,
      tags: [],
      createdAt: '2026-01-01T00:00:00+08:00',
    }
    expect(item.nickname).toBe('测试用户')
  })
})

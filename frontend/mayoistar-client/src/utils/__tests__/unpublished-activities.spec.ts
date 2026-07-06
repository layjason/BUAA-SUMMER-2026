import { describe, expect, it } from 'vitest'
import {
  mergeUnpublishedActivityItems,
  toActivityUnpublishedItems,
  toDraftUnpublishedItems,
  type UnpublishedActivityItem,
} from '@/utils/unpublished-activities'
import type { ActivityDraftSummary, ActivitySummary } from '@/api/modules/activities'

describe('未发布活动列表工具', () => {
  it('草稿和我的活动返回同一 activityId 时应只保留草稿项', () => {
    const drafts: ActivityDraftSummary[] = [
      {
        activityId: 'draft-1',
        title: '周末徒步',
        reviewStatus: 'draft',
        createdAt: '2026-07-01T10:00:00+08:00',
        updatedAt: '2026-07-02T10:00:00+08:00',
      },
    ]
    const activities = [
      {
        activityId: 'draft-1',
        title: '周末徒步',
        reviewStatus: 'draft',
        runtimeStatus: 'notStarted',
        startAt: '2026-08-01T10:00:00+08:00',
      },
      {
        activityId: 'pending-1',
        title: '审核中活动',
        reviewStatus: 'pending',
        runtimeStatus: 'registering',
        startAt: '2026-08-02T10:00:00+08:00',
      },
    ] as ActivitySummary[]

    const merged = mergeUnpublishedActivityItems([
      ...toDraftUnpublishedItems(drafts),
      ...toActivityUnpublishedItems(activities),
    ])

    expect(merged).toHaveLength(2)
    expect(merged.filter((item) => item.activityId === 'draft-1')).toHaveLength(1)
    expect(merged.find((item) => item.activityId === 'draft-1')?.source).toBe('draft')
  })

  it('合并后应按更新时间倒序排列', () => {
    const items: UnpublishedActivityItem[] = [
      {
        activityId: 'old',
        reviewStatus: 'draft',
        createdAt: '2026-07-01T10:00:00+08:00',
        updatedAt: '2026-07-01T10:00:00+08:00',
        source: 'draft',
      },
      {
        activityId: 'new',
        reviewStatus: 'pending',
        createdAt: '2026-07-03T10:00:00+08:00',
        updatedAt: '2026-07-03T10:00:00+08:00',
        source: 'activity',
      },
    ]

    expect(mergeUnpublishedActivityItems(items).map((item) => item.activityId)).toEqual([
      'new',
      'old',
    ])
  })
})

import type { ActivityDraftSummary, ActivitySummary } from '@/api/modules/activities'

export type UnpublishedSource = 'draft' | 'activity'

export type UnpublishedActivityItem = {
  activityId: string
  title?: string
  reviewStatus: ActivityDraftSummary['reviewStatus']
  createdAt: string
  updatedAt: string
  source: UnpublishedSource
  runtimeStatus?: ActivitySummary['runtimeStatus']
  startAt?: string
}

/**
 * 将草稿接口结果转换为未发布列表项。
 *
 * 前置条件：drafts 来自 getDrafts，可能包含非草稿状态历史数据。
 * 后置条件：仅返回 reviewStatus 为 draft 的列表项。
 * 不变量：不修改 drafts 原数组。
 *
 * @param drafts 草稿摘要列表
 */
export function toDraftUnpublishedItems(drafts: ActivityDraftSummary[]): UnpublishedActivityItem[] {
  return drafts
    .filter((item) => item.reviewStatus === 'draft')
    .map((item) => ({
      activityId: item.activityId,
      title: item.title,
      reviewStatus: item.reviewStatus,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
      source: 'draft' as const,
    }))
}

/**
 * 将我的活动接口结果转换为审核流程中的未发布列表项。
 *
 * 前置条件：activities 来自 getMyActivities。
 * 后置条件：过滤掉已发布活动，保留草稿、审核中、需修改和驳回状态。
 * 不变量：不修改 activities 原数组。
 *
 * @param activities 我的活动摘要列表
 */
export function toActivityUnpublishedItems(
  activities: ActivitySummary[],
): UnpublishedActivityItem[] {
  return activities
    .filter((item) => item.reviewStatus !== 'approved')
    .map((item) => ({
      activityId: item.activityId,
      title: item.title,
      reviewStatus: item.reviewStatus,
      createdAt: item.startAt,
      updatedAt: item.startAt,
      source: 'activity' as const,
      runtimeStatus: item.runtimeStatus,
      startAt: item.startAt,
    }))
}

/**
 * 合并未发布活动并按 activityId 去重。
 *
 * 前置条件：items 可包含来自草稿接口和我的活动接口的同一 activityId。
 * 后置条件：同一 activityId 只保留一项；草稿项优先于 activity 草稿重复项。
 * 不变量：返回结果按 updatedAt 倒序排列，不修改输入数组。
 *
 * @param items 待合并的未发布活动项
 */
export function mergeUnpublishedActivityItems(
  items: UnpublishedActivityItem[],
): UnpublishedActivityItem[] {
  const merged = new Map<string, UnpublishedActivityItem>()

  for (const item of items) {
    const existing = merged.get(item.activityId)
    if (!existing) {
      merged.set(item.activityId, item)
      continue
    }

    if (existing.source !== 'draft' && item.source === 'draft') {
      merged.set(item.activityId, item)
      continue
    }

    if (
      existing.source === item.source &&
      new Date(item.updatedAt) > new Date(existing.updatedAt)
    ) {
      merged.set(item.activityId, item)
    }
  }

  return [...merged.values()].sort(
    (left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
  )
}

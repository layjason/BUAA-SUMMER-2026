/**
 * 活动封面补全
 *
 * 搜索接口当前不返回 coverImage，通过活动详情接口按需补全封面。
 */
import { getActivityDetail } from '@/api/modules/activities'
import type { MediaFile } from '@/api/types/activity-schemas'

const coverCache = new Map<string, MediaFile | null>()

type CoverCarrier = { signedUrl?: string } | null | undefined

function hasCoverImage(cover?: CoverCarrier): boolean {
  return !!cover?.signedUrl?.trim()
}

/**
 * 从活动详情解析封面图
 */
export function resolveCoverFromDetail(detail: {
  coverImage?: MediaFile
  images?: MediaFile[]
}): MediaFile | null {
  if (hasCoverImage(detail.coverImage)) return detail.coverImage!
  const firstImage = detail.images?.find((image) => hasCoverImage(image))
  return firstImage ?? null
}

async function loadCoverForActivity(activityId: string): Promise<MediaFile | null> {
  if (coverCache.has(activityId)) return coverCache.get(activityId) ?? null
  try {
    const detail = await getActivityDetail(activityId)
    const cover = resolveCoverFromDetail(detail)
    coverCache.set(activityId, cover)
    return cover
  } catch {
    coverCache.set(activityId, null)
    return null
  }
}

/**
 * 为缺少封面的活动摘要补全 coverImage
 *
 * 前置条件：items 来自 searchActivities 或同类摘要列表
 * 后置条件：有图的活动带上 coverImage；失败或无图时保持原样
 */
export async function enrichActivitiesWithCoverImages<
  T extends { activityId: string; coverImage?: CoverCarrier },
>(items: T[]): Promise<T[]> {
  if (!items.length) return items

  const enriched = items.map((item) => ({ ...item }))
  const pending = enriched.filter((item) => !hasCoverImage(item.coverImage))
  if (!pending.length) return enriched

  await Promise.all(
    pending.map(async (item) => {
      const cover = await loadCoverForActivity(item.activityId)
      if (cover) item.coverImage = cover
    }),
  )

  return enriched
}

/** 测试或页面卸载时清空封面缓存 */
export function clearActivityCoverCache(): void {
  coverCache.clear()
}

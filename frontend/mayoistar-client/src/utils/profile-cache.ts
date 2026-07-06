import { getUserProfile } from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']

export interface ProfileCacheEntry {
  nickname: string
  avatar?: MediaFile
}

export interface UserAvatarCarrier {
  userId: string
  avatar?: MediaFile | null
}

/** 批量加载用户公开资料（昵称 + 头像），与 user-profile 同源 */
export async function loadUserProfileCache(
  userIds: Iterable<string>,
): Promise<Record<string, ProfileCacheEntry>> {
  const cache: Record<string, ProfileCacheEntry> = {}
  const uniqueIds = [...new Set([...userIds].filter(Boolean))]
  if (uniqueIds.length === 0) return cache

  await Promise.all(
    uniqueIds.map(async (userId) => {
      try {
        const profile = await getUserProfile(userId)
        cache[userId] = {
          nickname: profile.nickname || `用户 ${userId}`,
          avatar: profile.avatar,
        }
      } catch {
        cache[userId] = { nickname: `用户 ${userId}` }
      }
    }),
  )

  return cache
}

/** 从列表项中收集尚未带 avatar 的用户 ID，避免重复请求 profile */
export function collectUserIdsMissingAvatar(items: UserAvatarCarrier[]): string[] {
  return [
    ...new Set(items.filter((item) => !item.avatar?.signedUrl?.trim()).map((item) => item.userId)),
  ]
}

export function getCachedAvatar(
  cache: Record<string, ProfileCacheEntry>,
  userId: string,
): MediaFile | undefined {
  return cache[userId]?.avatar
}

export function getCachedNickname(
  cache: Record<string, ProfileCacheEntry>,
  userId: string,
  fallback?: string,
): string {
  return cache[userId]?.nickname || fallback || `用户 ${userId}`
}

/** 优先使用 profileCache，列表项自带 avatar 作为兜底 */
export function resolveCachedAvatar(
  cache: Record<string, ProfileCacheEntry>,
  userId: string,
  fallback?: MediaFile | null,
): MediaFile | undefined {
  const cached = getCachedAvatar(cache, userId)
  if (cached?.signedUrl?.trim()) return cached
  const fallbackUrl = fallback?.signedUrl?.trim()
  return fallbackUrl ? (fallback ?? undefined) : undefined
}

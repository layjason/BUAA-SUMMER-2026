/**
 * 活动同伴推荐（基于现有 OpenAPI 接口组合）
 *
 * 从「我的报名」+「活动参与者」推导同活动但非好友的用户。
 */
import { getMyRegistrations } from '@/api/modules/registrations'
import { getParticipants } from '@/api/modules/activities'
import { getFriends } from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']

export interface ActivityCompanionItem {
  userId: string
  nickname: string
  avatar?: MediaFile
  activityTitle?: string
}

function unwrapItems<T>(result: unknown): T[] {
  if (Array.isArray(result)) return result
  return ((result as Record<string, unknown>).items as T[]) ?? []
}

/** 获取可能认识的活动同伴（非好友、同活动参与者） */
export async function fetchActivityCompanions(
  currentUserId: string,
): Promise<ActivityCompanionItem[]> {
  const friends = unwrapItems<{ userId: string }>(await getFriends())
  const friendIds = new Set(friends.map((f) => f.userId))

  const regs = unwrapItems<{ activityId?: string; title?: string }>(await getMyRegistrations(1, 50))

  const seen = new Set<string>()
  const companions: ActivityCompanionItem[] = []

  for (const reg of regs) {
    if (!reg.activityId) continue
    try {
      const participants = unwrapItems<{
        userId: string
        nickname: string
        avatar?: MediaFile
      }>(await getParticipants(reg.activityId, 1, 50))
      for (const p of participants) {
        if (!p.userId || p.userId === currentUserId) continue
        if (friendIds.has(p.userId) || seen.has(p.userId)) continue
        seen.add(p.userId)
        companions.push({
          userId: p.userId,
          nickname: p.nickname,
          avatar: p.avatar,
          activityTitle: reg.title,
        })
      }
    } catch {
      /* 单个活动失败时跳过 */
    }
  }

  return companions
}

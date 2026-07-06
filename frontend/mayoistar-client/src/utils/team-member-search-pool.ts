import { getTeamMembers } from '@/api/modules/teams'
import type { components } from '@/api/types/schema'
import { extractPageItems } from '@/utils/page-result'
import { loadUserProfileCache, type ProfileCacheEntry } from '@/utils/profile-cache'

type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamMember = components['schemas']['Social.TeamMember']

export interface TeamMemberSearchUser {
  userId: string
  nickname: string
}

/** 从「我的小队 + 发现小队」聚合成员，并用 profile 补全头像 */
export async function collectUsersFromTeams(
  teams: TeamProfile[],
  currentUserId: string,
): Promise<{ users: TeamMemberSearchUser[]; profileCache: Record<string, ProfileCacheEntry> }> {
  const teamMap = new Map<string, TeamProfile>()
  for (const team of teams) {
    teamMap.set(team.teamId, team)
  }

  const pool = new Map<string, TeamMemberSearchUser>()
  const memberResults = await Promise.all(
    [...teamMap.values()].map((team) => getTeamMembers(team.teamId, 1, 100).catch(() => null)),
  )

  for (const result of memberResults) {
    if (!result) continue
    for (const member of extractPageItems<TeamMember>(result)) {
      if (!member.userId || member.userId === currentUserId || pool.has(member.userId)) continue
      pool.set(member.userId, {
        userId: member.userId,
        nickname: member.nickname,
      })
    }
  }

  const users = Array.from(pool.values())
  const profileCache = await loadUserProfileCache(users.map((user) => user.userId))
  return { users, profileCache }
}

export function matchTeamMemberKeyword(
  user: TeamMemberSearchUser,
  profileCache: Record<string, ProfileCacheEntry>,
  keyword: string,
): boolean {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) return false
  const haystack = [user.nickname, profileCache[user.userId]?.nickname ?? '', user.userId]
    .join(' ')
    .toLowerCase()
  return haystack.includes(normalized)
}

/**
 * 活动参与者列表访问权限
 *
 * 与 api-spec /activities/{activityId}/participants 及后端 ActivityQueryService 对齐：
 * 管理员、活动发起人或已加入该活动的用户可查看参与者列表。
 */
import type { ActivityParticipationState } from '@/api/modules/activities'

/** 视为已加入活动、可查看参与者列表的报名状态 */
export const ACTIVITY_PARTICIPANT_JOINED_STATUSES = [
  'registered',
  'waiting',
  'waitingConfirmation',
  'checkedIn',
] as const

export type ActivityParticipantJoinedStatus = (typeof ACTIVITY_PARTICIPANT_JOINED_STATUSES)[number]

/**
 * 判断当前用户是否可查看活动参与者列表。
 *
 * 前置条件：organizerId、userId、participation 来自活动详情与参与状态接口。
 * 后置条件：返回值与后端 checkParticipantListPermission 规则一致（不含管理员分支）。
 * 不变量：不发起网络请求，仅做前端准入判断。
 */
export function canViewActivityParticipants(options: {
  isLoggedIn: boolean
  organizerId?: string | null
  userId?: string | null
  participation?: ActivityParticipationState | null
}): boolean {
  if (!options.isLoggedIn || options.userId == null || options.userId === '') return false
  if (
    options.organizerId != null &&
    options.organizerId !== '' &&
    String(options.organizerId) === String(options.userId)
  ) {
    return true
  }
  const status = options.participation?.status
  if (status == null) return false
  return (ACTIVITY_PARTICIPANT_JOINED_STATUSES as readonly string[]).includes(status)
}

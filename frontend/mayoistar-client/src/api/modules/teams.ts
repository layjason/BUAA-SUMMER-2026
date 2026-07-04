/**
 * 队伍 API 模块
 *
 * 封装队伍创建、管理、加入/退出、成员角色、积分等接口。
 */
import { get, post, patch, del } from '@/api/request'
import type { components } from '@/api/types/schema'

type TeamCreateRequest = components['schemas']['Social.TeamCreateRequest']
type TeamJoinRequestDecision = components['schemas']['Social.TeamJoinRequestDecision']
type TeamMemberRoleUpdate = components['schemas']['Social.TeamMemberRoleUpdate']

/** 获取我的队伍列表 */
export function getTeams() {
  return get('/social/teams')
}

/** 搜索/发现小队 */
export function searchTeams(params?: {
  keyword?: string
  tags?: string[]
  page?: number
  pageSize?: number
}) {
  return get('/social/teams', {
    query: params as Record<string, unknown>,
  })
}

/** 创建队伍 */
export function createTeam(data: TeamCreateRequest) {
  return post('/social/teams', {
    body: data,
  })
}

/** 获取队伍详情 */
export function getTeamDetail(teamId: string) {
  return get('/social/teams/{teamId}', {
    path: { teamId },
  })
}

/** 解散队伍 */
export function dissolveTeam(teamId: string) {
  return del('/social/teams/{teamId}', {
    path: { teamId },
  })
}

/** 申请加入队伍 */
export function joinTeam(teamId: string) {
  return post('/social/teams/{teamId}/join', {
    path: { teamId },
  })
}

/** 退出队伍 */
export function leaveTeam(teamId: string) {
  return post('/social/teams/{teamId}/leave', {
    path: { teamId },
  })
}

/** 获取队伍成员列表 */
export function getTeamMembers(teamId: string, page: number, pageSize: number) {
  return get('/social/teams/{teamId}/members', {
    path: { teamId },
    query: { page, pageSize },
  })
}

/** 获取队伍加入请求列表 */
export function getTeamJoinRequests(teamId: string) {
  return get('/social/teams/{teamId}/join-requests', {
    path: { teamId },
  })
}

/** 处理队伍加入请求（接受或拒绝） */
export function handleJoinRequest(teamId: string, requestId: string, accept: boolean) {
  const body: TeamJoinRequestDecision = { accepted: accept }
  return post('/social/teams/{teamId}/join-requests/{requestId}/decision', {
    path: { teamId, requestId },
    body,
  })
}

/** 更新队伍成员角色 */
export function updateMemberRole(
  teamId: string,
  memberId: string,
  role: TeamMemberRoleUpdate['role'],
) {
  const body: TeamMemberRoleUpdate = { role }
  return patch('/social/teams/{teamId}/members/{memberId}/role', {
    path: { teamId, memberId },
    body,
  })
}

/** 获取队伍积分信息 */
export function getTeamPoints(teamId: string) {
  return get('/social/teams/{teamId}/points', {
    path: { teamId },
  })
}

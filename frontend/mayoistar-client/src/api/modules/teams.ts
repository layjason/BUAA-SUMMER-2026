/**
 * 队伍 API 模块
 *
 * 封装队伍创建、管理、加入/退出、成员角色、积分等接口。
 */
import { get, post, patch, del } from '@/api/request'
import type { components } from '@/api/types/schema'

type TeamCreateRequest = components['schemas']['Social.TeamCreateRequest']
type JoinTeamRequest = components['schemas']['Social.JoinTeamRequest']
type TeamJoinRequestDecision = components['schemas']['Social.TeamJoinRequestDecision']
type TeamMemberRoleUpdate = components['schemas']['Social.TeamMemberRoleUpdate']

/** 发现/搜索小队查询参数（对齐 OpenAPI SocialOperations_searchTeams） */
export type SearchTeamsParams = {
  keyword?: string
  tags?: string[]
  page: number
  pageSize: number
}

/** 获取当前用户加入的小队列表（含已解散/已停用，按加入时间倒序） */
export function listMyTeams(page = 1, pageSize = 20) {
  return get('/social/teams/mine', {
    query: { page, pageSize },
  })
}

/** 按名称或标签搜索可发现的小队 */
export function searchTeams(params: SearchTeamsParams) {
  return get('/social/teams', {
    query: params,
  })
}

/** @deprecated 请使用 listMyTeams */
export function getTeams(page = 1, pageSize = 20) {
  return listMyTeams(page, pageSize)
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
export function joinTeam(teamId: string, message?: string) {
  const body: JoinTeamRequest = {}
  if (message?.trim()) body.message = message.trim()
  return post('/social/teams/{teamId}/join', {
    path: { teamId },
    body,
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

/** 获取队伍积分排行榜 */
export function getTeamPoints(teamId: string, page = 1, pageSize = 50) {
  return get('/social/teams/{teamId}/points', {
    path: { teamId },
    query: { page, pageSize },
  })
}

type ActivityUpsertRequest = components['schemas']['Activities.ActivityUpsertRequest']

/** 发布队内活动 */
export function createTeamActivity(teamId: string, data: ActivityUpsertRequest) {
  return post('/social/teams/{teamId}/activities', { path: { teamId }, body: data })
}

/** 队内活动列表 */
export function listTeamActivities(teamId: string, page = 1, pageSize = 20) {
  return get('/social/teams/{teamId}/activities', {
    path: { teamId },
    query: { page, pageSize },
  })
}

/** 队内活动详情 */
export function getTeamActivity(teamId: string, activityId: string) {
  return get('/social/teams/{teamId}/activities/{activityId}', {
    path: { teamId, activityId },
  })
}

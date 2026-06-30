import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import {
  TeamProfile,
  AdminTeamDetail,
  AdminTeamsPage,
  AdminTeamMembersPage,
  AdminTeamActivitiesPage,
  AdminTeamReportsPage,
  TeamStatus,
} from '../types';

export interface ListTeamsParams {
  keyword?: string;
  status?: TeamStatus;
  page?: number;
  pageSize?: number;
}

/**
 * 查询小队列表。
 *
 * 前置条件：管理员已登录。
 * 后置条件：返回小队分页列表。
 *
 * @param params 查询筛选与分页参数
 */
export async function listTeams(params: ListTeamsParams): Promise<AdminTeamsPage> {
  const page = params.page || 1;
  const pageSize = params.pageSize || 10;

  if (isMockMode()) {
    await simulateLatency(150);
    const filtered = mockDb.getTeams({
      keyword: params.keyword,
      status: params.status,
    });

    const total = filtered.length;
    const start = (page - 1) * pageSize;
    const items = filtered.slice(start, start + pageSize);

    return buildPaginatedResult(items, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  if (params.keyword) queryParams.set('keyword', params.keyword);
  if (params.status) queryParams.set('status', params.status);
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminTeamsPage>(`/admin/teams?${queryParams.toString()}`);
}

/**
 * 停用小队。
 *
 * 前置条件：小队存在且未被停用，reason 非空。
 * 后置条件：小队状态迁移为 disabled。
 *
 * @param teamId 小队标识
 * @param reason 停用原因
 */
export async function disableTeam(teamId: string, reason: string): Promise<TeamProfile> {
  if (isMockMode()) {
    await simulateLatency(200);
    const team = mockDb.disableTeam(teamId, reason);
    if (!team) {
      throw new Error('未找到该小队');
    }
    return team;
  }

  return request<TeamProfile>(`/admin/teams/${teamId}/disable`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

/**
 * 恢复小队。
 *
 * 前置条件：小队处于停用状态。
 * 后置条件：小队状态恢复为 active。
 *
 * @param teamId 小队标识
 */
export async function restoreTeam(teamId: string): Promise<TeamProfile> {
  if (isMockMode()) {
    await simulateLatency(200);
    const team = mockDb.restoreTeam(teamId);
    if (!team) {
      throw new Error('未找到该小队');
    }
    return team;
  }

  return request<TeamProfile>(`/admin/teams/${teamId}/restore`, {
    method: 'POST',
  });
}

/**
 * 获取后台小队详情（含治理记录）。
 *
 * 前置条件：管理员已登录，teamId 为有效小队标识。
 * 后置条件：返回包含治理记录的小队详情。
 *
 * @param teamId 小队标识
 */
export async function getTeam(teamId: string): Promise<AdminTeamDetail> {
  if (isMockMode()) {
    await simulateLatency(150);
    const detail = mockDb.getTeamDetail(teamId);
    if (!detail) {
      throw new Error('未找到该小队');
    }
    return detail;
  }

  return request<AdminTeamDetail>(`/admin/teams/${teamId}`);
}

/**
 * 查询小队成员列表。
 *
 * 前置条件：管理员已登录，teamId 为有效小队标识。
 * 后置条件：返回该小队成员分页列表。
 *
 * @param teamId 小队标识
 * @param page 页码
 * @param pageSize 每页数量
 */
export async function listTeamMembers(
  teamId: string,
  page = 1,
  pageSize = 20,
): Promise<AdminTeamMembersPage> {
  if (isMockMode()) {
    await simulateLatency(150);
    const items = mockDb.getTeamMembers(teamId);
    const total = items.length;
    const start = (page - 1) * pageSize;
    const sliced = items.slice(start, start + pageSize);
    return buildPaginatedResult(sliced, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminTeamMembersPage>(`/admin/teams/${teamId}/members?${queryParams.toString()}`);
}

/**
 * 查询小队队内活动列表。
 *
 * 前置条件：管理员已登录，teamId 为有效小队标识。
 * 后置条件：返回该小队活动分页列表。
 *
 * @param teamId 小队标识
 * @param page 页码
 * @param pageSize 每页数量
 */
export async function listTeamActivities(
  teamId: string,
  page = 1,
  pageSize = 10,
): Promise<AdminTeamActivitiesPage> {
  if (isMockMode()) {
    await simulateLatency(150);
    const items = mockDb.getTeamActivities(teamId);
    const total = items.length;
    const start = (page - 1) * pageSize;
    const sliced = items.slice(start, start + pageSize);
    return buildPaginatedResult(sliced, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminTeamActivitiesPage>(
    `/admin/teams/${teamId}/activities?${queryParams.toString()}`,
  );
}

/**
 * 查询小队相关举报记录。
 *
 * 前置条件：管理员已登录，teamId 为有效小队标识。
 * 后置条件：返回 targetType=team 且 targetId 为该小队的举报分页列表。
 *
 * @param teamId 小队标识
 * @param page 页码
 * @param pageSize 每页数量
 */
export async function listTeamReports(
  teamId: string,
  page = 1,
  pageSize = 10,
): Promise<AdminTeamReportsPage> {
  if (isMockMode()) {
    await simulateLatency(150);
    const items = mockDb.getTeamReports(teamId);
    const total = items.length;
    const start = (page - 1) * pageSize;
    const sliced = items.slice(start, start + pageSize);
    return buildPaginatedResult(sliced, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminTeamReportsPage>(`/admin/teams/${teamId}/reports?${queryParams.toString()}`);
}

import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import {
  AdminUserSummary,
  AdminUserDetail,
  AdminUsersPage,
  AdminUserActivitiesPage,
  AdminUserTeamsPage,
  UserKind,
  AccountStatus,
  QualificationStatus,
} from '../types';

export interface ListUsersParams {
  keyword?: string;
  kind?: UserKind;
  status?: AccountStatus;
  qualificationStatus?: QualificationStatus;
  page?: number;
  pageSize?: number;
}

export async function listUsers(params: ListUsersParams): Promise<AdminUsersPage> {
  const page = params.page || 1;
  const pageSize = params.pageSize || 10;

  if (isMockMode()) {
    await simulateLatency(150);
    const filtered = mockDb.getUsers({
      keyword: params.keyword,
      kind: params.kind,
      status: params.status,
      qualificationStatus: params.qualificationStatus,
    });

    const total = filtered.length;
    const start = (page - 1) * pageSize;
    const items = filtered.slice(start, start + pageSize);

    return buildPaginatedResult(items, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  if (params.keyword) queryParams.set('keyword', params.keyword);
  if (params.kind) queryParams.set('kind', params.kind);
  if (params.status) queryParams.set('status', params.status);
  if (params.qualificationStatus)
    queryParams.set('qualificationStatus', params.qualificationStatus);
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminUsersPage>(`/admin/users?${queryParams.toString()}`);
}

export async function banUser(
  userId: string,
  reason: string,
  bannedUntil: string,
): Promise<AdminUserSummary> {
  if (isMockMode()) {
    await simulateLatency(200);
    const summary = mockDb.banUser(userId, reason, bannedUntil);
    if (!summary) {
      throw new Error('未找到该用户');
    }
    return summary;
  }

  return request<AdminUserSummary>(`/admin/users/${userId}/ban`, {
    method: 'POST',
    body: JSON.stringify({ reason, bannedUntil }),
  });
}

export async function unbanUser(userId: string): Promise<AdminUserSummary> {
  if (isMockMode()) {
    await simulateLatency(200);
    const summary = mockDb.unbanUser(userId);
    if (!summary) {
      throw new Error('未找到该用户');
    }
    return summary;
  }

  return request<AdminUserSummary>(`/admin/users/${userId}/unban`, {
    method: 'POST',
  });
}

/**
 * 获取后台用户详情。
 *
 * 前置条件：管理员已登录，userId 为有效用户标识。
 * 后置条件：返回包含封禁信息的用户详情。
 *
 * @param userId 用户标识
 */
export async function getUser(userId: string): Promise<AdminUserDetail> {
  if (isMockMode()) {
    await simulateLatency(150);
    const detail = mockDb.getUserDetail(userId);
    if (!detail) {
      throw new Error('未找到该用户');
    }
    return detail;
  }

  return request<AdminUserDetail>(`/admin/users/${userId}`);
}

/**
 * 查询指定用户发布的活动列表。
 *
 * 前置条件：管理员已登录，userId 为有效用户标识。
 * 后置条件：返回该用户发布的活动分页列表。
 *
 * @param userId 用户标识
 * @param page 页码
 * @param pageSize 每页数量
 */
export async function listUserActivities(
  userId: string,
  page = 1,
  pageSize = 10,
): Promise<AdminUserActivitiesPage> {
  if (isMockMode()) {
    await simulateLatency(150);
    const items = mockDb.getUserActivities(userId);
    const total = items.length;
    const start = (page - 1) * pageSize;
    const sliced = items.slice(start, start + pageSize);
    return buildPaginatedResult(sliced, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminUserActivitiesPage>(
    `/admin/users/${userId}/activities?${queryParams.toString()}`,
  );
}

/**
 * 查询指定用户创建或参与的小队列表。
 *
 * 前置条件：管理员已登录，userId 为有效用户标识。
 * 后置条件：返回该用户相关的小队分页列表。
 *
 * @param userId 用户标识
 * @param page 页码
 * @param pageSize 每页数量
 */
export async function listUserTeams(
  userId: string,
  page = 1,
  pageSize = 10,
): Promise<AdminUserTeamsPage> {
  if (isMockMode()) {
    await simulateLatency(150);
    const items = mockDb.getUserTeams(userId);
    const total = items.length;
    const start = (page - 1) * pageSize;
    const sliced = items.slice(start, start + pageSize);
    return buildPaginatedResult(sliced, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminUserTeamsPage>(`/admin/users/${userId}/teams?${queryParams.toString()}`);
}

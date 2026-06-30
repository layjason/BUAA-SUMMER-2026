import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import {
  AdminUserSummary,
  AdminUsersPage,
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

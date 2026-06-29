/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import { TeamProfile, AdminTeamsPage, TeamStatus } from '../types';

export interface ListTeamsParams {
  keyword?: string;
  status?: TeamStatus;
  page?: number;
  pageSize?: number;
}

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

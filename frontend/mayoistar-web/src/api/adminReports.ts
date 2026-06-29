/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import { UserReport, AdminUserReportsPage, ReportStatus } from '../types';

export interface ListReportsParams {
  status?: ReportStatus;
  reporterUserId?: string;
  targetUserId?: string;
  page?: number;
  pageSize?: number;
}

export async function listUserReports(params: ListReportsParams): Promise<AdminUserReportsPage> {
  const page = params.page || 1;
  const pageSize = params.pageSize || 10;

  if (isMockMode()) {
    await simulateLatency(150);
    const filtered = mockDb.getReports({
      status: params.status,
      reporterUserId: params.reporterUserId,
      targetUserId: params.targetUserId,
    });

    const total = filtered.length;
    const start = (page - 1) * pageSize;
    const items = filtered.slice(start, start + pageSize);

    return buildPaginatedResult(items, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  if (params.status) queryParams.set('status', params.status);
  if (params.reporterUserId) queryParams.set('reporterUserId', params.reporterUserId);
  if (params.targetUserId) queryParams.set('targetUserId', params.targetUserId);
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminUserReportsPage>(`/admin/user-reports?${queryParams.toString()}`);
}

export async function decideUserReport(
  reportId: string,
  status: ReportStatus,
  handlingNote: string,
): Promise<UserReport> {
  if (isMockMode()) {
    await simulateLatency(200);
    const report = mockDb.decideUserReport(reportId, status, handlingNote);
    if (!report) {
      throw new Error('未找到该举报记录');
    }
    return report;
  }

  return request<UserReport>(`/admin/user-reports/${reportId}/decision`, {
    method: 'POST',
    body: JSON.stringify({ status, handlingNote }),
  });
}

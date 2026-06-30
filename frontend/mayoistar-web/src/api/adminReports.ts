import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import { Report, AdminReportsPage, ReportStatus, ReportTargetType } from '../types';

/**
 * 后台举报列表查询参数。
 *
 * 前置条件：各筛选字段可选，不传时不施加对应筛选。
 * 后置条件：构造的查询参数与 OpenAPI AdminReportQuery 对齐。
 */
export interface ListReportsParams {
  status?: ReportStatus;
  reporterUserId?: string;
  targetType?: ReportTargetType;
  targetId?: string;
  page?: number;
  pageSize?: number;
}

/**
 * 查询举报列表。
 *
 * 前置条件：管理员已登录。
 * 后置条件：返回分页举报列表，Mock 模式下使用本地数据。
 *
 * @param params 查询筛选与分页参数
 */
export async function listReports(params: ListReportsParams): Promise<AdminReportsPage> {
  const page = params.page || 1;
  const pageSize = params.pageSize || 10;

  if (isMockMode()) {
    await simulateLatency(150);
    const filtered = mockDb.getReports({
      status: params.status,
      reporterUserId: params.reporterUserId,
      targetType: params.targetType,
      targetId: params.targetId,
    });

    const total = filtered.length;
    const start = (page - 1) * pageSize;
    const items = filtered.slice(start, start + pageSize);

    return buildPaginatedResult(items, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  if (params.status) queryParams.set('status', params.status);
  if (params.reporterUserId) queryParams.set('reporterUserId', params.reporterUserId);
  if (params.targetType) queryParams.set('targetType', params.targetType);
  if (params.targetId) queryParams.set('targetId', params.targetId);
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminReportsPage>(`/admin/reports?${queryParams.toString()}`);
}

/**
 * 提交举报处理决议。
 *
 * 前置条件：reportId 为有效举报标识，status 和 handlingNote 非空。
 * 后置条件：举报状态和处理说明被更新，返回更新后的举报记录。
 *
 * @param reportId 举报记录标识
 * @param status 更新后的处理状态
 * @param handlingNote 后台处理说明
 */
export async function decideReport(
  reportId: string,
  status: ReportStatus,
  handlingNote: string,
): Promise<Report> {
  if (isMockMode()) {
    await simulateLatency(200);
    const report = mockDb.decideReport(reportId, status, handlingNote);
    if (!report) {
      throw new Error('未找到该举报记录');
    }
    return report;
  }

  return request<Report>(`/admin/reports/${reportId}/decision`, {
    method: 'POST',
    body: JSON.stringify({ status, handlingNote }),
  });
}

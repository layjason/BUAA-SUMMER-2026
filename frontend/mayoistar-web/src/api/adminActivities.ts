import { request, isMockMode, simulateLatency, buildPaginatedResult } from './client';
import { mockDb } from './mockDb';
import {
  ActivitySummary,
  ActivityDetail,
  AdminActivitiesPage,
  ActivityReviewStatus,
  ActivityRuntimeStatus,
} from '../types';

export interface ListActivitiesParams {
  keyword?: string;
  reviewStatus?: ActivityReviewStatus;
  runtimeStatus?: ActivityRuntimeStatus;
  page?: number;
  pageSize?: number;
}

export async function listActivities(params: ListActivitiesParams): Promise<AdminActivitiesPage> {
  const page = params.page || 1;
  const pageSize = params.pageSize || 10;

  if (isMockMode()) {
    await simulateLatency(200);
    const filtered = mockDb.getActivities({
      keyword: params.keyword,
      reviewStatus: params.reviewStatus,
      runtimeStatus: params.runtimeStatus,
    });

    const total = filtered.length;
    const start = (page - 1) * pageSize;
    const items = filtered.slice(start, start + pageSize).map((act) => {
      // Map Detail to Summary
      const summary: ActivitySummary = {
        activityId: act.activityId,
        title: act.title,
        tags: act.tags,
        startAt: act.startAt,
        endAt: act.endAt,
        location: act.location,
        coverImage: act.coverImage,
        feeAmount: act.feeAmount,
        reviewStatus: act.reviewStatus,
        runtimeStatus: act.runtimeStatus,
        registeredCount: act.registeredCount,
        occupiedCount: act.occupiedCount,
        capacity: act.capacity,
        requireLocationCheck: act.requireLocationCheck,
      };
      return summary;
    });

    return buildPaginatedResult(items, total, page, pageSize);
  }

  const queryParams = new URLSearchParams();
  if (params.keyword) queryParams.set('keyword', params.keyword);
  if (params.reviewStatus) queryParams.set('reviewStatus', params.reviewStatus);
  if (params.runtimeStatus) queryParams.set('runtimeStatus', params.runtimeStatus);
  queryParams.set('page', String(page));
  queryParams.set('pageSize', String(pageSize));

  return request<AdminActivitiesPage>(`/admin/activities?${queryParams.toString()}`);
}

export async function getActivity(activityId: string): Promise<ActivityDetail> {
  if (isMockMode()) {
    await simulateLatency(150);
    const detail = mockDb.getActivityDetail(activityId);
    if (!detail) {
      throw new Error('未找到该活动');
    }
    return detail;
  }

  return request<ActivityDetail>(`/admin/activities/${activityId}`);
}

export async function reviewActivity(
  activityId: string,
  result: 'approved' | 'rejected' | 'changeRequired',
  reason?: string,
): Promise<ActivityDetail> {
  if (isMockMode()) {
    await simulateLatency(200);
    const detail = mockDb.reviewActivity(activityId, result, reason);
    if (!detail) {
      throw new Error('未找到该活动');
    }
    return detail;
  }

  return request<ActivityDetail>(`/admin/activities/${activityId}/review`, {
    method: 'POST',
    body: JSON.stringify({ result, reason }),
  });
}

export async function takeDownActivity(
  activityId: string,
  reason: string,
): Promise<ActivityDetail> {
  if (isMockMode()) {
    await simulateLatency(200);
    const detail = mockDb.takeDownActivity(activityId, reason);
    if (!detail) {
      throw new Error('未找到该活动');
    }
    return detail;
  }

  return request<ActivityDetail>(`/admin/activities/${activityId}/take-down`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export async function restoreActivity(activityId: string): Promise<ActivityDetail> {
  if (isMockMode()) {
    await simulateLatency(200);
    const detail = mockDb.restoreActivity(activityId);
    if (!detail) {
      throw new Error('未找到该活动');
    }
    return detail;
  }

  return request<ActivityDetail>(`/admin/activities/${activityId}/restore`, {
    method: 'POST',
  });
}

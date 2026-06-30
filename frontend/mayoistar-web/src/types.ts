import type { components, paths } from './api/generated/openapi';

type JsonResponseBody<Response> = Response extends {
  content: { 'application/json': infer ResponseBody };
}
  ? ResponseBody
  : never;

type SuccessResponse<Response> = Extract<JsonResponseBody<Response>, { code: 200 }>;

/**
 * 根据 OpenAPI 路径与方法提取成功响应 data 类型。
 *
 * 前置条件：Path 和 Method 必须对应 OpenAPI 中存在且返回 JSON APIResult 的操作。
 * 后置条件：返回该操作 code=200 成功响应的 data 字段类型。
 * 不变量：仅做类型推导，不生成运行时代码。
 *
 * @template ApiPath OpenAPI paths 中的接口路径字面量
 * @template HttpMethod 该路径下存在的 HTTP 方法名
 */
export type SuccessData<
  ApiPath extends keyof paths,
  HttpMethod extends keyof paths[ApiPath],
> = paths[ApiPath][HttpMethod] extends { responses: { 200: infer Response } }
  ? SuccessResponse<Response> extends { data: infer ResponseData }
    ? ResponseData
    : never
  : never;

export type EntityId = components['schemas']['EntityId'];
export type DateTimeString = components['schemas']['DateTimeString'];
export type DateString = components['schemas']['DateString'];

export type UserKind = components['schemas']['Identity.UserKind'];
export type AccountStatus = components['schemas']['Identity.AccountStatus'];
export type QualificationStatus = components['schemas']['Identity.QualificationStatus'];
export type Gender = components['schemas']['Identity.Gender'];
export type TokenPair = components['schemas']['Identity.TokenPair'];
export type LoginResult = components['schemas']['Identity.LoginResult'];
export type QualificationDetail = components['schemas']['Identity.QualificationDetail'];
export type MerchantProfile = components['schemas']['Identity.MerchantProfile'];
export type PublicUserProfile = components['schemas']['Identity.PublicUserProfile'];
export type AdminUserSummary = components['schemas']['Admin.AdminUserSummary'];

export type MediaUsage = components['schemas']['MediaUsage'];
export type MediaFile = components['schemas']['MediaFile'];
export type GeoPoint = components['schemas']['GeoPoint'];
export type LocationInfo = components['schemas']['LocationInfo'];

export type ActivityReviewStatus = components['schemas']['Activities.ActivityReviewStatus'];
export type ActivityRuntimeStatus = components['schemas']['Activities.ActivityRuntimeStatus'];
export type AiCallStatus = components['schemas']['Ai.AiCallStatus'];
export type AiRiskLevel = components['schemas']['Ai.AiRiskLevel'];
export type AiContentReviewResult = components['schemas']['Ai.AiContentReviewResult'];
export type ReviewRecord = components['schemas']['ReviewRecord'];
export type ActivitySummary = components['schemas']['Activities.ActivitySummary'];
export type ActivityDetail = components['schemas']['Activities.ActivityDetail'];

export type TeamJoinMode = components['schemas']['Social.TeamJoinMode'];
export type TeamStatus = components['schemas']['Social.TeamStatus'];
export type TeamProfile = components['schemas']['Social.TeamProfile'];

export type ReportStatus = components['schemas']['Social.ReportStatus'];
export type UserReport = components['schemas']['Social.UserReport'];

export type AdminLoginRequest = components['schemas']['Admin.AdminLoginRequest'];
export type AdminChangePasswordRequest = components['schemas']['Admin.AdminChangePasswordRequest'];

export type AdminUsersPage = SuccessData<'/admin/users', 'get'>;
export type AdminActivitiesPage = SuccessData<'/admin/activities', 'get'>;
export type AdminTeamsPage = SuccessData<'/admin/teams', 'get'>;
export type AdminUserReportsPage = SuccessData<'/admin/user-reports', 'get'>;

export type PaginatedResult<Item> = {
  items: Item[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
};

export type APIResponse<ResponseData> = {
  code: 200;
  message: 'For Super Earth!';
  data: ResponseData;
};

export type APIResultEnvelope<ResponseData> = {
  code: number;
  message: string;
  data: ResponseData;
};

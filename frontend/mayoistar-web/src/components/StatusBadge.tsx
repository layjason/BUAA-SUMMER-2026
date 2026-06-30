import React from 'react';
import {
  USER_KIND_MAP,
  ACCOUNT_STATUS_MAP,
  QUALIFICATION_STATUS_MAP,
  ACTIVITY_REVIEW_MAP,
  ACTIVITY_RUNTIME_MAP,
  TEAM_JOIN_MODE_MAP,
  TEAM_STATUS_MAP,
  REPORT_STATUS_MAP,
  AI_RISK_LEVEL_MAP,
  StatusMapItem,
} from '../constants/statusMaps';

type StatusBadgeType =
  | 'userKind'
  | 'accountStatus'
  | 'qualificationStatus'
  | 'activityReview'
  | 'activityRuntime'
  | 'teamJoinMode'
  | 'teamStatus'
  | 'reportStatus'
  | 'aiRiskLevel';

const STATUS_MAPS: Record<StatusBadgeType, Record<string, StatusMapItem>> = {
  userKind: USER_KIND_MAP,
  accountStatus: ACCOUNT_STATUS_MAP,
  qualificationStatus: QUALIFICATION_STATUS_MAP,
  activityReview: ACTIVITY_REVIEW_MAP,
  activityRuntime: ACTIVITY_RUNTIME_MAP,
  teamJoinMode: TEAM_JOIN_MODE_MAP,
  teamStatus: TEAM_STATUS_MAP,
  reportStatus: REPORT_STATUS_MAP,
  aiRiskLevel: AI_RISK_LEVEL_MAP,
};

const FALLBACK_STATUS: StatusMapItem = {
  text: '',
  bgClass: 'bg-slate-100',
  textClass: 'text-slate-700',
};

interface StatusBadgeProps {
  id?: string;
  type: StatusBadgeType;
  value: string;
}

/**
 * 渲染平台状态枚举标签。
 *
 * 前置条件：type 为已登记的状态类型，value 为后端或 Mock 数据返回的状态值。
 * 后置条件：返回匹配状态文案和配色的标签；未知状态回退显示原始值。
 * 不变量：组件只根据 props 渲染，不修改外部状态。
 */
export const StatusBadge: React.FC<StatusBadgeProps> = ({ id, type, value }) => {
  const mapped = STATUS_MAPS[type][value] ?? { ...FALLBACK_STATUS, text: value };

  return (
    <span
      id={id}
      className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold transition-all whitespace-nowrap ${mapped.bgClass} ${mapped.textClass}`}
    >
      {mapped.text}
    </span>
  );
};

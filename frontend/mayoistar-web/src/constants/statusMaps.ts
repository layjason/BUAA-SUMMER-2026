import {
  UserKind,
  AccountStatus,
  QualificationStatus,
  ActivityReviewStatus,
  ActivityRuntimeStatus,
  TeamJoinMode,
  TeamStatus,
  ReportStatus,
  AiRiskLevel,
} from '../types';

export interface StatusMapItem {
  text: string;
  bgClass: string;
  textClass: string;
}

export const USER_KIND_MAP: Record<UserKind, StatusMapItem> = {
  personal: { text: '个人用户', bgClass: 'bg-blue-50', textClass: 'text-blue-700' },
  merchant: { text: '商家用户', bgClass: 'bg-emerald-50', textClass: 'text-emerald-700' },
  admin: { text: '管理员', bgClass: 'bg-purple-50', textClass: 'text-purple-700' },
};

export const ACCOUNT_STATUS_MAP: Record<AccountStatus, StatusMapItem> = {
  inactive: {
    text: '未激活',
    bgClass: 'bg-amber-50 border border-amber-200',
    textClass: 'text-amber-700',
  },
  active: {
    text: '正常',
    bgClass: 'bg-green-50 border border-green-200',
    textClass: 'text-green-700',
  },
  banned: { text: '已封禁', bgClass: 'bg-red-50 border border-red-200', textClass: 'text-red-700' },
};

export const QUALIFICATION_STATUS_MAP: Record<QualificationStatus, StatusMapItem> = {
  not_submitted: { text: '未提交', bgClass: 'bg-slate-100', textClass: 'text-slate-600' },
  pending: {
    text: '待审核',
    bgClass: 'bg-orange-50 border border-orange-200 animate-pulse',
    textClass: 'text-orange-700',
  },
  approved: {
    text: '已通过',
    bgClass: 'bg-emerald-50 border border-emerald-200',
    textClass: 'text-emerald-700',
  },
  rejected: {
    text: '已驳回',
    bgClass: 'bg-red-50 border border-red-200',
    textClass: 'text-red-700',
  },
};

export const ACTIVITY_REVIEW_MAP: Record<ActivityReviewStatus, StatusMapItem> = {
  draft: { text: '草稿', bgClass: 'bg-slate-100', textClass: 'text-slate-600' },
  pending: {
    text: '待审核',
    bgClass: 'bg-orange-50 border border-orange-200',
    textClass: 'text-orange-700',
  },
  approved: {
    text: '已通过',
    bgClass: 'bg-emerald-50 border border-emerald-200',
    textClass: 'text-emerald-700',
  },
  rejected: {
    text: '已驳回',
    bgClass: 'bg-red-50 border border-red-200',
    textClass: 'text-red-700',
  },
  changeRequired: {
    text: '需修改',
    bgClass: 'bg-amber-50 border border-amber-200',
    textClass: 'text-amber-700',
  },
};

export const ACTIVITY_RUNTIME_MAP: Record<ActivityRuntimeStatus, StatusMapItem> = {
  notStarted: {
    text: '未开始',
    bgClass: 'bg-slate-100 text-slate-700',
    textClass: 'text-slate-700',
  },
  registering: {
    text: '报名中',
    bgClass: 'bg-blue-50 border border-blue-200',
    textClass: 'text-blue-700',
  },
  registrationClosed: {
    text: '已截止',
    bgClass: 'bg-slate-200 text-slate-600',
    textClass: 'text-slate-600',
  },
  ongoing: {
    text: '进行中',
    bgClass: 'bg-indigo-50 border border-indigo-200',
    textClass: 'text-indigo-700',
  },
  ended: { text: '已结束', bgClass: 'bg-slate-100 text-slate-500', textClass: 'text-slate-500' },
  takenDown: {
    text: '已下架',
    bgClass: 'bg-red-50 border border-red-200',
    textClass: 'text-red-700 font-medium',
  },
};

export const TEAM_JOIN_MODE_MAP: Record<TeamJoinMode, StatusMapItem> = {
  publicJoin: { text: '公开加入', bgClass: 'bg-sky-50 text-sky-700', textClass: 'text-sky-700' },
  approvalRequired: {
    text: '审核加入',
    bgClass: 'bg-violet-50 text-violet-700',
    textClass: 'text-violet-700',
  },
};

export const TEAM_STATUS_MAP: Record<TeamStatus, StatusMapItem> = {
  active: {
    text: '正常',
    bgClass: 'bg-green-50 border border-green-200',
    textClass: 'text-green-700',
  },
  dissolved: {
    text: '已解散',
    bgClass: 'bg-slate-100 text-slate-500',
    textClass: 'text-slate-500',
  },
  disabled: {
    text: '已停用',
    bgClass: 'bg-red-50 border border-red-200',
    textClass: 'text-red-700 font-medium',
  },
};

export const REPORT_STATUS_MAP: Record<ReportStatus, StatusMapItem> = {
  pending: {
    text: '待处理',
    bgClass: 'bg-orange-50 border border-orange-200 animate-pulse',
    textClass: 'text-orange-700',
  },
  processing: {
    text: '处理中',
    bgClass: 'bg-blue-50 border border-blue-200',
    textClass: 'text-blue-700',
  },
  resolved: {
    text: '已解决',
    bgClass: 'bg-emerald-50 border border-emerald-200',
    textClass: 'text-emerald-700',
  },
  rejected: {
    text: '已驳回',
    bgClass: 'bg-slate-100 border border-slate-300',
    textClass: 'text-slate-600',
  },
};

export const AI_RISK_LEVEL_MAP: Record<AiRiskLevel, StatusMapItem> = {
  low: {
    text: '低风险',
    bgClass: 'bg-green-50 border border-green-200',
    textClass: 'text-green-700',
  },
  medium: {
    text: '中风险',
    bgClass: 'bg-amber-50 border border-amber-200',
    textClass: 'text-amber-700',
  },
  high: {
    text: '高风险',
    bgClass: 'bg-red-50 border border-red-200 animate-bounce',
    textClass: 'text-red-700 font-bold',
  },
  uncertain: {
    text: '不确定',
    bgClass: 'bg-slate-100 border border-slate-200',
    textClass: 'text-slate-700',
  },
};

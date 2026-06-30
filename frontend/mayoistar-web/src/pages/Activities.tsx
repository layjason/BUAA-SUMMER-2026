import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { TagList } from '../components/TagList';
import { Pagination } from '../components/Pagination';
import { EmptyState } from '../components/EmptyState';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { DetailDrawer } from '../components/DetailDrawer';
import { ReviewTimeline } from '../components/ReviewTimeline';
import { ImagePreviewGrid } from '../components/ImagePreviewGrid';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  listActivities,
  getActivity,
  reviewActivity,
  takeDownActivity,
  restoreActivity,
} from '../api/adminActivities';
import {
  ActivitySummary,
  ActivityDetail,
  ActivityReviewStatus,
  ActivityRuntimeStatus,
} from '../types';
import { Search, Eye, AlertTriangle, User, MapPin, Sparkles, HelpCircle } from 'lucide-react';

export const Activities: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // Search & Filter state
  const [keyword, setKeyword] = useState('');
  const [reviewStatus, setReviewStatus] = useState<string>('');
  const [runtimeStatus, setRuntimeStatus] = useState<string>('');

  // Selected Detail Drawer state
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ActivityDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Confirm Reason Modal state
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    type: 'approve' | 'reject' | 'changeRequired' | 'takedown' | 'restore';
    title: string;
    message: string;
    requireReason: boolean;
  }>({
    isOpen: false,
    type: 'approve',
    title: '',
    message: '',
    requireReason: false,
  });

  const [activitiesList, setActivitiesList] = useState<ActivitySummary[]>([]);
  const [totalItems, setTotalItems] = useState(0);

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await listActivities({
        keyword: keyword.trim() || undefined,
        reviewStatus: reviewStatus ? (reviewStatus as ActivityReviewStatus) : undefined,
        runtimeStatus: runtimeStatus ? (runtimeStatus as ActivityRuntimeStatus) : undefined,
        page,
        pageSize,
      });
      setActivitiesList(data.items);
      setTotalItems(data.total);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [page, reviewStatus, runtimeStatus]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    loadData();
  };

  const handleOpenDrawer = async (activityId: string) => {
    setSelectedId(activityId);
    setDetailLoading(true);
    setDetail(null);
    try {
      const data = await getActivity(activityId);
      setDetail(data);
    } catch (e) {
      console.error('Failed to fetch activity detail:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const triggerAction = (
    type: 'approve' | 'reject' | 'changeRequired' | 'takedown' | 'restore',
  ) => {
    if (!detail) return;

    let title = '';
    let message = '';
    let requireReason = false;

    if (type === 'approve') {
      title = '批准活动发布申请';
      message = `您确定批准活动「${detail.title}」发布吗？通过后该活动将正式对趣聚平台所有用户公开。`;
    } else if (type === 'reject') {
      title = '驳回活动发布申请';
      message = `请填写驳回活动「${detail.title}」的原因。该说明将直接推送给发起人反馈。`;
      requireReason = true;
    } else if (type === 'changeRequired') {
      title = '要求活动修改再议';
      message = `请对活动「${detail.title}」提出明确的修改意见（例如：安全方案不全、图片包含低俗涉嫌、地址模糊等）。`;
      requireReason = true;
    } else if (type === 'takedown') {
      title = '强制下架违规活动';
      message = `安全治理警告：您确定将活动「${detail.title}」强制下架吗？下架后该活动将对平台所有人不可见，请务必填写充分的下架依据。`;
      requireReason = true;
    } else if (type === 'restore') {
      title = '恢复活动上架可见';
      message = `您确定恢复活动「${detail.title}」吗？恢复后活动将恢复发布可见性，并对已有报名数据无损保留。`;
    }

    setConfirmConfig({
      isOpen: true,
      type,
      title,
      message,
      requireReason,
    });
  };

  const handleConfirmAction = async (reason?: string) => {
    if (!detail) return;
    const type = confirmConfig.type;

    // Close modal
    setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
    setDetailLoading(true);

    try {
      let updated: ActivityDetail | null = null;
      if (type === 'approve') {
        updated = await reviewActivity(detail.activityId, 'approved');
      } else if (type === 'reject') {
        updated = await reviewActivity(detail.activityId, 'rejected', reason);
      } else if (type === 'changeRequired') {
        updated = await reviewActivity(detail.activityId, 'changeRequired', reason);
      } else if (type === 'takedown') {
        updated = await takeDownActivity(detail.activityId, reason || '');
      } else if (type === 'restore') {
        updated = await restoreActivity(detail.activityId);
      }

      if (updated) {
        setDetail(updated);
        // Sync local list summary state
        setActivitiesList((prev) =>
          prev.map((item) =>
            item.activityId === updated!.activityId
              ? {
                  ...item,
                  reviewStatus: updated!.reviewStatus,
                  runtimeStatus: updated!.runtimeStatus,
                }
              : item,
          ),
        );
      }
    } catch (e) {
      console.error('Failed to submit administrative action:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const formatDateTime = (isoString?: string) => {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="活动合规审核与治理"
        description="趣聚平台线下兴趣活动的生命周期监控中心。可在这里执行人工内容审核、高客流风险合规终审、强制违规下架及争议上诉恢复。"
      />

      {/* Filter and search bar */}
      <form
        onSubmit={handleSearchSubmit}
        className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row gap-4 items-center justify-between"
      >
        <div className="relative w-full md:max-w-md">
          <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
            <Search className="h-4 w-4" />
          </span>
          <input
            type="text"
            placeholder="搜索活动名称、标签或发起人..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto justify-end">
          {/* Review Status select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">审核状态</span>
            <select
              value={reviewStatus}
              onChange={(e) => {
                setReviewStatus(e.target.value);
                setPage(1);
              }}
              className="text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none text-slate-700 font-bold focus:ring-2 focus:ring-blue-500/20 cursor-pointer min-w-[5.5rem]"
            >
              <option value="">全部</option>
              <option value="pending">待审核</option>
              <option value="approved">已通过</option>
              <option value="rejected">已驳回</option>
              <option value="changeRequired">需修改</option>
              <option value="draft">草稿</option>
            </select>
          </div>

          {/* Runtime Status select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">进行状态</span>
            <select
              value={runtimeStatus}
              onChange={(e) => {
                setRuntimeStatus(e.target.value);
                setPage(1);
              }}
              className="text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none text-slate-700 font-bold focus:ring-2 focus:ring-blue-500/20 cursor-pointer min-w-[5.5rem]"
            >
              <option value="">全部</option>
              <option value="registering">报名中</option>
              <option value="ongoing">进行中</option>
              <option value="registrationClosed">已截止</option>
              <option value="ended">已结束</option>
              <option value="takenDown">已下架</option>
            </select>
          </div>

          <button
            type="submit"
            className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold shadow-sm cursor-pointer transition-colors focus:outline-none shrink-0"
          >
            筛选查询
          </button>
        </div>
      </form>

      {/* Main Datatable Grid */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {loading ? (
          <SkeletonBlock rows={5} />
        ) : activitiesList.length === 0 ? (
          <EmptyState
            title="暂无符合条件的活动记录"
            description="没有找到契合当前关键词、审核状态或进行状态的活动申报。请精简搜索条件或清空筛选后重新搜索。"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] border-collapse text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider text-[10px] font-bold">
                  <th className="px-6 py-3.5">活动名称 / 分类</th>
                  <th className="px-6 py-3.5">开始与截止时间</th>
                  <th className="px-6 py-3.5">举办城市及地点</th>
                  <th className="px-6 py-3.5">当前客流量限额</th>
                  <th className="px-6 py-3.5">审核状态</th>
                  <th className="px-6 py-3.5">运行状态</th>
                  <th className="px-6 py-3.5 text-right pr-8">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {activitiesList.map((act) => (
                  <tr
                    key={act.activityId}
                    className="hover:bg-slate-50/50 transition-colors group text-xs text-slate-600 font-medium"
                  >
                    {/* Title */}
                    <td className="px-6 py-4 max-w-xs">
                      <div className="space-y-1.5 text-left">
                        <p className="font-bold text-slate-800 group-hover:text-blue-600 transition-colors line-clamp-2">
                          {act.title}
                        </p>
                        <TagList tags={act.tags} maxCount={2} />
                      </div>
                    </td>

                    {/* Timeline */}
                    <td className="px-6 py-4 font-mono">
                      <div className="space-y-0.5 text-left">
                        <p className="text-slate-700 font-semibold">
                          {formatDateTime(act.startAt)}
                        </p>
                        <p className="text-[10px] text-slate-400">开始举办</p>
                      </div>
                    </td>

                    {/* Location */}
                    <td className="px-6 py-4 max-w-[180px]">
                      <div className="space-y-0.5 text-left truncate">
                        <p className="text-slate-800 font-bold">{act.location.city}</p>
                        <p className="text-[10px] text-slate-400 truncate">
                          {act.location.placeName || act.location.address}
                        </p>
                      </div>
                    </td>

                    {/* Capacity and traffic */}
                    <td className="px-6 py-4 font-mono">
                      <div className="space-y-0.5 text-left">
                        <p className="text-slate-800 font-bold">
                          {act.registeredCount} /{' '}
                          <span className="text-slate-400">{act.capacity}</span>
                        </p>
                        <div className="w-20 bg-slate-100 h-1.5 rounded-full overflow-hidden">
                          <div
                            className="bg-blue-500 h-full rounded-full"
                            style={{
                              width: `${Math.min(100, (act.registeredCount / act.capacity) * 100)}%`,
                            }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Status badges */}
                    <td className="px-6 py-4">
                      <StatusBadge type="activityReview" value={act.reviewStatus} />
                    </td>

                    <td className="px-6 py-4">
                      <StatusBadge type="activityRuntime" value={act.runtimeStatus} />
                    </td>

                    {/* Actions link */}
                    <td className="px-6 py-4 text-right pr-8 shrink-0">
                      <button
                        onClick={() => handleOpenDrawer(act.activityId)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-blue-600 text-slate-600 hover:text-white rounded-xl font-bold cursor-pointer transition-all focus:outline-none whitespace-nowrap"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>合规审计</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Table Pagination */}
        <Pagination
          page={page}
          pageSize={pageSize}
          total={totalItems}
          onChange={(p) => setPage(p)}
        />
      </div>

      {/* Details drawer (Right slip context) */}
      <DetailDrawer
        isOpen={!!selectedId}
        onClose={() => setSelectedId(null)}
        title="线下活动安全合规审计"
        subtitle={detail ? `活动编号 ID: ${detail.activityId}` : '数据检索中...'}
        footer={
          detail && (
            <div className="flex items-center gap-2">
              {/* Drafts or pending approvals */}
              {detail.reviewStatus === 'pending' && (
                <>
                  <button
                    onClick={() => triggerAction('changeRequired')}
                    className="px-4 py-2 bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-700 text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors"
                  >
                    要求修改再议
                  </button>
                  <button
                    onClick={() => triggerAction('reject')}
                    className="px-4 py-2 bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors"
                  >
                    直接驳回发布
                  </button>
                  <button
                    onClick={() => triggerAction('approve')}
                    className="px-4.5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                  >
                    审核批准上架
                  </button>
                </>
              )}

              {/* Already published and not taken down yet */}
              {detail.reviewStatus === 'approved' && detail.runtimeStatus !== 'takenDown' && (
                <button
                  onClick={() => triggerAction('takedown')}
                  className="px-4.5 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  强制违规下架
                </button>
              )}

              {/* Taken down events */}
              {detail.runtimeStatus === 'takenDown' && (
                <button
                  onClick={() => triggerAction('restore')}
                  className="px-4.5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  撤销下架恢复
                </button>
              )}
            </div>
          )
        }
      >
        {detailLoading ? (
          <SkeletonBlock rows={4} />
        ) : !detail ? (
          <div className="text-center py-12 text-slate-400">
            <HelpCircle className="h-10 w-10 mx-auto text-slate-300 stroke-[1.5] mb-2 animate-bounce" />
            <p className="text-xs font-semibold">无法抓取该活动明细</p>
          </div>
        ) : (
          <div className="space-y-6 text-xs text-slate-600 leading-relaxed font-semibold">
            {/* Header info */}
            <div className="space-y-3 pb-5 border-b border-slate-100 text-left">
              {detail.coverImage?.url && (
                <div className="aspect-16/9 rounded-2xl overflow-hidden border border-slate-200/80 mb-3 bg-slate-100 shadow-inner">
                  <img
                    src={detail.coverImage.url}
                    alt="活动封面"
                    referrerPolicy="no-referrer"
                    className="w-full h-full object-cover"
                  />
                </div>
              )}

              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge type="activityReview" value={detail.reviewStatus} />
                <StatusBadge type="activityRuntime" value={detail.runtimeStatus} />
                {detail.manualReviewRequired && (
                  <span className="bg-red-50 border border-red-100 text-red-700 px-2 py-0.5 rounded-full text-[10px] font-bold">
                    大客流审查 (人数&gt;50)
                  </span>
                )}
              </div>

              <h4 className="text-sm font-black text-slate-800 tracking-tight leading-snug">
                {detail.title}
              </h4>
              <TagList tags={detail.tags} />
            </div>

            {/* AI CONTENT SEGMENT */}
            {detail.aiContentReview && (
              <div className="bg-indigo-50/50 border border-indigo-100 p-4.5 rounded-2xl space-y-3.5 text-left">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 text-indigo-900 font-bold text-xs">
                    <Sparkles className="h-4.5 w-4.5 text-indigo-600" />
                    <span>AI 智能合规预审报告</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] text-slate-400 font-bold">内容风险评分:</span>
                    <StatusBadge type="aiRiskLevel" value={detail.aiContentReview.riskLevel} />
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="text-[11px] font-bold text-indigo-950">
                    AI 检测依据与研判意见：
                  </div>
                  <ul className="list-disc pl-4 space-y-1 text-slate-500 font-medium">
                    {detail.aiContentReview.reasons.map((reason, idx) => (
                      <li key={idx}>{reason}</li>
                    ))}
                  </ul>
                </div>
              </div>
            )}

            {/* BASIC METADATA GRID */}
            <div className="grid grid-cols-2 gap-4 bg-slate-50/80 p-4 rounded-2xl border border-slate-100 text-left">
              <div className="space-y-1">
                <p className="text-slate-400 font-bold text-[10px]">活动开始时间</p>
                <p className="text-slate-800 font-bold font-mono">
                  {formatDateTime(detail.startAt)}
                </p>
              </div>
              <div className="space-y-1">
                <p className="text-slate-400 font-bold text-[10px]">报名截止时间</p>
                <p className="text-slate-800 font-bold font-mono">
                  {formatDateTime(detail.registrationDeadline)}
                </p>
              </div>
              <div className="space-y-1">
                <p className="text-slate-400 font-bold text-[10px]">活动费用</p>
                <p className="text-slate-800 font-bold font-mono">
                  {detail.feeAmount ? `￥${detail.feeAmount} / 人` : '免费活动'}
                </p>
              </div>
              <div className="space-y-1">
                <p className="text-slate-400 font-bold text-[10px]">客流量统计</p>
                <p className="text-slate-800 font-bold font-mono">
                  已报名 {detail.registeredCount}人 / 上限 {detail.capacity}人{' '}
                  {detail.waitingCount > 0 && (
                    <span className="text-amber-600 font-semibold">
                      (候补 {detail.waitingCount}人)
                    </span>
                  )}
                </p>
              </div>
            </div>

            {/* GEOGRAPHY LOCATION */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
                <MapPin className="h-3.5 w-3.5 text-blue-500" />
                <span>举办地点与位置参考</span>
              </p>
              <div className="p-3 bg-slate-50 border border-slate-100 rounded-xl space-y-0.5">
                <p className="text-slate-800 font-bold">
                  {detail.location.placeName || '无名场地'}
                </p>
                <p className="text-[10px] text-slate-500 font-semibold">
                  {detail.location.city} - {detail.location.address}
                </p>
                <p className="text-[9px] text-slate-400 font-bold font-mono pt-1">
                  坐标: 经度 {detail.location.point.longitude.toFixed(4)}, 纬度{' '}
                  {detail.location.point.latitude.toFixed(4)}
                </p>
              </div>
            </div>

            {/* ORGANIZER PROFILE */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
                <User className="h-3.5 w-3.5 text-blue-500" />
                <span>发起者与负责人档案</span>
              </p>
              <div className="p-3 bg-slate-50 border border-slate-100 rounded-xl flex items-center justify-between">
                <div className="space-y-0.5">
                  <p className="text-slate-800 font-bold">{detail.organizerName}</p>
                  <p className="text-[10px] text-slate-400 font-semibold font-mono">
                    ID: {detail.organizerId}
                  </p>
                </div>
              </div>
            </div>

            {/* DETAIL DESCRIPTIONS */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider">
                活动简介与主题描述
              </p>
              <div className="p-4 bg-slate-50/50 border border-slate-100 rounded-xl text-slate-700 leading-relaxed font-semibold whitespace-pre-wrap">
                {detail.introduction}
              </div>
            </div>

            {/* SAFETY NOTICE */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
                <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
                <span>户外活动及人身安全须知</span>
              </p>
              <div className="p-4 bg-orange-50/30 border border-orange-100/50 rounded-xl text-slate-700 leading-relaxed font-semibold whitespace-pre-wrap">
                {detail.safetyNotice || '未提供额外的特别安全预警说明。'}
              </div>
            </div>

            {/* EVENT ATTACHED IMAGES */}
            {detail.images && detail.images.length > 0 && (
              <div className="space-y-2 text-left">
                <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider">
                  活动现场环境/物料附图
                </p>
                <ImagePreviewGrid images={detail.images.map((img) => img.url || '')} />
              </div>
            )}

            {/* TIMELINE RECORDS LOGS */}
            <div className="space-y-4 pt-4 border-t border-slate-100 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider">
                审核轨迹与稽查记录档案
              </p>
              <ReviewTimeline records={detail.reviewRecords} />
            </div>
          </div>
        )}
      </DetailDrawer>

      {/* Confirmation Dialog with customizable text area */}
      <ConfirmDialog
        isOpen={confirmConfig.isOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        requireReason={confirmConfig.requireReason}
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
};

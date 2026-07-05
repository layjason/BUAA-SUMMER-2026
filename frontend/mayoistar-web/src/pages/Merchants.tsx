import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { Pagination } from '../components/Pagination';
import { EmptyState } from '../components/EmptyState';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { DetailDrawer } from '../components/DetailDrawer';
import { ImagePreviewGrid } from '../components/ImagePreviewGrid';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { listUsers } from '../api/adminUsers';
import { getMerchant, reviewMerchant } from '../api/adminMerchants';
import { AdminUserSummary, MerchantProfile, QualificationStatus } from '../types';
import { Search, FileText, XCircle, ShieldAlert } from 'lucide-react';

export const Merchants: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [merchants, setMerchants] = useState<AdminUserSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // Active filter tab for quick switching
  const [activeTab, setActiveTab] = useState<QualificationStatus>('pending');
  const [keyword, setKeyword] = useState('');

  // Selected Detail Drawer state
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Confirm dialog state
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    approved: boolean;
    title: string;
    message: string;
    requireReason: boolean;
  }>({
    isOpen: false,
    approved: true,
    title: '',
    message: '',
    requireReason: false,
  });

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await listUsers({
        keyword: keyword.trim() || undefined,
        kind: 'merchant',
        qualificationStatus: activeTab,
        page,
        pageSize,
      });
      setMerchants(res.items);
      setTotal(res.total);
    } catch (e) {
      console.error('Failed to list merchant users:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPage(1);
    fetchList();
  }, [activeTab]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    fetchList();
  };

  const handleOpenDrawer = async (merchantId: string) => {
    setSelectedId(merchantId);
    setDetailLoading(true);
    setProfile(null);
    try {
      const data = await getMerchant(merchantId);
      setProfile(data);
    } catch (e) {
      console.error('Failed to load merchant qualification profile:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const triggerAction = (approved: boolean) => {
    if (!profile) return;

    let title = '';
    let message = '';
    let requireReason = false;

    if (approved) {
      title = '批准通过商家资质申请';
      message = `您确定核验并同意「${profile.merchantName}」的商家资质吗？批准后该用户将获得正式的商家权限，并可在平台内发布商业性质兴趣活动。`;
    } else {
      title = '驳回商家资质申请';
      message = `请对「${profile.merchantName}」的商家资质申请填写具体的驳回原因（例如：营业执照过期、印章不清、执照字迹模糊等），以便对方核对更正。`;
      requireReason = true;
    }

    setConfirmConfig({
      isOpen: true,
      approved,
      title,
      message,
      requireReason,
    });
  };

  const handleConfirmAction = async (reason?: string) => {
    if (!profile) return;
    const isApproved = confirmConfig.approved;

    // Reset confirmation
    setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
    setDetailLoading(true);

    try {
      const updated = await reviewMerchant(profile.userId, isApproved, reason);
      if (updated) {
        setProfile(updated);
        // Refresh local list since the merchant qualification status shifted, they should drop off the active tab list
        setMerchants((prev) => prev.filter((m) => m.userId !== updated.userId));
        setTotal((prev) => Math.max(0, prev - 1));
      }
    } catch (e) {
      console.error('Failed to submit merchant audit decision:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const formatDateTime = (isoString?: string) => {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const tabs: { key: QualificationStatus; label: string }[] = [
    { key: 'pending', label: '待审核 ⏳' },
    { key: 'approved', label: '已通过 🟢' },
    { key: 'rejected', label: '已驳回 🔴' },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="商家入驻资质审核"
        description="趣聚平台商家执照资格终审模块。负责审核商家的营业凭证，审核通过后允许其在平台发布官方兴趣活动与群聊小队。"
      />

      {/* Tabs navigation panel */}
      <div className="flex border-b border-slate-200">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-6 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer focus:outline-none ${
                isActive
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-slate-400 hover:text-slate-600 hover:border-slate-300'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Search form controls */}
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
            placeholder="输入商家名称、昵称、邮箱关键字..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
          />
        </div>

        <button
          type="submit"
          className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold shadow-sm cursor-pointer transition-colors focus:outline-none shrink-0"
        >
          筛选查询
        </button>
      </form>

      {/* Grid listing */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {loading ? (
          <SkeletonBlock rows={5} />
        ) : merchants.length === 0 ? (
          <EmptyState
            title="暂无待审核内容"
            description="当前分类下没有发现待办的商户申请记录。清空筛选或选择其他分类面板进行检索。"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] border-collapse text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider text-[10px] font-bold">
                  <th className="px-6 py-3.5">商户昵称 / 账户邮箱</th>
                  <th className="px-6 py-3.5">经营活动领域</th>
                  <th className="px-6 py-3.5">当前登录权限</th>
                  <th className="px-6 py-3.5">资质审核状态</th>
                  <th className="px-6 py-3.5">创建时间</th>
                  <th className="px-6 py-3.5 text-right pr-8">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {merchants.map((m) => (
                  <tr
                    key={m.userId}
                    className="hover:bg-slate-50/50 transition-colors group text-xs text-slate-600 font-medium"
                  >
                    {/* Bio and contact info */}
                    <td className="px-6 py-4 text-left">
                      <div className="space-y-0.5">
                        <p className="font-bold text-slate-800 group-hover:text-blue-600 transition-colors">
                          {m.nickname || '未命名商户'}
                        </p>
                        <p className="text-[10px] text-slate-400 font-mono font-semibold">
                          {m.email}
                        </p>
                      </div>
                    </td>

                    {/* Interest domains */}
                    <td className="px-6 py-4">
                      <span className="font-semibold text-slate-700">户外运动 / 文化娱乐等</span>
                    </td>

                    {/* Status mapping */}
                    <td className="px-6 py-4">
                      <StatusBadge type="accountStatus" value={m.status} />
                    </td>

                    <td className="px-6 py-4">
                      <StatusBadge
                        type="qualificationStatus"
                        value={m.qualificationStatus || 'not_submitted'}
                      />
                    </td>

                    {/* Date registry */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-500">
                      {formatDateTime(m.createdAt)}
                    </td>

                    {/* Actions audit */}
                    <td className="px-6 py-4 text-right pr-8 shrink-0">
                      <button
                        onClick={() => handleOpenDrawer(m.userId)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-blue-600 text-slate-600 hover:text-white rounded-xl font-bold cursor-pointer transition-all focus:outline-none whitespace-nowrap"
                      >
                        <FileText className="h-3.5 w-3.5" />
                        <span>资质审查</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pagination page={page} pageSize={pageSize} total={total} onChange={(p) => setPage(p)} />
      </div>

      {/* Selected details drawer */}
      <DetailDrawer
        isOpen={!!selectedId}
        onClose={() => setSelectedId(null)}
        title="商家营业资质核验"
        subtitle={profile ? `商户主体 ID: ${profile.userId}` : '凭证抓取中...'}
        footer={
          profile &&
          profile.qualificationStatus === 'pending' && (
            <div className="flex items-center gap-2">
              <button
                onClick={() => triggerAction(false)}
                className="px-4 py-2 bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors"
              >
                资质不合格驳回
              </button>
              <button
                onClick={() => triggerAction(true)}
                className="px-4.5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
              >
                核验合格批准通过
              </button>
            </div>
          )
        }
      >
        {detailLoading ? (
          <SkeletonBlock rows={4} />
        ) : !profile ? (
          <div className="text-center py-12 text-slate-400">
            <ShieldAlert className="h-10 w-10 mx-auto text-slate-300 stroke-[1.5] mb-2" />
            <p className="text-xs font-semibold">无法加载该商家资质详情</p>
          </div>
        ) : (
          <div className="space-y-6 text-xs text-slate-600 leading-relaxed font-semibold">
            {/* Header branding */}
            <div className="space-y-3 pb-5 border-b border-slate-100 text-left">
              {profile.avatar?.signedUrl && (
                <img
                  src={profile.avatar.signedUrl}
                  referrerPolicy="no-referrer"
                  className="h-12 w-12 rounded-2xl object-cover border border-slate-200"
                  alt="商家头像"
                />
              )}
              <h4 className="text-sm font-black text-slate-800 tracking-tight leading-snug">
                {profile.merchantName}
              </h4>
              <p className="text-xs text-slate-400">平台昵称: {profile.nickname}</p>
              <div className="flex items-center gap-2 pt-1">
                <StatusBadge type="qualificationStatus" value={profile.qualificationStatus} />
                <StatusBadge type="accountStatus" value={profile.accountStatus} />
              </div>
            </div>

            {/* INTEREST ACTIVITY DOMAINS */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider">
                经营活动领域
              </p>
              <div className="flex flex-wrap gap-1.5">
                {profile.interestedActivityFields.map((field, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 border border-blue-100 text-[10px] font-bold"
                  >
                    {field}
                  </span>
                ))}
              </div>
            </div>

            {/* SYSTEM AUDIT METADATA */}
            {profile.qualification && (
              <div className="space-y-3">
                <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left">
                  资质申报时间线
                </h5>
                <div className="p-4 bg-slate-50 border border-slate-100 rounded-2xl text-left space-y-2">
                  {profile.qualification.submittedAt && (
                    <div className="flex justify-between items-center text-[10px]">
                      <span className="text-slate-400">申报上传时间:</span>
                      <span className="font-mono text-slate-700 font-bold">
                        {formatDateTime(profile.qualification.submittedAt)}
                      </span>
                    </div>
                  )}
                  {profile.qualification.reviewedAt && (
                    <div className="flex justify-between items-center text-[10px] pt-1.5 border-t border-slate-100/50">
                      <span className="text-slate-400">最后审核时间:</span>
                      <span className="font-mono text-slate-700 font-bold">
                        {formatDateTime(profile.qualification.reviewedAt)}
                      </span>
                    </div>
                  )}
                  {profile.qualification.rejectReason && (
                    <div className="pt-2 border-t border-slate-200 space-y-1">
                      <p className="text-[10px] text-red-500 font-bold flex items-center gap-1">
                        <XCircle className="h-3.5 w-3.5" />
                        <span>历史驳回依据批注:</span>
                      </p>
                      <p className="p-3 bg-red-50/50 border border-red-100 text-red-700 rounded-xl leading-relaxed font-semibold">
                        {profile.qualification.rejectReason}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* BUSINESS LICENSE UPLOADS WITH ZOOM */}
            {profile.qualification?.licenseImageUrls && (
              <div className="space-y-2 text-left">
                <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
                  <FileText className="h-3.5 w-3.5 text-blue-500" />
                  <span>商家营业执照/营业证明凭证</span>
                </p>
                <ImagePreviewGrid images={profile.qualification.licenseImageUrls} />
              </div>
            )}
          </div>
        )}
      </DetailDrawer>

      {/* Confirmation Audit Decision Dialog */}
      <ConfirmDialog
        isOpen={confirmConfig.isOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        requireReason={confirmConfig.requireReason}
        reasonPlaceholder="请填写驳回此资质申请的不合格细节依据，例如：营业执照法定代表人印章模糊不清，或证件有效期截止于2025年..."
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
};

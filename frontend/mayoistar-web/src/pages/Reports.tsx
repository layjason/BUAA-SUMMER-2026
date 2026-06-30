import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { Pagination } from '../components/Pagination';
import { EmptyState } from '../components/EmptyState';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { listUserReports, decideUserReport } from '../api/adminReports';
import { UserReport, ReportStatus } from '../types';
import { Edit3, AlertTriangle, User } from 'lucide-react';

export const Reports: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [reports, setReports] = useState<UserReport[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // Filters state
  const [status, setStatus] = useState<string>('');
  const [reporterUserId, setReporterUserId] = useState('');
  const [targetUserId, setTargetUserId] = useState('');

  // Decision Modal state
  const [activeReport, setActiveReport] = useState<UserReport | null>(null);
  const [decisionNote, setHandlingNote] = useState('');
  const [decisionStatus, setDecisionStatus] = useState<ReportStatus>('resolved');
  const [submitLoading, setSubmitLoading] = useState(false);

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await listUserReports({
        status: status ? (status as ReportStatus) : undefined,
        reporterUserId: reporterUserId.trim() || undefined,
        targetUserId: targetUserId.trim() || undefined,
        page,
        pageSize,
      });
      setReports(res.items);
      setTotal(res.total);
    } catch (e) {
      console.error('Failed to list user reports:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, [page, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    fetchList();
  };

  const handleOpenDecision = (report: UserReport) => {
    setActiveReport(report);
    setDecisionStatus(report.status === 'pending' ? 'resolved' : report.status);
    setHandlingNote(report.handlingNote || '');
  };

  const handleSaveDecision = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeReport) return;

    if (!decisionNote.trim()) {
      alert('请填写具体的处理意见说明，这是必填项。');
      return;
    }

    setSubmitLoading(true);
    try {
      const updated = await decideUserReport(
        activeReport.reportId,
        decisionStatus,
        decisionNote.trim(),
      );

      if (updated) {
        // Sync local list summary state
        setReports((prev) =>
          prev.map((item) => (item.reportId === updated.reportId ? updated : item)),
        );
        setActiveReport(null);
        setHandlingNote('');
      }
    } catch (e) {
      console.error('Failed to update report decision state:', e);
    } finally {
      setSubmitLoading(false);
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

  return (
    <div className="space-y-6">
      <PageHeader
        title="用户及违法内容举报处理"
        description="趣聚平台用户举报信息处理中心。管理员可对用户举报理由进行事实复核、研判处理状态，并给出最终治理决议和处理反馈说明。"
      />

      {/* Filter and search bar */}
      <form
        onSubmit={handleSearchSubmit}
        className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row gap-4 items-center justify-between"
      >
        <div className="flex flex-col sm:flex-row gap-3 w-full md:max-w-2xl">
          <div className="relative flex-1">
            <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
              <User className="h-4 w-4" />
            </span>
            <input
              type="text"
              placeholder="举报人用户 ID..."
              value={reporterUserId}
              onChange={(e) => setReporterUserId(e.target.value)}
              className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
            />
          </div>
          <div className="relative flex-1">
            <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
              <User className="h-4 w-4 text-red-500" />
            </span>
            <input
              type="text"
              placeholder="被举报人用户 ID..."
              value={targetUserId}
              onChange={(e) => setTargetUserId(e.target.value)}
              className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto justify-end">
          {/* Report Status select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">处理状态</span>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
              className="text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none text-slate-700 font-bold focus:ring-2 focus:ring-blue-500/20 cursor-pointer min-w-[5.5rem]"
            >
              <option value="">全部</option>
              <option value="pending">待处理</option>
              <option value="processing">处理中</option>
              <option value="resolved">已解决</option>
              <option value="rejected">已驳回</option>
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

      {/* Main Table Grid */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {loading ? (
          <SkeletonBlock rows={5} />
        ) : reports.length === 0 ? (
          <EmptyState
            title="暂无待处理内容"
            description="未找到符合特定属性或用户标识的纠纷举报记录。这代表平台当前无挂起的争议诉求。"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] border-collapse text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider text-[10px] font-bold">
                  <th className="px-6 py-3.5 w-[140px]">举报编号 ID</th>
                  <th className="px-6 py-3.5 w-[130px]">举报发起人</th>
                  <th className="px-6 py-3.5 w-[130px]">被举报目标</th>
                  <th className="px-6 py-3.5">举报原因描述</th>
                  <th className="px-6 py-3.5 w-[110px]">处理状态</th>
                  <th className="px-6 py-3.5 w-[120px]">创建时间</th>
                  <th className="px-6 py-3.5 text-right pr-8 w-[120px]">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {reports.map((rpt) => (
                  <tr
                    key={rpt.reportId}
                    className="hover:bg-slate-50/50 transition-colors group text-xs text-slate-600 font-medium"
                  >
                    {/* Report ID */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-400">{rpt.reportId}</td>

                    {/* Reporter ID */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-800">
                      {rpt.reporterUserId}
                    </td>

                    {/* Target ID */}
                    <td className="px-6 py-4 font-mono font-bold text-red-600">
                      {rpt.targetUserId}
                    </td>

                    {/* Reason text */}
                    <td className="px-6 py-4 max-w-sm">
                      <div className="space-y-1.5 text-left">
                        <p className="text-slate-700 font-semibold line-clamp-2 leading-relaxed">
                          {rpt.reason}
                        </p>
                        {rpt.handlingNote && (
                          <div className="text-[10px] bg-slate-50 border border-slate-100 rounded-xl p-2 text-slate-500">
                            <span className="font-bold text-slate-600">批注:</span>{' '}
                            {rpt.handlingNote}
                          </div>
                        )}
                      </div>
                    </td>

                    {/* Status */}
                    <td className="px-6 py-4">
                      <StatusBadge type="reportStatus" value={rpt.status} />
                    </td>

                    {/* CreatedAt */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-400">
                      {formatDateTime(rpt.createdAt)}
                    </td>

                    {/* Actions audit */}
                    <td className="px-6 py-4 text-right pr-8 shrink-0">
                      <button
                        onClick={() => handleOpenDecision(rpt)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-blue-600 text-slate-600 hover:text-white rounded-xl font-bold cursor-pointer transition-all focus:outline-none whitespace-nowrap"
                      >
                        <Edit3 className="h-3.5 w-3.5" />
                        <span>介入裁决</span>
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

      {/* Decision Modal overlay */}
      {activeReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in">
          <div className="bg-white w-full max-w-md rounded-2xl border border-slate-100 shadow-2xl overflow-hidden animate-scale-up">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-red-500 shrink-0" />
                <h3 className="text-base font-bold text-slate-800">举报稽查处理裁决</h3>
              </div>
              <button
                onClick={() => setActiveReport(null)}
                className="text-slate-400 hover:text-slate-600 focus:outline-none cursor-pointer"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleSaveDecision}>
              {/* Content body */}
              <div className="px-6 py-4 space-y-4 text-xs font-semibold text-slate-600 leading-relaxed text-left">
                {/* ID review card */}
                <div className="p-3 bg-slate-50 border border-slate-100 rounded-xl space-y-1">
                  <div className="flex justify-between">
                    <span className="text-slate-400">举报单 ID:</span>
                    <span className="font-mono font-bold text-slate-700">
                      {activeReport.reportId}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">被举报账户 ID:</span>
                    <span className="font-mono font-bold text-red-600">
                      {activeReport.targetUserId}
                    </span>
                  </div>
                </div>

                {/* Complaint text */}
                <div className="space-y-1">
                  <span className="text-slate-400 block">用户申诉原由说明：</span>
                  <div className="p-3 bg-red-50/30 border border-red-100 text-slate-700 rounded-xl whitespace-pre-wrap leading-relaxed max-h-[120px] overflow-y-auto">
                    {activeReport.reason}
                  </div>
                </div>

                {/* Status selector */}
                <div className="space-y-1.5">
                  <label className="text-slate-400 font-bold block">设定制裁处理决策：</label>
                  <select
                    value={decisionStatus}
                    onChange={(e) => setDecisionStatus(e.target.value as ReportStatus)}
                    className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-700 font-bold cursor-pointer"
                  >
                    <option value="processing">研判处理中 ⏳</option>
                    <option value="resolved">已核实解决 🟢</option>
                    <option value="rejected">驳回不受理 🔴</option>
                  </select>
                </div>

                {/* Notes textarea */}
                <div className="space-y-1.5">
                  <label className="text-slate-400 font-bold block">
                    平台处理决议书/文字批注：
                  </label>
                  <textarea
                    required
                    rows={4}
                    placeholder="请输入对被举报用户的处理结论（例如：已对相关非法兼职引流活动执行下架，并封锁其发布账号30天，反馈举报人处理结果）..."
                    value={decisionNote}
                    onChange={(e) => setHandlingNote(e.target.value)}
                    className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-800 font-medium resize-none"
                  />
                </div>
              </div>

              {/* Actions footer */}
              <div className="bg-slate-50 px-6 py-4 flex items-center justify-end gap-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setActiveReport(null)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:text-slate-800 focus:outline-none cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={submitLoading}
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl shadow-xs transition-colors focus:outline-none cursor-pointer"
                >
                  {submitLoading ? '提交中...' : '提交处理决议'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { listActivities } from '../api/adminActivities';
import { listUsers } from '../api/adminUsers';
import { listReports } from '../api/adminReports';
import { listTeams } from '../api/adminTeams';
import { ActivitySummary, Report } from '../types';
import {
  ShieldAlert,
  Store,
  AlertTriangle,
  FolderMinus,
  UserX,
  Users,
  ArrowRight,
  TrendingUp,
  Sparkles,
  ClipboardCheck,
} from 'lucide-react';

interface WorkbenchProps {
  onNavigate: (route: string) => void;
}

export const Workbench: React.FC<WorkbenchProps> = ({ onNavigate }) => {
  const [loading, setLoading] = useState(true);

  // Dashboard calculated statistics
  const [stats, setStats] = useState({
    pendingActivities: 0,
    pendingMerchants: 0,
    pendingReports: 0,
    takenDownActivities: 0,
    bannedUsers: 0,
    disabledTeams: 0,
  });

  // Hot lists for quick preview
  const [hotActivities, setHotActivities] = useState<ActivitySummary[]>([]);
  const [hotReports, setHotReports] = useState<Report[]>([]);

  const fetchStats = async () => {
    setLoading(true);
    try {
      // 1. Fire parallel list counts (fetching with limit=1 to read overall "total" directly)
      const [actPending, merchPending, rptPending, actTakedown, usrBanned, teamDisabled] =
        await Promise.all([
          listActivities({ reviewStatus: 'pending', pageSize: 1 }),
          listUsers({ kind: 'merchant', qualificationStatus: 'pending', pageSize: 1 }),
          listReports({ status: 'pending', pageSize: 1 }),
          listActivities({ runtimeStatus: 'takenDown', pageSize: 1 }),
          listUsers({ status: 'banned', pageSize: 1 }),
          listTeams({ status: 'disabled', pageSize: 1 }),
        ]);

      setStats({
        pendingActivities: actPending.total,
        pendingMerchants: merchPending.total,
        pendingReports: rptPending.total,
        takenDownActivities: actTakedown.total,
        bannedUsers: usrBanned.total,
        disabledTeams: teamDisabled.total,
      });

      // 2. Fetch active hot items for quick action
      const actQueue = await listActivities({ reviewStatus: 'pending', pageSize: 3 });
      const rptQueue = await listReports({ status: 'pending', pageSize: 3 });

      setHotActivities(actQueue.items);
      setHotReports(rptQueue.items);
    } catch (error) {
      console.error('Failed to pull dashboard statistics:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <PageHeader
        title="工作台"
        description="趣聚平台当前运营状况总监视。各核心卡片状态数基于后台系统实时列表统计计算得出。"
        actions={
          <button
            onClick={fetchStats}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200/80 rounded-xl shadow-xs transition-all cursor-pointer focus:outline-none"
          >
            <TrendingUp className="h-4 w-4 text-blue-500" />
            <span>重新统计数据</span>
          </button>
        }
      />

      {loading ? (
        <SkeletonBlock rows={5} />
      ) : (
        <div className="space-y-6">
          {/* Section: Pending Actions / 待处理事项 */}
          <div className="space-y-3">
            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
              <span>●</span> 核心待审核及治理队列
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              <StatCard
                title="待审核活动"
                value={stats.pendingActivities}
                color="orange"
                description="用户新创建、模板生成或克隆的活动草案，需经人内容安全复核及风险终审。"
                icon={<ShieldAlert className="h-5 w-5" />}
                onAction={() => onNavigate('activities')}
              />
              <StatCard
                title="待审核商家"
                value={stats.pendingMerchants}
                color="blue"
                description="商户自主提交的营业执照彩色影印件和主体证明。审核通过后方可开放商户功能。"
                icon={<Store className="h-5 w-5" />}
                onAction={() => onNavigate('merchants')}
              />
              <StatCard
                title="待处理举报"
                value={stats.pendingReports}
                color="red"
                description="涉及低俗内容、虚假欺诈、不合规线下集会的用户实名举报。需快速介入审查。"
                icon={<AlertTriangle className="h-5 w-5" />}
                onAction={() => onNavigate('reports')}
              />
            </div>
          </div>

          {/* Section: Archived Governance Metrics / 已下架已停用指标 */}
          <div className="space-y-3 pt-2">
            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
              <span>●</span> 已处置治理指标快照
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
              <div className="bg-slate-50 rounded-2xl border border-slate-200/60 p-5 text-left flex items-center gap-4">
                <div className="h-10 w-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
                  <FolderMinus className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                    已下架违规活动
                  </p>
                  <p className="text-xl font-extrabold text-slate-700 mt-0.5">
                    {stats.takenDownActivities}
                  </p>
                </div>
              </div>

              <div className="bg-slate-50 rounded-2xl border border-slate-200/60 p-5 text-left flex items-center gap-4">
                <div className="h-10 w-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
                  <UserX className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                    已限制封禁用户
                  </p>
                  <p className="text-xl font-extrabold text-slate-700 mt-0.5">
                    {stats.bannedUsers}
                  </p>
                </div>
              </div>

              <div className="bg-slate-50 rounded-2xl border border-slate-200/60 p-5 text-left flex items-center gap-4">
                <div className="h-10 w-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
                  <Users className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                    已停用违规小队
                  </p>
                  <p className="text-xl font-extrabold text-slate-700 mt-0.5">
                    {stats.disabledTeams}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Section: Active Hot Task Queues / 待办详情列表 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 pt-4">
            {/* Hot Activities Queue */}
            <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-4 shadow-xs text-left">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-5 w-5 text-amber-500" />
                  <h4 className="text-sm font-bold text-slate-800">最新活动审核申请</h4>
                </div>
                <button
                  onClick={() => onNavigate('activities')}
                  className="text-xs font-bold text-blue-600 hover:text-blue-800 flex items-center gap-1 focus:outline-none cursor-pointer"
                >
                  <span>全部审核页</span>
                  <ArrowRight className="h-3.5 w-3.5" />
                </button>
              </div>

              {hotActivities.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400 font-medium">
                  暂无待处理的活动发布申请
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {hotActivities.map((act) => (
                    <div
                      key={act.activityId}
                      className="py-3.5 first:pt-0 last:pb-0 flex items-center justify-between gap-4"
                    >
                      <div className="space-y-1 min-w-0 flex-1">
                        <p className="text-xs font-bold text-slate-800 truncate">{act.title}</p>
                        <p className="text-[10px] text-slate-400 font-semibold flex items-center gap-2">
                          <span>城市: {act.location.city}</span>
                          <span>•</span>
                          <span>限额: {act.capacity}人</span>
                        </p>
                      </div>
                      <button
                        onClick={() => onNavigate('activities')}
                        className="text-xs bg-slate-50 hover:bg-blue-50 border border-slate-200/60 hover:border-blue-200 text-slate-600 hover:text-blue-600 px-3 py-1.5 rounded-xl font-bold cursor-pointer transition-all shrink-0 focus:outline-none"
                      >
                        立即初审
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Hot Reports Queue */}
            <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-4 shadow-xs text-left">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <ClipboardCheck className="h-5 w-5 text-red-500" />
                  <h4 className="text-sm font-bold text-slate-800">未处理实名举报记录</h4>
                </div>
                <button
                  onClick={() => onNavigate('reports')}
                  className="text-xs font-bold text-blue-600 hover:text-blue-800 flex items-center gap-1 focus:outline-none cursor-pointer"
                >
                  <span>全部举报页</span>
                  <ArrowRight className="h-3.5 w-3.5" />
                </button>
              </div>

              {hotReports.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400 font-medium">
                  暂无未归档或处于挂起态的举报
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {hotReports.map((rpt) => (
                    <div
                      key={rpt.reportId}
                      className="py-3.5 first:pt-0 last:pb-0 flex items-center justify-between gap-4"
                    >
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] bg-red-50 border border-red-100 text-red-700 px-1.5 py-0.5 rounded font-bold">
                            待介入
                          </span>
                          <span className="text-[10px] text-slate-400 font-bold font-mono">
                            ID: {rpt.reportId}
                          </span>
                        </div>
                        <p className="text-xs text-slate-600 truncate mt-1 font-medium leading-relaxed">
                          {rpt.reason}
                        </p>
                      </div>
                      <button
                        onClick={() => onNavigate('reports')}
                        className="text-xs bg-slate-50 hover:bg-red-50 border border-slate-200/60 hover:border-red-200 text-slate-600 hover:text-red-600 px-3 py-1.5 rounded-xl font-bold cursor-pointer transition-all shrink-0 focus:outline-none"
                      >
                        介入研判
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { TagList } from '../components/TagList';
import { Pagination } from '../components/Pagination';
import { EmptyState } from '../components/EmptyState';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { DetailDrawer } from '../components/DetailDrawer';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  listTeams,
  disableTeam,
  restoreTeam,
  getTeam,
  listTeamMembers,
  listTeamActivities,
  listTeamReports,
} from '../api/adminTeams';
import {
  TeamProfile,
  AdminTeamDetail,
  TeamMember,
  ActivitySummary,
  Report,
  TeamStatus,
} from '../types';
import { AuthImage } from '../components/AuthImage';
import {
  Search,
  Eye,
  AlertTriangle,
  Users2,
  KeyRound,
  MessageSquareCode,
  Activity,
  Flag,
  Shield,
} from 'lucide-react';

const formatDateTime = (isoString?: string) => {
  if (!isoString) return '-';
  try {
    const date = new Date(isoString);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  } catch {
    return isoString;
  }
};

export const Teams: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<TeamProfile[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // Filters state
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>('');

  // Selected Detail Drawer state
  const [selectedTeam, setSelectedTeam] = useState<TeamProfile | null>(null);
  const [teamDetail, setTeamDetail] = useState<AdminTeamDetail | null>(null);
  const [teamMembers, setTeamMembers] = useState<TeamMember[]>([]);
  const [teamActivities, setTeamActivities] = useState<ActivitySummary[]>([]);
  const [teamReports, setTeamReports] = useState<Report[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  // Confirm dialogue controls
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    type: 'disable' | 'restore';
    title: string;
    message: string;
    requireReason: boolean;
  }>({
    isOpen: false,
    type: 'disable',
    title: '',
    message: '',
    requireReason: false,
  });

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await listTeams({
        keyword: keyword.trim() || undefined,
        status: status ? (status as TeamStatus) : undefined,
        page,
        pageSize,
      });
      setTeams(res.items);
      setTotal(res.total);
    } catch (e) {
      console.error('Failed to query teams lists:', e);
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

  const handleOpenDrawer = async (team: TeamProfile) => {
    setSelectedTeam(team);
    setDetailLoading(true);
    setTeamDetail(null);
    setTeamMembers([]);
    setTeamActivities([]);
    setTeamReports([]);

    try {
      const [detail, membersRes, activitiesRes, reportsRes] = await Promise.all([
        getTeam(team.teamId),
        listTeamMembers(team.teamId, 1, 100),
        listTeamActivities(team.teamId, 1, 50),
        listTeamReports(team.teamId, 1, 50),
      ]);
      setTeamDetail(detail);
      setTeamMembers(membersRes.items);
      setTeamActivities(activitiesRes.items);
      setTeamReports(reportsRes.items);
    } catch (e) {
      console.error('Failed to fetch team detail:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const triggerAction = (type: 'disable' | 'restore') => {
    if (!selectedTeam) return;

    let title = '';
    let message = '';
    let requireReason = false;

    if (type === 'disable') {
      title = '停用处罚违规小队';
      message = `安全治理警告：您确定要强制停用小队「${selectedTeam.name}」吗？停用后该社群将禁止发布活动、新增成员，历史对话和小队资源将对非已有成员封锁。请阐明停用理由。`;
      requireReason = true;
    } else {
      title = '撤销停用恢复小队';
      message = `您确定解除小队「${selectedTeam.name}」的停用限制，并恢复其完整社交功能吗？`;
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
    if (!selectedTeam) return;
    const type = confirmConfig.type;

    // Reset confirmation
    setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
    setLoading(true);

    try {
      let updated: TeamProfile | null = null;
      if (type === 'disable') {
        updated = await disableTeam(selectedTeam.teamId, reason || '');
      } else {
        updated = await restoreTeam(selectedTeam.teamId);
      }

      if (updated) {
        setSelectedTeam(updated);
        // Sync local list summary state
        setTeams((prev) => prev.map((item) => (item.teamId === updated!.teamId ? updated! : item)));
      }
    } catch (e) {
      console.error('Failed to submit team moderation action:', e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="兴趣小队合规治理"
        description="趣聚平台以兴趣为纽带建立的小队社群治理中心。可在这里监视小队状态，对群发不良言论或含有擦边诱导等违规问题的社群执行强制停用，或恢复其使用。"
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
            placeholder="搜小队名称、特长兴趣标签..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto justify-end">
          {/* Team Status select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">社群状态</span>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
              className="text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none text-slate-700 font-bold focus:ring-2 focus:ring-blue-500/20 cursor-pointer min-w-[5.5rem]"
            >
              <option value="">全部</option>
              <option value="active">正常</option>
              <option value="disabled">已停用</option>
              <option value="dissolved">已解散</option>
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

      {/* Grid Table Container */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {loading ? (
          <SkeletonBlock rows={5} />
        ) : teams.length === 0 ? (
          <EmptyState
            title="没有符合条件的记录"
            description="没有发现契合当前搜索关键字或状态属性的兴趣小队数据。"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] border-collapse text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider text-[10px] font-bold">
                  <th className="px-6 py-3.5">小队群聊名称 / 标签</th>
                  <th className="px-6 py-3.5">加入方式</th>
                  <th className="px-6 py-3.5">当前成员占有率</th>
                  <th className="px-6 py-3.5">小队运营状态</th>
                  <th className="px-6 py-3.5">队长用户 ID</th>
                  <th className="px-6 py-3.5 text-right pr-8">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {teams.map((t) => (
                  <tr
                    key={t.teamId}
                    className="hover:bg-slate-50/50 transition-colors group text-xs text-slate-600 font-medium"
                  >
                    {/* Name and tags */}
                    <td className="px-6 py-4 max-w-xs text-left">
                      <div className="space-y-1.5">
                        <p className="font-bold text-slate-800 group-hover:text-blue-600 transition-colors">
                          {t.name}
                        </p>
                        <TagList tags={t.tags} />
                      </div>
                    </td>

                    {/* JoinMode */}
                    <td className="px-6 py-4">
                      <StatusBadge type="teamJoinMode" value={t.joinMode} />
                    </td>

                    {/* Members size ratio */}
                    <td className="px-6 py-4 font-mono font-semibold">
                      <div className="space-y-0.5 text-left">
                        <p className="text-slate-800">
                          {t.memberCount} / <span className="text-slate-400">{t.capacity}</span>
                        </p>
                        <div className="w-20 bg-slate-100 h-1.5 rounded-full overflow-hidden">
                          <div
                            className="bg-indigo-500 h-full rounded-full"
                            style={{
                              width: `${Math.min(100, (t.memberCount / t.capacity) * 100)}%`,
                            }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Status */}
                    <td className="px-6 py-4">
                      <StatusBadge type="teamStatus" value={t.status} />
                    </td>

                    {/* LeaderId */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-500">{t.leaderId}</td>

                    {/* Actions audit */}
                    <td className="px-6 py-4 text-right pr-8 shrink-0">
                      <button
                        onClick={() => handleOpenDrawer(t)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-blue-600 text-slate-600 hover:text-white rounded-xl font-bold cursor-pointer transition-all focus:outline-none whitespace-nowrap"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>社群档案</span>
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
        isOpen={!!selectedTeam}
        onClose={() => setSelectedTeam(null)}
        title="小队社群合规档案"
        subtitle={selectedTeam ? `小队 ID: ${selectedTeam.teamId}` : ''}
        footer={
          selectedTeam && (
            <div className="flex items-center gap-2">
              {selectedTeam.status === 'disabled' ? (
                <button
                  onClick={() => triggerAction('restore')}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  撤销停用并恢复
                </button>
              ) : selectedTeam.status === 'active' ? (
                <button
                  onClick={() => triggerAction('disable')}
                  className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  强制停用整改
                </button>
              ) : null}
            </div>
          )
        }
      >
        {selectedTeam && (
          <div className="space-y-6 text-xs text-slate-600 leading-relaxed font-semibold">
            {/* Header branding */}
            <div className="flex items-start gap-4 p-5 bg-slate-50 border border-slate-100 rounded-2xl text-left">
              {selectedTeam.avatar?.signedUrl ? (
                <AuthImage
                  signedUrl={selectedTeam.avatar.signedUrl}
                  className="h-12 w-12 rounded-2xl object-cover border border-slate-200 shrink-0"
                  alt="群头像"
                />
              ) : (
                <div className="h-12 w-12 rounded-2xl bg-indigo-100 flex items-center justify-center font-bold text-indigo-700 text-base shrink-0 border border-indigo-200/50">
                  <Users2 className="h-5 w-5" />
                </div>
              )}
              <div className="space-y-1 text-left min-w-0 flex-1">
                <h4 className="text-sm font-black text-slate-800 truncate">{selectedTeam.name}</h4>
                <TagList tags={selectedTeam.tags} />
              </div>
            </div>

            {/* Core credentials status segment */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left">
                小队架构及群聊设置
              </h5>
              <div className="grid grid-cols-2 gap-4 bg-slate-50/50 border border-slate-100 p-4 rounded-2xl text-left">
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">群聊天会话 ID</p>
                  <p className="text-slate-800 font-bold font-mono flex items-center gap-1">
                    <MessageSquareCode className="h-3.5 w-3.5 text-slate-400" />
                    <span>{selectedTeam.chatId}</span>
                  </p>
                </div>
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">小队加入审核机制</p>
                  <div>
                    <StatusBadge type="teamJoinMode" value={selectedTeam.joinMode} />
                  </div>
                </div>
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">群主 / 队长责任人</p>
                  <p className="text-slate-800 font-bold font-mono flex items-center gap-1">
                    <KeyRound className="h-3.5 w-3.5 text-slate-400" />
                    <span>{selectedTeam.leaderId}</span>
                  </p>
                </div>
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">小队社群状态</p>
                  <div>
                    <StatusBadge type="teamStatus" value={selectedTeam.status} />
                  </div>
                </div>
              </div>
            </div>

            {/* TEAM INTRO DESCRIPTIONS */}
            <div className="space-y-2 text-left">
              <p className="text-slate-400 font-bold text-[10px] uppercase tracking-wider">
                小队及群公告简介
              </p>
              <div className="p-4 bg-slate-50/50 border border-slate-100 rounded-xl text-slate-700 leading-relaxed font-semibold whitespace-pre-wrap">
                {selectedTeam.description || '该小队队长很懒，暂未公布任何详细的群介绍。'}
              </div>
            </div>

            {/* Moderation Records */}
            {teamDetail && teamDetail.moderationRecords.length > 0 && (
              <div className="space-y-3">
                <h5 className="text-[10px] font-bold text-red-500 uppercase tracking-wider text-left flex items-center gap-1.5">
                  <Shield className="h-3.5 w-3.5" />
                  <span>治理记录 ({teamDetail.moderationRecords.length})</span>
                </h5>
                <div className="space-y-2 max-h-[150px] overflow-y-auto">
                  {teamDetail.moderationRecords.map((rec) => (
                    <div
                      key={rec.recordId}
                      className="p-3 bg-red-50 border border-red-100 rounded-xl text-left"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold text-red-700">{rec.action}</span>
                        <span className="text-[10px] font-mono text-red-400">
                          {formatDateTime(rec.createdAt)}
                        </span>
                      </div>
                      <p className="text-[10px] text-red-600 mt-1 font-medium">{rec.reason}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Team Members List */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
                <Users2 className="h-3.5 w-3.5" />
                <span>成员列表 ({teamMembers.length})</span>
              </h5>
              {detailLoading ? (
                <SkeletonBlock rows={2} />
              ) : teamMembers.length === 0 ? (
                <div className="p-4 bg-slate-50 border border-dashed border-slate-200 rounded-xl text-center text-[10px] text-slate-400 font-semibold">
                  该小队暂无成员数据。
                </div>
              ) : (
                <div className="space-y-2 max-h-[180px] overflow-y-auto">
                  {teamMembers.map((m) => (
                    <div
                      key={m.userId}
                      className="p-3 bg-slate-50 border border-slate-100 rounded-xl flex items-center justify-between"
                    >
                      <div className="text-left min-w-0 flex-1">
                        <p className="text-slate-800 font-bold text-xs truncate">{m.nickname}</p>
                        <p className="text-[10px] text-slate-400 font-mono">{m.userId}</p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span
                          className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                            m.role === 'leader'
                              ? 'bg-amber-50 text-amber-700'
                              : m.role === 'admin'
                                ? 'bg-indigo-50 text-indigo-700'
                                : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {m.role === 'leader' ? '队长' : m.role === 'admin' ? '管理' : '成员'}
                        </span>
                        <span className="text-[10px] text-slate-400 font-mono">
                          积分: {m.points}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Team Activities List */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
                <Activity className="h-3.5 w-3.5" />
                <span>小队活动 ({teamActivities.length})</span>
              </h5>
              {detailLoading ? (
                <SkeletonBlock rows={2} />
              ) : teamActivities.length === 0 ? (
                <div className="p-4 bg-slate-50 border border-dashed border-slate-200 rounded-xl text-center text-[10px] text-slate-400 font-semibold">
                  该小队暂无关联活动。
                </div>
              ) : (
                <div className="space-y-2 max-h-[180px] overflow-y-auto">
                  {teamActivities.map((act) => (
                    <div
                      key={act.activityId}
                      className="p-3 bg-slate-50 border border-slate-100 rounded-xl text-left"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-slate-800 font-bold text-xs line-clamp-1 flex-1">
                          {act.title}
                        </p>
                        <div className="flex gap-1 shrink-0">
                          <StatusBadge type="activityReview" value={act.reviewStatus} />
                          <StatusBadge type="activityRuntime" value={act.runtimeStatus} />
                        </div>
                      </div>
                      <div className="flex items-center gap-3 mt-1.5 text-[10px] text-slate-400 font-mono">
                        <span>
                          参与: {act.registeredCount}/{act.capacity}
                        </span>
                        <span>{formatDateTime(act.startAt)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Team Reports List */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
                <Flag className="h-3.5 w-3.5" />
                <span>相关举报 ({teamReports.length})</span>
              </h5>
              {detailLoading ? (
                <SkeletonBlock rows={2} />
              ) : teamReports.length === 0 ? (
                <div className="p-4 bg-slate-50 border border-dashed border-slate-200 rounded-xl text-center text-[10px] text-slate-400 font-semibold">
                  该小队暂无举报记录。
                </div>
              ) : (
                <div className="space-y-2 max-h-[180px] overflow-y-auto">
                  {teamReports.map((rpt) => (
                    <div
                      key={rpt.reportId}
                      className="p-3 bg-slate-50 border border-slate-100 rounded-xl text-left"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-mono text-[10px] text-slate-400">{rpt.reportId}</span>
                        <StatusBadge type="reportStatus" value={rpt.status} />
                      </div>
                      <p className="text-xs text-slate-700 font-medium mt-1 line-clamp-2">
                        {rpt.reason}
                      </p>
                      <p className="text-[10px] text-slate-400 font-mono mt-1">
                        举报人: {rpt.reporterUserId} | {formatDateTime(rpt.createdAt)}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Hard rule warning about profiles/passwords editing */}
            <div className="p-3.5 bg-amber-50 border border-amber-100 text-amber-800 rounded-xl text-left flex gap-3">
              <AlertTriangle className="h-5 w-5 text-amber-500 shrink-0" />
              <div className="space-y-1 text-slate-600">
                <p className="text-[10px] text-amber-800 font-bold">后台管理权责约束警告：</p>
                <p className="text-[10px] font-medium leading-relaxed">
                  管理员账号受到合规管理制度约束：
                  <strong>
                    严禁插手机制直接修改小队的内部配置名称、公告详情、小队的私有兴趣标签，或介入转移/变更队长及管理员身份
                  </strong>
                  。运营干预手段仅限于执行整顿停用和合规上架操作。
                </p>
              </div>
            </div>
          </div>
        )}
      </DetailDrawer>

      {/* Confirmation disabled dialogue */}
      <ConfirmDialog
        isOpen={confirmConfig.isOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        requireReason={confirmConfig.requireReason}
        reasonPlaceholder="请填写停用该小队社群的具体违规违约原由（例如：该小队在群聊中发表政治、色情、诈骗宣传广告，且队长长期未对群内违规分子进行清理）..."
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
};

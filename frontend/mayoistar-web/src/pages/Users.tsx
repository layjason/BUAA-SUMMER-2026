import React, { useState, useEffect } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { Pagination } from '../components/Pagination';
import { EmptyState } from '../components/EmptyState';
import { SkeletonBlock } from '../components/SkeletonBlock';
import { DetailDrawer } from '../components/DetailDrawer';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  listUsers,
  banUser,
  unbanUser,
  getUser,
  listUserActivities,
  listUserTeams,
} from '../api/adminUsers';
import {
  AdminUserSummary,
  AdminUserDetail,
  ActivitySummary,
  TeamProfile,
  UserKind,
  AccountStatus,
  QualificationStatus,
} from '../types';
import { Search, Eye, AlertTriangle, Mail, Calendar, Activity, Users2 } from 'lucide-react';

export const Users: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);

  // Filters state
  const [keyword, setKeyword] = useState('');
  const [kind, setKind] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [qualificationStatus] = useState<string>('');

  // Selected details state
  const [selectedUser, setSelectedUser] = useState<AdminUserSummary | null>(null);
  const [userDetail, setUserDetail] = useState<AdminUserDetail | null>(null);
  const [userActivities, setUserActivities] = useState<ActivitySummary[]>([]);
  const [userTeams, setUserTeams] = useState<TeamProfile[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  // Confirm dialog state
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    type: 'ban' | 'unban';
    title: string;
    message: string;
    requireReason: boolean;
    showBanUntil: boolean;
  }>({
    isOpen: false,
    type: 'ban',
    title: '',
    message: '',
    requireReason: false,
    showBanUntil: false,
  });

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await listUsers({
        keyword: keyword.trim() || undefined,
        kind: kind ? (kind as UserKind) : undefined,
        status: status ? (status as AccountStatus) : undefined,
        qualificationStatus: qualificationStatus
          ? (qualificationStatus as QualificationStatus)
          : undefined,
        page,
        pageSize,
      });
      setUsers(res.items);
      setTotal(res.total);
    } catch (e) {
      console.error('Failed to list users:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, [page, kind, status, qualificationStatus]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    fetchList();
  };

  const handleOpenDrawer = async (user: AdminUserSummary) => {
    setSelectedUser(user);
    setDetailLoading(true);
    setUserDetail(null);
    setUserActivities([]);
    setUserTeams([]);

    try {
      const [detail, activitiesRes, teamsRes] = await Promise.all([
        getUser(user.userId),
        listUserActivities(user.userId, 1, 50),
        listUserTeams(user.userId, 1, 50),
      ]);
      setUserDetail(detail);
      setUserActivities(activitiesRes.items);
      setUserTeams(teamsRes.items);
    } catch (e) {
      console.error('Failed to fetch user detail:', e);
    } finally {
      setDetailLoading(false);
    }
  };

  const triggerAction = (type: 'ban' | 'unban') => {
    if (!selectedUser) return;

    let title = '';
    let message = '';
    let requireReason = false;
    let showBanUntil = false;

    if (type === 'ban') {
      title = '限制封禁用户账号';
      message = `安全治理提示：您确定要对账号「${selectedUser.nickname || selectedUser.email}」执行封禁吗？封禁期内该用户将无法登录趣聚平台。请选择封禁截止截止期并阐明封禁理由。`;
      requireReason = true;
      showBanUntil = true;
    } else if (type === 'unban') {
      title = '解除限制启用账号';
      message = `您确定解除账号「${selectedUser.nickname || selectedUser.email}」的封禁限制吗？解封后用户将立即恢复正常登录及完整业务操作权限。`;
    }

    setConfirmConfig({
      isOpen: true,
      type,
      title,
      message,
      requireReason,
      showBanUntil,
    });
  };

  const handleConfirmAction = async (reason?: string, bannedUntil?: string) => {
    if (!selectedUser) return;
    const type = confirmConfig.type;

    // Reset confirm state
    setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
    setLoading(true);

    try {
      let updated: AdminUserSummary | null = null;
      if (type === 'ban') {
        updated = await banUser(selectedUser.userId, reason || '违规被封禁', bannedUntil || '');
      } else if (type === 'unban') {
        updated = await unbanUser(selectedUser.userId);
      }

      if (updated) {
        setSelectedUser(updated);
        // Sync local list summary state
        setUsers((prev) => prev.map((item) => (item.userId === updated!.userId ? updated! : item)));
      }
    } catch (e) {
      console.error('Failed to update user security state:', e);
    } finally {
      setLoading(false);
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
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="用户及商户账户治理"
        description="趣聚平台个人玩家与商家资质的账户总控枢纽。可审查注册流、发布流、封禁限制不良行为账号，或解除锁定以保障良性生态。"
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
            placeholder="搜索邮箱、昵称、商户名称..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full text-xs pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-semibold"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto justify-end">
          {/* User Kind select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">用户类型</span>
            <select
              value={kind}
              onChange={(e) => {
                setKind(e.target.value);
                setPage(1);
              }}
              className="text-xs px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none text-slate-700 font-bold focus:ring-2 focus:ring-blue-500/20 cursor-pointer min-w-[5.5rem]"
            >
              <option value="">全部</option>
              <option value="personal">个人用户</option>
              <option value="merchant">商家用户</option>
            </select>
          </div>

          {/* Account Status select */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-400 shrink-0">账户状态</span>
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
              <option value="banned">已封禁</option>
              <option value="inactive">未激活</option>
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
        ) : users.length === 0 ? (
          <EmptyState
            title="没有符合条件的记录"
            description="没有找到该筛选设置下的用户数据，请重新核对查询关键词或状态选择。"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] border-collapse text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-400 uppercase tracking-wider text-[10px] font-bold">
                  <th className="px-6 py-3.5">账号昵称 / 邮箱</th>
                  <th className="px-6 py-3.5">用户角色</th>
                  <th className="px-6 py-3.5">账户登录状态</th>
                  <th className="px-6 py-3.5">商家资质状态</th>
                  <th className="px-6 py-3.5">发布统计 (活动/小队)</th>
                  <th className="px-6 py-3.5">创建时间</th>
                  <th className="px-6 py-3.5 text-right pr-8">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => (
                  <tr
                    key={u.userId}
                    className="hover:bg-slate-50/50 transition-colors group text-xs text-slate-600 font-medium"
                  >
                    {/* Username & email */}
                    <td className="px-6 py-4 max-w-xs text-left">
                      <div className="space-y-0.5">
                        <p className="font-bold text-slate-800 truncate">
                          {u.nickname || '未设定昵称'}
                        </p>
                        <p className="text-[10px] text-slate-400 font-mono font-semibold">
                          {u.email}
                        </p>
                      </div>
                    </td>

                    {/* Role */}
                    <td className="px-6 py-4">
                      <StatusBadge type="userKind" value={u.kind} />
                    </td>

                    {/* Account login state */}
                    <td className="px-6 py-4">
                      <StatusBadge type="accountStatus" value={u.status} />
                    </td>

                    {/* Merchant qualification status */}
                    <td className="px-6 py-4">
                      {u.kind === 'merchant' && u.qualificationStatus ? (
                        <StatusBadge type="qualificationStatus" value={u.qualificationStatus} />
                      ) : (
                        <span className="text-slate-300 font-bold">-</span>
                      )}
                    </td>

                    {/* Activity & Team count stats */}
                    <td className="px-6 py-4 font-mono font-semibold">
                      <div className="space-y-0.5 text-left">
                        <p className="text-slate-800">
                          活动:{' '}
                          <span className="font-extrabold text-blue-600">{u.activityCount}</span> 个
                        </p>
                        <p className="text-[10px] text-slate-400">
                          小队:{' '}
                          <span className="font-extrabold text-indigo-500">{u.teamCount}</span> 个
                        </p>
                      </div>
                    </td>

                    {/* CreatedAt */}
                    <td className="px-6 py-4 font-mono font-bold text-slate-500">
                      {formatDateTime(u.createdAt)}
                    </td>

                    {/* Actions */}
                    <td className="px-6 py-4 text-right pr-8 shrink-0">
                      <button
                        onClick={() => handleOpenDrawer(u)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100 hover:bg-blue-600 text-slate-600 hover:text-white rounded-xl font-bold cursor-pointer transition-all focus:outline-none whitespace-nowrap"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>账户档案</span>
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

      {/* Details Drawer */}
      <DetailDrawer
        isOpen={!!selectedUser}
        onClose={() => setSelectedUser(null)}
        title="账户安全档案明细"
        subtitle={selectedUser ? `用户 ID: ${selectedUser.userId}` : ''}
        footer={
          selectedUser && (
            <div className="flex items-center gap-2">
              {selectedUser.status === 'banned' ? (
                <button
                  onClick={() => triggerAction('unban')}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  解除限制并恢复
                </button>
              ) : (
                <button
                  onClick={() => triggerAction('ban')}
                  className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-xl cursor-pointer focus:outline-none transition-colors shadow-sm"
                >
                  安全封禁此账号
                </button>
              )}
            </div>
          )
        }
      >
        {selectedUser && (
          <div className="space-y-6 text-xs text-slate-600 leading-relaxed font-semibold">
            {/* Header bio Card */}
            <div className="flex items-start gap-4 p-5 bg-slate-50 border border-slate-100 rounded-2xl text-left">
              <div className="h-12 w-12 rounded-2xl bg-blue-100 flex items-center justify-center font-bold text-blue-700 text-base shrink-0 border border-blue-200/50">
                {selectedUser.nickname?.slice(0, 1) || '用'}
              </div>
              <div className="space-y-1 text-left min-w-0 flex-1">
                <h4 className="text-sm font-black text-slate-800 truncate">
                  {selectedUser.nickname || '无昵称个人'}
                </h4>
                <div className="flex items-center gap-1.5 text-[10px] text-slate-400 font-semibold font-mono">
                  <Mail className="h-3.5 w-3.5" />
                  <span className="truncate">{selectedUser.email}</span>
                </div>
              </div>
            </div>

            {/* Core credentials status segment */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left">
                账户登录及信誉评分
              </h5>
              <div className="grid grid-cols-2 gap-4 bg-slate-50/50 border border-slate-100 p-4 rounded-2xl text-left">
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">账户类型</p>
                  <div>
                    <StatusBadge type="userKind" value={selectedUser.kind} />
                  </div>
                </div>
                <div className="space-y-1">
                  <p className="text-[10px] text-slate-400">登录限制状态</p>
                  <div>
                    <StatusBadge type="accountStatus" value={selectedUser.status} />
                  </div>
                </div>
                <div className="space-y-1 col-span-2 pt-2 border-t border-slate-100">
                  <p className="text-[10px] text-slate-400">平台发布统计指标</p>
                  <p className="text-slate-700 font-bold pt-0.5">
                    该账户总计在平台发布了{' '}
                    <strong className="text-blue-600 font-extrabold">
                      {userActivities.length}
                    </strong>{' '}
                    个活动，并参与了{' '}
                    <strong className="text-indigo-600 font-extrabold">{userTeams.length}</strong>{' '}
                    个兴趣社群小队。
                  </p>
                </div>
              </div>
            </div>

            {/* Ban info (if banned) */}
            {userDetail?.currentBanInfo && (
              <div className="space-y-3">
                <h5 className="text-[10px] font-bold text-red-500 uppercase tracking-wider text-left">
                  当前封禁信息
                </h5>
                <div className="p-4 bg-red-50 border border-red-100 rounded-2xl space-y-2 text-left">
                  <div className="flex justify-between">
                    <span className="text-[10px] text-red-400">封禁原因</span>
                    <span className="text-xs text-red-700 font-bold max-w-[200px] text-right">
                      {userDetail.currentBanInfo.reason}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[10px] text-red-400">封禁截止</span>
                    <span className="text-xs text-red-700 font-mono font-bold">
                      {formatDateTime(userDetail.currentBanInfo.bannedUntil)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[10px] text-red-400">执行时间</span>
                    <span className="text-xs text-red-700 font-mono font-bold">
                      {formatDateTime(userDetail.currentBanInfo.createdAt)}
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* User Activities List */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
                <Activity className="h-3.5 w-3.5" />
                <span>发布的活动 ({userActivities.length})</span>
              </h5>
              {detailLoading ? (
                <SkeletonBlock rows={2} />
              ) : userActivities.length === 0 ? (
                <div className="p-4 bg-slate-50 border border-dashed border-slate-200 rounded-xl text-center text-[10px] text-slate-400 font-semibold">
                  该用户尚未发布任何活动。
                </div>
              ) : (
                <div className="space-y-2 max-h-[200px] overflow-y-auto">
                  {userActivities.map((act) => (
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

            {/* User Teams List */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left flex items-center gap-1.5">
                <Users2 className="h-3.5 w-3.5" />
                <span>参与的小队 ({userTeams.length})</span>
              </h5>
              {detailLoading ? (
                <SkeletonBlock rows={2} />
              ) : userTeams.length === 0 ? (
                <div className="p-4 bg-slate-50 border border-dashed border-slate-200 rounded-xl text-center text-[10px] text-slate-400 font-semibold">
                  该用户未创建或加入任何小队。
                </div>
              ) : (
                <div className="space-y-2 max-h-[200px] overflow-y-auto">
                  {userTeams.map((team) => (
                    <div
                      key={team.teamId}
                      className="p-3 bg-slate-50 border border-slate-100 rounded-xl text-left"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-slate-800 font-bold text-xs line-clamp-1 flex-1">
                          {team.name}
                        </p>
                        <StatusBadge type="teamStatus" value={team.status} />
                      </div>
                      <div className="flex items-center gap-3 mt-1.5 text-[10px] text-slate-400 font-mono">
                        <span>
                          成员: {team.memberCount}/{team.capacity}
                        </span>
                        <span>队长: {team.leaderId === selectedUser.userId ? '是' : '否'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Timestamps */}
            <div className="space-y-3">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-left">
                基本系统标记与注册期
              </h5>
              <div className="p-3.5 bg-slate-50/50 border border-slate-100 rounded-xl flex items-center justify-between text-left">
                <div className="flex items-center gap-2 text-slate-500 font-semibold">
                  <Calendar className="h-4 w-4 text-blue-500" />
                  <span>注册入库时间</span>
                </div>
                <span className="font-mono text-slate-800 font-bold">
                  {formatDateTime(selectedUser.createdAt)}
                </span>
              </div>
            </div>

            {/* Hard rule warning about profiles/passwords editing */}
            <div className="p-3.5 bg-amber-50 border border-amber-100 text-amber-800 rounded-xl text-left flex gap-3">
              <AlertTriangle className="h-5 w-5 text-amber-500 shrink-0" />
              <div className="space-y-1 text-slate-600">
                <p className="text-[10px] text-amber-800 font-bold">后台管理权责约束警告：</p>
                <p className="text-[10px] font-medium leading-relaxed">
                  管理员账号受到合规安全红线制约：
                  <strong>严禁修改用户个人隐私资料、头像、昵称或强制重置、修改其密码</strong>
                  。相关操作仅能由用户本人验证通过后执行。运营干预仅限于限制登录(封禁)和解封操作。
                </p>
              </div>
            </div>
          </div>
        )}
      </DetailDrawer>

      {/* Confirmation ban controls */}
      <ConfirmDialog
        isOpen={confirmConfig.isOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        requireReason={confirmConfig.requireReason}
        showBanUntil={confirmConfig.showBanUntil}
        reasonPlaceholder="请填写封禁此用户的法律法规或平台守则依据（如：群发理财引流高息贷款诈骗垃圾广告）..."
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
};

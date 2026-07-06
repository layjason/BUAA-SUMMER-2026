import React, { useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { changePassword } from '../api/adminAuth';
import { BusinessError } from '../api/client';
import { ADMIN_PASSWORD_MIN_LENGTH } from '../constants/admin';
import { getAdminErrorMessage } from '../constants/adminErrorMessages';
import { KeyRound, ShieldAlert, Sparkles, RefreshCw, Eye, EyeOff } from 'lucide-react';

export const SettingsPassword: React.FC = () => {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Visibility states
  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  /**
   * 提交管理员密码修改表单。
   *
   * 前置条件：旧密码、新密码和确认密码均来自当前表单输入。
   * 后置条件：校验失败时展示本地错误；服务端修改成功后清空密码字段。
   * 不变量：该函数不修改登录令牌，不改变当前页面路由。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Client-side validations
    if (!oldPassword || !newPassword || !confirmPassword) {
      setError('所有密码字段均不能为空');
      return;
    }

    if (newPassword === oldPassword) {
      setError('新密码不能与原有密码相同');
      return;
    }

    if (newPassword.length < ADMIN_PASSWORD_MIN_LENGTH) {
      setError(`新密码长度必须不小于 ${ADMIN_PASSWORD_MIN_LENGTH} 位，以确保后台治理安全性`);
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致，请检查');
      return;
    }

    setLoading(true);
    try {
      await changePassword({ oldPassword, newPassword });

      // Clear fields upon successful update
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: unknown) {
      if (err instanceof BusinessError) {
        setError(getAdminErrorMessage(err.code, err.message));
      } else {
        setError(err instanceof Error ? err.message : '修改密码失败，请核对旧密码是否输入正确');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl mx-auto">
      <PageHeader
        title="管理员安全密码设置"
        description="修改您个人的后台管理员登录密码。新密码将进行高强度服务端加盐单向哈希保存，不可逆向还原。"
      />

      {/* Security notice block */}
      <div className="flex items-start gap-4 p-4 bg-slate-50 border border-slate-200/60 rounded-2xl text-left">
        <ShieldAlert className="h-5 w-5 text-amber-500 shrink-0 mt-0.5" />
        <div className="space-y-1 text-slate-600 leading-relaxed font-semibold text-xs">
          <p className="text-slate-800 font-bold">后台审计级安全规范：</p>
          <p className="text-slate-400 font-semibold leading-relaxed">
            由于您具备趣聚平台最高级别的内容下架、用户封锁和资质审核权限，请确保新设置的密码不低于8位，包含字符、数字的组合，并且严禁与微信、邮箱等日常账号的密码相同，以防止社交劫持。
          </p>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden text-left">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-2">
          <KeyRound className="h-5 w-5 text-blue-500" />
          <h3 className="text-sm font-bold text-slate-800">安全修改表单</h3>
        </div>

        <form
          onSubmit={handleSubmit}
          className="p-6 space-y-4 text-xs font-semibold text-slate-600"
        >
          {/* Old Password */}
          <div className="space-y-1.5 text-left">
            <label className="text-xs font-bold text-slate-500 block">请输入当前旧密码</label>
            <div className="relative">
              <input
                type={showOld ? 'text' : 'password'}
                required
                disabled={loading}
                placeholder="请输入当前正在使用的旧密码"
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                className="w-full text-xs px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-800 font-mono font-bold"
              />
              <button
                type="button"
                onClick={() => setShowOld(!showOld)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 focus:outline-none cursor-pointer"
              >
                {showOld ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          {/* New Password */}
          <div className="space-y-1.5 text-left">
            <label className="text-xs font-bold text-slate-500 block">请输入新密码</label>
            <div className="relative">
              <input
                type={showNew ? 'text' : 'password'}
                required
                disabled={loading}
                placeholder="设置新密码 (长度不小于 8 位)"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="w-full text-xs px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-800 font-mono font-bold"
              />
              <button
                type="button"
                onClick={() => setShowNew(!showNew)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 focus:outline-none cursor-pointer"
              >
                {showNew ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          {/* Confirm Password */}
          <div className="space-y-1.5 text-left">
            <label className="text-xs font-bold text-slate-500 block">重复输入新密码确认</label>
            <div className="relative">
              <input
                type={showConfirm ? 'text' : 'password'}
                required
                disabled={loading}
                placeholder="请再次输入新密码核对"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="w-full text-xs px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-800 font-mono font-bold"
              />
              <button
                type="button"
                onClick={() => setShowConfirm(!showConfirm)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 focus:outline-none cursor-pointer"
              >
                {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          {error && (
            <p className="text-xs font-semibold text-red-500 pl-1 animate-pulse text-left">
              {error}
            </p>
          )}

          {/* Actions button */}
          <div className="pt-2">
            <button
              type="submit"
              disabled={loading}
              className="flex items-center justify-center gap-2 py-2.5 px-6 rounded-xl text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 shadow-md shadow-blue-500/10 focus:outline-none transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                <>
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  <span>提请加密哈希中...</span>
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4" />
                  <span>提交更新安全密码</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

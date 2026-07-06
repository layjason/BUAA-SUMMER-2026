import React, { useState } from 'react';
import { Lock, Smartphone, Loader2, CheckCircle2, AlertCircle, Eye, EyeOff } from 'lucide-react';
import { resetPassword } from '../api/identityAuth';
import { BusinessError } from '../api/client';
import {
  readResetToken,
  buildAppUrl,
  isValidPassword,
  MIN_PASSWORD_LENGTH,
} from './passwordResetUtils';

/** 密码重置 token 无效/过期/已使用的业务错误码 */
const INVALID_TOKEN_CODE = 10017;

/** 重置页面状态 */
type ResetState = 'form' | 'submitting' | 'success' | 'fallback';

/**
 * 重置密码页 — 通过邮件链接中的 token 设置新密码。
 *
 * 前置条件：用户通过邮件链接访问 /reset-password?token=xxx。
 * 后置条件：成功时展示确认并引导登录；token 无效时展示 APP 引导。
 * 不变量：不修改本地认证状态，token 仅在提交时传输一次。
 */
export const ResetPassword: React.FC = () => {
  const [token] = useState<string | null>(() => readResetToken(window.location.search));
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [formError, setFormError] = useState('');
  const [state, setState] = useState<ResetState>(() => (token ? 'form' : 'fallback'));
  /**
   * 根据当前状态构造正确的 APP 深度链接。
   * 成功时指向 reset-password 确认页；其它情况指向 forgot-password 以便用户重新发起。
   */
  const appUrl =
    state === 'success' ? buildAppUrl('reset-password') : buildAppUrl('forgot-password');

  /**
   * 提交新密码。
   *
   * 前置条件：token 有效，新密码满足强度要求且两次输入一致。
   * 后置条件：成功时切换到 success 状态；token 无效时切换到 fallback 状态。
   * 不变量：提交过程中不修改 token，只更新页面状态。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || state === 'submitting') return;

    setPasswordError('');
    setFormError('');

    if (!isValidPassword(password)) {
      setPasswordError(`密码长度不能少于 ${MIN_PASSWORD_LENGTH} 位`);
      return;
    }
    if (password !== confirmPassword) {
      setPasswordError('两次输入的密码不一致');
      return;
    }

    setState('submitting');

    try {
      await resetPassword(token, password);
      setState('success');
    } catch (error: unknown) {
      if (error instanceof BusinessError && error.code === INVALID_TOKEN_CODE) {
        setState('fallback');
      } else if (error instanceof BusinessError) {
        setFormError(error.message);
        setState('form');
      } else {
        setFormError('网络错误，请检查连接后重试');
        setState('form');
      }
    }
  };

  return (
    <div className="relative min-h-screen overflow-hidden bg-slate-50/50">
      {/* 背景装饰光斑 */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-100/40 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-indigo-100/40 rounded-full blur-3xl pointer-events-none" />

      <div className="relative min-h-screen flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white rounded-3xl border border-slate-200/80 shadow-2xl p-6 sm:p-8 text-center space-y-6">
          {/* 品牌标识 */}
          <div className="space-y-2">
            <div className="mx-auto h-12 w-12 rounded-2xl bg-blue-600 flex items-center justify-center shadow-xl shadow-blue-500/20 text-white font-black text-xl">
              趣
            </div>
            <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">趣聚平台</h2>
            <p className="text-xs text-slate-400 font-semibold uppercase tracking-wider">
              重置密码
            </p>
          </div>

          {/* 新密码表单 */}
          {(state === 'form' || state === 'submitting') && token && (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5 text-left">
                <label className="text-xs font-bold text-slate-500 block">新密码</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                    <Lock className="h-4 w-4" />
                  </span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    disabled={state === 'submitting'}
                    placeholder={`至少 ${MIN_PASSWORD_LENGTH} 位`}
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                      setPasswordError('');
                    }}
                    className="w-full text-sm pl-10 pr-10 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-mono font-medium"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600 cursor-pointer"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-1.5 text-left">
                <label className="text-xs font-bold text-slate-500 block">确认新密码</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                    <Lock className="h-4 w-4" />
                  </span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    disabled={state === 'submitting'}
                    placeholder="再次输入新密码"
                    value={confirmPassword}
                    onChange={(e) => {
                      setConfirmPassword(e.target.value);
                      setPasswordError('');
                    }}
                    className="w-full text-sm pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-mono font-medium"
                  />
                </div>
                {passwordError && (
                  <p className="text-xs font-semibold text-red-500 pl-1">{passwordError}</p>
                )}
              </div>

              {formError && (
                <p className="text-xs font-semibold text-red-500 text-left pl-1">{formError}</p>
              )}

              <button
                type="submit"
                disabled={state === 'submitting'}
                className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-bold text-white bg-blue-600 hover:bg-blue-700 shadow-lg shadow-blue-500/10 transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {state === 'submitting' ? (
                  <span className="flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>重置中…</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5">
                    <Lock className="h-4 w-4" />
                    <span>确认重置密码</span>
                  </span>
                )}
              </button>
            </form>
          )}

          {/* 重置成功 */}
          {state === 'success' && (
            <div className="space-y-4">
              <div className="flex flex-col items-center gap-3 py-2">
                <div className="h-14 w-14 rounded-full bg-emerald-50 flex items-center justify-center">
                  <CheckCircle2 className="h-8 w-8 text-emerald-500" />
                </div>
                <p className="text-base font-bold text-slate-800">密码重置成功</p>
                <p className="text-xs text-slate-500">您的密码已成功更新，请使用新密码登录。</p>
              </div>
            </div>
          )}

          {/* 兜底：token 无效或无 token */}
          {state === 'fallback' && (
            <div className="space-y-4">
              <div className="flex flex-col items-center gap-3 py-2">
                <div className="h-14 w-14 rounded-full bg-amber-50 flex items-center justify-center">
                  <AlertCircle className="h-8 w-8 text-amber-500" />
                </div>
                <p className="text-base font-bold text-slate-800">请在趣聚 APP 中处理</p>
                <p className="text-xs text-slate-500">
                  重置链接无效或已过期，请打开趣聚 APP 重新发起密码重置。
                </p>
              </div>
            </div>
          )}

          {/* 打开趣聚 APP */}
          <a
            href={appUrl}
            className="inline-flex items-center justify-center gap-2 w-full py-3 px-4 rounded-xl text-sm font-bold text-white bg-slate-700 hover:bg-slate-800 shadow-lg shadow-slate-500/10 transition-all duration-200"
          >
            <Smartphone className="h-4 w-4" />
            <span>打开趣聚 APP</span>
          </a>

          <div className="pt-2">
            <p className="text-[10px] text-slate-400 font-medium">
              迷星群聚平台运营体系 © 2026 - All Rights Reserved
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState } from 'react';
import { Mail, Smartphone, Loader2, CheckCircle2, RefreshCw } from 'lucide-react';
import { sendPasswordResetEmail } from '../api/identityAuth';
import { BusinessError } from '../api/client';
import { isValidEmail, buildAppUrl } from './passwordResetUtils';

/** 邮件发送频率限制的业务错误码，与 TypeSpec Errors.Identity.PasswordResetEmailRateLimited 对齐 */
const RATE_LIMIT_CODE = 10018;

/** 冷却倒计时秒数 */
const COOLDOWN_SECONDS = 60;

/**
 * 忘记密码页 — 输入邮箱发送密码重置邮件。
 *
 * 前置条件：用户通过登录页"忘记密码"链接或直接访问 /forgot-password。
 * 后置条件：发送成功后展示确认信息和重发按钮；频率限制时启动冷却倒计时。
 * 不变量：不修改本地认证状态，不存储用户输入的邮箱到持久化存储。
 */
export const ForgotPassword: React.FC = () => {
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [resendSuccess, setResendSuccess] = useState(false);
  const appUrl = buildAppUrl('forgot-password');

  /**
   * 启动冷却倒计时。
   *
   * 前置条件：cooldown 为 0 且需要开始新的冷却周期。
   * 后置条件：每秒递减计数直到归零。
   * 不变量：不影响其它状态，只维护倒计时数值。
   */
  const startCooldown = () => {
    setCooldown(COOLDOWN_SECONDS);
    const timer = setInterval(() => {
      setCooldown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  /**
   * 校验邮箱格式。
   *
   * 前置条件：用户已输入邮箱。
   * 后置条件：格式合法返回 true；不合法时设置 emailError 并返回 false。
   * 不变量：只更新 emailError 状态。
   */
  const validateEmail = (): boolean => {
    setEmailError('');
    const trimmed = email.trim();
    if (!trimmed) {
      setEmailError('请输入邮箱地址');
      return false;
    }
    if (!isValidEmail(trimmed)) {
      setEmailError('邮箱格式不正确');
      return false;
    }
    return true;
  };

  /**
   * 发送密码重置邮件。
   *
   * 前置条件：邮箱格式校验通过，不在加载状态。
   * 后置条件：成功时切换到已发送状态；频率限制时启动冷却倒计时。
   * 不变量：发送请求不改变认证状态。
   */
  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading || cooldown > 0) return;
    if (!validateEmail()) return;

    setLoading(true);
    setFormError('');

    try {
      await sendPasswordResetEmail(email.trim());
      setSent(true);
      startCooldown();
    } catch (error: unknown) {
      if (error instanceof BusinessError) {
        if (error.code === RATE_LIMIT_CODE) {
          setFormError('发送过于频繁，请稍后再试');
          startCooldown();
        } else {
          setFormError(error.message);
        }
      } else {
        setFormError('网络错误，请检查连接后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  /**
   * 重发密码重置邮件。
   *
   * 前置条件：已发送过邮件且不在冷却期内。
   * 后置条件：成功时显示重发成功提示并开始新的冷却周期。
   * 不变量：不改变 sent 状态，只更新 resendSuccess 和 cooldown。
   */
  const handleResend = async () => {
    if (loading || cooldown > 0) return;

    setLoading(true);
    setResendSuccess(false);
    setFormError('');

    try {
      await sendPasswordResetEmail(email.trim());
      setResendSuccess(true);
      startCooldown();
    } catch (error: unknown) {
      if (error instanceof BusinessError) {
        if (error.code === RATE_LIMIT_CODE) {
          setFormError('发送过于频繁，请稍后再试');
          startCooldown();
        } else {
          setFormError(error.message);
        }
      } else {
        setFormError('网络错误，请检查连接后重试');
      }
    } finally {
      setLoading(false);
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
              找回密码
            </p>
          </div>

          {!sent ? (
            /* 邮箱输入表单 */
            <form onSubmit={handleSend} className="space-y-4">
              <div className="space-y-1.5 text-left">
                <label className="text-xs font-bold text-slate-500 block">注册邮箱</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                    <Mail className="h-4 w-4" />
                  </span>
                  <input
                    type="email"
                    disabled={loading}
                    placeholder="请输入注册邮箱"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
                      setEmailError('');
                    }}
                    className="w-full text-sm pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-medium"
                  />
                </div>
                {emailError && (
                  <p className="text-xs font-semibold text-red-500 pl-1">{emailError}</p>
                )}
              </div>

              {formError && (
                <p className="text-xs font-semibold text-red-500 text-left pl-1">{formError}</p>
              )}

              <button
                type="submit"
                disabled={loading || cooldown > 0}
                className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-bold text-white bg-blue-600 hover:bg-blue-700 shadow-lg shadow-blue-500/10 transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <span className="flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>发送中…</span>
                  </span>
                ) : cooldown > 0 ? (
                  <span>发送过于频繁（{cooldown}s）</span>
                ) : (
                  <span className="flex items-center gap-1.5">
                    <Mail className="h-4 w-4" />
                    <span>发送重置邮件</span>
                  </span>
                )}
              </button>
            </form>
          ) : (
            /* 已发送确认区域 */
            <div className="space-y-4">
              <div className="flex flex-col items-center gap-3 py-2">
                <div className="h-14 w-14 rounded-full bg-emerald-50 flex items-center justify-center">
                  <CheckCircle2 className="h-8 w-8 text-emerald-500" />
                </div>
                <p className="text-base font-bold text-slate-800">重置邮件已发送</p>
                <p className="text-sm font-semibold text-blue-600 break-all">{email.trim()}</p>
                <p className="text-xs text-slate-500">请查收邮件并点击链接重置密码。</p>
              </div>

              {resendSuccess && (
                <p className="text-xs font-semibold text-emerald-500">重发成功，请查收邮件</p>
              )}
              {formError && <p className="text-xs font-semibold text-red-500">{formError}</p>}

              <button
                type="button"
                onClick={handleResend}
                disabled={loading || cooldown > 0}
                className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-bold text-blue-600 bg-blue-50 hover:bg-blue-100 transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <RefreshCw className="h-4 w-4" />
                )}
                <span>{cooldown > 0 ? `重新发送（${cooldown}s）` : '重新发送'}</span>
              </button>
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

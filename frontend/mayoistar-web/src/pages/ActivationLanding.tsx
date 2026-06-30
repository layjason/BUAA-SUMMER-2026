import React, { useEffect, useState } from 'react';
import { CheckCircle2, AlertCircle, Smartphone, Loader2 } from 'lucide-react';
import { readActivationToken, buildAppActivationUrl } from './activationUtils';
import { consumeActivationTokenOnce, type ActivationState } from './activationFlow';

/**
 * 激活落地页，处理邮件激活链接的 token 并展示对应状态。
 *
 * 前置条件：用户通过邮件链接访问 /activate?token=xxx 页面。
 * 后置条件：token 有效或已激活时展示成功状态；其余情况引导用户打开趣聚 APP。
 * 不变量：不修改本地存储或认证状态，不在侧边栏中注册路由。
 */
export const ActivationLanding: React.FC = () => {
  const [state, setState] = useState<ActivationState>('loading');
  const [token] = useState<string | null>(() => readActivationToken(window.location.search));
  const appUrl = buildAppActivationUrl(token);

  /**
   * 页面加载后自动触发激活请求。
   *
   * 前置条件：组件已挂载，token 来自 URL 查询参数。
   * 后置条件：根据响应结果设置页面状态为 success 或 fallback。
   * 不变量：通过 consumeActivationTokenOnce 保证同一 token 只发起一次请求，
   *         React StrictMode 重复挂载时复用首次请求的 Promise。
   */
  useEffect(() => {
    if (!token) {
      setState('fallback');
      return;
    }

    let cancelled = false;

    consumeActivationTokenOnce(token).then((nextState) => {
      if (!cancelled) setState(nextState);
    });

    return () => {
      cancelled = true;
    };
  }, [token]);

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
              账号激活
            </p>
          </div>

          {/* 状态展示区域 */}
          <div className="space-y-4">
            {state === 'loading' && (
              <div className="flex flex-col items-center gap-3 py-4">
                <Loader2 className="h-10 w-10 text-blue-500 animate-spin" />
                <p className="text-sm font-semibold text-slate-600">正在验证激活链接…</p>
              </div>
            )}

            {state === 'success' && (
              <div className="flex flex-col items-center gap-3 py-4">
                <div className="h-14 w-14 rounded-full bg-emerald-50 flex items-center justify-center">
                  <CheckCircle2 className="h-8 w-8 text-emerald-500" />
                </div>
                <p className="text-base font-bold text-slate-800">激活成功</p>
                <p className="text-xs text-slate-500">
                  您的账号已成功激活，可前往趣聚 APP 登录使用。
                </p>
              </div>
            )}

            {state === 'fallback' && (
              <div className="flex flex-col items-center gap-3 py-4">
                <div className="h-14 w-14 rounded-full bg-amber-50 flex items-center justify-center">
                  <AlertCircle className="h-8 w-8 text-amber-500" />
                </div>
                <p className="text-base font-bold text-slate-800">请在趣聚 APP 中处理</p>
                <p className="text-xs text-slate-500">
                  激活链接无效或已过期，请打开趣聚 APP 完成激活操作。
                </p>
              </div>
            )}
          </div>

          {/* 打开趣聚 APP 操作按钮 */}
          <a
            href={appUrl}
            className="inline-flex items-center justify-center gap-2 w-full py-3 px-4 rounded-xl text-sm font-bold text-white bg-blue-600 hover:bg-blue-700 shadow-lg shadow-blue-500/10 transition-all duration-200"
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

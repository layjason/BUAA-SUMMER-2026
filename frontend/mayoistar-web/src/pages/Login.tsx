import React, { useState } from 'react';
import { login } from '../api/adminAuth';
import { Lock, User, Eye, EyeOff, Sparkles } from 'lucide-react';

interface LoginProps {
  onLoginSuccess: () => void;
}

export const Login: React.FC<LoginProps> = ({ onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  /**
   * 提交管理员登录表单。
   *
   * 前置条件：用户已经输入管理员账号和密码。
   * 后置条件：认证成功后通知父组件切换到已登录状态；失败时展示错误文案。
   * 不变量：提交过程中只修改当前登录表单状态，不直接跳转路由。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('请输入管理员账号及密码');
      return;
    }

    setLoading(true);
    setError('');

    try {
      await login({ username: username.trim(), password: password.trim() });
      onLoginSuccess();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '登录失败，请检查账号和密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50/50 p-4">
      {/* Decorative ambient subtle circle */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-100/40 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-indigo-100/40 rounded-full blur-3xl pointer-events-none" />

      {/* Main card */}
      <div className="relative w-full max-w-md bg-white rounded-3xl border border-slate-200/80 shadow-2xl p-8 text-center space-y-6">
        {/* Brand visual header */}
        <div className="space-y-2">
          <div className="mx-auto h-12 w-12 rounded-2xl bg-blue-600 flex items-center justify-center shadow-xl shadow-blue-500/20 text-white font-black text-xl">
            趣
          </div>
          <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">趣聚平台</h2>
          <p className="text-xs text-slate-400 font-semibold uppercase tracking-wider">
            运营管理控制台
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Username Input */}
          <div className="space-y-1.5 text-left">
            <label className="text-xs font-bold text-slate-500 block">管理员账号</label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                <User className="h-4 w-4" />
              </span>
              <input
                type="text"
                required
                disabled={loading}
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full text-sm pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-medium"
              />
            </div>
          </div>

          {/* Password Input */}
          <div className="space-y-1.5 text-left">
            <label className="text-xs font-bold text-slate-500 block">登录密码</label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                <Lock className="h-4 w-4" />
              </span>
              <input
                type={showPassword ? 'text' : 'password'}
                required
                disabled={loading}
                placeholder="请输入登录密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full text-sm pl-10 pr-10 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 font-mono font-medium"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600 focus:outline-none cursor-pointer"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          {error && (
            <p className="text-xs font-semibold text-red-500 text-left animate-pulse pl-1">
              {error}
            </p>
          )}

          {/* Submit Action */}
          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-bold text-white bg-blue-600 hover:bg-blue-700 shadow-lg shadow-blue-500/10 focus:outline-none transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <span className="flex items-center gap-2">
                <RefreshCw className="h-4 w-4 animate-spin" />
                <span>认证检查中...</span>
              </span>
            ) : (
              <span className="flex items-center gap-1.5">
                <Sparkles className="h-4 w-4" />
                <span>进入管理控制台</span>
              </span>
            )}
          </button>
        </form>

        <div className="pt-2">
          <p className="text-[10px] text-slate-400 font-medium">
            迷星群聚平台运营体系 © 2026 - All Rights Reserved
          </p>
        </div>
      </div>
    </div>
  );
};

// Helper inside code, imported Icon as dynamic
import { RefreshCw } from 'lucide-react';

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { LogOut, Wifi, Laptop, RefreshCw } from 'lucide-react';
import { isMockMode } from '../api/client';

interface TopBarProps {
  id?: string;
  onLogout: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({ id, onLogout }) => {
  const handleReload = () => {
    window.location.reload();
  };

  return (
    <header
      id={id}
      className="h-16 bg-white border-b border-slate-200/80 px-8 flex items-center justify-between shadow-xs shrink-0"
    >
      {/* Left side environment indicators */}
      <div className="flex items-center gap-4">
        {/* Connection status badge */}
        <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 bg-slate-50 px-2.5 py-1 rounded-lg border border-slate-200/60">
          <Wifi className="h-3.5 w-3.5 text-green-500 animate-pulse" />
          <span>网络状态: 正常</span>
        </div>

        {/* Database engine status */}
        <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 bg-slate-50 px-2.5 py-1 rounded-lg border border-slate-200/60">
          <Laptop className="h-3.5 w-3.5 text-blue-500" />
          <span>
            数据模式:{' '}
            <span className={isMockMode() ? 'text-amber-600 font-bold' : 'text-blue-600 font-bold'}>
              {isMockMode() ? '本地沙盒 Mock' : '远程后端 API'}
            </span>
          </span>
        </div>
      </div>

      {/* Right side controls and profile */}
      <div className="flex items-center gap-6">
        {/* Refresh button */}
        <button
          onClick={handleReload}
          title="刷新数据"
          className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors focus:outline-none cursor-pointer"
        >
          <RefreshCw className="h-4 w-4" />
        </button>

        {/* Avatar & identity details */}
        <div className="flex items-center gap-3 pl-4 border-l border-slate-200">
          <div className="h-9 w-9 rounded-xl bg-blue-100 flex items-center justify-center font-bold text-blue-700 text-sm border border-blue-200/50">
            管
          </div>
          <div className="hidden md:block text-left">
            <h3 className="text-xs font-bold text-slate-800">系统管理员</h3>
            <p className="text-[10px] text-slate-400 font-medium mt-0.5">运营管理总监</p>
          </div>
        </div>

        {/* Logout control button */}
        <button
          onClick={onLogout}
          title="安全登出"
          className="flex items-center gap-1.5 text-xs font-bold text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100/50 px-3 py-1.5 rounded-xl transition-all cursor-pointer focus:outline-none"
        >
          <LogOut className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">退出登录</span>
        </button>
      </div>
    </header>
  );
};

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  LayoutDashboard,
  ShieldCheck,
  UserCheck,
  Store,
  Users2,
  AlertOctagon,
  KeyRound,
  Sparkles,
} from 'lucide-react';

interface SidebarNavProps {
  id?: string;
  currentRoute: string;
  onNavigate: (route: string) => void;
}

interface NavItem {
  key: string;
  label: string;
  icon: React.ReactNode;
}

export const SidebarNav: React.FC<SidebarNavProps> = ({ id, currentRoute, onNavigate }) => {
  const navItems: NavItem[] = [
    {
      key: 'workbench',
      label: '工作台',
      icon: <LayoutDashboard className="h-5 w-5" />,
    },
    {
      key: 'activities',
      label: '活动审核',
      icon: <ShieldCheck className="h-5 w-5" />,
    },
    {
      key: 'users',
      label: '用户治理',
      icon: <UserCheck className="h-5 w-5" />,
    },
    {
      key: 'merchants',
      label: '商家审核',
      icon: <Store className="h-5 w-5" />,
    },
    {
      key: 'teams',
      label: '小队治理',
      icon: <Users2 className="h-5 w-5" />,
    },
    {
      key: 'reports',
      label: '举报处理',
      icon: <AlertOctagon className="h-5 w-5" />,
    },
    {
      key: 'settings/password',
      label: '账号设置',
      icon: <KeyRound className="h-5 w-5" />,
    },
  ];

  return (
    <aside
      id={id}
      className="w-64 bg-slate-900 text-slate-300 flex flex-col shrink-0 border-r border-slate-800 shadow-xl"
    >
      {/* Brand Header */}
      <div className="px-6 py-6 border-b border-slate-800 flex items-center gap-3">
        <div className="h-9 w-9 rounded-xl bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/20 text-white font-black text-lg">
          趣
        </div>
        <div>
          <h1 className="text-sm font-bold text-white tracking-wide flex items-center gap-1.5">
            趣聚平台
            <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-blue-400 font-semibold uppercase tracking-widest">
              Admin
            </span>
          </h1>
          <p className="text-[10px] text-slate-500 mt-0.5">运营管理控制台</p>
        </div>
      </div>

      {/* Nav Menu Items */}
      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const isSelected = currentRoute === item.key || currentRoute.startsWith(item.key + '/');
          return (
            <button
              key={item.key}
              onClick={() => onNavigate(item.key)}
              className={`w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-sm font-semibold tracking-wide transition-all duration-200 focus:outline-none cursor-pointer ${
                isSelected
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/10 scale-[1.02]'
                  : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200'
              }`}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* System Footer Info */}
      <div className="p-4 border-t border-slate-800 bg-slate-950/40 text-center space-y-1.5">
        <div className="flex items-center justify-center gap-1.5 text-xs font-semibold text-slate-500">
          <Sparkles className="h-3.5 w-3.5 text-blue-500 animate-spin" />
          <span>系统核心就绪</span>
        </div>
        <p className="text-[9px] text-slate-600">v1.2.0 - For Super Earth!</p>
      </div>
    </aside>
  );
};

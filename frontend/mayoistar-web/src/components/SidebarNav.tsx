import React from 'react';
import {
  LayoutDashboard,
  ShieldCheck,
  UserCheck,
  Store,
  Users2,
  AlertOctagon,
  KeyRound,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

interface SidebarNavProps {
  id?: string;
  currentRoute: string;
  onNavigate: (route: string) => void;
  /** 侧边栏是否处于收起状态 */
  collapsed?: boolean;
  /** 切换侧边栏收起/展开的回调 */
  onToggle?: () => void;
}

interface NavItem {
  key: string;
  label: string;
  icon: React.ReactNode;
}

/**
 * 侧边导航栏组件
 *
 * 职责：展示应用品牌标识与导航菜单，支持收起/展开切换。
 * 收起状态下仅显示图标，展开状态下显示图标与文字标签。
 *
 * 前置条件：currentRoute 必须是有效的路由 key
 * 后置条件：点击导航项时调用 onNavigate 并传入选中的路由 key
 * 不变量：navItems 列表在组件生命周期内保持不变
 */
export const SidebarNav: React.FC<SidebarNavProps> = ({
  id,
  currentRoute,
  onNavigate,
  collapsed = false,
  onToggle,
}) => {
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
      className={`${
        collapsed ? 'w-20' : 'w-64'
      } bg-slate-900 text-slate-300 flex flex-col shrink-0 border-r border-slate-800 shadow-xl transition-all duration-300 ease-in-out`}
    >
      {/* 品牌头部区域 */}
      <div
        className={`px-6 py-6 border-b border-slate-800 flex items-center gap-3 ${
          collapsed ? 'justify-center px-0' : ''
        }`}
      >
        <div className="h-9 w-9 rounded-xl bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/20 text-white font-black text-lg shrink-0">
          趣
        </div>
        {!collapsed && (
          <div className="overflow-hidden">
            <h1 className="text-sm font-bold text-white tracking-wide flex items-center gap-1.5">
              趣聚平台
              <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-blue-400 font-semibold uppercase tracking-widest">
                Admin
              </span>
            </h1>
            <p className="text-[10px] text-slate-500 mt-0.5">运营管理控制台</p>
          </div>
        )}
      </div>

      {/* 导航菜单列表 */}
      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto overflow-x-hidden">
        {navItems.map((item) => {
          const isSelected = currentRoute === item.key || currentRoute.startsWith(item.key + '/');
          return (
            <button
              key={item.key}
              onClick={() => onNavigate(item.key)}
              title={collapsed ? item.label : undefined}
              className={`w-full flex items-center gap-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all duration-200 focus:outline-none cursor-pointer ${
                collapsed ? 'justify-center px-0 py-3' : 'px-4 py-3'
              } ${
                isSelected
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/10 scale-[1.02]'
                  : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200'
              }`}
            >
              <span className="shrink-0">{item.icon}</span>
              {!collapsed && <span>{item.label}</span>}
            </button>
          );
        })}
      </nav>

      {/* 收起/展开切换按钮 */}
      {onToggle && (
        <div
          className={`px-4 py-4 border-t border-slate-800 ${collapsed ? 'flex justify-center' : ''}`}
        >
          <button
            onClick={onToggle}
            title={collapsed ? '展开侧边栏' : '收起侧边栏'}
            className="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-slate-500 hover:bg-slate-800/60 hover:text-slate-300 transition-all duration-200 focus:outline-none cursor-pointer"
          >
            {collapsed ? (
              <ChevronRight className="h-5 w-5 mx-auto" />
            ) : (
              <>
                <ChevronLeft className="h-5 w-5 shrink-0" />
                <span>收起菜单</span>
              </>
            )}
          </button>
        </div>
      )}
    </aside>
  );
};

import React from 'react';
import { ChevronRight } from 'lucide-react';

interface StatCardProps {
  id?: string;
  title: string;
  value: number | string;
  description?: string;
  icon: React.ReactNode;
  color?: 'blue' | 'emerald' | 'amber' | 'orange' | 'red' | 'indigo' | 'purple' | 'slate';
  onAction?: () => void;
  actionText?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  id,
  title,
  value,
  description,
  icon,
  color = 'blue',
  onAction,
  actionText = '去处理',
}) => {
  const colorsMap = {
    blue: {
      bg: 'bg-blue-50/50',
      iconBg: 'bg-blue-100 text-blue-600',
      border: 'hover:border-blue-200',
    },
    emerald: {
      bg: 'bg-emerald-50/50',
      iconBg: 'bg-emerald-100 text-emerald-600',
      border: 'hover:border-emerald-200',
    },
    amber: {
      bg: 'bg-amber-50/50',
      iconBg: 'bg-amber-100 text-amber-600',
      border: 'hover:border-amber-200',
    },
    orange: {
      bg: 'bg-orange-50/50',
      iconBg: 'bg-orange-100 text-orange-600',
      border: 'hover:border-orange-200',
    },
    red: {
      bg: 'bg-red-50/50',
      iconBg: 'bg-red-100 text-red-600',
      border: 'hover:border-red-200',
    },
    indigo: {
      bg: 'bg-indigo-50/50',
      iconBg: 'bg-indigo-100 text-indigo-600',
      border: 'hover:border-indigo-200',
    },
    purple: {
      bg: 'bg-purple-50/50',
      iconBg: 'bg-purple-100 text-purple-600',
      border: 'hover:border-purple-200',
    },
    slate: {
      bg: 'bg-slate-50/50',
      iconBg: 'bg-slate-100 text-slate-600',
      border: 'hover:border-slate-300',
    },
  };

  const scheme = colorsMap[color] || colorsMap.blue;

  return (
    <div
      id={id}
      className={`bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between transition-all duration-200 ${scheme.border}`}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1 text-left">
          <p className="text-xs font-semibold text-slate-400 tracking-wider uppercase">{title}</p>
          <p className="text-2xl font-extrabold text-slate-800 tracking-tight leading-none pt-1">
            {value}
          </p>
        </div>
        <div
          className={`h-10 w-10 rounded-xl flex items-center justify-center shrink-0 ${scheme.iconBg}`}
        >
          {icon}
        </div>
      </div>

      {description && (
        <p className="text-xs text-slate-400 font-medium mt-3 text-left leading-relaxed">
          {description}
        </p>
      )}

      {onAction && (
        <button
          onClick={onAction}
          className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs font-bold text-blue-600 hover:text-blue-800 focus:outline-none w-full text-left cursor-pointer group"
        >
          <span>{actionText}</span>
          <ChevronRight className="h-4 w-4 transform group-hover:translate-x-1 transition-transform" />
        </button>
      )}
    </div>
  );
};

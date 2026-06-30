import React from 'react';

interface PageHeaderProps {
  id?: string;
  title: string;
  description: string;
  actions?: React.ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ id, title, description, actions }) => {
  return (
    <div
      id={id}
      className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-200/60 mb-6"
    >
      <div className="text-left">
        <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">{title}</h2>
        <p className="text-xs text-slate-400 font-medium mt-1">{description}</p>
      </div>
      {actions && <div className="flex items-center gap-3 shrink-0">{actions}</div>}
    </div>
  );
};

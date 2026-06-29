/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ClipboardX } from 'lucide-react';

interface EmptyStateProps {
  id?: string;
  title?: string;
  description?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  id,
  title = '暂无待处理内容',
  description = '没有符合当前筛选条件的记录，请尝试调整筛选器。',
}) => {
  return (
    <div
      id={id}
      className="flex flex-col items-center justify-center p-12 text-center bg-slate-50/50 rounded-2xl border border-dashed border-slate-200"
    >
      <ClipboardX className="h-10 w-10 text-slate-300 stroke-[1.5] mb-4 animate-pulse" />
      <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
      <p className="text-xs text-slate-400 mt-1 max-w-xs">{description}</p>
    </div>
  );
};

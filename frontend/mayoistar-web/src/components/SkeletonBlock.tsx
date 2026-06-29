/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';

interface SkeletonBlockProps {
  id?: string;
  rows?: number;
}

export const SkeletonBlock: React.FC<SkeletonBlockProps> = ({ id, rows = 3 }) => {
  return (
    <div id={id} className="w-full space-y-4 animate-pulse p-4">
      {Array.from({ length: rows }).map((_, idx) => (
        <div key={idx} className="flex gap-4 items-center">
          <div className="h-10 w-10 rounded-full bg-slate-200"></div>
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-slate-200 rounded w-1/3"></div>
            <div className="h-3 bg-slate-100 rounded w-1/2"></div>
          </div>
        </div>
      ))}
    </div>
  );
};

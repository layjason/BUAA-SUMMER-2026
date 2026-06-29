/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';

interface TagListProps {
  id?: string;
  tags: string[];
  maxCount?: number;
  interactive?: boolean;
}

export const TagList: React.FC<TagListProps> = ({ id, tags, maxCount, interactive }) => {
  if (!tags || tags.length === 0) {
    return <span className="text-slate-400 text-xs">-</span>;
  }

  const visibleTags = maxCount ? tags.slice(0, maxCount) : tags;
  const hasMore = maxCount && tags.length > maxCount;

  return (
    <div id={id} className="flex flex-wrap gap-1.5 items-center">
      {visibleTags.map((tag, idx) => (
        <span
          key={idx}
          className={`inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium ${
            interactive
              ? 'bg-blue-50 text-blue-700 hover:bg-blue-100 cursor-pointer transition-colors'
              : 'bg-slate-100 text-slate-600'
          }`}
        >
          {tag}
        </span>
      ))}
      {hasMore && (
        <span className="text-[11px] text-slate-400 font-medium ml-1">
          +{tags.length - maxCount}
        </span>
      )}
    </div>
  );
};

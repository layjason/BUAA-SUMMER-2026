/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  id?: string;
  page: number;
  pageSize: number;
  total: number;
  onChange: (newPage: number) => void;
}

export const Pagination: React.FC<PaginationProps> = ({ id, page, pageSize, total, onChange }) => {
  const totalPages = Math.ceil(total / pageSize) || 1;

  if (total === 0) return null;

  return (
    <div id={id} className="flex items-center justify-between px-4 py-3 sm:px-6">
      <div className="flex flex-1 justify-between sm:hidden">
        <button
          onClick={() => onChange(Math.max(1, page - 1))}
          disabled={page === 1}
          className="relative inline-flex items-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        >
          上一页
        </button>
        <button
          onClick={() => onChange(Math.min(totalPages, page + 1))}
          disabled={page === totalPages}
          className="relative ml-3 inline-flex items-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        >
          下一页
        </button>
      </div>
      <div className="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
        <div>
          <p className="text-sm text-slate-500">
            显示第 <span className="font-semibold text-slate-800">{(page - 1) * pageSize + 1}</span>{' '}
            至{' '}
            <span className="font-semibold text-slate-800">{Math.min(page * pageSize, total)}</span>{' '}
            条记录，共 <span className="font-semibold text-slate-800">{total}</span> 条记录
          </p>
        </div>
        <div>
          <nav
            className="isolate inline-flex -space-x-px rounded-lg shadow-xs"
            aria-label="Pagination"
          >
            <button
              onClick={() => onChange(Math.max(1, page - 1))}
              disabled={page === 1}
              className="relative inline-flex items-center rounded-l-lg border border-slate-300 bg-white p-2 text-sm font-medium text-slate-500 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              <span className="sr-only">Previous</span>
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </button>

            {Array.from({ length: totalPages }).map((_, idx) => {
              const p = idx + 1;
              const isCurrent = p === page;
              return (
                <button
                  key={p}
                  onClick={() => onChange(p)}
                  className={`relative inline-flex items-center px-3.5 py-1.5 text-sm font-semibold border focus:z-20 cursor-pointer ${
                    isCurrent
                      ? 'z-10 bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  {p}
                </button>
              );
            })}

            <button
              onClick={() => onChange(Math.min(totalPages, page + 1))}
              disabled={page === totalPages}
              className="relative inline-flex items-center rounded-r-lg border border-slate-300 bg-white p-2 text-sm font-medium text-slate-500 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              <span className="sr-only">Next</span>
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </button>
          </nav>
        </div>
      </div>
    </div>
  );
};

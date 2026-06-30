import React from 'react';
import { ReviewRecord } from '../types';
import { StatusBadge } from './StatusBadge';
import { Sparkles, User, Calendar } from 'lucide-react';

interface ReviewTimelineProps {
  id?: string;
  records: ReviewRecord[];
}

export const ReviewTimeline: React.FC<ReviewTimelineProps> = ({ id, records }) => {
  if (!records || records.length === 0) {
    return (
      <div
        id={id}
        className="text-center py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200"
      >
        <p className="text-xs text-slate-400 font-semibold">暂无任何历史审核记录</p>
      </div>
    );
  }

  const formatTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div id={id} className="flow-root text-left">
      <ul className="-mb-8">
        {records.map((record, recordIdx) => {
          const isAi = !record.reviewerId;
          return (
            <li key={record.reviewId}>
              <div className="relative pb-8">
                {/* Visual vertical connector connector */}
                {recordIdx !== records.length - 1 ? (
                  <span
                    className="absolute top-4 left-4 -ml-px h-full w-0.5 bg-slate-200"
                    aria-hidden="true"
                  />
                ) : null}

                <div className="relative flex space-x-3 items-start">
                  <div>
                    <span
                      className={`h-8 w-8 rounded-full flex items-center justify-center ring-8 ring-white ${
                        isAi
                          ? 'bg-indigo-50 text-indigo-600 border border-indigo-200/50'
                          : 'bg-slate-100 text-slate-600'
                      }`}
                    >
                      {isAi ? <Sparkles className="h-4 w-4" /> : <User className="h-4 w-4" />}
                    </span>
                  </div>

                  <div className="flex-1 min-w-0 pt-1">
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-slate-800">
                          {isAi ? 'AI 自动内容安检系统' : `人工终核员 (${record.reviewerId})`}
                        </span>
                        <StatusBadge type="qualificationStatus" value={record.result} />
                      </div>

                      <div className="flex items-center gap-1 text-[10px] text-slate-400 font-medium">
                        <Calendar className="h-3.5 w-3.5" />
                        <span>{formatTime(record.reviewedAt)}</span>
                      </div>
                    </div>

                    {record.reason && (
                      <div className="mt-2 text-xs text-slate-600 bg-slate-50 p-2.5 rounded-xl border border-slate-100/80 leading-relaxed font-medium">
                        {record.reason}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
};

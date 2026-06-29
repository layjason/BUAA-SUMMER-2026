/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

export interface ToastItem {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface ToastContainerProps {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}

export const ToastContainer: React.FC<ToastContainerProps> = ({ toasts, onDismiss }) => {
  return (
    <div
      id="toast-container"
      className="fixed top-5 right-5 z-[100] flex flex-col gap-3 max-w-sm w-full pointer-events-none"
    >
      {toasts.map((toast) => {
        let bgClass = 'bg-white border-slate-200 text-slate-800';
        let icon = <Info className="h-5 w-5 text-blue-500 shrink-0" />;

        if (toast.type === 'success') {
          bgClass = 'bg-emerald-50 border-emerald-200 text-emerald-900';
          icon = <CheckCircle2 className="h-5 w-5 text-emerald-500 shrink-0" />;
        } else if (toast.type === 'error') {
          bgClass = 'bg-red-50 border-red-200 text-red-900';
          icon = <AlertCircle className="h-5 w-5 text-red-500 shrink-0" />;
        }

        return (
          <div
            key={toast.id}
            className={`flex items-start gap-3 p-4 rounded-xl border shadow-lg pointer-events-auto animate-fade-in transition-all ${bgClass}`}
          >
            {icon}
            <div className="flex-1 text-sm font-medium leading-relaxed">{toast.message}</div>
            <button
              onClick={() => onDismiss(toast.id)}
              className="text-slate-400 hover:text-slate-600 focus:outline-none shrink-0 cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
};

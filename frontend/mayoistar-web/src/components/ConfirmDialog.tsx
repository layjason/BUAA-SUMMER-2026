/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { AlertTriangle, X } from 'lucide-react';

interface ConfirmDialogProps {
  id?: string;
  isOpen: boolean;
  title: string;
  message: string;
  requireReason?: boolean;
  reasonPlaceholder?: string;
  confirmText?: string;
  cancelText?: string;
  showBanUntil?: boolean; // For Ban actions, need duration limit
  onConfirm: (reason?: string, bannedUntil?: string) => void;
  onCancel: () => void;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  id,
  isOpen,
  title,
  message,
  requireReason = false,
  reasonPlaceholder = '请输入处理原因/审批意见...',
  confirmText = '确认执行',
  cancelText = '取消',
  showBanUntil = false,
  onConfirm,
  onCancel,
}) => {
  const [reason, setReason] = useState('');
  const [bannedUntil, setBannedUntil] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setReason('');
      // Default to 7 days from now for ban duration
      const defaultDate = new Date();
      defaultDate.setDate(defaultDate.getDate() + 7);
      const isoStr = defaultDate.toISOString().slice(0, 16); // format to yyyy-MM-ddThh:mm
      setBannedUntil(isoStr);
      setError('');
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (requireReason && !reason.trim()) {
      setError('处理说明/原因不能为空');
      return;
    }
    if (showBanUntil && !bannedUntil) {
      setError('请选择封禁截止时间');
      return;
    }
    onConfirm(reason.trim(), showBanUntil ? new Date(bannedUntil).toISOString() : undefined);
  };

  return (
    <div
      id={id}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in"
    >
      <div className="bg-white w-full max-w-md rounded-2xl border border-slate-100 shadow-2xl overflow-hidden animate-scale-up">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-500 shrink-0" />
            <h3 className="text-base font-semibold text-slate-800">{title}</h3>
          </div>
          <button
            onClick={onCancel}
            className="text-slate-400 hover:text-slate-600 focus:outline-none cursor-pointer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Content */}
          <div className="px-6 py-4 space-y-4">
            <p className="text-sm text-slate-600 leading-relaxed">{message}</p>

            {/* Banned Until Datepicker */}
            {showBanUntil && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600 block">
                  封禁截止日期及时间：
                </label>
                <input
                  type="datetime-local"
                  required
                  value={bannedUntil}
                  onChange={(e) => setBannedUntil(e.target.value)}
                  className="w-full text-sm px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800"
                />
              </div>
            )}

            {/* Reason Textarea */}
            {requireReason && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600 block">
                  处理原由/审核批注：
                </label>
                <textarea
                  required
                  placeholder={reasonPlaceholder}
                  rows={4}
                  value={reason}
                  onChange={(e) => {
                    setReason(e.target.value);
                    if (e.target.value.trim()) setError('');
                  }}
                  className="w-full text-sm px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all text-slate-800 resize-none"
                />
              </div>
            )}

            {error && <p className="text-xs font-medium text-red-500 animate-pulse">{error}</p>}
          </div>

          {/* Footer actions */}
          <div className="bg-slate-50 px-6 py-4 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onCancel}
              className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 focus:outline-none cursor-pointer"
            >
              {cancelText}
            </button>
            <button
              type="submit"
              className={`px-5 py-2 text-sm font-semibold rounded-xl focus:outline-none shadow-sm cursor-pointer ${
                title.includes('驳回') ||
                title.includes('封禁') ||
                title.includes('停用') ||
                title.includes('下架')
                  ? 'bg-red-600 text-white hover:bg-red-700'
                  : 'bg-blue-600 text-white hover:bg-blue-700'
              }`}
            >
              {confirmText}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

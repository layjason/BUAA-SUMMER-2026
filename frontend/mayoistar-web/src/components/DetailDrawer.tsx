import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface DetailDrawerProps {
  id?: string;
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export const DetailDrawer: React.FC<DetailDrawerProps> = ({
  id,
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
}) => {
  // Prevent body scrolling when drawer is open
  useEffect(() => {
    if (isOpen) {
      document.body.classList.add('overflow-hidden');
    } else {
      document.body.classList.remove('overflow-hidden');
    }
    return () => {
      document.body.classList.remove('overflow-hidden');
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div id={id} className="fixed inset-0 z-40 flex justify-end">
      {/* Backdrop overlay */}
      <div
        onClick={onClose}
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity animate-fade-in"
      />

      {/* Drawer content block */}
      <div className="relative w-full max-w-xl bg-white h-full flex flex-col shadow-2xl border-l border-slate-200 animate-slide-left z-10 overflow-hidden">
        {/* Header section */}
        <div className="px-6 py-5 border-b border-slate-200/80 flex items-center justify-between bg-slate-50">
          <div className="text-left">
            <h3 className="text-base font-bold text-slate-800 tracking-tight">{title}</h3>
            {subtitle && (
              <p className="text-[11px] text-slate-400 font-semibold mt-0.5">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-all cursor-pointer focus:outline-none"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Scrollable content body */}
        <div className="flex-1 overflow-y-auto px-6 py-6 space-y-6">{children}</div>

        {/* Action footer block */}
        {footer && (
          <div className="px-6 py-4 border-t border-slate-200/80 bg-slate-50 flex items-center gap-3 justify-end shrink-0">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

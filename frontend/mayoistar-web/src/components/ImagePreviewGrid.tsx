import React, { useState } from 'react';
import { Eye, X, ZoomIn } from 'lucide-react';
import { AuthImage } from './AuthImage';

interface ImagePreviewGridProps {
  id?: string;
  images: string[];
  columns?: number;
}

export const ImagePreviewGrid: React.FC<ImagePreviewGridProps> = ({ id, images, columns = 2 }) => {
  const [activeImage, setActiveImage] = useState<string | null>(null);

  if (!images || images.length === 0) {
    return (
      <div
        id={id}
        className="text-center py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200"
      >
        <p className="text-xs text-slate-400 font-semibold">暂无营业执照或证明图片</p>
      </div>
    );
  }

  const gridColsClass =
    columns === 3 ? 'grid-cols-3' : columns === 4 ? 'grid-cols-4' : 'grid-cols-2';

  return (
    <div id={id} className="space-y-4 text-left">
      <div className={`grid gap-3 ${gridColsClass}`}>
        {images.map((url, idx) => (
          <div
            key={idx}
            onClick={() => setActiveImage(url)}
            className="group relative aspect-4/3 rounded-xl border border-slate-200 overflow-hidden bg-slate-100 cursor-pointer shadow-xs hover:shadow-md hover:border-blue-300 transition-all"
          >
            <AuthImage
              signedUrl={url}
              alt={`凭证_${idx + 1}`}
              className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-slate-900/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <span className="flex items-center gap-1.5 text-xs font-bold text-white bg-blue-600 px-3 py-1.5 rounded-full shadow-lg transform translate-y-2 group-hover:translate-y-0 transition-transform">
                <Eye className="h-3.5 w-3.5" />
                <span>放大预览</span>
              </span>
            </div>
          </div>
        ))}
      </div>

      {activeImage && (
        <div
          onClick={() => setActiveImage(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-xs p-4 animate-fade-in"
        >
          <button
            onClick={() => setActiveImage(null)}
            className="absolute top-5 right-5 p-2 rounded-full bg-white/10 hover:bg-white/20 text-white transition-all cursor-pointer focus:outline-none"
          >
            <X className="h-6 w-6" />
          </button>

          <div
            onClick={(e) => e.stopPropagation()}
            className="relative w-[min(92vw,56rem)] min-h-[min(70vh,32rem)] max-h-[85vh] flex flex-col overflow-hidden rounded-2xl shadow-2xl border border-white/10 animate-scale-up bg-slate-900"
          >
            <AuthImage
              signedUrl={activeImage}
              alt="证件大图"
              className="flex-1 object-contain w-full min-h-[min(65vh,28rem)] max-h-[78vh]"
            />
            <div className="bg-slate-900/90 text-white px-4 py-2.5 text-xs font-semibold flex items-center gap-2">
              <ZoomIn className="h-4 w-4 text-blue-400" />
              <span>双击或手势拉伸可进行高级细节审查</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

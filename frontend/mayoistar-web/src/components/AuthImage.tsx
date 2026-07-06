import React, { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { fetchMediaBlob, MediaFetchError } from '../api/media';
import { isMockMode } from '../api/client';

interface AuthImageProps {
  signedUrl?: string;
  alt: string;
  className?: string;
}

/**
 * 鉴权图片组件：私有 signedUrl 通过 fetch + Bearer 转为 blob URL 后展示。
 * Mock 模式或已是绝对 URL 时直接使用 src，避免多余请求。
 */
export const AuthImage: React.FC<AuthImageProps> = ({ signedUrl, alt, className }) => {
  const [displayUrl, setDisplayUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);

  useEffect(() => {
    if (!signedUrl) {
      setDisplayUrl(null);
      setFailed(false);
      setErrorStatus(null);
      return;
    }

    if (isMockMode() || signedUrl.startsWith('http')) {
      setDisplayUrl(signedUrl);
      setFailed(false);
      setErrorStatus(null);
      return;
    }

    let objectUrl: string | null = null;
    let cancelled = false;

    fetchMediaBlob(signedUrl)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setDisplayUrl(objectUrl);
        setFailed(false);
        setErrorStatus(null);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setDisplayUrl(null);
        setFailed(true);
        setErrorStatus(error instanceof MediaFetchError ? error.status : null);
      });

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [signedUrl]);

  if (!signedUrl) return null;

  if (failed) {
    return (
      <div
        className={`flex flex-col items-center justify-center gap-1.5 bg-slate-50 border border-dashed border-slate-200 text-slate-400 p-2 ${className ?? ''}`}
        role="img"
        aria-label={errorStatus ? `${alt}加载失败，HTTP ${errorStatus}` : `${alt}加载失败`}
      >
        <ImageOff className="h-5 w-5 shrink-0 opacity-60" />
        <span className="text-[10px] font-semibold text-center leading-tight">
          {errorStatus ? `加载失败 (${errorStatus})` : '图片加载失败'}
        </span>
      </div>
    );
  }

  if (!displayUrl) {
    return <div className={`animate-pulse bg-slate-100 ${className ?? ''}`} aria-hidden />;
  }

  return <img src={displayUrl} alt={alt} referrerPolicy="no-referrer" className={className} />;
};

import { getAccessToken, handleSessionExpired, parseErrorBody } from './client';

const apiBaseUrl = () => import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? '';

/** 媒体拉取失败，携带 HTTP 状态码供 UI 展示 */
export class MediaFetchError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'MediaFetchError';
    this.status = status;
  }
}

/**
 * 将后端返回的相对 signedUrl 解析为可 fetch 的绝对 URL。
 * 开发环境 VITE_API_BASE_URL 留空时保持相对路径，由 Vite 代理转发。
 */
export function resolveMediaUrl(path: string): string {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  return `${apiBaseUrl()}${path}`;
}

/**
 * 携带 Bearer Token 拉取私有媒体资源并返回 Blob。
 * HTTP 401 且曾携带 token 时与 API 请求共用会话失效处理。
 */
export async function fetchMediaBlob(url: string): Promise<Blob> {
  const resolved = resolveMediaUrl(url);
  const headers = new Headers();
  const token = getAccessToken();
  const hadToken = !!token;
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolved, { headers });
  if (!response.ok) {
    const { message } = await parseErrorBody(response);

    if (response.status === 401 && hadToken) {
      const errMsg = handleSessionExpired(message);
      throw new MediaFetchError(401, errMsg);
    }

    throw new MediaFetchError(
      response.status,
      message || `Media fetch failed: HTTP ${response.status}`,
    );
  }

  return response.blob();
}

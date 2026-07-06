import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('媒体 URL 工具', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
      clear: () => store.clear(),
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('resolveMediaUrl 在 API 基址为空时保留相对路径', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    const { resolveMediaUrl } = await import('./media');
    expect(resolveMediaUrl('/media/abc?sig=1')).toBe('/media/abc?sig=1');
  });

  it('resolveMediaUrl 拼接 API 基址', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test');
    const { resolveMediaUrl } = await import('./media');
    expect(resolveMediaUrl('/media/abc')).toBe('https://api.example.test/media/abc');
  });

  it('fetchMediaBlob 在 HTTP 401 时触发会话失效处理', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 401, message: 'Authentication is required', data: {} }), {
        headers: { 'Content-Type': 'application/json' },
        status: 401,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const unauthorized = vi.fn();
    const { setAccessToken, registerUnauthorizedHandler } = await import('./client');
    const { fetchMediaBlob, MediaFetchError } = await import('./media');
    registerUnauthorizedHandler(unauthorized);
    setAccessToken('expired-token');

    try {
      await fetchMediaBlob('/media/license-1');
      expect.unreachable('should have thrown');
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(MediaFetchError);
      const mediaError = error as InstanceType<typeof MediaFetchError>;
      expect(mediaError.status).toBe(401);
      expect(mediaError.message).toBe('登录已过期，请重新登录');
    }

    expect(unauthorized).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('mayoistar_admin_access_token')).toBeNull();
  });

  it('fetchMediaBlob 携带 Bearer Token', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Blob(['image']), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const { setAccessToken } = await import('./client');
    const { fetchMediaBlob } = await import('./media');
    setAccessToken('media-token');

    const blob = await fetchMediaBlob('/media/license-1');
    expect(blob).toBeInstanceOf(Blob);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer media-token');
  });
});

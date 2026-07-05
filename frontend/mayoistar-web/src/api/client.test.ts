import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminUsersPage } from '../types';

const adminUsersPage = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 10,
  totalPages: 1,
} satisfies AdminUsersPage;

const successResponse = (data: unknown) =>
  new Response(JSON.stringify({ code: 200, message: 'For Super Earth!', data }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });

const businessErrorResponse = (code: number, message: string) =>
  new Response(JSON.stringify({ code, message, data: {} }), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });

describe('API 请求客户端', () => {
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

  it('使用 VITE_API_BASE_URL 拼接相对路径请求并携带 Bearer Token', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test');
    const fetchMock = vi.fn().mockResolvedValue(successResponse(adminUsersPage));
    vi.stubGlobal('fetch', fetchMock);

    const { request, setAccessToken } = await import('./client');
    setAccessToken('access-token');

    await expect(request<AdminUsersPage>('/admin/users')).resolves.toEqual(adminUsersPage);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('https://api.example.test/admin/users');
    const headers = init.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer access-token');
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('绝对 URL 请求不追加 API 基础地址', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test');
    const fetchMock = vi.fn().mockResolvedValue(successResponse(adminUsersPage));
    vi.stubGlobal('fetch', fetchMock);

    const { request } = await import('./client');

    await request('https://other.example.test/health');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('https://other.example.test/health');
  });

  it('仅将契约规定的 code 200 视为业务成功', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(businessErrorResponse(0, 'Legacy success code is invalid'));
    vi.stubGlobal('fetch', fetchMock);

    const { request } = await import('./client');

    await expect(request('/admin/users')).rejects.toThrow('Legacy success code is invalid');
  });

  it('业务错误抛出 BusinessError 并保留 code 和 message', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(businessErrorResponse(10006, 'Activation token is invalid'));
    vi.stubGlobal('fetch', fetchMock);

    const { request, BusinessError } = await import('./client');

    try {
      await request('/identity/auth/activate');
      expect.fail('Expected BusinessError to be thrown');
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(BusinessError);
      const bizError = error as InstanceType<typeof BusinessError>;
      expect(bizError.code).toBe(10006);
      expect(bizError.message).toBe('Activation token is invalid');
    }
  });

  it('已登录时 HTTP 401 清除 token 并触发未授权回调', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 401, message: 'Authentication is required', data: {} }), {
        headers: { 'Content-Type': 'application/json' },
        status: 401,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const unauthorized = vi.fn();
    const { request, setAccessToken, registerUnauthorizedHandler } = await import('./client');
    registerUnauthorizedHandler(unauthorized);
    setAccessToken('expired-token');

    await expect(request('/admin/users')).rejects.toThrow('登录已过期，请重新登录');
    expect(unauthorized).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('mayoistar_admin_access_token')).toBeNull();
  });

  it('后台业务错误 toast 使用中文映射', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(businessErrorResponse(60006, 'Review reason is required'));
    vi.stubGlobal('fetch', fetchMock);

    const { request, BusinessError } = await import('./client');

    try {
      await request('/admin/activities/act_1/review');
      expect.unreachable('should have thrown');
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(BusinessError);
      const bizErr = error as InstanceType<typeof BusinessError>;
      expect(bizErr.code).toBe(60006);
      expect(bizErr.message).toBe('请填写审核、下架或停用原因');
    }
  });

  it('未登录时 HTTP 401 不触发未授权回调', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 401, message: 'Authentication is required', data: {} }), {
        headers: { 'Content-Type': 'application/json' },
        status: 401,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const unauthorized = vi.fn();
    const { request, registerUnauthorizedHandler } = await import('./client');
    registerUnauthorizedHandler(unauthorized);

    await expect(request('/admin/auth/login')).rejects.toThrow('Authentication is required');
    expect(unauthorized).not.toHaveBeenCalled();
  });

  it('业务错误携带 BusinessError 类型并保留 code 和 message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(businessErrorResponse(10018, '发送过于频繁'));
    vi.stubGlobal('fetch', fetchMock);

    const { request, BusinessError } = await import('./client');

    try {
      await request('/identity/auth/password-reset-email');
      expect.unreachable('should have thrown');
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(BusinessError);
      const bizErr = error as InstanceType<typeof BusinessError>;
      expect(bizErr.code).toBe(10018);
      expect(bizErr.message).toBe('发送过于频繁');
    }
  });
});

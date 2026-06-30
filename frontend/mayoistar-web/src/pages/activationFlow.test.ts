import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockActivateAccount = vi.fn();

vi.mock('../api/identityAuth', () => ({
  activateAccount: (...args: unknown[]) => mockActivateAccount(...args),
}));

describe('consumeActivationTokenOnce', () => {
  beforeEach(() => {
    vi.resetModules();
    mockActivateAccount.mockReset();
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
      clear: () => store.clear(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('激活成功时返回 success', async () => {
    mockActivateAccount.mockResolvedValue(undefined);
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    const result = await consumeActivationTokenOnce('valid-token');

    expect(result).toBe('success');
    expect(mockActivateAccount).toHaveBeenCalledWith('valid-token');
  });

  it('同一 token 调用两次只发起一次请求', async () => {
    mockActivateAccount.mockResolvedValue(undefined);
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    const first = consumeActivationTokenOnce('same-token');
    const second = consumeActivationTokenOnce('same-token');

    const [r1, r2] = await Promise.all([first, second]);

    expect(r1).toBe('success');
    expect(r2).toBe('success');
    expect(mockActivateAccount).toHaveBeenCalledTimes(1);
  });

  it('不同 token 分别发起请求', async () => {
    mockActivateAccount.mockResolvedValue(undefined);
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    await consumeActivationTokenOnce('token-a');
    await consumeActivationTokenOnce('token-b');

    expect(mockActivateAccount).toHaveBeenCalledTimes(2);
  });

  it('业务错误码 10012（已激活）视为 success', async () => {
    const { BusinessError } = await import('../api/client');
    mockActivateAccount.mockRejectedValue(new BusinessError(10012, 'Account is already active'));
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    const result = await consumeActivationTokenOnce('already-active-token');

    expect(result).toBe('success');
  });

  it('其它 BusinessError 返回 fallback', async () => {
    const { BusinessError } = await import('../api/client');
    mockActivateAccount.mockRejectedValue(new BusinessError(10006, 'Activation token is invalid'));
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    const result = await consumeActivationTokenOnce('invalid-token');

    expect(result).toBe('fallback');
  });

  it('非 BusinessError 的网络错误返回 fallback', async () => {
    mockActivateAccount.mockRejectedValue(new Error('Network failure'));
    const { consumeActivationTokenOnce } = await import('./activationFlow');

    const result = await consumeActivationTokenOnce('error-token');

    expect(result).toBe('fallback');
  });
});

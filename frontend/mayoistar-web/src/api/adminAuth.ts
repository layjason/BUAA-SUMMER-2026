import { request, isMockMode, simulateLatency, setAccessToken, showToast } from './client';
import { mockDb } from './mockDb';
import { AdminLoginRequest, AdminChangePasswordRequest, LoginResult } from '../types';

export async function login(payload: AdminLoginRequest): Promise<LoginResult> {
  if (isMockMode()) {
    await simulateLatency(300);
    const storedHash = mockDb.getPasswordHash();
    if (payload.username === 'admin' && payload.password === storedHash) {
      const mockResult: LoginResult = {
        userId: 'admin_001',
        kind: 'admin',
        accountStatus: 'active',
        tokens: {
          accessToken: 'mock_jwt_token_admin_001',
          refreshToken: 'mock_refresh_token_admin_001',
          expiresAt: new Date(Date.now() + 86400000).toISOString(),
        },
      };
      setAccessToken(mockResult.tokens.accessToken);
      showToast('登录成功！欢迎使用趣聚管理系统', 'success');
      return mockResult;
    } else {
      showToast('管理员账号或密码错误，请检查！', 'error');
      throw new Error('认证失败');
    }
  }

  const result = await request<LoginResult>('/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  setAccessToken(result.tokens.accessToken);
  return result;
}

export async function changePassword(payload: AdminChangePasswordRequest): Promise<void> {
  if (isMockMode()) {
    await simulateLatency(250);
    const storedHash = mockDb.getPasswordHash();
    if (payload.oldPassword === storedHash) {
      mockDb.updatePassword(payload.newPassword);
      showToast('管理员密码修改成功！', 'success');
      return;
    } else {
      showToast('旧密码输入错误！', 'error');
      throw new Error('旧密码错误');
    }
  }

  return request<void>('/admin/auth/password', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function logout(): void {
  setAccessToken('');
  showToast('账号已成功登出', 'info');
}

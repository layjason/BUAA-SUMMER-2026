import { request, isMockMode, simulateLatency } from './client';
import type { components } from './generated/openapi';

/** 激活账号请求体，与 TypeSpec Identity.AccountActivationRequest 对齐 */
type AccountActivationRequest = components['schemas']['Identity.AccountActivationRequest'];

/** 密码重置邮件请求体，与 TypeSpec Identity.PasswordResetEmailRequest 对齐 */
type PasswordResetEmailRequest = components['schemas']['Identity.PasswordResetEmailRequest'];

/** 重置密码请求体，与 TypeSpec Identity.ResetPasswordRequest 对齐 */
type ResetPasswordRequest = components['schemas']['Identity.ResetPasswordRequest'];

/**
 * 调用 POST /identity/auth/activate 激活账号。
 *
 * 前置条件：token 为激活邮件中的一次性激活 token，非空。
 * 后置条件：成功时返回空数据；业务错误（如 10006 token 无效、10012 已激活）时抛出 BusinessError。
 * 不变量：不修改本地存储或认证状态，只发送一次 POST 请求。
 */
export async function activateAccount(token: string): Promise<void> {
  if (isMockMode()) {
    await simulateLatency(250);
    return;
  }

  const body: AccountActivationRequest = { token };
  await request<void>('/identity/auth/activate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/**
 * 调用 POST /identity/auth/password-reset-email 发送密码重置邮件。
 *
 * 前置条件：email 为需要重置密码的账号邮箱，格式合法。
 * 后置条件：成功时返回空数据；频率限制（10018）时抛出 BusinessError。
 * 不变量：不修改本地存储或认证状态，接口不暴露邮箱是否存在。
 */
export async function sendPasswordResetEmail(email: string): Promise<void> {
  if (isMockMode()) {
    await simulateLatency(250);
    return;
  }

  const body: PasswordResetEmailRequest = { email };
  await request<void>('/identity/auth/password-reset-email', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/**
 * 调用 POST /identity/auth/password-reset 通过邮件 token 重置密码。
 *
 * 前置条件：token 为密码重置邮件中的一次性 token，newPassword 满足密码强度要求。
 * 后置条件：成功时返回空数据，旧登录凭据失效；token 无效（10017）时抛出 BusinessError。
 * 不变量：不修改本地存储或认证状态，只发送一次 POST 请求。
 */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  if (isMockMode()) {
    await simulateLatency(250);
    return;
  }

  const body: ResetPasswordRequest = { token, newPassword };
  await request<void>('/identity/auth/password-reset', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

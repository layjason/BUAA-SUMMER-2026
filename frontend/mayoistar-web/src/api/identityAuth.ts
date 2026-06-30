import { request, isMockMode, simulateLatency } from './client';
import type { components } from './generated/openapi';

/** 激活账号请求体，与 TypeSpec Identity.AccountActivationRequest 对齐 */
type AccountActivationRequest = components['schemas']['Identity.AccountActivationRequest'];

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

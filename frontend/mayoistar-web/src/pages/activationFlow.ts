import { activateAccount } from '../api/identityAuth';
import { BusinessError } from '../api/client';

/** 页面渲染状态：加载中、激活成功、需要前往 APP 处理 */
export type ActivationState = 'loading' | 'success' | 'fallback';

/** 账号已激活的业务错误码，与 TypeSpec Errors.Identity.AccountAlreadyActive 对齐 */
const ACCOUNT_ALREADY_ACTIVE_CODE = 10012;

/** 同一页面运行周期内的激活请求缓存，防止 React StrictMode 重复挂载导致 token 被消费两次 */
const activationAttemptCache = new Map<string, Promise<ActivationState>>();

/**
 * 消费激活 token，并在同一页面运行周期内复用同一 token 的请求结果。
 *
 * 前置条件：token 为 URL 中解析出的非空激活 token。
 * 后置条件：同一 token 只会发起一次激活请求，重复调用复用首次请求结果。
 * 不变量：不修改本地认证状态，只缓存本页面运行周期内的激活请求 Promise。
 */
export function consumeActivationTokenOnce(token: string): Promise<ActivationState> {
  const cached = activationAttemptCache.get(token);
  if (cached) return cached;

  const attempt = activateAccount(token)
    .then((): ActivationState => 'success')
    .catch((error: unknown): ActivationState => {
      if (error instanceof BusinessError && error.code === ACCOUNT_ALREADY_ACTIVE_CODE) {
        return 'success';
      }
      return 'fallback';
    });

  activationAttemptCache.set(token, attempt);
  return attempt;
}

/**
 * 清空激活请求缓存，仅用于单元测试重置状态。
 *
 * 前置条件：无。
 * 后置条件：缓存被清空，后续调用 consumeActivationTokenOnce 会重新发起请求。
 * 不变量：不影响正在执行中的 Promise。
 */
export function clearActivationAttemptCache(): void {
  activationAttemptCache.clear();
}

/** 趣聚 APP 激活页深度链接基础路径，遵循 UniApp scheme 页面路径规范 */
const APP_ACTIVATION_BASE = 'mayoistar://pages/activate/index';

/** 趣聚 APP 登录页深度链接，激活成功后引导用户前往登录 */
export const APP_LOGIN_URL = 'mayoistar://pages/login/index';

/**
 * 从 URL 查询字符串中提取激活 token。
 *
 * 前置条件：search 为 window.location.search 格式的查询字符串（可含 ? 前缀）。
 * 后置条件：存在非空 token 参数时返回该字符串，否则返回 null。
 * 不变量：不修改 URL 或浏览器状态，只做纯字符串解析。
 */
export function readActivationToken(search: string): string | null {
  const params = new URLSearchParams(search);
  const token = params.get('token');
  return token && token.trim().length > 0 ? token.trim() : null;
}

/**
 * 构造趣聚 APP 激活深度链接。
 *
 * 前置条件：token 为有效激活 token 或 null。
 * 后置条件：token 非空时返回带 token 查询参数的深度链接；token 为 null 时返回基础深度链接。
 * 不变量：不对 token 做网络校验，只执行字符串拼接与 URL 编码。
 */
export function buildAppActivationUrl(token: string | null): string {
  if (!token) {
    return APP_ACTIVATION_BASE;
  }
  return `${APP_ACTIVATION_BASE}?token=${encodeURIComponent(token)}`;
}

/** 趣聚 APP 深度链接基础路径，遵循 UniApp scheme 页面路径规范 */
const APP_BASE = 'mayoistar://pages';

/** 密码最短长度要求 */
export const MIN_PASSWORD_LENGTH = 8;

/**
 * 从 URL 查询字符串中提取密码重置 token。
 *
 * 前置条件：search 为 window.location.search 格式的查询字符串（可含 ? 前缀）。
 * 后置条件：存在非空 token 参数时返回该字符串，否则返回 null。
 * 不变量：不修改 URL 或浏览器状态，只做纯字符串解析。
 */
export function readResetToken(search: string): string | null {
  const params = new URLSearchParams(search);
  const token = params.get('token');
  return token && token.trim().length > 0 ? token.trim() : null;
}

/**
 * 构造趣聚 APP 深度链接。
 *
 * 前置条件：page 为 forgot-password 或 reset-password，token 可选。
 * 后置条件：返回符合 UniApp 页面路径规范的深度链接，token 非空时附带查询参数。
 * 不变量：不对 token 做网络校验，只执行字符串拼接与 URL 编码。
 */
export function buildAppUrl(
  page: 'forgot-password' | 'reset-password',
  token?: string | null,
): string {
  const base = `${APP_BASE}/${page}/index`;
  if (token) {
    return `${base}?token=${encodeURIComponent(token)}`;
  }
  return base;
}

/**
 * 校验邮箱格式是否合法。
 *
 * 前置条件：email 为用户输入的邮箱字符串。
 * 后置条件：符合基本邮箱格式时返回 true，否则返回 false。
 * 不变量：只做正则匹配，不发送网络请求验证邮箱是否存在。
 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

/**
 * 校验密码是否满足最低强度要求。
 *
 * 前置条件：password 为用户输入的新密码字符串。
 * 后置条件：长度不低于 MIN_PASSWORD_LENGTH 时返回 true，否则返回 false。
 * 不变量：只做长度校验，不修改密码内容。
 */
export function isValidPassword(password: string): boolean {
  return password.length >= MIN_PASSWORD_LENGTH;
}

/**
 * 认证 API 模块
 *
 * 封装登录、注册、令牌刷新、密码重置等身份认证相关接口。
 */
import { post } from '@/api/request'

/** 用户登录，返回访问令牌与刷新令牌 */
export function login(email: string, password: string) {
  return post('/identity/auth/login', {
    body: { email, password },
  })
}

/** 注册个人用户账号 */
export function registerPersonal(email: string, password: string, nickname: string) {
  return post('/identity/auth/register/personal', {
    body: { email, password, nickname },
  })
}

/** 注册商户账号 */
export function registerMerchant(email: string, password: string, nickname: string) {
  return post('/identity/auth/register/merchant', {
    body: { email, password, nickname },
  })
}

/** 退出登录，撤销当前会话令牌 */
export function logout() {
  return post('/identity/auth/logout')
}

/** 使用刷新令牌换取新的访问令牌 */
export function refreshToken(refreshToken: string) {
  return post('/identity/auth/refresh', {
    body: { refreshToken },
  })
}

/** 通过激活令牌激活用户账号 */
export function activate(token: string) {
  return post('/identity/auth/activate', {
    body: { token },
  })
}

/** 重新发送账号激活邮件 */
export function resendActivationEmail(email: string) {
  return post('/identity/auth/activation-email', {
    body: { email },
  })
}

/** 请求密码重置邮件 */
export function requestPasswordResetEmail(email: string) {
  return post('/identity/auth/password-reset-email', {
    body: { email },
  })
}

/** 通过重置令牌设置新密码 */
export function resetPassword(token: string, newPassword: string) {
  return post('/identity/auth/password-reset', {
    body: { token, newPassword },
  })
}

/** 修改当前登录用户密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return post('/identity/me/password', {
    body: { oldPassword, newPassword },
  })
}

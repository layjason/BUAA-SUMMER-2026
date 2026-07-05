/** 后台业务错误码中文映射，与 api-spec Errors.Admin 对齐 */
const ADMIN_ERROR_MESSAGES: Record<number, string> = {
  60000: '管理员账号或密码错误，请检查后重试',
  60001: '旧密码输入错误，请核对后重试',
  60002: '目标用户不存在',
  60003: '该用户已处于封禁状态',
  60004: '该用户未处于封禁状态',
  60005: '商家审核状态不允许当前操作',
  60006: '请填写审核、下架或停用原因',
  60007: '举报记录不存在',
  60008: '活动不存在',
  60009: '活动当前状态不允许该操作',
  60010: '小队不存在',
  60011: '小队当前状态不允许该操作',
};

const SESSION_EXPIRED_MESSAGE = '登录已过期，请重新登录';

/**
 * 将后台业务错误码映射为中文提示；未知码回退到服务端 message 或通用文案。
 */
export function getAdminErrorMessage(code: number, fallback?: string): string {
  return ADMIN_ERROR_MESSAGES[code] ?? fallback ?? '操作失败，请稍后重试';
}

/**
 * 将 HTTP 401 英文提示统一为中文；其余 message 原样返回。
 */
export function resolveUnauthorizedMessage(message?: string): string {
  if (!message || message === 'Authentication is required') {
    return SESSION_EXPIRED_MESSAGE;
  }
  return message;
}

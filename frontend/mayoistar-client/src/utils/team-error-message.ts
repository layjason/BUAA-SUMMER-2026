/**
 * 小队相关业务错误码中文提示
 */
const TEAM_ERROR_MESSAGES: Record<number, string> = {
  40001: '存在黑名单关系，无法执行此操作',
  40009: '小队不存在或不可见',
  40010: '小队人数已满',
  40011: '小队已解散或已停用',
  40012: '你已是该小队成员',
  40016: '队长不能直接退出，请先解散小队',
  40020: '没有小队管理权限',
}

/**
 * 将小队业务错误码转为用户可读文案
 */
export function getTeamErrorMessage(code: number, fallback?: string): string {
  return TEAM_ERROR_MESSAGES[code] ?? fallback ?? '操作失败，请稍后重试'
}

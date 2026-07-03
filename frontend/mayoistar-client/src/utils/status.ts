/**
 * 活动运行时状态工具
 *
 * 集中管理运行时状态的 i18n key 映射，避免在多页面重复定义。
 */

/** 运行时状态 i18n key 映射 */
export const RUNTIME_STATUS_KEYS: Record<string, string> = {
  notStarted: 'myActivities.statusNotStarted',
  registering: 'myActivities.statusRegistering',
  registrationClosed: 'myActivities.statusRegistrationClosed',
  ongoing: 'myActivities.statusOngoing',
  ended: 'myActivities.statusEnded',
  takenDown: 'myActivities.statusTakenDown',
}

/**
 * 获取运行时状态的展示文本
 *
 * @param status 运行时状态值
 * @param t i18n 翻译函数
 * @returns 本地化展示文本
 */
export function runtimeStatusText(status: string, t: (key: string) => string): string {
  const key = RUNTIME_STATUS_KEYS[status]
  return key ? t(key) : status
}

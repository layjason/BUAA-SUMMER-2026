/**
 * 日期与时间处理工具
 *
 * 所有日期时间格式遵循 api-convention.md 规范：
 * - 日期时间：ISO 8601，如 2023-10-05T14:30:00Z
 * - 日期：ISO 8601，如 2023-10-05
 * - 时间：从 00:00:00 开始的秒数
 */

/** 一天秒数 */
const SECONDS_PER_DAY = 86400
/** 一小时秒数 */
const SECONDS_PER_HOUR = 3600
/** 一分钟秒数 */
const SECONDS_PER_MINUTE = 60

/**
 * 将 ISO 8601 日期时间字符串格式化为本地展示文本
 *
 * 前置条件：isoString 为有效的 ISO 8601 日期时间字符串
 * 后置条件：返回格式为 YYYY-MM-DD HH:mm 的本地时间字符串
 *
 * @param isoString ISO 8601 日期时间
 * @returns 本地展示文本
 */
export function formatDateTime(isoString: string): string {
  const date = new Date(isoString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

/**
 * 将 ISO 8601 日期字符串格式化为本地展示文本
 *
 * 前置条件：isoDate 为有效的 ISO 8601 日期字符串
 *
 * @param isoDate ISO 8601 日期
 * @returns 本地展示文本 YYYY-MM-DD
 */
export function formatDate(isoDate: string): string {
  const date = new Date(isoDate)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 将秒数格式化为 HH:mm 时间字符串
 *
 * 前置条件：seconds 为从 00:00:00 开始的秒数，范围 [0, 86399]
 *
 * @param seconds 秒数
 * @returns HH:mm 格式
 */
export function formatTimeOfDay(seconds: number): string {
  const hours = Math.floor(seconds / SECONDS_PER_HOUR)
  const minutes = Math.floor((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

/**
 * 将时间范围表示为可读文本（起始时间～结束时间）
 *
 * @param startAt 开始时间 ISO 8601
 * @param endAt 结束时间 ISO 8601
 */
export function formatTimeRange(startAt: string, endAt: string): string {
  return `${formatDateTime(startAt)} ~ ${formatDateTime(endAt)}`
}

/**
 * 计算相对时间描述
 *
 * @param isoString ISO 8601 日期时间
 * @returns 相对时间，如 "3分钟前"、"2小时前"、"昨天"、"3天前"
 */
export function relativeTime(isoString: string): string {
  const now = Date.now()
  const target = new Date(isoString).getTime()
  const diffMs = now - target

  if (diffMs < 0) {
    return '即将开始'
  }

  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / SECONDS_PER_MINUTE)
  const diffHours = Math.floor(diffSeconds / SECONDS_PER_HOUR)
  const diffDays = Math.floor(diffSeconds / SECONDS_PER_DAY)

  if (diffSeconds < SECONDS_PER_MINUTE) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return formatDate(isoString)
}

/**
 * 活动搜索筛选辅助
 *
 * 将搜索页轻量筛选 UI 的选中项转换为 OpenAPI query 参数。
 */

export type CityFilter = '北京' | '上海' | '广州'
export type FeeFilter = 'free' | 'paid'
export type TimeFilter = 'today' | 'week' | 'month'
export type DistanceFilter = 1000 | 3000 | 5000 | 10000

export interface SearchFilterSelection {
  activityTypes: string[]
  city: CityFilter | null
  fee: FeeFilter | null
  time: TimeFilter | null
  distanceMeters: DistanceFilter | null
  location: { longitude: number; latitude: number } | null
}

export interface SearchActivitiesQuery {
  keyword?: string
  page: number
  pageSize: number
  activityTypes?: string[]
  city?: string
  startAtFrom?: string
  startAtTo?: string
  minFee?: number
  maxFee?: number
  longitude?: number
  latitude?: number
  distanceMeters?: number
}

const UTC8_OFFSET_MS = 8 * 60 * 60 * 1000

/**
 * 判断是否存在可执行的筛选条件
 *
 * 前置条件：selection 已初始化
 * 后置条件：任一筛选项非空时返回 true
 */
export function hasSearchFilters(selection: SearchFilterSelection): boolean {
  return !!(
    selection.activityTypes.length ||
    selection.city ||
    selection.fee ||
    selection.time ||
    selection.distanceMeters
  )
}

/**
 * 将时间戳格式化为 UTC+8 ISO 字符串
 *
 * 前置条件：timestampMs 为有效 Unix 毫秒时间戳
 * 后置条件：返回 YYYY-MM-DDTHH:mm:ss+08:00 格式
 */
function formatTimestampOffset8(timestampMs: number): string {
  const local = new Date(timestampMs + UTC8_OFFSET_MS)
  const iso = local.toISOString().slice(0, 19)
  return `${iso}+08:00`
}

/**
 * 将 UTC+8 墙钟时间转换为真实时间戳
 *
 * 前置条件：year/month/day/hour/minute/second/millisecond 组成合法日期时间。
 * 后置条件：返回该 UTC+8 本地时间对应的 Unix 毫秒时间戳。
 * 不变量：计算不依赖宿主运行时区。
 */
function offset8WallTimeToTimestamp(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second: number,
  millisecond: number,
): number {
  return Date.UTC(year, month, day, hour, minute, second, millisecond) - UTC8_OFFSET_MS
}

/**
 * 获取时间筛选对应的起止时间
 *
 * 前置条件：preset 为 today/week/month
 * 后置条件：返回覆盖该时间范围的 ISO 字符串
 * 不变量：始终按 UTC+8 自然日/周/月计算，不受 CI 或设备运行时区影响。
 */
export function getTimeRange(
  preset: TimeFilter,
  now: Date = new Date(),
): { startAtFrom: string; startAtTo: string } {
  const offsetNow = new Date(now.getTime() + UTC8_OFFSET_MS)
  const year = offsetNow.getUTCFullYear()
  const month = offsetNow.getUTCMonth()
  const dayOfMonth = offsetNow.getUTCDate()

  let startDay = dayOfMonth
  let startMonth = month
  let startYear = year
  let endDay = dayOfMonth
  let endMonth = month
  let endYear = year

  if (preset === 'today') {
    startDay = dayOfMonth
    endDay = dayOfMonth
  } else if (preset === 'week') {
    const weekday = offsetNow.getUTCDay()
    const mondayOffset = weekday === 0 ? -6 : 1 - weekday
    const monday = new Date(Date.UTC(year, month, dayOfMonth + mondayOffset))
    const sunday = new Date(Date.UTC(year, month, dayOfMonth + mondayOffset + 6))
    startDay = monday.getUTCDate()
    startMonth = monday.getUTCMonth()
    startYear = monday.getUTCFullYear()
    endDay = sunday.getUTCDate()
    endMonth = sunday.getUTCMonth()
    endYear = sunday.getUTCFullYear()
  } else {
    startDay = 1
    const lastDayOfMonth = new Date(Date.UTC(year, month + 1, 0))
    endDay = lastDayOfMonth.getUTCDate()
  }

  const startTimestamp = offset8WallTimeToTimestamp(startYear, startMonth, startDay, 0, 0, 0, 0)
  const endTimestamp = offset8WallTimeToTimestamp(endYear, endMonth, endDay, 23, 59, 59, 999)
  return {
    startAtFrom: formatTimestampOffset8(startTimestamp),
    startAtTo: formatTimestampOffset8(endTimestamp),
  }
}

/**
 * 组装 searchActivities 请求参数
 *
 * 前置条件：page、pageSize 为正整数
 * 后置条件：仅包含已选中的筛选项对应字段
 */
export function buildSearchActivitiesQuery(
  keyword: string,
  selection: SearchFilterSelection,
  page: number,
  pageSize: number,
): SearchActivitiesQuery {
  const query: SearchActivitiesQuery = { page, pageSize }
  const trimmedKeyword = keyword.trim()
  if (trimmedKeyword) query.keyword = trimmedKeyword
  if (selection.activityTypes.length) query.activityTypes = [...selection.activityTypes]
  if (selection.city) query.city = selection.city
  if (selection.fee === 'free') {
    query.minFee = 0
    query.maxFee = 0
  } else if (selection.fee === 'paid') {
    query.minFee = 0.01
  }
  if (selection.time) {
    const range = getTimeRange(selection.time)
    query.startAtFrom = range.startAtFrom
    query.startAtTo = range.startAtTo
  }
  if (selection.distanceMeters && selection.location) {
    query.longitude = selection.location.longitude
    query.latitude = selection.location.latitude
    query.distanceMeters = selection.distanceMeters
  }
  return query
}

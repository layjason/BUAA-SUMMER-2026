/**
 * 活动搜索筛选辅助
 *
 * 将搜索页轻量筛选 UI 的选中项转换为 OpenAPI query 参数。
 */
import { normalizeCityForSearch } from '@/utils/city-name'

/** 搜索页手动选择的城市名（不含「市」后缀，发送请求前会规范化） */
export type CityFilter = string
export type FeeFilter = 'free' | 'paid'
export type TimeFilter = 'today' | 'week' | 'month'
export type DistanceFilter = 1000 | 3000 | 5000 | 10000

export interface SearchFilterSelection {
  activityTypes: string[]
  city: CityFilter | null
  /** 高德定位解析的当前城市，作为未手动选城市时的默认 query.city */
  detectedCity: string | null
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
 * 将日期格式化为 UTC+8 ISO 字符串
 *
 * 前置条件：date 为有效日期
 * 后置条件：返回 YYYY-MM-DDTHH:mm:ss+08:00 格式
 */
function formatDateTimeOffset8(date: Date): string {
  const offsetMs = 8 * 60 * 60 * 1000
  const local = new Date(date.getTime() + offsetMs)
  const iso = local.toISOString().slice(0, 19)
  return `${iso}+08:00`
}

/**
 * 获取时间筛选对应的起止时间
 *
 * 前置条件：preset 为 today/week/month
 * 后置条件：返回覆盖该时间范围的 ISO 字符串
 */
export function getTimeRange(
  preset: TimeFilter,
  now: Date = new Date(),
): { startAtFrom: string; startAtTo: string } {
  const start = new Date(now)
  const end = new Date(now)

  if (preset === 'today') {
    start.setHours(0, 0, 0, 0)
    end.setHours(23, 59, 59, 999)
  } else if (preset === 'week') {
    const day = start.getDay()
    const mondayOffset = day === 0 ? -6 : 1 - day
    start.setDate(start.getDate() + mondayOffset)
    start.setHours(0, 0, 0, 0)
    end.setTime(start.getTime())
    end.setDate(start.getDate() + 6)
    end.setHours(23, 59, 59, 999)
  } else {
    start.setDate(1)
    start.setHours(0, 0, 0, 0)
    end.setMonth(start.getMonth() + 1, 0)
    end.setHours(23, 59, 59, 999)
  }

  return {
    startAtFrom: formatDateTimeOffset8(start),
    startAtTo: formatDateTimeOffset8(end),
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
  const cityForQuery = selection.city ?? selection.detectedCity
  if (cityForQuery) query.city = normalizeCityForSearch(cityForQuery)
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

/**
 * 城市名称规范化
 *
 * 将高德等地图服务返回的城市字段转换为活动搜索接口使用的格式。
 */

/** 已带行政后缀的城市名不再追加「市」 */
const CITY_WITH_ADMIN_SUFFIX = /[省市自治区特别行政区]$/

/**
 * 将城市名规范为搜索 query 使用的格式（带「市」）
 *
 * 前置条件：city 来自筛选 chips（如「北京」）或高德逆地理编码（如「北京市」）
 * 后置条件：返回带「市」后缀的城市名，与地图选点写入活动的 city 字段一致
 *
 * @param city 原始城市名
 */
export function normalizeCityForSearch(city: string): string {
  const trimmed = city.trim()
  if (!trimmed) return ''
  if (CITY_WITH_ADMIN_SUFFIX.test(trimmed)) return trimmed
  return `${trimmed}市`
}

/**
 * 基于高德定位解析当前城市
 */
import { getCurrentLocation, reverseGeocode } from '@/services/amap'
import { normalizeCityForSearch } from '@/utils/city-name'

/**
 * 获取当前所在城市（用于活动搜索默认筛选）
 *
 * 前置条件：定位或 IP 近似定位可用
 * 后置条件：返回规范化后的城市名；完全失败时返回 null
 */
export async function resolveDetectedCity(): Promise<string | null> {
  const { latitude, longitude } = await getCurrentLocation()
  const geo = await reverseGeocode(latitude, longitude)
  const city = normalizeCityForSearch(geo.city)
  return city || null
}

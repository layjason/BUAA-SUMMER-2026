/**
 * 活动地点查看服务。
 *
 * 前置条件：活动地点来自 OpenAPI LocationInfo。
 * 后置条件：生成活动地点地图页路由。
 * 不变量：只处理页面跳转参数，不读取或修改业务状态。
 */
import type { components } from '@/api/types/schema'

type ActivityLocationInfo = components['schemas']['LocationInfo']

/** 可用于地图页展示的活动地点数据。 */
export interface ActivityLocationDisplay {
  title: string
  address: string
  city: string
  longitude: number
  latitude: number
}

/**
 * 将 OpenAPI 活动地点转为地图页展示数据。
 *
 * 前置条件：location.point 包含经纬度。
 * 后置条件：返回地图页所需的标题、地址、城市和坐标。
 * 不变量：缺省展示文案只影响 UI，不回写 activity。
 *
 * @param location 活动地点
 */
export function buildActivityLocationDisplay(
  location: ActivityLocationInfo,
): ActivityLocationDisplay {
  return {
    title: location.placeName || location.address || '活动地点',
    address: location.address || location.city || '活动地点',
    city: location.city || '',
    longitude: location.point.longitude,
    latitude: location.point.latitude,
  }
}

/**
 * 构造活动地点地图页路由。
 *
 * 前置条件：display 已由 buildActivityLocationDisplay 归一化。
 * 后置条件：返回 encode 后的 uni.navigateTo URL。
 * 不变量：不把未定义字段写入 query。
 *
 * @param display 活动地点展示数据
 */
export function buildActivityLocationMapRoute(display: ActivityLocationDisplay): string {
  const parts = [
    `latitude=${encodeURIComponent(String(display.latitude))}`,
    `longitude=${encodeURIComponent(String(display.longitude))}`,
    `title=${encodeURIComponent(display.title)}`,
    `address=${encodeURIComponent(display.address)}`,
    `city=${encodeURIComponent(display.city)}`,
  ]
  return `/pages/activity/location-map?${parts.join('&')}`
}

/**
 * 打开活动地点地图页。
 *
 * 前置条件：display 包含有效坐标。
 * 后置条件：优先打开内置地图页；路由失败时回退到系统地图。
 * 不变量：不会跳转浏览器或调用第三方 Web 链接。
 *
 * @param display 活动地点展示数据
 */
export function openActivityLocationMap(display: ActivityLocationDisplay): void {
  uni.navigateTo({
    url: buildActivityLocationMapRoute(display),
    fail: () => {
      uni.openLocation({
        latitude: display.latitude,
        longitude: display.longitude,
        name: display.title,
        address: display.address,
        scale: 16,
      })
    },
  })
}

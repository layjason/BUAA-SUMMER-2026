/**
 * 位置服务工具
 *
 * 封装 uni.getLocation，提供坐标获取、距离计算
 */

/** 地球半径（米） */
const EARTH_RADIUS_METERS = 6371000

/**
 * 定位信息
 */
export interface LocationInfo {
  latitude: number
  longitude: number
}

/**
 * 获取当前位置
 *
 * 前置条件：用户已授权定位权限
 * 后置条件：返回当前经纬度
 *
 * @returns 当前位置或 null（权限拒绝/超时）
 */
export function getCurrentLocation(): Promise<LocationInfo | null> {
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude,
        })
      },
      fail: () => {
        resolve(null)
      },
    })
  })
}

/**
 * 计算两点间距离（Haversine 公式）
 *
 * @param from 起点
 * @param to 终点
 * @returns 距离（米）
 */
export function distanceBetween(from: LocationInfo, to: LocationInfo): number {
  const dLat = ((to.latitude - from.latitude) * Math.PI) / 180
  const dLon = ((to.longitude - from.longitude) * Math.PI) / 180

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((from.latitude * Math.PI) / 180) *
      Math.cos((to.latitude * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2)

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return EARTH_RADIUS_METERS * c
}

/**
 * 格式化距离显示
 *
 * @param meters 距离（米）
 * @returns 可读距离，如 "500m"、"3.2km"
 */
export function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${Math.round(meters)}m`
  }
  return `${(meters / 1000).toFixed(1)}km`
}

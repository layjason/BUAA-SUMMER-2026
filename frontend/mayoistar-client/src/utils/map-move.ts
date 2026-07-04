/** 地图视口移动请求 */
export interface MapMoveRequest {
  latitude: number
  longitude: number
}

/** OpenAPI LocationInfo.point 坐标结构 */
export interface GeoPoint {
  longitude: number
  latitude: number
}

/**
 * 判断坐标是否可用于地图视口移动
 *
 * 前置条件：调用方传入待校验的经纬度数值。
 * 后置条件：仅当经纬度均为有限数且落在地图坐标合法范围内时返回 true。
 * 不变量：函数不修改入参，不依赖外部状态。
 *
 * @param latitude 纬度
 * @param longitude 经度
 * @returns 坐标是否合法
 */
export function isValidMapCoordinate(latitude: number, longitude: number): boolean {
  return (
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    longitude >= -180 &&
    longitude <= 180
  )
}

/**
 * 创建地图视口移动请求
 *
 * 前置条件：调用方传入从定位、选点或搜索结果得到的经纬度。
 * 后置条件：合法坐标返回可直接传给 mapContext.moveToLocation 的请求；非法坐标返回 null。
 * 不变量：返回对象的经纬度与入参保持一致，不进行坐标系转换。
 *
 * @param latitude 纬度
 * @param longitude 经度
 * @returns 地图移动请求，非法坐标返回 null
 */
export function createMapMoveRequest(latitude: number, longitude: number): MapMoveRequest | null {
  if (!isValidMapCoordinate(latitude, longitude)) {
    return null
  }

  return { latitude, longitude }
}

/**
 * 标准化 OpenAPI GeoPoint 坐标。
 *
 * 前置条件：调用方传入疑似 longitude/latitude 的两个数字。
 * 后置条件：合法时返回 { longitude, latitude }；若发现经纬度明显传反，则自动纠正；无法判断为合法坐标时返回 null。
 * 不变量：不做坐标系转换，仅校正字段顺序。
 *
 * @param longitude 经度候选值
 * @param latitude 纬度候选值
 * @returns 标准 OpenAPI GeoPoint，非法坐标返回 null
 */
export function normalizeGeoPoint(longitude: number, latitude: number): GeoPoint | null {
  if (isValidMapCoordinate(latitude, longitude)) {
    return { longitude, latitude }
  }
  if (isValidMapCoordinate(longitude, latitude)) {
    return { longitude: latitude, latitude: longitude }
  }
  return null
}

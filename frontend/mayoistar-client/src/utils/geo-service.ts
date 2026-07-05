/**
 * 高德地图 Web API 服务
 *
 * 封装 POI 搜索、地理编码、逆地理编码等 REST API 调用。
 * 调用失败时自动降级为静态预设数据，确保 mock 模式下不依赖外网。
 *
 * 前置条件：AMAP_WEB_API_KEY 为有效的高德 Web API Key
 * 后置条件：所有接口均返回标准化的 POIItem / ReverseGeocodeResult 结构
 * 不变量：调用失败时降级到预设数据，不抛出异常
 */
import { MOCK_ACTIVITY_LOCATIONS } from '@/config/mock-locations'
import type { MockLocationOption } from '@/config/mock-locations'

/** 高德 Web API Key（与 manifest.json maps.amap.key 保持一致） */
export const AMAP_WEB_API_KEY = 'c0d8c161adaf0d0e378a1c4a9ab3198a'

/** 高德 REST API 基础 URL */
const AMAP_REST_BASE = 'https://restapi.amap.com/v3'

/** POI 搜索结果项 */
export interface POIItem {
  id: string
  name: string
  address: string
  longitude: number
  latitude: number
  city?: string
  district?: string
}

/** 逆地理编码结果 */
export interface ReverseGeocodeResult {
  address: string
  city: string
  district: string
}

/** 正向地理编码结果 */
export interface GeocodeResult {
  address: string
  longitude: number
  latitude: number
  city?: string
}

/**
 * 将高德返回的字段安全转换为字符串。
 *
 * 前置条件：value 来自外部 Web API，可能是字符串、数组、对象或空值。
 * 后置条件：仅返回可展示的字符串；数组和对象等非文本值会被忽略。
 * 不变量：不会把 `[]`、`[object Object]` 等技术形态暴露到页面。
 *
 * @param value 外部接口字段值
 */
function asDisplayString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

/**
 * 将预设地点列表转为 POIItem[]
 *
 * @param locations 预设地点数组
 * @returns 标准化的 POI 列表
 */
export function mockLocationsToPOI(locations: MockLocationOption[]): POIItem[] {
  return locations.map((loc, index) => ({
    id: `mock_poi_${index}`,
    name: loc.placeName,
    address: loc.address,
    longitude: loc.point.longitude,
    latitude: loc.point.latitude,
    city: loc.city,
    district: loc.city,
  }))
}

/**
 * POI 关键词搜索
 *
 * 先尝试调用高德 Web API，失败时降级为按关键词过滤预设地点。
 *
 * @param keyword 搜索关键词
 * @param city    城市限定（可选）
 * @returns POI 结果列表
 */
export async function searchPOI(keyword: string, city?: string): Promise<POIItem[]> {
  if (!keyword.trim()) return []

  try {
    const res = await uni.request({
      url: `${AMAP_REST_BASE}/place/text`,
      data: {
        key: AMAP_WEB_API_KEY,
        keywords: keyword.trim(),
        city: city ?? '',
        offset: 20,
        extensions: 'base',
      },
    })

    const data = (res.data as unknown as Record<string, unknown>) ?? {}
    const pois = data.pois as Array<Record<string, unknown>> | undefined
    if (pois && Array.isArray(pois) && pois.length > 0) {
      return pois.map((poi: Record<string, unknown>) => {
        const [lng, lat] = ((poi.location as string) ?? '0,0').split(',').map(Number)
        return {
          id: (poi.id as string) ?? '',
          name: (poi.name as string) ?? '',
          address: (poi.address as string) ?? '',
          longitude: lng,
          latitude: lat,
          city: asDisplayString(poi.cityname) || asDisplayString(poi.pname),
          district: asDisplayString(poi.adname),
        }
      })
    }
    /* API 返回空结果，尝试本地降级 */
    return fallbackSearchPOI(keyword)
  } catch {
    return fallbackSearchPOI(keyword)
  }
}

/**
 * 本地降级：按关键词过滤预设地点
 *
 * @param keyword 搜索关键词
 * @returns 匹配的预设地点列表
 */
/**
 * 查询指定坐标周边 POI。
 *
 * 前置条件：longitude/latitude 为 GCJ-02 坐标。
 * 后置条件：优先返回高德周边地点；网络失败时返回按距离排序的本地候选。
 * 不变量：返回的 POIItem 始终使用 longitude/latitude 字段表达坐标。
 *
 * @param longitude 中心点经度
 * @param latitude 中心点纬度
 * @param radiusMeters 查询半径，单位米
 * @returns 周边 POI 列表
 */
export async function searchNearbyPOI(
  longitude: number,
  latitude: number,
  radiusMeters = 1000,
): Promise<POIItem[]> {
  try {
    const res = await uni.request({
      url: `${AMAP_REST_BASE}/place/around`,
      data: {
        key: AMAP_WEB_API_KEY,
        location: `${longitude},${latitude}`,
        radius: radiusMeters,
        offset: 20,
        extensions: 'base',
      },
    })

    const data = (res.data as unknown as Record<string, unknown>) ?? {}
    const pois = data.pois as Array<Record<string, unknown>> | undefined
    if (pois && Array.isArray(pois) && pois.length > 0) {
      return pois.map((poi: Record<string, unknown>) => {
        const [lng, lat] = ((poi.location as string) ?? '0,0').split(',').map(Number)
        return {
          id: (poi.id as string) ?? '',
          name: (poi.name as string) ?? '',
          address: (poi.address as string) ?? '',
          longitude: lng,
          latitude: lat,
          city: asDisplayString(poi.cityname) || asDisplayString(poi.pname),
          district: asDisplayString(poi.adname),
        }
      })
    }
  } catch {
    /* 网络失败时使用本地位置数据兜底 */
  }
  return fallbackNearbyPOI(longitude, latitude)
}

function fallbackSearchPOI(keyword: string): POIItem[] {
  const lowerKeyword = keyword.trim().toLowerCase()
  const matched = MOCK_ACTIVITY_LOCATIONS.filter(
    (loc) =>
      loc.placeName.toLowerCase().includes(lowerKeyword) ||
      loc.address.toLowerCase().includes(lowerKeyword) ||
      loc.city.toLowerCase().includes(lowerKeyword),
  )
  return mockLocationsToPOI(matched)
}

/**
 * 逆地理编码：根据经纬度获取地址信息
 *
 * @param latitude  纬度
 * @param longitude 经度
 * @returns 地址信息
 */
/**
 * 本地周边 POI 兜底。
 *
 * 前置条件：longitude/latitude 为地图中心坐标。
 * 后置条件：返回距离中心最近的本地地点。
 * 不变量：不修改 MOCK_ACTIVITY_LOCATIONS。
 *
 * @param longitude 中心点经度
 * @param latitude 中心点纬度
 */
function fallbackNearbyPOI(longitude: number, latitude: number): POIItem[] {
  return mockLocationsToPOI(MOCK_ACTIVITY_LOCATIONS)
    .map((item) => ({
      item,
      distance:
        (item.longitude - longitude) * (item.longitude - longitude) +
        (item.latitude - latitude) * (item.latitude - latitude),
    }))
    .sort((left, right) => left.distance - right.distance)
    .slice(0, 12)
    .map(({ item }) => item)
}

export async function reverseGeocode(
  latitude: number,
  longitude: number,
): Promise<ReverseGeocodeResult> {
  try {
    const res = await uni.request({
      url: `${AMAP_REST_BASE}/geocode/regeo`,
      data: {
        key: AMAP_WEB_API_KEY,
        location: `${longitude},${latitude}`,
        radius: 1000,
        extensions: 'base',
      },
    })

    const data = (res.data as unknown as Record<string, unknown>) ?? {}
    const regeocode = data.regeocode as Record<string, unknown> | undefined
    if (regeocode) {
      const addrComp = (regeocode.addressComponent as Record<string, unknown>) ?? {}
      return {
        address: asDisplayString(regeocode.formatted_address),
        city: asDisplayString(addrComp.city) || asDisplayString(addrComp.province),
        district: asDisplayString(addrComp.district),
      }
    }
  } catch {
    /* 降级：返回占位地址 */
  }

  return {
    address: '当前位置',
    city: '',
    district: '',
  }
}

/**
 * 正向地理编码：根据地址获取经纬度
 *
 * @param address 地址文本
 * @param city    城市限定（可选）
 * @returns 地理编码结果，未找到返回 null
 */
export async function geocode(address: string, city?: string): Promise<GeocodeResult | null> {
  if (!address.trim()) return null

  try {
    const res = await uni.request({
      url: `${AMAP_REST_BASE}/geocode/geo`,
      data: {
        key: AMAP_WEB_API_KEY,
        address: address.trim(),
        city: city ?? '',
        batch: false,
      },
    })

    const data = (res.data as unknown as Record<string, unknown>) ?? {}
    const geocodes = data.geocodes as Array<Record<string, unknown>> | undefined
    if (geocodes && Array.isArray(geocodes) && geocodes.length > 0) {
      const geo = geocodes[0]
      const [lng, lat] = ((geo.location as string) ?? '0,0').split(',').map(Number)
      return {
        address: (geo.formatted_address as string) ?? address,
        longitude: lng,
        latitude: lat,
        city: asDisplayString(geo.city) || city || '',
      }
    }
  } catch {
    /* 降级：返回 null，由调用方处理 */
  }

  return null
}

/**
 * 获取当前位置，失败时使用 IP 近似定位
 *
 * 前置条件：用户已授权定位权限
 * 后置条件：返回经纬度；权限被拒时使用 IP 近似定位
 *
 * @returns 经纬度，完全失败时返回北京的默认坐标
 */
export async function getCurrentLocation(): Promise<{ latitude: number; longitude: number }> {
  try {
    const res = await uni.getLocation({ type: 'gcj02' })
    return { latitude: res.latitude, longitude: res.longitude }
  } catch {
    try {
      const ipRes = await uni.request({
        url: `${AMAP_REST_BASE}/ip`,
        data: { key: AMAP_WEB_API_KEY },
      })
      const data = (ipRes.data as unknown as Record<string, unknown>) ?? {}
      if (data.location) {
        const [lng, lat] = (data.location as string).split(',').map(Number)
        if (!isNaN(lng) && !isNaN(lat)) {
          return { latitude: lat, longitude: lng }
        }
      }
    } catch {
      /* 完全失败 */
    }
    return { latitude: 39.908, longitude: 116.397 }
  }
}

/**
 * 位置消息服务
 *
 * 基于高德地图 Web API 生成类微信位置卡片所需的数据。
 * 仅负责组装消息载荷，不负责具体聊天发送逻辑。
 *
 * 前置条件：经纬度为 GCJ-02 坐标
 * 后置条件：返回可直接用于聊天消息渲染的 LocationMessagePayload
 * 不变量：生成失败时不会抛出异常，会返回可展示的兜底位置消息
 */
import {
  AMAP_WEB_API_KEY,
  getCurrentLocation,
  reverseGeocode,
  searchNearbyPOI,
  type POIItem,
} from '@/services/amap'
import { AMAP_REST_BASE_URL, AMAP_URI_BASE_URL } from '@/config/env'
import type { components } from '@/api/types/schema'

type ApiLocationInfo = components['schemas']['LocationInfo']

/** 高德 URI API 基础 URL（由环境变量 VITE_AMAP_URI_BASE_URL 控制） */
const AMAP_URI_BASE = AMAP_URI_BASE_URL

/** 高德 REST API 基础 URL（由环境变量 VITE_AMAP_REST_BASE_URL 控制） */
const AMAP_REST_BASE = AMAP_REST_BASE_URL

/** 位置消息载荷（聊天 UI 渲染用） */
export interface LocationMessagePayload {
  type: 'location'
  provider: 'amap'
  title: string
  address: string
  city: string
  longitude: number
  latitude: number
  previewImageUrl: string
  openMapUrl: string
}

/** 创建位置消息参数 */
export interface CreateLocationMessageOptions {
  longitude: number
  latitude: number
  title?: string
  address?: string
  city?: string
  nearbyPOI?: POIItem
}

/**
 * 生成高德静态地图 URL。
 *
 * 前置条件：longitude/latitude 为 GCJ-02 坐标。
 * 后置条件：返回可用于 image src 的地图预览图地址。
 * 不变量：只生成 URL，不发起网络请求。
 */
export function createAmapStaticMapUrl(longitude: number, latitude: number): string {
  const params = new URLSearchParams({
    key: AMAP_WEB_API_KEY,
    location: `${longitude},${latitude}`,
    zoom: '16',
    size: '600*260',
    scale: '2',
  })

  return `${AMAP_REST_BASE}/staticmap?${params.toString()}`
}

/**
 * 生成高德地图打开链接。
 *
 * 前置条件：longitude/latitude 为 GCJ-02 坐标。
 * 后置条件：返回点击位置卡片时可打开的地图链接。
 * 不变量：只生成 URL，不发起网络请求。
 */
export function createAmapMarkerUrl(longitude: number, latitude: number, title: string): string {
  const params = new URLSearchParams({
    position: `${longitude},${latitude}`,
    name: title,
  })

  return `${AMAP_URI_BASE}/marker?${params.toString()}`
}

/**
 * 将 OpenAPI LocationInfo 转为类微信位置卡片展示数据。
 *
 * 前置条件：location.point 含有效 GCJ-02 坐标。
 * 后置条件：返回可直接交给 LocationMessageCard 渲染的载荷。
 */
export function buildLocationDisplay(location: ApiLocationInfo): LocationMessagePayload {
  const { longitude, latitude } = location.point
  const title = location.placeName || location.address || '位置'
  const address = location.address || location.city || title

  return {
    type: 'location',
    provider: 'amap',
    title,
    address,
    city: location.city || '',
    longitude,
    latitude,
    previewImageUrl: createAmapStaticMapUrl(longitude, latitude),
    openMapUrl: createAmapMarkerUrl(longitude, latitude, title),
  }
}

/**
 * 将位置消息载荷转为 OpenAPI LocationInfo（发送聊天消息用）。
 */
export function toApiLocationInfo(payload: LocationMessagePayload): ApiLocationInfo {
  return {
    point: { longitude: payload.longitude, latitude: payload.latitude },
    city: payload.city,
    address: payload.address,
    placeName: payload.title,
  }
}

/**
 * 创建类微信位置消息载荷。
 *
 * 前置条件：longitude/latitude 为 GCJ-02 坐标。
 * 后置条件：返回聊天系统可发送和渲染的位置消息。
 * 不变量：失败时返回兜底 title/address，不抛出异常。
 */
export async function createLocationMessage(
  options: CreateLocationMessageOptions,
): Promise<LocationMessagePayload> {
  const { longitude, latitude } = options

  try {
    const regeo = await reverseGeocode(latitude, longitude)
    const nearbyPOI = options.nearbyPOI ?? (await searchNearbyPOI(longitude, latitude, 500))[0]

    const title = options.title || nearbyPOI?.name || regeo.address || '当前位置'

    const address =
      options.address || nearbyPOI?.address || regeo.address || options.city || '当前位置'

    return {
      type: 'location',
      provider: 'amap',
      title,
      address,
      city: options.city || regeo.city || '',
      longitude,
      latitude,
      previewImageUrl: createAmapStaticMapUrl(longitude, latitude),
      openMapUrl: createAmapMarkerUrl(longitude, latitude, title),
    }
  } catch {
    const title = options.title || '当前位置'
    const address = options.address || options.city || '当前位置'

    return {
      type: 'location',
      provider: 'amap',
      title,
      address,
      city: options.city || '',
      longitude,
      latitude,
      previewImageUrl: createAmapStaticMapUrl(longitude, latitude),
      openMapUrl: createAmapMarkerUrl(longitude, latitude, title),
    }
  }
}

/**
 * 创建当前位置消息。
 *
 * 前置条件：用户已授权定位权限；未授权时 getCurrentLocation 会自动降级。
 * 后置条件：返回当前位置对应的位置消息载荷。
 * 不变量：失败时仍返回默认位置消息。
 */
export async function createCurrentLocationMessage(): Promise<LocationMessagePayload> {
  const location = await getCurrentLocation()

  return createLocationMessage({
    longitude: location.longitude,
    latitude: location.latitude,
  })
}

/**
 * 构造聊天位置详情页路由（全屏 <map>，与 PR #126 一致）。
 */
export function buildLocationMapRoute(display: LocationMessagePayload): string {
  const parts = [
    `latitude=${encodeURIComponent(String(display.latitude))}`,
    `longitude=${encodeURIComponent(String(display.longitude))}`,
    `title=${encodeURIComponent(display.title)}`,
    `address=${encodeURIComponent(display.address)}`,
    `city=${encodeURIComponent(display.city || '')}`,
  ]
  return `/pages/messages/location-map?${parts.join('&')}`
}

function openLocationFallback(display: LocationMessagePayload): void {
  uni.openLocation({
    latitude: display.latitude,
    longitude: display.longitude,
    name: display.title,
    address: display.address,
    scale: 16,
  })
}

/**
 * 打开聊天位置详情页（全屏原生 <map>，不跳浏览器）。
 *
 * 与 PR #126 map-picker 相同技术栈：uni.navigateTo + <map> 组件。
 * openMapUrl 保留在载荷中供外部场景使用，本函数不打开网页。
 */
export function openLocationMap(display: LocationMessagePayload): void {
  uni.navigateTo({
    url: buildLocationMapRoute(display),
    fail: () => openLocationFallback(display),
  })
}

/** @deprecated 请改用 openLocationMap */
export const openAmapLocation = openLocationMap

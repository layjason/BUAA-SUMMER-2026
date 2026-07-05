<template>
  <view class="activity-location-page">
    <view class="location-topbar">
      <text class="location-back" @tap="goBack">返回</text>
      <text class="location-title">活动地点</text>
      <view class="location-topbar-spacer" />
    </view>

    <view class="location-map-shell">
      <map
        id="activityLocationMap"
        class="location-map"
        :latitude="centerLatitude"
        :longitude="centerLongitude"
        :scale="scale"
        :markers="markers"
      />
    </view>

    <view class="location-bottom">
      <view class="location-summary">
        <view>
          <view class="location-place-name">{{ placeTitle }}</view>
          <view v-if="placeAddress" class="location-place-address">{{ placeAddress }}</view>
          <view v-if="placeCity" class="location-place-city">{{ placeCity }}</view>
        </view>
        <view class="map-actions">
          <button class="map-action-button" @tap="showActivityLocation">活动位置</button>
          <button
            class="map-action-button map-action-button--primary"
            :disabled="locating"
            @tap="showMyLocation"
          >
            {{ locating ? '定位中' : '我的位置' }}
          </button>
        </view>
      </view>
      <view class="location-current" :class="{ 'location-current--muted': !currentLocationText }">
        {{ currentLocationText || '暂未获取当前位置，可点击“我的位置”重试' }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动地点地图页。
 *
 * 前置条件：由活动详情页携带活动地点经纬度、标题、地址跳转进入。
 * 后置条件：地图默认展示活动地点 marker，并在定位成功后可切换到“我的位置”。
 * 不变量：当前位置仅用于本页展示，不写入活动地点或签到数据。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { distanceBetween, formatDistance, type LocationInfo } from '@/utils/location'
import { normalizeGeoPoint } from '@/utils/map-move'

interface LocationMarker {
  id: number
  latitude: number
  longitude: number
  title: string
  iconPath: string
  width: number
  height: number
  anchor: { x: number; y: number }
}

const DEFAULT_CENTER = { longitude: 116.397, latitude: 39.908 }
const ACTIVITY_MARKER_ID = 1
const CURRENT_MARKER_ID = 2

let activityMarkerIcon = '/static/map/location-marker.svg'
let currentMarkerIcon = '/static/map/my-location.svg'
/* #ifdef APP-PLUS */
activityMarkerIcon = '/static/map/location-marker.png'
currentMarkerIcon = '/static/map/my-location.png'
/* #endif */

const activityLocation = ref<LocationInfo>(DEFAULT_CENTER)
const currentLocation = ref<LocationInfo | null>(null)
const centerLongitude = ref(DEFAULT_CENTER.longitude)
const centerLatitude = ref(DEFAULT_CENTER.latitude)
const scale = ref(16)
const placeTitle = ref('活动地点')
const placeAddress = ref('')
const placeCity = ref('')
const locating = ref(false)

/** 地图 marker 列表，始终包含活动地点，定位成功后追加当前位置。 */
const markers = computed<LocationMarker[]>(() => {
  const result: LocationMarker[] = [
    {
      id: ACTIVITY_MARKER_ID,
      latitude: activityLocation.value.latitude,
      longitude: activityLocation.value.longitude,
      title: placeTitle.value,
      iconPath: activityMarkerIcon,
      width: 40,
      height: 40,
      anchor: { x: 0.5, y: 1 },
    },
  ]

  if (currentLocation.value) {
    result.push({
      id: CURRENT_MARKER_ID,
      latitude: currentLocation.value.latitude,
      longitude: currentLocation.value.longitude,
      title: '我的位置',
      iconPath: currentMarkerIcon,
      width: 36,
      height: 36,
      anchor: { x: 0.5, y: 0.5 },
    })
  }

  return result
})

/** 当前定位文案，定位成功时展示距活动地点的直线距离。 */
const currentLocationText = computed(() => {
  const current = currentLocation.value
  if (!current) return ''
  return `我的位置距活动地点约 ${formatDistance(distanceBetween(current, activityLocation.value))}`
})

/**
 * 解码 query 字符串。
 *
 * 前置条件：value 来自页面 query。
 * 后置条件：返回可展示字符串，解码失败时保留原值。
 * 不变量：不抛出 URIError。
 *
 * @param value query 值
 * @param fallback 缺省文案
 */
function decodeQuery(value: string | undefined, fallback = ''): string {
  if (!value) return fallback
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

/**
 * 解析经纬度参数。
 *
 * 前置条件：value 来自页面 query。
 * 后置条件：合法数值返回 number，否则返回 fallback。
 * 不变量：不接受 NaN 与 Infinity。
 *
 * @param value query 值
 * @param fallback 缺省坐标
 */
function parseCoordinate(value: string | undefined, fallback: number): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/**
 * 移动地图中心。
 *
 * 前置条件：坐标已通过 normalizeGeoPoint 校验。
 * 后置条件：更新响应式中心，并尽量驱动原生 map 上下文移动。
 * 不变量：map context 不可用时不影响页面渲染。
 *
 * @param location 目标中心点
 */
function moveMapCenter(location: LocationInfo): void {
  centerLongitude.value = location.longitude
  centerLatitude.value = location.latitude
  try {
    uni.createMapContext('activityLocationMap').moveToLocation({
      longitude: location.longitude,
      latitude: location.latitude,
    })
  } catch {
    /* 地图上下文不可用时，绑定中心点仍会生效 */
  }
}

/**
 * 获取当前位置。
 *
 * 前置条件：用户允许定位或平台可返回定位。
 * 后置条件：定位成功时添加当前位置 marker；失败时展示提示。
 * 不变量：定位失败不会清空活动地点 marker。
 */
async function fetchCurrentLocation(): Promise<LocationInfo | null> {
  if (locating.value) return
  locating.value = true
  try {
    const location = await uni.getLocation({
      type: 'gcj02',
      /* H5 兼容：防止非 HTTPS 环境下 geolocation 永不回调 */
      timeout: 8000,
    })
    const point = normalizeGeoPoint(location.longitude, location.latitude)
    if (!point) throw new Error('invalid location')
    currentLocation.value = point
    return point
  } catch {
    uni.showToast({ title: '当前位置获取失败', icon: 'none' })
    return null
  } finally {
    locating.value = false
  }
}

/**
 * 切换到我的位置。
 *
 * 前置条件：用户点击“我的位置”按钮。
 * 后置条件：已有定位则移动到当前位置，否则先获取定位再移动。
 * 不变量：进入页面时不会自动移动到我的位置。
 */
async function showMyLocation(): Promise<void> {
  const location = currentLocation.value ?? (await fetchCurrentLocation())
  if (location) moveMapCenter(location)
}

/**
 * 切换回活动位置。
 *
 * 前置条件：活动地点坐标已从 query 初始化。
 * 后置条件：地图中心回到活动地点。
 * 不变量：不清空当前位置 marker。
 */
function showActivityLocation(): void {
  moveMapCenter(activityLocation.value)
}

/** 返回上一页。 */
function goBack(): void {
  uni.navigateBack()
}

onLoad((query) => {
  const routeQuery = (query ?? {}) as Record<string, string | undefined>
  const longitude = parseCoordinate(routeQuery.longitude, DEFAULT_CENTER.longitude)
  const latitude = parseCoordinate(routeQuery.latitude, DEFAULT_CENTER.latitude)
  const normalized = normalizeGeoPoint(longitude, latitude) ?? DEFAULT_CENTER

  activityLocation.value = normalized
  centerLongitude.value = normalized.longitude
  centerLatitude.value = normalized.latitude
  placeTitle.value = decodeQuery(routeQuery.title, '活动地点')
  placeAddress.value = decodeQuery(routeQuery.address)
  placeCity.value = decodeQuery(routeQuery.city)

  void fetchCurrentLocation()
})
</script>

<style scoped lang="scss">
@import '@/styles/theme.scss';

.activity-location-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: $color-bg;
  overflow: hidden;
  box-sizing: border-box;
}

.location-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(var(--status-bar-height) + 12rpx) 28rpx 18rpx;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid $color-border-light;
}

.location-back {
  font-size: $font-sm;
  color: $color-primary;
  min-width: 96rpx;
}

.location-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.location-topbar-spacer {
  min-width: 96rpx;
}

.location-map-shell {
  position: relative;
  flex: 1;
  min-height: 0;
}

.location-map {
  width: 100%;
  height: 100%;
}

.location-bottom {
  flex-shrink: 0;
  padding: 28rpx 32rpx calc(28rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
}

.location-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.location-place-name {
  font-size: 34rpx;
  font-weight: $weight-semibold;
  color: $color-text;
  line-height: 1.35;
}

.location-place-address {
  margin-top: 10rpx;
  font-size: $font-sm;
  color: $color-text-muted;
  line-height: 1.4;
}

.location-place-city {
  margin-top: 8rpx;
  font-size: $font-xs;
  color: $color-text-muted;
}

.map-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.map-action-button {
  margin: 0;
  padding: 0 22rpx;
  height: 60rpx;
  line-height: 60rpx;
  border-radius: 999rpx;
  border: none;
  background: #f2f3f5;
  color: $color-text;
  font-size: $font-xs;
}

.map-action-button--primary {
  background: $color-primary;
  color: #ffffff;
}

.map-action-button[disabled] {
  background: #c8c9cc;
  color: #ffffff;
}

.location-current {
  margin-top: 18rpx;
  font-size: $font-xs;
  color: $color-text;
  line-height: 1.4;
}

.location-current--muted {
  color: $color-text-muted;
}
</style>

<style>
html,
body,
page {
  height: 100%;
  overflow: hidden;
}
</style>

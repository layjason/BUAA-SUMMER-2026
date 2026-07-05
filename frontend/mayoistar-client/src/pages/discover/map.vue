<script setup lang="ts">
/**
 * 地图发现页
 *
 * 使用 uni-app <map> 组件展示附近活动点位，并通过 /activities/map 获取 OpenAPI 地图点位。
 * 前置条件：用户可未登录浏览，H5/Android 已配置地图能力。
 * 后置条件：展示当前位置附近活动 marker，点击 marker 或底部卡片可查看活动详情。
 * 不变量：定位、地图视口移动和页面跳转均使用 uni-app 跨端 API。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getMapActivities, type MapActivitiesParams } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'
import { normalizeGeoPoint } from '@/utils/map-move'
import { runtimeStatusText } from '@/utils/status'

/** 地图活动点位条目 */
interface MapActivityItem {
  activityId: string
  title: string
  startAt: string
  runtimeStatus: string
  point: { longitude: number; latitude: number }
}

/** 地图 marker 展示结构 */
interface ActivityMarker {
  id: number
  activityId?: string
  latitude: number
  longitude: number
  title: string
  iconPath: string
  width: number
  height: number
  anchor: { x: number; y: number }
}

const DEFAULT_CENTER = { longitude: 116.4, latitude: 39.9 }
const DEFAULT_DISTANCE_METERS = 10000
const CURRENT_LOCATION_MARKER_ID = 999999
const { t } = useI18n()

let locationMarkerIcon = '/static/map/location-marker.svg'
let myLocationMarkerIcon = '/static/map/my-location.svg'
/* #ifdef APP-PLUS */
locationMarkerIcon = '/static/map/location-marker.png'
myLocationMarkerIcon = '/static/map/my-location.png'
/* #endif */

const mapActivities = ref<MapActivityItem[]>([])
const isLoading = ref(false)
const selectedId = ref<string | null>(null)
const errorMsg = ref('')
const centerLongitude = ref(DEFAULT_CENTER.longitude)
const centerLatitude = ref(DEFAULT_CENTER.latitude)
const currentLongitude = ref<number | null>(null)
const currentLatitude = ref<number | null>(null)
const distanceMeters = ref(DEFAULT_DISTANCE_METERS)
const routeFilters = ref<Omit<MapActivitiesParams, 'longitude' | 'latitude' | 'distanceMeters'>>({})

/** 地图 marker 列表 */
const markers = computed<ActivityMarker[]>(() => {
  const currentMarker: ActivityMarker[] =
    currentLongitude.value != null && currentLatitude.value != null
      ? [
          {
            id: CURRENT_LOCATION_MARKER_ID,
            latitude: currentLatitude.value,
            longitude: currentLongitude.value,
            title: '我的位置',
            iconPath: myLocationMarkerIcon,
            width: 40,
            height: 40,
            anchor: { x: 0.5, y: 0.5 },
          },
        ]
      : []
  const activityMarkers = mapActivities.value
    .map((item, index) => {
      const point = normalizeGeoPoint(item.point.longitude, item.point.latitude)
      if (!point) return null
      return {
        id: index + 1,
        activityId: item.activityId,
        latitude: point.latitude,
        longitude: point.longitude,
        title: item.title,
        iconPath: locationMarkerIcon,
        width: selectedId.value === item.activityId ? 56 : 48,
        height: selectedId.value === item.activityId ? 56 : 48,
        anchor: { x: 0.5, y: 1 },
      } as ActivityMarker
    })
    .filter((item): item is ActivityMarker => item != null)
  return [...currentMarker, ...activityMarkers]
})

/** 当前选中的活动 */
const selectedActivity = computed(() =>
  mapActivities.value.find((item) => item.activityId === selectedId.value),
)

/** 当前选中活动对应的列表 DOM id，用于 scroll-view 自动定位 */
const selectedScrollId = computed(() => {
  return selectedId.value ? activityDomId(selectedId.value) : ''
})

/**
 * 从页面 query 中读取地图筛选条件
 *
 * 前置条件：query 来自 uni-app onLoad。
 * 后置条件：routeFilters 仅包含 OpenAPI 地图查询已定义字段。
 * 不变量：不会把页面私有字段透传给业务接口。
 */
function readRouteFilters(query: Record<string, string | undefined>): void {
  const nextFilters: Omit<MapActivitiesParams, 'longitude' | 'latitude' | 'distanceMeters'> = {}
  if (query.keyword) nextFilters.keyword = decodeURIComponent(query.keyword)
  if (query.city) nextFilters.city = decodeURIComponent(query.city)
  if (query.activityTypes) {
    nextFilters.activityTypes = decodeURIComponent(query.activityTypes)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  }
  if (query.startAtFrom) nextFilters.startAtFrom = decodeURIComponent(query.startAtFrom)
  if (query.startAtTo) nextFilters.startAtTo = decodeURIComponent(query.startAtTo)
  if (query.minFee) nextFilters.minFee = Number(query.minFee)
  if (query.maxFee) nextFilters.maxFee = Number(query.maxFee)
  routeFilters.value = nextFilters
}

/**
 * 获取用户当前位置
 *
 * 前置条件：页面需要初始化地图中心或用户点击定位。
 * 后置条件：定位成功返回 GCJ-02 坐标，失败返回默认北京中心点。
 * 不变量：失败不会阻断地图活动加载。
 *
 * 在 H5 端，浏览器 Geolocation API 在非 HTTPS 环境下可能永不回调（不触发 success
 * 也不触发 error），因此必须设置 timeout 避免 Promise 永久悬挂。
 */
async function getCurrentCenter(): Promise<{ longitude: number; latitude: number }> {
  try {
    const location = await uni.getLocation({
      type: 'gcj02',
      /* H5 兼容：防止非 HTTPS 环境下 geolocation 永不回调导致页面卡死 */
      timeout: 8000,
    })
    currentLongitude.value = location.longitude
    currentLatitude.value = location.latitude
    return { longitude: location.longitude, latitude: location.latitude }
  } catch {
    return DEFAULT_CENTER
  }
}

/**
 * 移动地图视口到指定中心
 *
 * 前置条件：经纬度来自定位或 marker 点位。
 * 后置条件：响应式中心点更新，并尝试驱动原生地图移动。
 * 不变量：地图上下文不可用时保留响应式中心点。
 */
function moveMapTo(longitude: number, latitude: number): void {
  centerLongitude.value = longitude
  centerLatitude.value = latitude
  try {
    uni.createMapContext('discoverMap').moveToLocation({ longitude, latitude })
  } catch {
    /* 地图上下文不可用时忽略，绑定中心点仍会生效 */
  }
}

/**
 * 加载地图活动点位
 *
 * 前置条件：centerLongitude、centerLatitude 已初始化。
 * 后置条件：mapActivities 更新为接口返回点位，默认选中第一条。
 * 不变量：接口失败时清空列表并展示错误提示。
 */
async function loadMapActivities(): Promise<void> {
  isLoading.value = true
  errorMsg.value = ''
  try {
    const result = await getMapActivities(
      centerLongitude.value,
      centerLatitude.value,
      distanceMeters.value,
      routeFilters.value,
    )
    mapActivities.value = (result ?? []) as MapActivityItem[]
    selectedId.value = mapActivities.value[0]?.activityId ?? null
  } catch {
    mapActivities.value = []
    selectedId.value = null
    errorMsg.value = '附近活动加载失败'
  } finally {
    isLoading.value = false
  }
}

/**
 * 初始化页面：先以默认中心加载活动，再异步尝试定位。
 *
 * 前置条件：页面由 onLoad 触发。
 * 后置条件：活动列表在默认中心加载完成；定位成功后移动地图并刷新。
 * 不变量：定位失败或超时不会阻塞首次数据展示。
 *
 * H5 端关键修复：浏览器 Geolocation API 在非 HTTPS 环境下可能永久不回调，
 * 因此先加载默认位置的活动数据，再将定位作为可选增强，确保页面不会白屏。
 */
async function initializePage(): Promise<void> {
  // 立刻以默认中心加载活动点位，避免等待定位导致白屏/卡死
  await loadMapActivities()
  // 异步尝试获取用户位置，成功则移动地图并重新加载
  const center = await getCurrentCenter()
  if (
    center.longitude !== DEFAULT_CENTER.longitude ||
    center.latitude !== DEFAULT_CENTER.latitude
  ) {
    moveMapTo(center.longitude, center.latitude)
    await loadMapActivities()
  }
}

/**
 * 定位到当前位置并刷新点位（用户点击"定位"按钮时触发）
 *
 * 前置条件：用户点击定位按钮。
 * 后置条件：地图中心移动到当前位置并重新加载附近活动。
 * 不变量：定位失败使用默认中心点继续演示。
 */
async function locateAndLoad(): Promise<void> {
  const center = await getCurrentCenter()
  moveMapTo(center.longitude, center.latitude)
  await loadMapActivities()
}

/**
 * 处理 marker 点击
 *
 * 前置条件：地图组件返回 markerId，H5 与 App 端可能位于 event.detail 或 event 本身。
 * 后置条件：选中对应活动并移动底部卡片焦点。
 * 不变量：未知 markerId 不改变当前选择。
 */
function onMarkerTap(event: { detail?: { markerId?: number }; markerId?: number }): void {
  const markerId = event.detail?.markerId ?? event.markerId
  if (markerId === CURRENT_LOCATION_MARKER_ID) return
  const marker = markers.value.find((item) => item.id === markerId)
  if (!marker?.activityId) return
  selectedId.value = marker.activityId
  moveMapTo(marker.longitude, marker.latitude)
}

/**
 * 选择底部活动卡片
 *
 * 前置条件：item 来自当前地图点位列表。
 * 后置条件：高亮活动并将地图中心移动到该点位。
 * 不变量：不立即跳转详情，避免误触。
 */
function selectActivity(item: MapActivityItem): void {
  const point = normalizeGeoPoint(item.point.longitude, item.point.latitude)
  if (!point) return
  selectedId.value = item.activityId
  moveMapTo(point.longitude, point.latitude)
}

/**
 * 生成活动卡片在 scroll-view 中的稳定 id。
 *
 * 前置条件：activityId 来自 OpenAPI 活动点位数据。
 * 后置条件：返回可用于 DOM id 与 scroll-into-view 的字符串。
 * 不变量：同一个 activityId 始终映射到同一个 id。
 *
 * @param activityId 活动 ID
 */
function activityDomId(activityId: string): string {
  return `map-activity-${activityId}`
}

/**
 * 跳转活动详情
 *
 * 前置条件：activityId 非空。
 * 后置条件：进入活动详情页。
 * 不变量：不修改地图点位列表。
 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/**
 * 返回上一页
 *
 * 前置条件：页面由 navigateTo 打开。
 * 后置条件：回到上一个页面。
 * 不变量：不清理 mock 数据。
 */
function goBack(): void {
  uni.navigateBack()
}

/**
 * 获取状态文本
 *
 * 前置条件：status 来自 OpenAPI ActivityRuntimeStatus。
 * 后置条件：返回中文展示文本。
 * 不变量：不修改状态值。
 */
function getStatusText(status: string): string {
  return runtimeStatusText(status, t)
}

onLoad((query) => {
  readRouteFilters((query ?? {}) as Record<string, string | undefined>)
  void initializePage()
})
</script>

<template>
  <view class="map-page">
    <view class="map-topbar">
      <text class="map-back" @tap="goBack">返回</text>
      <text class="map-title">地图发现</text>
      <text class="map-locate" @tap="locateAndLoad">定位</text>
    </view>

    <view class="map-shell">
      <map
        id="discoverMap"
        class="activity-map"
        :longitude="centerLongitude"
        :latitude="centerLatitude"
        :markers="markers"
        :scale="13"
        @markertap="onMarkerTap"
      />

      <view v-if="isLoading" class="map-loading">
        <text>加载附近活动...</text>
      </view>
    </view>

    <view class="bottom-panel">
      <view class="panel-header">
        <text class="list-title">附近活动（{{ mapActivities.length }}）</text>
        <text v-if="selectedActivity" class="selected-title">{{ selectedActivity.title }}</text>
      </view>

      <view v-if="errorMsg" class="list-empty">
        <text>{{ errorMsg }}</text>
        <button class="retry-btn" @tap="loadMapActivities">重试</button>
      </view>

      <view v-else-if="mapActivities.length === 0 && !isLoading" class="list-empty">
        <text>当前范围内没有发现活动</text>
      </view>

      <scroll-view
        v-else
        scroll-y
        scroll-with-animation
        class="list-scroll"
        :scroll-into-view="selectedScrollId"
      >
        <view
          v-for="item in mapActivities"
          :key="item.activityId"
          :id="activityDomId(item.activityId)"
          class="list-card"
          :class="{ 'list-card-selected': selectedId === item.activityId }"
          @tap="selectActivity(item)"
        >
          <view class="list-card-body">
            <text class="list-card-title">{{ item.title }}</text>
            <view class="list-card-meta">
              <text class="list-card-time">{{ formatDate(item.startAt) }}</text>
              <text class="list-card-status" :class="'status-' + item.runtimeStatus">{{
                getStatusText(item.runtimeStatus)
              }}</text>
            </view>
          </view>
          <button class="detail-btn" @tap.stop="goDetail(item.activityId)">详情</button>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.map-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: $color-bg;
  box-sizing: border-box;
}

.map-shell {
  position: relative;
  flex: 1;
  min-height: 0;
}

.activity-map {
  width: 100%;
  height: 100%;
}

.map-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(var(--status-bar-height) + 12rpx) 28rpx 18rpx;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid $color-border-light;
}

.map-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.map-back,
.map-locate {
  font-size: $font-sm;
  color: $color-primary;
}

.map-loading {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  padding: 16rpx 24rpx;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.92);

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.bottom-panel {
  flex: 0 0 520rpx;
  display: flex;
  flex-direction: column;
  min-height: 440rpx;
  max-height: 56%;
  padding: 24rpx 28rpx calc(#{$safe-bottom} + 24rpx);
  background: $color-bg-glass-heavy;
  border-top: 1px solid $color-border;
  border-radius: 24rpx 24rpx 0 0;
  box-sizing: border-box;
}

.panel-header {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.list-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.selected-title {
  flex: 1;
  font-size: $font-xs;
  color: $color-text-muted;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-scroll {
  flex: 1;
  min-height: 0;
}

.list-empty {
  flex: 1;
  min-height: 280rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.retry-btn {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 28rpx;
  margin: 0;
  font-size: $font-xs;
  color: $color-primary;
  background: $color-primary-light;
  border: none;
  border-radius: $radius-full;
}

.list-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  background: $color-bg-glass;
  border: 1px solid $color-border-light;
  border-radius: $radius-md;
  padding: 18rpx 20rpx;
  margin-bottom: 12rpx;

  &:active {
    opacity: 0.85;
  }
}

.list-card-selected {
  border-color: $color-primary;
  background: $color-primary-light;
}

.list-card-body {
  flex: 1;
  min-width: 0;
}

.list-card-title {
  display: block;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-card-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}

.list-card-time {
  font-size: $font-xs;
  color: $color-text-muted;
}

.list-card-status {
  font-size: $font-xs;
  padding: 2rpx 10rpx;
  border-radius: $radius-sm;
}

.detail-btn {
  flex-shrink: 0;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 24rpx;
  margin: 0;
  font-size: $font-xs;
  color: $color-primary;
  background: #fff;
  border: 1px solid rgba(94, 200, 167, 0.35);
  border-radius: $radius-full;
}

.status-registering {
  color: $color-primary;
  background: $color-primary-light;
}

.status-registrationClosed {
  color: $color-warning;
  background: rgba(246, 197, 111, 0.1);
}

.status-ongoing {
  color: $color-success;
  background: rgba(94, 200, 167, 0.1);
}

.status-ended {
  color: $color-text-muted;
  background: rgba(123, 129, 144, 0.08);
}

.status-notStarted {
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.08);
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

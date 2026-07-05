<script setup lang="ts">
/**
 * 地图发现页
 *
 * 使用 mock 地图展示附近活动点位，接入 mock server 的 getMapActivities 接口。
 * 不接入真实地图 SDK，以演示 workflow 为目标。
 *
 * 前置条件：用户可未登录浏览
 * 后置条件：展示 mock 地图区域内附近活动点位和底部列表
 */
import { ref, computed, onMounted } from 'vue'
import { getMapActivities } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'
import { getCurrentLocation } from '@/utils/location'

/** 地图活动点位接口 */
interface MapActivityItem {
  activityId: string
  title: string
  startAt: string
  runtimeStatus: string
  point: { longitude: number; latitude: number }
}

const mapActivities = ref<MapActivityItem[]>([])
const isLoading = ref(false)
const selectedId = ref<string | null>(null)
const fallbackLocation = { longitude: 116.4, latitude: 39.9 }
const mapDistanceMeters = 10000

/**
 * 为每个活动计算在 mock 地图上的相对位置（百分比坐标）
 *
 * 以所有活动的经纬度范围为基准，映射到 0-100% 的 CSS 定位。
 */
const pinnedActivities = computed(() => {
  if (mapActivities.value.length === 0) return []
  const lons = mapActivities.value.map((a) => a.point.longitude)
  const lats = mapActivities.value.map((a) => a.point.latitude)
  const minLon = Math.min(...lons) - 0.01
  const maxLon = Math.max(...lons) + 0.01
  const minLat = Math.min(...lats) - 0.01
  const maxLat = Math.max(...lats) + 0.01
  const lonRange = maxLon - minLon || 1
  const latRange = maxLat - minLat || 1

  return mapActivities.value.map((a) => ({
    ...a,
    left: ((a.point.longitude - minLon) / lonRange) * 80 + 10,
    top: ((maxLat - a.point.latitude) / latRange) * 80 + 10,
  }))
})

/** 获取状态文本 */
function getStatusText(status: string): string {
  const map: Record<string, string> = {
    registering: '报名中',
    registrationClosed: '报名截止',
    ongoing: '进行中',
    ended: '已结束',
    notStarted: '未开始',
  }
  return map[status] ?? status
}

onMounted(async () => {
  isLoading.value = true
  try {
    const location = await getCurrentLocation()
    const center = location ?? fallbackLocation
    if (!location) {
      uni.showToast({ title: '无法获取当前位置，已使用默认位置展示附近活动', icon: 'none' })
    }
    const result = await getMapActivities({
      page: 1,
      pageSize: 100,
      longitude: center.longitude,
      latitude: center.latitude,
      distanceMeters: mapDistanceMeters,
    })
    mapActivities.value = (result ?? []) as MapActivityItem[]
  } catch {
    mapActivities.value = []
  } finally {
    isLoading.value = false
  }
})

/** 点击活动点位，跳转详情 */
function onTapActivity(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/** 高亮选中的活动 */
function onSelectPin(activityId: string): void {
  selectedId.value = selectedId.value === activityId ? null : activityId
}

/** 返回列表模式 */
function goBack(): void {
  uni.navigateBack()
}
</script>

<template>
  <view class="map-page">
    <!-- Mock 地图区域 -->
    <view class="mock-map">
      <view class="map-header">
        <text class="map-title">📍 地图发现</text>
        <text class="map-back" @tap="goBack">返回</text>
      </view>

      <view class="map-canvas">
        <view v-if="isLoading" class="map-loading">
          <text>加载附近活动...</text>
        </view>

        <view v-else-if="pinnedActivities.length === 0" class="map-bg map-bg-empty">
          <text class="empty-text">🗺️ 暂无附近活动</text>
          <text class="empty-sub">当前范围内没有发现活动</text>
        </view>

        <view v-else class="map-bg">
          <text class="map-placeholder-text">Mock 地图区域</text>

          <!-- Mock 活动点位 -->
          <view
            v-for="pin in pinnedActivities"
            :key="pin.activityId"
            class="map-pin"
            :class="{ 'pin-selected': selectedId === pin.activityId }"
            :style="{ left: pin.left + '%', top: pin.top + '%' }"
            @tap="onSelectPin(pin.activityId)"
          >
            <text class="pin-dot">📍</text>
            <text class="pin-label">{{ pin.title }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部活动列表 -->
    <view class="bottom-list">
      <text class="list-title">附近活动（{{ mapActivities.length }}）</text>

      <view v-if="mapActivities.length === 0 && !isLoading" class="list-empty">
        <text>暂无附近活动</text>
      </view>

      <scroll-view v-else scroll-y class="list-scroll">
        <view
          v-for="item in mapActivities"
          :key="item.activityId"
          class="list-card"
          :class="{ 'list-card-selected': selectedId === item.activityId }"
          @tap="onTapActivity(item.activityId)"
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
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.map-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(#{$safe-bottom} + #{$spacing-xl});
}

.mock-map {
  flex-shrink: 0;
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  background: $color-bg-glass-heavy;
}

.map-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.map-back {
  font-size: $font-sm;
  color: $color-primary;
}

.map-canvas {
  padding: $spacing-md;
}

.map-loading {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.map-bg {
  min-height: 300px;
  background: linear-gradient(180deg, #e8f4ee 0%, #d4ebe3 100%);
  border-radius: $radius-xl;
  border: 1px solid $color-border;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  position: relative;
}

.map-bg-empty {
  gap: $spacing-sm;
}

.map-placeholder-text {
  font-size: $font-sm;
  color: $color-text-muted;
  opacity: 0.6;
  margin-bottom: $spacing-sm;
}

.empty-text {
  font-size: $font-base;
  color: $color-text-sub;
}

.empty-sub {
  font-size: $font-sm;
  color: $color-text-muted;
}

.map-pin {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translate(-50%, -100%);
  z-index: 1;

  &:active {
    transform: translate(-50%, -100%) scale(1.1);
  }
}

.pin-selected {
  z-index: 2;

  .pin-dot {
    font-size: 30px;
  }

  .pin-label {
    background: $color-primary;
    color: $color-text-inverse;
    font-weight: $weight-semibold;
  }
}

.pin-dot {
  font-size: 24px;
}

.pin-label {
  font-size: $font-xs;
  background: $color-bg-glass-heavy;
  padding: 2px 8px;
  border-radius: $radius-sm;
  white-space: nowrap;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: -4px;
}

.bottom-list {
  flex: 1;
  padding: $spacing-lg;
  background: $color-bg-glass-heavy;
  border-top: 1px solid $color-border;
  border-radius: $radius-xl $radius-xl 0 0;
}

.list-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  margin-bottom: $spacing-md;
}

.list-empty {
  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.list-scroll {
  max-height: 300px;
}

.list-card {
  background: $color-bg-glass;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-sm;

  &:active {
    opacity: 0.85;
  }
}

.list-card-selected {
  border-color: $color-primary;
  background: $color-primary-light;
}

.list-card-body {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.list-card-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-card-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.list-card-time {
  font-size: $font-xs;
  color: $color-text-muted;
}

.list-card-status {
  font-size: $font-xs;
  padding: 1px 6px;
  border-radius: $radius-sm;
}

.status-registering {
  color: $color-primary;
  background: $color-primary-light;
}

.status-ongoing {
  color: $color-success;
  background: rgba(94, 200, 167, 0.1);
}

.status-ended {
  color: $color-text-muted;
  background: rgba(123, 129, 144, 0.08);
}

.status-registrationClosed {
  color: $color-warning;
  background: rgba(246, 197, 111, 0.1);
}

.status-notStarted {
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.08);
}
</style>

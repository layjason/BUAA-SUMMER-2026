<template>
  <view class="loc-page">
    <view class="loc-topbar">
      <text class="loc-back" @tap="goBack">返回</text>
      <text class="loc-title">位置</text>
      <view class="loc-topbar-spacer" />
    </view>

    <view class="loc-map-shell">
      <map
        id="locationMap"
        class="loc-map"
        :latitude="latitude"
        :longitude="longitude"
        :scale="scale"
        :markers="markers"
        :show-location="true"
      />
    </view>

    <view class="loc-bottom">
      <view class="loc-place-name">{{ placeTitle }}</view>
      <view v-if="placeAddress" class="loc-place-address">{{ placeAddress }}</view>
      <view v-if="placeCity" class="loc-place-city">{{ placeCity }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 聊天位置详情页（只读）
 *
 * 与 PR #126 map-picker 一致，使用 uni-app <map> 原生组件全屏展示地点，
 * 不跳转浏览器、不调用 uni.openLocation。
 *
 * 前置条件：由 LocationMessageCard 携带 latitude/longitude/title/address 跳转
 * 后置条件：地图居中展示目标坐标与标记点
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const latitude = ref(39.908)
const longitude = ref(116.397)
const scale = ref(16)
const placeTitle = ref('位置')
const placeAddress = ref('')
const placeCity = ref('')

let markerIcon = '/static/map/location-marker.svg'
/* #ifdef APP-PLUS */
markerIcon = '/static/map/location-marker.svg'
/* #endif */

const markers = computed(() => [
  {
    id: 1,
    latitude: latitude.value,
    longitude: longitude.value,
    title: placeTitle.value,
    iconPath: markerIcon,
    width: 32,
    height: 32,
    anchor: { x: 0.5, y: 1 },
  },
])

function decodeQuery(value: string | undefined, fallback = ''): string {
  if (!value) return fallback
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function parseCoordinate(value: string | undefined, fallback: number): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function goBack(): void {
  uni.navigateBack()
}

onLoad((query) => {
  const routeQuery = (query ?? {}) as Record<string, string | undefined>
  latitude.value = parseCoordinate(routeQuery.latitude, latitude.value)
  longitude.value = parseCoordinate(routeQuery.longitude, longitude.value)
  placeTitle.value = decodeQuery(routeQuery.title, '位置')
  placeAddress.value = decodeQuery(routeQuery.address)
  placeCity.value = decodeQuery(routeQuery.city)
})
</script>

<style scoped lang="scss">
@import '@/styles/theme.scss';

.loc-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: $color-bg;
  overflow: hidden;
  box-sizing: border-box;
}

.loc-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(var(--status-bar-height) + 12rpx) 28rpx 18rpx;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid $color-border-light;
}

.loc-back {
  font-size: $font-sm;
  color: $color-primary;
  min-width: 80rpx;
}

.loc-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.loc-topbar-spacer {
  min-width: 80rpx;
}

.loc-map-shell {
  position: relative;
  flex: 1;
  min-height: 0;
}

.loc-map {
  width: 100%;
  height: 100%;
}

.loc-bottom {
  flex-shrink: 0;
  padding: 28rpx 32rpx calc(28rpx + env(safe-area-inset-bottom));
  background: var(--q-color-bg-card);
  border-radius: 24rpx 24rpx 0 0;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
}

.loc-place-name {
  font-size: 34rpx;
  font-weight: $weight-semibold;
  color: $color-text;
  line-height: 1.35;
}

.loc-place-address {
  margin-top: 10rpx;
  font-size: $font-sm;
  color: $color-text-muted;
  line-height: 1.4;
}

.loc-place-city {
  margin-top: 8rpx;
  font-size: $font-xs;
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

<template>
  <view class="location-card" @tap="handleOpenLocation">
    <view class="location-title">
      {{ display.title }}
    </view>

    <view class="map-preview">
      <image class="map-image" :src="display.previewImageUrl" mode="aspectFill" />

      <view class="wechat-pin">
        <view class="pin-circle" />
        <view class="pin-line" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildLocationDisplay, openLocationMap } from '@/services/location-message'
import type { components } from '@/api/types/schema'

type LocationInfo = components['schemas']['LocationInfo']

const props = defineProps<{
  location: LocationInfo
}>()

const display = computed(() => buildLocationDisplay(props.location))

function handleOpenLocation() {
  openLocationMap(display.value)
}
</script>

<style scoped lang="scss">
.location-card {
  width: 520rpx;
  overflow: hidden;
  border-radius: 12rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.location-title {
  padding: 22rpx 24rpx 18rpx;
  font-size: 32rpx;
  font-weight: 500;
  color: #111111;
  line-height: 1.3;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.map-preview {
  position: relative;
  width: 100%;
  height: 210rpx;
  overflow: hidden;
  background: #f2f2f2;
}

.map-image {
  width: 100%;
  height: 100%;
}

.wechat-pin {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -70%);
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
}

.pin-circle {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  border: 16rpx solid #19c15f;
  background: #ffffff;
  box-sizing: border-box;
}

.pin-line {
  width: 8rpx;
  height: 44rpx;
  margin-top: -4rpx;
  border-radius: 8rpx;
  background: #19c15f;
}
</style>

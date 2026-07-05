<template>
  <view class="page-header">
    <text class="page-header__title">{{ title }}</text>
    <text v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 页面标题组件
 *
 * 展示页面标题和可选的副标题。
 * 前置条件：无
 * 后置条件：渲染标题和副标题
 */
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page-header {
  margin-bottom: 48rpx;
}

.page-header__title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: $color-text;
  margin-bottom: 12rpx;
}

.page-header__subtitle {
  display: block;
  font-size: 28rpx;
  color: $color-text-sub;
}
</style>

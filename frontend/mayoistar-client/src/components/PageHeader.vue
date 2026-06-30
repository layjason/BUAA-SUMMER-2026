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

<style scoped>
.page-header {
  margin-bottom: 64rpx;
}

.page-header__title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #323233;
  margin-bottom: 16rpx;
}

.page-header__subtitle {
  display: block;
  font-size: 28rpx;
  color: #969799;
}
</style>

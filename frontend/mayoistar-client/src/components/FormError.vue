<template>
  <view v-if="message" class="form-error">
    <text>{{ message }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 表单错误提示组件
 *
 * 当 message 非空时展示错误信息框，为空时自动隐藏。
 * 前置条件：无
 * 后置条件：message 非空时渲染错误框
 */
defineProps<{
  message: string
}>()
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.form-error {
  background-color: rgba(220, 38, 38, 0.06);
  border: 1rpx solid rgba(220, 38, 38, 0.18);
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
  margin-bottom: 24rpx;
  font-size: 26rpx;
  color: $color-danger;
}
</style>

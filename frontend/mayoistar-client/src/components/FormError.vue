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

<style scoped>
.form-error {
  background-color: #fff2f0;
  border: 1rpx solid #ffccc7;
  border-radius: 8rpx;
  padding: 16rpx 24rpx;
  margin-bottom: 24rpx;
  font-size: 26rpx;
  color: #ee0a24;
}
</style>

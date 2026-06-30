<template>
  <view class="page">
    <PageHeader :title="title" />
    <text class="placeholder">功能开发中</text>
  </view>
</template>

<script setup lang="ts">
import { PageHeader } from '@/components'

const title = '功能开发中'
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
  padding: 32rpx;
}
.placeholder {
  display: block;
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  margin-top: 80rpx;
}
</style>

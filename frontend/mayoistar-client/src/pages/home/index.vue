<template>
  <view class="page">
    <view class="placeholder">
      <text class="placeholder-text">首页</text>
      <text class="placeholder-desc">活动信息流即将上线</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 首页 - 活动信息流
 *
 * 展示推荐/最新/附近 Tab 活动列表
 * 前置条件：用户可未登录浏览
 */
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
  padding-bottom: 48rpx;
}
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 200rpx;
}
.placeholder-text {
  font-size: 36rpx;
  color: #323233;
  font-weight: 600;
}
.placeholder-desc {
  font-size: 28rpx;
  color: #969799;
  margin-top: 16rpx;
}
</style>

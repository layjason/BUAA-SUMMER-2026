<template>
  <view class="page">
    <view class="placeholder">
      <text class="placeholder-text">首页</text>
      <text class="placeholder-desc">活动信息流即将上线</text>
    </view>

    <view class="fab" @click="goCreate">
      <text class="fab-icon">+</text>
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

/**
 * 跳转到活动创建页
 */
function goCreate(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
  padding-bottom: 48rpx;
  min-height: 100vh;
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

.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(32rpx + env(safe-area-inset-bottom) + 120rpx);
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background-color: #1989fa;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(25, 137, 250, 0.4);
}

.fab-icon {
  font-size: 56rpx;
  color: #fff;
  line-height: 1;
  margin-top: -4rpx;
}
</style>

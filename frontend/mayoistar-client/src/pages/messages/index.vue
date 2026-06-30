<template>
  <view class="page">
    <view class="placeholder">
      <text class="placeholder-text">消息</text>
      <text class="placeholder-desc">即时通讯即将上线</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 消息页 - 会话列表
 *
 * 前置条件：需登录后使用
 */
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
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

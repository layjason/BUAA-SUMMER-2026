<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="placeholder">
        <text class="placeholder-text">消息</text>
        <text class="placeholder-desc">即时通讯即将上线</text>
      </view>
    </scroll-view>
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
  background-color: #f7f8fa;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
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

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

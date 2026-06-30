<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <text class="placeholder">功能开发中</text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts"></script>

<style scoped>
.page {
  background-color: #f7f8fa;
  padding: 32rpx 32rpx 48rpx;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.placeholder {
  display: block;
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  margin-top: 80rpx;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

<style > page {
  height: 100%;
  overflow: hidden;
}
</style>

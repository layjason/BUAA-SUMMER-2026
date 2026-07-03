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
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.placeholder {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 200rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

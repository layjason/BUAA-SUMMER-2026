<template>
  <view class="page">
    <view class="placeholder">
      <text class="placeholder-text">注册</text>
      <text class="placeholder-desc">注册页面即将在 M2 中实现</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 注册页
 *
 * M2 将在此实现邮箱注册（个人/商家）
 * 前置条件：用户未登录
 * 后置条件：注册成功跳转激活提示页
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

<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="container">
        <view class="header">
          <text class="title">{{ t('resetPassword.title') }}</text>
        </view>

        <view class="success-box">
          <text class="success-icon">✅</text>
          <text class="success-text">{{ t('resetPassword.success') }}</text>
          <text class="success-hint">{{ t('resetPassword.successHint') }}</text>
          <button class="action-btn" @click="goLogin">{{ t('resetPassword.goLogin') }}</button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 重置密码成功页 — 由网页端完成密码重置后通过 scheme 打开
 *
 * 前置条件：网页端已成功调用 POST /identity/auth/password-reset
 * 后置条件：用户点击前往登录后进入登录页
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

function goLogin(): void {
  uni.redirectTo({ url: '/pages/login/index' })
}
</script>

<style scoped>
.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f7f8fa;
}

.container {
  padding-bottom: 48rpx;
  padding: 160rpx 48rpx 0;
}

.header {
  margin-bottom: 56rpx;
}

.title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #323233;
}

.success-box {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 56rpx 32rpx;
  text-align: center;
  margin-bottom: 40rpx;
}

.success-icon {
  display: block;
  font-size: 64rpx;
  margin-bottom: 24rpx;
}

.success-text {
  display: block;
  font-size: 30rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.success-hint {
  display: block;
  font-size: 26rpx;
  color: #969799;
  line-height: 1.6;
  margin-bottom: 32rpx;
}

.action-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background-color: #1989fa;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  border: none;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

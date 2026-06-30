<template>
  <view class="page">
    <!-- 未登录：登录入口 -->
    <view v-if="!authStore.isLoggedIn" class="placeholder">
      <text class="placeholder-text">{{ t('我的') }}</text>
      <text class="placeholder-desc">{{ t('profile.notLoggedInTip') }}</text>
      <button class="login-entry-btn" @click="goLogin">{{ t('登录') }}</button>
    </view>

    <!-- 已登录：个人中心 -->
    <view v-else class="profile-container">
      <view class="user-card">
        <view class="avatar-placeholder">
          <text class="avatar-text">{{ initialChar }}</text>
        </view>
        <view class="user-info">
          <text class="user-id">{{ t('profile.userPrefix') }} {{ authStore.userId }}</text>
          <text class="user-kind">{{
            authStore.userKind === 'merchant' ? t('profile.merchant') : t('profile.personal')
          }}</text>
        </view>
      </view>

      <view class="menu-list">
        <view class="menu-item">
          <text class="menu-text">{{ t('profile.myActivities') }}</text>
          <text class="menu-arrow">&gt;</text>
        </view>
        <view class="menu-item">
          <text class="menu-text">{{ t('profile.myRegistrations') }}</text>
          <text class="menu-arrow">&gt;</text>
        </view>
        <view class="menu-item">
          <text class="menu-text">{{ t('profile.myReviews') }}</text>
          <text class="menu-arrow">&gt;</text>
        </view>
        <view class="menu-item">
          <text class="menu-text">{{ t('profile.settings') }}</text>
          <text class="menu-arrow">&gt;</text>
        </view>
      </view>

      <button class="logout-btn" @click="handleLogout">{{ t('退出登录') }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 我的 - 个人中心
 *
 * 未登录时展示登录入口，已登录时展示用户信息和功能菜单。
 * 前置条件：无
 * 后置条件：登出后清除认证状态并刷新页面状态
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/api'

const { t } = useI18n()
const authStore = useAuthStore()

/**
 * 用户头像首字符
 */
const initialChar = computed(() => {
  if (!authStore.userId) return '?'
  return authStore.userId.charAt(0).toUpperCase()
})

/**
 * 跳转登录页
 */
function goLogin(): void {
  uni.navigateTo({ url: '/pages/login/index' })
}

/**
 * 处理退出登录
 *
 * 调用服务端注销接口作废 Refresh Token，再清除本地认证状态。
 * 前置条件：用户已登录
 * 后置条件：服务端 Token 作废，本地认证状态清除
 */
async function handleLogout(): Promise<void> {
  try {
    await api.post('/identity/auth/logout')
  } catch {
    /* 即使服务端调用失败也清除本地状态 */
  }
  authStore.clearTokens()
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

/* 未登录 */
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

.login-entry-btn {
  margin-top: 48rpx;
  width: 320rpx;
  height: 80rpx;
  line-height: 80rpx;
  background-color: #1989fa;
  color: #fff;
  font-size: 30rpx;
  border-radius: 40rpx;
  border: none;
}

/* 已登录 - 用户卡片 */
.profile-container {
  padding: 32rpx 32rpx 0;
}

.user-card {
  display: flex;
  align-items: center;
  background-color: #fff;
  border-radius: 12rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.avatar-placeholder {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background-color: #1989fa;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}

.avatar-text {
  font-size: 40rpx;
  color: #fff;
  font-weight: 600;
}

.user-info {
  flex: 1;
}

.user-id {
  display: block;
  font-size: 32rpx;
  color: #323233;
  font-weight: 600;
}

.user-kind {
  display: block;
  font-size: 24rpx;
  color: #1989fa;
  margin-top: 8rpx;
}

/* 菜单 */
.menu-list {
  background-color: #fff;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #ebedf0;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-text {
  font-size: 28rpx;
  color: #323233;
}

.menu-arrow {
  font-size: 28rpx;
  color: #c8c9cc;
}

/* 退出按钮 */
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background-color: #fff;
  color: #ee0a24;
  font-size: 30rpx;
  border-radius: 12rpx;
  border: none;
  margin-top: 16rpx;
}
</style>

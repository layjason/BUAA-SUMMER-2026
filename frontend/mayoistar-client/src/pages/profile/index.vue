<template>
  <view class="page">
    <view class="profile-container">
      <!-- 用户卡片：未登录/已登录共用布局 -->
      <view class="user-card" @click="onCardClick">
        <view class="avatar-placeholder">
          <text class="avatar-text">{{ initialChar }}</text>
        </view>
        <view class="user-info">
          <text class="user-id">{{ displayName }}</text>
          <text v-if="authStore.isLoggedIn" class="user-kind">{{
            authStore.userKind === 'merchant' ? t('profile.merchant') : t('profile.personal')
          }}</text>
        </view>
      </view>

      <!-- 菜单：仅登录后可见 -->
      <view v-if="authStore.isLoggedIn">
        <view v-for="section in menuSections" :key="section.title" class="menu-section">
          <text class="menu-section-title">{{ section.title }}</text>
          <view class="menu-list">
            <view
              v-for="item in section.items"
              :key="item.key"
              class="menu-item"
              @click="goToPage(item.route)"
            >
              <text class="menu-text">{{ item.label }}</text>
              <text class="menu-arrow">&gt;</text>
            </view>
          </view>
        </view>
      </view>

      <button v-if="authStore.isLoggedIn" class="logout-btn" @click="handleLogout">
        {{ t('退出登录') }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 我的 - 个人中心
 *
 * 未登录时显示与已登录相同的卡片布局，"点我登录/注册"替代昵称；
 * 首次进入本 Tab 时若未登录则自动跳转登录页。
 * 前置条件：无
 * 后置条件：登出后清除认证状态；未登录时自动跳转登录页
 */
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/api'

const { t } = useI18n()
const authStore = useAuthStore()

/**
 * 防止自动跳转死循环的标志
 *
 * 首次进入本页且未登录时自动跳转登录页并设置此标志。
 * 标志仅在登录成功后重置，避免 navigateTo 异步导致 onHide 误判。
 */
let autoRedirected = false

/**
 * 登录成功后重置自动跳转标志，允许下次未登录时再次触发跳转
 */
watch(
  () => authStore.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      autoRedirected = false
    }
  },
)

/**
 * 菜单分组数据
 */
interface MenuItem {
  key: string
  label: string
  route: string
}

interface MenuSection {
  title: string
  items: MenuItem[]
}

const menuSections: MenuSection[] = [
  {
    title: t('profile.sectionActivities'),
    items: [
      {
        key: 'myActivities',
        label: t('profile.myActivities'),
        route: '/pages/profile/my-activities',
      },
      {
        key: 'myRegistrations',
        label: t('profile.myRegistrations'),
        route: '/pages/profile/my-registrations',
      },
    ],
  },
  {
    title: t('profile.sectionSocial'),
    items: [
      { key: 'myFriends', label: t('profile.myFriends'), route: '/pages/profile/my-friends' },
      {
        key: 'friendRequests',
        label: t('profile.friendRequests'),
        route: '/pages/profile/friend-requests',
      },
      { key: 'myTeams', label: t('profile.myTeams'), route: '/pages/profile/my-teams' },
      { key: 'blacklist', label: t('profile.blacklist'), route: '/pages/profile/blacklist' },
    ],
  },
  {
    title: t('profile.sectionOther'),
    items: [
      { key: 'editProfile', label: t('profile.editProfile'), route: '/pages/profile/edit' },
      { key: 'settings', label: t('profile.settings'), route: '/pages/profile/settings' },
    ],
  },
]

/**
 * 跳转子页面
 *
 * @param route 目标页面路径
 */
function goToPage(route: string): void {
  uni.navigateTo({ url: route })
}

/**
 * 头像首字符
 */
const initialChar = computed(() => {
  if (!authStore.userId) return '?'
  return authStore.userId.charAt(0).toUpperCase()
})

/**
 * 卡片显示名称：已登录显示用户ID，未登录显示引导文案
 */
const displayName = computed(() => {
  if (authStore.isLoggedIn) {
    return t('profile.userPrefix') + ' ' + authStore.userId
  }
  return t('profile.tapToLogin')
})

/**
 * Tab 页面每次显示时检查登录态，未登录则自动跳转登录页
 *
 * 使用 navigateTo 以便用户可从登录页返回本页查看引导卡片。
 * autoRedirected 标志防止返回后再次跳转形成死循环，
 * 标志在登录成功后重置。
 */
onShow(() => {
  if (!authStore.isLoggedIn && !autoRedirected) {
    autoRedirected = true
    uni.navigateTo({ url: '/pages/login/index' })
  }
})

/**
 * 点击用户卡片
 *
 * 未登录时跳转登录页；已登录时暂无操作（预留给后续个人资料编辑入口）
 */
function onCardClick(): void {
  if (!authStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/login/index' })
  }
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
  autoRedirected = false
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

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

.menu-section {
  margin-bottom: 24rpx;
}

.menu-section-title {
  display: block;
  font-size: 26rpx;
  color: #969799;
  padding: 0 4rpx 16rpx;
}

.menu-list {
  background-color: #fff;
  border-radius: 12rpx;
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

<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="profile-container">
        <!-- 用户卡片：未登录/已登录共用布局 -->
        <view class="user-card">
          <view class="user-card-main" @click="onCardClick">
            <UserAvatar
              class="profile-avatar"
              size="lg"
              :avatar-url="avatarUrl"
              :name="nickname"
              :user-id="authStore.userId || ''"
              :initial="initialChar"
            />
            <view class="user-info">
              <view class="user-name-row">
                <text class="user-id">{{ displayName }}</text>
              </view>
              <text v-if="authStore.isLoggedIn" class="user-kind">{{
                authStore.userKind === 'merchant' ? t('profile.merchant') : t('profile.personal')
              }}</text>
              <text v-if="merchantQualificationText" class="qualification-state">{{
                merchantQualificationText
              }}</text>
            </view>
          </view>
          <view v-if="authStore.isLoggedIn" class="qr-btn" @click.stop="openMyQrCode">
            <image class="qr-btn__icon" src="/static/icons/qr-code.png" mode="aspectFit" />
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
    </scroll-view>
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
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/modules/auth'
import { getMerchantProfile } from '@/api/modules/profile'
import { ensureAuthenticatedAccess } from '@/utils/auth-guard'
import { UserAvatar } from '@/components'
import { loadCurrentUserProfileDisplay } from '@/utils/current-user-profile'

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
      { key: 'myFriends', label: t('profile.myFriends'), route: '/pages/social/friends' },
      {
        key: 'friendRequests',
        label: t('profile.friendRequests'),
        route: '/pages/social/friend-requests',
      },
      { key: 'myTeams', label: t('profile.myTeams'), route: '/pages/teams/index' },
      { key: 'blacklist', label: t('profile.blacklist'), route: '/pages/social/blacklist' },
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

/** 打开添加好友页并直接展示「我的二维码」 */
function openMyQrCode(): void {
  uni.navigateTo({ url: '/pages/social/add-friend?showQr=1' })
}

const nickname = ref('')
const avatarUrl = ref('')
const merchantQualificationStatus = ref('')

const qualificationStatusMap: Record<string, string> = {
  not_submitted: '未提交资质',
  pending: '资质审核中',
  approved: '资质已通过',
  rejected: '资质已驳回',
}

async function loadNickname(): Promise<void> {
  if (!authStore.isLoggedIn) return
  try {
    const profile = await loadCurrentUserProfileDisplay()
    nickname.value = profile.nickname
    avatarUrl.value = profile.avatarUrl
    if (authStore.userKind === 'merchant') {
      const merchantProfile = await getMerchantProfile()
      merchantQualificationStatus.value = merchantProfile.qualificationStatus
      return
    }
    merchantQualificationStatus.value = ''
  } catch {
    avatarUrl.value = ''
    merchantQualificationStatus.value = ''
  }
}

/** 清空页面本地展示资料
 *
 * 前置条件：无。
 * 后置条件：昵称、头像和商家资质状态恢复到未登录展示态。
 * 不变量：仅清空本页展示缓存，不修改认证 Store。
 */
function clearProfileDisplayState(): void {
  nickname.value = ''
  avatarUrl.value = ''
  merchantQualificationStatus.value = ''
}

const merchantQualificationText = computed(() => {
  if (!authStore.isLoggedIn || authStore.userKind !== 'merchant') return ''
  return qualificationStatusMap[merchantQualificationStatus.value] ?? ''
})

/**
 * 头像首字符（优先昵称，其次 userId）
 */
const initialChar = computed(() => {
  if (nickname.value) return nickname.value.charAt(0).toUpperCase()
  if (authStore.userId) return authStore.userId.charAt(0).toUpperCase()
  return '?'
})

/**
 * 卡片显示名称：已登录显示昵称，未登录显示引导文案
 */
const displayName = computed(() => {
  if (authStore.isLoggedIn) {
    return nickname.value || t('profile.userPrefix') + ' ' + authStore.userId
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
  if (!authStore.isLoggedIn) {
    clearProfileDisplayState()
  }
  if (!authStore.isLoggedIn && !autoRedirected) {
    autoRedirected = true
    ensureAuthenticatedAccess('/pages/profile/index', () => authStore.isLoggedIn)
  }
  if (authStore.isLoggedIn) loadNickname()
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
    await logout()
  } catch {
    /* 即使服务端调用失败也清除本地状态 */
  }
  authStore.clearTokens()
  clearProfileDisplayState()
  autoRedirected = false
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: $color-bg;
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

.profile-container {
  padding: 32rpx 32rpx calc(120rpx + env(safe-area-inset-bottom));
}

.user-card {
  display: flex;
  align-items: center;
  background-color: $color-bg-card;
  border: 1rpx solid $color-border-light;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  gap: 16rpx;
}

.user-card-main {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.profile-avatar {
  margin-right: 24rpx;
  flex-shrink: 0;
}

.avatar-placeholder {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background-color: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
  flex-shrink: 0;
}

.avatar-image {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  margin-right: 24rpx;
  flex-shrink: 0;
}

.avatar-text {
  font-size: 40rpx;
  color: $color-text-inverse;
  font-weight: 600;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.user-id {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  color: $color-text;
  font-weight: 600;
}

.qr-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
  background-color: $color-bg-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.qr-btn__icon {
  width: 55rpx;
  height: 55rpx;
}

.user-kind {
  display: block;
  font-size: 24rpx;
  color: $color-primary;
  margin-top: 8rpx;
}

.qualification-state {
  display: block;
  font-size: 22rpx;
  color: $color-primary;
  margin-top: 6rpx;
}

.menu-section {
  margin-bottom: 24rpx;
}

.menu-section-title {
  display: block;
  font-size: 26rpx;
  color: $color-text-muted;
  padding: 0 4rpx 16rpx;
}

.menu-list {
  background-color: $color-bg-card;
  border: 1rpx solid $color-border-light;
  border-radius: 24rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid $color-border-light;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-text {
  font-size: 28rpx;
  color: $color-text;
}

.menu-arrow {
  font-size: 28rpx;
  color: $color-text-muted;
}

.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background-color: $color-bg-card;
  color: $color-danger;
  font-size: 30rpx;
  border-radius: 24rpx;
  border: 1rpx solid rgba(220, 38, 38, 0.18);
  margin-top: 16rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

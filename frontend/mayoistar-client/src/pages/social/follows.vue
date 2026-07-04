<template>
  <view class="page">
    <AppNavbar title="关注与粉丝" />

    <!-- Tab Bar -->
    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'follows' }"
        @tap="switchTab('follows')"
      >
        <text class="tab-label">我的关注 ({{ follows.length }})</text>
      </view>
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'followers' }"
        @tap="switchTab('followers')"
      >
        <text class="tab-label">我的粉丝 ({{ followers.length }})</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="currentList.length === 0" class="empty-state">
        <EmptyState
          :title="activeTab === 'follows' ? '暂无关注' : '暂无粉丝'"
          :description="
            activeTab === 'follows' ? '去发现有趣的人并关注他们吧' : '分享你的精彩，吸引粉丝'
          "
        />
      </view>

      <view v-else class="list">
        <view
          v-for="item in currentList"
          :key="item.userId"
          class="list-item"
          @tap="goToProfile(item.userId)"
        >
          <view class="item-avatar">
            <image
              v-if="item.avatar?.signedUrl"
              :src="item.avatar.signedUrl"
              class="item-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="item-avatar-placeholder">👤</text>
          </view>

          <view class="item-info">
            <view class="item-name-row">
              <text class="item-name">{{ item.nickname }}</text>
              <view v-if="isFriend(item.userId)" class="friend-badge">
                <text class="friend-text">已是好友</text>
              </view>
              <view v-else-if="item.mutual" class="mutual-badge">
                <text class="mutual-text">互相关注</text>
              </view>
            </view>
            <text class="item-time">{{ formatDate(item.followedAt) }}</text>
          </view>

          <!-- Action button -->
          <view
            v-if="activeTab === 'follows'"
            class="item-action"
            @tap.stop="confirmUnfollow(item)"
          >
            <text class="action-text action-text--danger">取消关注</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 关注与粉丝页面
 */
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { getFollows, getFollowers, getFriends, unfollowUser } from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type FollowItem = components['schemas']['Social.FollowItem']

const activeTab = ref<'follows' | 'followers'>('follows')
const loading = ref(false)
const follows = ref<FollowItem[]>([])
const followers = ref<FollowItem[]>([])
const friendIds = ref<Set<string>>(new Set())

const currentList = computed(() =>
  activeTab.value === 'follows' ? follows.value : followers.value,
)

function formatDate(iso: string): string {
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

function switchTab(tab: 'follows' | 'followers') {
  activeTab.value = tab
}

function isFriend(userId: string): boolean {
  return friendIds.value.has(userId)
}

async function loadData() {
  loading.value = true
  try {
    const [followsResult, followersResult, friendsResult] = await Promise.all([
      getFollows().catch(() => []),
      getFollowers().catch(() => []),
      getFriends().catch(() => []),
    ])

    const followItems = Array.isArray(followsResult)
      ? followsResult
      : (((followsResult as Record<string, unknown>).items as FollowItem[]) ?? [])
    follows.value = followItems

    const followerItems = Array.isArray(followersResult)
      ? followersResult
      : (((followersResult as Record<string, unknown>).items as FollowItem[]) ?? [])
    followers.value = followerItems

    type FriendItem = components['schemas']['Social.FriendItem']
    const friendItems = Array.isArray(friendsResult)
      ? friendsResult
      : (((friendsResult as Record<string, unknown>).items as FriendItem[]) ?? [])
    friendIds.value = new Set(friendItems.map((f) => f.userId))
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goToProfile(userId: string) {
  uni.navigateTo({ url: `/pages/social/user-profile?id=${userId}` })
}

function confirmUnfollow(item: FollowItem) {
  uni.showModal({
    title: '取消关注',
    content: `确定要取消关注「${item.nickname}」吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          const wasFriend = friendIds.value.has(item.userId)
          await unfollowUser(item.userId)
          follows.value = follows.value.filter((f) => f.userId !== item.userId)
          await loadData()
          const friendshipRemoved = wasFriend && !friendIds.value.has(item.userId)
          uni.showToast({
            title: friendshipRemoved ? '已取消关注并解除好友' : '已取消关注',
            icon: 'success',
          })
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    },
  })
}

onMounted(() => {
  loadData()
})

onShow(() => {
  loadData()
})
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

.tab-bar {
  display: flex;
  background: #ffffff;
  border-bottom: 1px solid $color-border-light;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md 0;
  position: relative;

  &--active {
    .tab-label {
      color: $color-primary;
      font-weight: $weight-semibold;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 40px;
      height: 3px;
      background: $color-primary;
      border-radius: 2px;
    }
  }
}

.tab-label {
  font-size: $font-base;
  color: $color-text-sub;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.loading-text {
  font-size: $font-base;
  color: $color-text-sub;
}

.empty-state {
  padding: $spacing-xl;
}

.list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.list-item {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.item-avatar {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.item-avatar-image {
  width: 100%;
  height: 100%;
}

.item-avatar-placeholder {
  font-size: 20px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: 2px;
}

.item-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
}

.mutual-badge,
.friend-badge {
  padding: 1px $spacing-xs;
  border-radius: $radius-sm;
}

.mutual-badge {
  background: $color-primary-light;
}

.friend-badge {
  background: #e8f0fe;
}

.mutual-text {
  font-size: 10px;
  color: $color-primary;
}

.friend-text {
  font-size: 10px;
  color: #3b82f6;
}

.item-time {
  font-size: $font-xs;
  color: $color-text-sub;
}

.item-action {
  flex-shrink: 0;
}

.action-text {
  font-size: $font-sm;
  font-weight: $weight-medium;

  &--danger {
    color: $color-danger;
  }
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

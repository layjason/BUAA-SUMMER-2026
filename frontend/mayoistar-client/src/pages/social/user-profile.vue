<template>
  <view class="page">
    <AppNavbar :title="profile?.nickname || '用户主页'" />
    <scroll-view class="scroll-area" scroll-y>
      <!-- Loading -->
      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <template v-else-if="profile">
        <!-- Profile Card -->
        <view class="profile-card">
          <view class="profile-header">
            <UserAvatar
              size="xl"
              :avatar-url="profile.avatar?.signedUrl || ''"
              :name="profile.nickname"
              :user-id="profile.userId"
            />

            <view class="profile-main">
              <text class="profile-name">{{ profile.nickname }}</text>
              <text v-if="profile.signature" class="profile-signature">
                {{ profile.signature }}
              </text>
              <view class="profile-badges">
                <text class="profile-badge profile-badge--kind">{{ kindLabel }}</text>
                <text v-if="isMutual && !isFriend" class="profile-badge profile-badge--mutual">
                  互相关注
                </text>
                <text
                  v-if="profile.gender && profile.gender !== 'unspecified'"
                  class="profile-badge"
                >
                  {{ genderLabel }}
                </text>
                <text v-if="profile.birthday" class="profile-badge">{{ profile.birthday }}</text>
              </view>
            </view>
          </view>

          <!-- Interest Tags -->
          <view v-if="profile.interestTags.length > 0" class="profile-tags">
            <text v-for="(tag, idx) in profile.interestTags" :key="idx" class="profile-tag">
              {{ tag }}
            </text>
          </view>

          <!-- Reputation Score -->
          <view class="profile-reputation">
            <text class="profile-reputation-label">信誉分</text>
            <view class="profile-reputation-bar">
              <view
                class="profile-reputation-fill"
                :style="{ width: `${Math.min(profile.reputationScore, 100)}%` }"
              ></view>
            </view>
            <text class="profile-reputation-value">{{ profile.reputationScore }}</text>
          </view>
        </view>

        <!-- Action Buttons（非本人时展示） -->
        <view v-if="!isSelf" class="actions-section">
          <view v-if="blockedByMe" class="relation-hint">
            <text class="relation-hint__text">你已屏蔽该用户</text>
          </view>

          <view class="actions-row">
            <view v-if="showAddFriend" class="action-btn action-btn--primary" @tap="onAddFriend">
              <text class="action-btn-icon">👋</text>
              <text class="action-btn-label">加好友</text>
            </view>
            <view v-else-if="showPendingSent" class="action-btn action-btn--pending">
              <text class="action-btn-icon">⏳</text>
              <text class="action-btn-label">等待确认</text>
            </view>
            <view
              v-else-if="showPendingReceived"
              class="action-btn action-btn--pending action-btn--tap"
              @tap="goFriendRequests"
            >
              <text class="action-btn-icon">📨</text>
              <text class="action-btn-label">待你确认</text>
            </view>

            <view
              v-if="!blockedByMe"
              class="action-btn"
              :class="isFollowing ? 'action-btn--active' : 'action-btn--default'"
              @tap="onToggleFollow"
            >
              <text class="action-btn-icon">{{ isFollowing ? '💖' : '🤍' }}</text>
              <text class="action-btn-label">{{ followLabel }}</text>
            </view>

            <view v-if="isFriend" class="action-btn action-btn--default" @tap="onChat">
              <text class="action-btn-icon">💬</text>
              <text class="action-btn-label">发消息</text>
            </view>
          </view>

          <!-- More actions -->
          <view v-if="!blockedByMe" class="actions-row actions-row--secondary">
            <view class="action-btn-sm action-btn-sm--default" @tap="onReport">
              <text class="action-btn-sm-icon">⚠️</text>
              <text class="action-btn-sm-label">举报</text>
            </view>
            <view class="action-btn-sm action-btn-sm--danger" @tap="onBlock">
              <text class="action-btn-sm-icon">🚫</text>
              <text class="action-btn-sm-label">屏蔽</text>
            </view>
          </view>
        </view>
      </template>

      <!-- Error State -->
      <view v-else class="empty-state">
        <EmptyState title="无法加载" description="该用户不存在或已被屏蔽" />
      </view>

      <view class="bottom-safe"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 用户公开资料页面
 *
 * 查看其他用户公开信息，支持加好友、关注、发消息、举报、屏蔽
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import {
  getUserProfile,
  sendFriendRequest,
  followUser,
  unfollowUser,
  blockUser,
  createReport,
} from '@/api/modules/social'
import { resolveApiError } from '@/utils/error'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import { fetchSocialRelationState, type FriendRequestPending } from '@/utils/social-relation'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type PublicUserProfile = components['schemas']['Identity.PublicUserProfile']
type FriendRequestSource = components['schemas']['Social.FriendRequestSource']

const authStore = useAuthStore()
const profile = ref<PublicUserProfile | null>(null)
const loading = ref(false)
const isFollowing = ref(false)
const isFollowedBy = ref(false)
const isMutual = ref(false)
const isFriend = ref(false)
const blockedByMe = ref(false)
const friendRequestPending = ref<FriendRequestPending>('none')

const userId = ref('')
const friendRequestSource = ref<FriendRequestSource>('profile')

const isSelf = computed(() => {
  const currentId = authStore.userId || ''
  return userId.value === currentId
})

const followLabel = computed(() => {
  if (isMutual.value) return '互相关注'
  if (isFollowing.value) return '已关注'
  if (isFollowedBy.value) return '回关'
  return '关注'
})

const showAddFriend = computed(
  () => !isFriend.value && !blockedByMe.value && friendRequestPending.value === 'none',
)
const showPendingSent = computed(
  () => !isFriend.value && !blockedByMe.value && friendRequestPending.value === 'sent',
)
const showPendingReceived = computed(
  () => !isFriend.value && !blockedByMe.value && friendRequestPending.value === 'received',
)

const kindLabel = computed(() => {
  if (!profile.value) return ''
  const map: Record<string, string> = { personal: '个人用户', business: '商家' }
  return map[profile.value.kind] || profile.value.kind
})

const genderLabel = computed(() => {
  if (!profile.value?.gender) return ''
  const map: Record<string, string> = { male: '♂', female: '♀', unspecified: '' }
  return map[profile.value.gender] || ''
})

async function loadRelationState() {
  try {
    const relation = await fetchSocialRelationState(userId.value)
    isFriend.value = relation.isFriend
    isFollowing.value = relation.isFollowing
    isFollowedBy.value = relation.isFollowedBy
    isMutual.value = relation.isMutual
    blockedByMe.value = relation.blockedByMe
    friendRequestPending.value = relation.friendRequestPending
  } catch {
    // 静默失败，保留默认态
  }
}

function goFriendRequests() {
  uni.navigateTo({ url: '/pages/social/friend-requests' })
}

async function loadProfile() {
  if (!userId.value) return
  loading.value = true
  try {
    const result = await getUserProfile(userId.value)
    profile.value = result as unknown as PublicUserProfile

    await loadRelationState()
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

function onAddFriend() {
  if (!profile.value || !showAddFriend.value) return
  uni.showModal({
    title: '添加好友',
    editable: true,
    placeholderText: '发送一句打招呼的话（可选）',
    success: async (res) => {
      if (res.confirm) {
        try {
          await sendFriendRequest(
            profile.value!.userId,
            res.content || undefined,
            friendRequestSource.value,
          )
          friendRequestPending.value = 'sent'
          uni.showToast({ title: '申请已发送', icon: 'success' })
        } catch (error) {
          uni.showToast({ title: resolveApiError(error, '发送失败'), icon: 'none' })
        }
      }
    },
  })
}

async function onToggleFollow() {
  if (!profile.value || blockedByMe.value) return
  try {
    if (isFollowing.value) {
      const result = await unfollowUser(profile.value.userId)
      isFollowing.value = result.following
      isMutual.value = result.mutual
      uni.showToast({ title: '已取消关注', icon: 'success' })
    } else {
      const result = await followUser(profile.value.userId)
      isFollowing.value = result.following
      isMutual.value = result.mutual
      if (result.friendshipCreated) {
        isFriend.value = true
        friendRequestPending.value = 'none'
        uni.showToast({ title: '互相关注，已成为好友', icon: 'success' })
      } else if (result.mutual) {
        uni.showToast({ title: '已互相关注', icon: 'success' })
      } else {
        uni.showToast({ title: '已关注', icon: 'success' })
      }
    }
  } catch (error) {
    uni.showToast({ title: resolveApiError(error, '操作失败'), icon: 'none' })
  }
}

async function onChat() {
  if (!profile.value) return
  try {
    const conversationId = await resolveFriendConversationId(profile.value.userId)
    if (conversationId) {
      uni.navigateTo({
        url: `/pages/messages/chat?conversationId=${conversationId}&kind=friend`,
      })
      return
    }
  } catch {
    // fallback
  }
  uni.navigateTo({
    url: `/pages/messages/chat?targetUserId=${profile.value.userId}&kind=friend`,
  })
}

function onReport() {
  if (!profile.value || isSelf.value) return
  uni.showModal({
    title: '举报用户',
    editable: true,
    placeholderText: '请描述举报原因',
    success: async (res) => {
      if (!res.confirm) return
      const reason = res.content?.trim()
      if (!reason) {
        uni.showToast({ title: '请填写举报原因', icon: 'none' })
        return
      }
      try {
        await createReport('user', profile.value!.userId, reason)
        uni.showToast({ title: '举报已提交', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: resolveApiError(error, '提交失败'), icon: 'none' })
      }
    },
  })
}

function onBlock() {
  if (!profile.value) return
  uni.showModal({
    title: '屏蔽用户',
    content: `确定要屏蔽「${profile.value.nickname}」吗？屏蔽后将无法互相关注和发送好友申请。`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await blockUser(profile.value!.userId)
          blockedByMe.value = true
          isFollowing.value = false
          isMutual.value = false
          uni.showToast({ title: '已屏蔽', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 1000)
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    },
  })
}

onLoad((query) => {
  userId.value = query?.id || query?.userId || ''
  const source = query?.source as FriendRequestSource | undefined
  if (
    source === 'profile' ||
    source === 'activityParticipants' ||
    source === 'team' ||
    source === 'qrCode'
  ) {
    friendRequestSource.value = source
  }
  if (userId.value) {
    loadProfile()
  }
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: $color-bg;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* ===== Loading & Empty ===== */
.loading-state {
  display: flex;
  justify-content: center;
  padding-top: 200rpx;
}

.loading-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.empty-state {
  padding: $spacing-2xl;
}

/* ===== Profile Card ===== */
.profile-card {
  background: var(--q-color-bg-card);
  margin: $spacing-lg;
  border-radius: $radius-xl;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.profile-header {
  display: flex;
  align-items: flex-start;
  gap: $spacing-lg;
}

.profile-avatar-wrapper {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  margin-right: $spacing-lg;
}

.profile-avatar {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  border: 3px solid var(--q-color-bg-card);
  box-shadow: $shadow-md;
}

.profile-avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-avatar-text {
  font-size: 28px;
  color: $color-primary;
  font-weight: $weight-bold;
}

.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $color-text;
  display: block;
}

.profile-signature {
  font-size: $font-sm;
  color: $color-text-sub;
  display: block;
  margin-top: 4px;
  line-height: 1.5;
}

.profile-badges {
  display: flex;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
  flex-wrap: wrap;
}

.profile-badge {
  font-size: $font-xs;
  padding: 2px $spacing-sm;
  background: rgba(0, 0, 0, 0.06);
  color: $color-text-sub;
  border-radius: $radius-full;

  &--kind {
    background: $color-primary-light;
    color: $color-primary-dark;
  }

  &--mutual {
    background: $color-secondary-light;
    color: $color-secondary;
  }
}

/* ===== Tags ===== */
.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1px solid $color-border-light;
}

.profile-tag {
  font-size: $font-sm;
  padding: $spacing-xs $spacing-md;
  background: $color-secondary-light;
  color: $color-secondary;
  border-radius: $radius-full;
  font-weight: $weight-medium;
}

/* ===== Reputation ===== */
.profile-reputation {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1px solid $color-border-light;
}

.profile-reputation-label {
  font-size: $font-sm;
  color: $color-text-sub;
  flex-shrink: 0;
}

.profile-reputation-bar {
  flex: 1;
  height: 6px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: $radius-full;
  overflow: hidden;
}

.profile-reputation-fill {
  height: 100%;
  background: $color-primary;
  border-radius: $radius-full;
  transition: width 0.5s ease;
}

.profile-reputation-value {
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $color-primary;
  flex-shrink: 0;
}

/* ===== Actions ===== */
.actions-section {
  padding: 0 $spacing-lg $spacing-lg;
}

.actions-row {
  display: flex;
  gap: $spacing-md;
  margin-bottom: $spacing-md;

  &--secondary {
    justify-content: center;
    gap: $spacing-2xl;
  }
}

.action-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-lg;
  border-radius: $radius-xl;
  background: var(--q-color-bg-card);
  box-shadow: $shadow-xs;
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.96);
    box-shadow: none;
  }

  &--primary {
    background: $color-primary;

    .action-btn-label {
      color: var(--q-color-bg-card);
    }
  }

  &--active {
    background: $color-primary-light;

    .action-btn-label {
      color: $color-primary;
    }
  }

  &--default {
    background: var(--q-color-bg-card);
  }

  &--pending {
    background: rgba(0, 0, 0, 0.06);

    .action-btn-label {
      color: $color-text-sub;
    }
  }

  &--tap:active {
    transform: scale(0.96);
    opacity: 0.9;
  }
}

.relation-hint {
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba(0, 0, 0, 0.04);
  border-radius: $radius-lg;
  text-align: center;
}

.relation-hint__text {
  font-size: $font-sm;
  color: $color-text-sub;
}

.action-btn-icon {
  font-size: 24px;
  margin-bottom: $spacing-xs;
}

.action-btn-label {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: $color-text;
}

.action-btn-sm {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  transition: all 0.2s ease;

  &:active {
    opacity: 0.8;
  }

  &--default {
    background: rgba(0, 0, 0, 0.06);
  }

  &--danger {
    background: rgba(220, 38, 38, 0.08);
  }
}

.action-btn-sm-icon {
  font-size: 16px;
}

.action-btn-sm-label {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: $color-text-sub;
}

.action-btn-sm--danger .action-btn-sm-label {
  color: $color-danger;
}

.bottom-safe {
  height: calc($tabbar-height + $safe-bottom);
}
</style>

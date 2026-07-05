<template>
  <view class="page">
    <AppNavbar title="好友申请" />

    <!-- Tab Switcher -->
    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'received' }"
        @tap="switchTab('received')"
      >
        <text class="tab-label">收到的申请</text>
        <view v-if="receivedPendingCount > 0" class="tab-badge">{{ receivedPendingCount }}</view>
      </view>
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'sent' }"
        @tap="switchTab('sent')"
      >
        <text class="tab-label">发出的申请</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <!-- Loading -->
      <view v-if="loading && items.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <!-- Empty -->
      <view v-else-if="!loading && items.length === 0" class="empty-state">
        <EmptyState
          :title="activeTab === 'received' ? '暂无收到的申请' : '暂无发出的申请'"
          :description="activeTab === 'received' ? '暂时没有新的好友申请' : '去添加好友吧！'"
        />
      </view>

      <!-- Request List -->
      <view v-else class="request-list">
        <view v-for="req in items" :key="req.requestId" class="request-item">
          <view class="request-avatar-wrapper" @tap="goToProfile(getCounterpartId(req))">
            <view class="request-avatar-placeholder">
              <text class="request-avatar-text">{{ getCounterpartName(req).charAt(0) }}</text>
            </view>
          </view>

          <view class="request-info" @tap="goToProfile(getCounterpartId(req))">
            <text class="request-name">{{ getCounterpartName(req) }}</text>
            <text v-if="req.message" class="request-message">「{{ req.message }}」</text>
            <view class="request-meta">
              <text class="request-source">{{ getSourceLabel(req.source) }}</text>
              <text class="request-time">{{ formatTime(req.createdAt) }}</text>
            </view>
            <view v-if="req.status !== 'pending'" class="request-status-badge">
              <text class="request-status-text" :class="`request-status-text--${req.status}`">
                {{ getStatusLabel(req.status) }}
              </text>
            </view>
          </view>

          <!-- Actions for received pending requests -->
          <view v-if="activeTab === 'received' && req.status === 'pending'" class="request-actions">
            <view class="btn-accept" @tap="onAccept(req)">
              <text class="btn-text">接受</text>
            </view>
            <view class="btn-reject" @tap="onReject(req)">
              <text class="btn-text btn-text--reject">拒绝</text>
            </view>
          </view>
        </view>

        <view class="bottom-safe"></view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 好友申请页面
 *
 * 展示收到的好友申请（接受/拒绝）和发出的申请
 */
import { ref, computed, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import {
  getReceivedFriendRequests,
  getSentFriendRequests,
  handleFriendRequest,
  getUserProfile,
} from '@/api/modules/social'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import type { components } from '@/api/types/schema'

type FriendRequest = components['schemas']['Social.FriendRequest']

const activeTab = ref<'received' | 'sent'>('received')
const receivedRequests = ref<FriendRequest[]>([])
const sentRequests = ref<FriendRequest[]>([])
const loading = ref(false)
const nicknameCache = ref<Record<string, string>>({})

const items = computed(() =>
  activeTab.value === 'received' ? receivedRequests.value : sentRequests.value,
)

const receivedPendingCount = computed(
  () => receivedRequests.value.filter((r) => r.status === 'pending').length,
)

function switchTab(tab: 'received' | 'sent') {
  activeTab.value = tab
}

/** Get the counterpart user ID (the other person in the request) */
function getCounterpartId(req: FriendRequest): string {
  // For received: the requester; for sent: the target
  return activeTab.value === 'received' ? req.requesterId : req.targetUserId
}

function getCounterpartName(req: FriendRequest): string {
  const id = getCounterpartId(req)
  return nicknameCache.value[id] || `用户 ${id}`
}

function getSourceLabel(source: string): string {
  const map: Record<string, string> = {
    profile: '个人主页',
    activityParticipants: '活动参与者',
    team: '小队',
    qrCode: '二维码',
  }
  return map[source] || '未知'
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待处理',
    accepted: '已接受',
    rejected: '已拒绝',
    canceled: '已撤回',
  }
  return map[status] || status
}

function formatTime(isoStr: string): string {
  const date = new Date(isoStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function goToProfile(userId: string) {
  uni.navigateTo({ url: `/pages/social/user-profile?id=${userId}` })
}

/** 接受申请后引导用户进入单聊 */
async function navigateToFriendChat(userId: string) {
  try {
    const conversationId = await resolveFriendConversationId(userId)
    if (conversationId) {
      uni.navigateTo({
        url: `/pages/messages/chat?conversationId=${conversationId}&kind=friend`,
      })
      return
    }
  } catch {
    // fallback below
  }
  uni.navigateTo({
    url: `/pages/messages/chat?targetUserId=${userId}&kind=friend`,
  })
}

async function onAccept(req: FriendRequest) {
  try {
    await handleFriendRequest(req.requestId, true)
    req.status = 'accepted'
    const counterpartId = req.requesterId
    uni.showModal({
      title: '已添加好友',
      content: '是否立即发送消息？',
      confirmText: '发消息',
      cancelText: '稍后',
      success: (res) => {
        if (res.confirm) {
          navigateToFriendChat(counterpartId)
        }
      },
    })
  } catch {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

async function onReject(req: FriendRequest) {
  uni.showModal({
    title: '拒绝申请',
    content: '确定拒绝该好友申请吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await handleFriendRequest(req.requestId, false)
          req.status = 'rejected'
          uni.showToast({ title: '已拒绝', icon: 'success' })
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    },
  })
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const [received, sent] = await Promise.all([
      getReceivedFriendRequests(),
      getSentFriendRequests(),
    ])
    receivedRequests.value = Array.isArray(received)
      ? received
      : (((received as Record<string, unknown>).items as FriendRequest[]) ?? [])
    sentRequests.value = Array.isArray(sent)
      ? sent
      : (((sent as Record<string, unknown>).items as FriendRequest[]) ?? [])

    // 加载所有申请人的昵称
    const allIds = new Set<string>()
    receivedRequests.value.forEach((r) => allIds.add(r.requesterId))
    sentRequests.value.forEach((r) => r.targetUserId && allIds.add(r.targetUserId))
    await Promise.all(
      [...allIds].map(async (id) => {
        try {
          const profile = await getUserProfile(id)
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          nicknameCache.value[id] = (profile as any).nickname || `用户 ${id}`
        } catch {
          nicknameCache.value[id] = `用户 ${id}`
        }
      }),
    )
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
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
}

/* ===== Tab Bar ===== */
.tab-bar {
  display: flex;
  background: var(--q-color-bg-card);
  border-bottom: 1px solid $color-border-light;
  padding: 0 $spacing-xl;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-lg 0;
  position: relative;
  border-bottom: 3px solid transparent;
  transition: all 0.2s ease;

  &--active {
    border-bottom-color: $color-primary;

    .tab-label {
      color: $color-primary;
      font-weight: $weight-semibold;
    }
  }
}

.tab-label {
  font-size: $font-base;
  color: $color-text-sub;
  font-weight: $weight-medium;
}

.tab-badge {
  min-width: 18px;
  height: 18px;
  background: $color-danger;
  color: var(--q-color-bg-card);
  font-size: 10px;
  font-weight: $weight-semibold;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
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

/* ===== Request List ===== */
.request-list {
  padding: $spacing-sm 0;
}

.request-item {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg $spacing-xl;
  background: var(--q-color-bg-card);
  margin: 0 $spacing-lg $spacing-sm;
  border-radius: $radius-lg;
  box-shadow: $shadow-xs;
}

.request-avatar-wrapper {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  margin-right: $spacing-md;
}

.request-avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  background: $color-secondary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.request-avatar-text {
  font-size: 20px;
  color: $color-secondary;
  font-weight: $weight-semibold;
}

.request-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.request-name {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.request-message {
  font-size: $font-sm;
  color: $color-text-sub;
  display: block;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.request-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: $spacing-xs;
}

.request-source {
  font-size: $font-xs;
  color: $color-text-muted;
}

.request-time {
  font-size: $font-xs;
  color: $color-text-muted;
}

.request-status-badge {
  margin-top: $spacing-xs;
}

.request-status-text {
  font-size: $font-xs;
  font-weight: $weight-medium;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.05);
  color: $color-text-muted;

  &--accepted {
    background: $color-primary-light;
    color: $color-success;
  }

  &--rejected {
    background: rgba(220, 38, 38, 0.08);
    color: $color-danger;
  }

  &--canceled {
    background: rgba(0, 0, 0, 0.05);
    color: $color-text-muted;
  }
}

/* ===== Action Buttons ===== */
.request-actions {
  display: flex;
  gap: $spacing-sm;
  flex-shrink: 0;
  margin-left: $spacing-sm;
  align-items: center;
  padding-top: $spacing-sm;
}

.btn-accept,
.btn-reject,
.btn-cancel {
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-full;
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.95);
    opacity: 0.9;
  }
}

.btn-accept {
  background: $color-primary;
}

.btn-reject {
  background: rgba(0, 0, 0, 0.06);
}

.btn-cancel {
  background: rgba(0, 0, 0, 0.06);
}

.btn-text {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: var(--q-color-bg-card);

  &--reject {
    color: $color-text-sub;
  }

  &--cancel {
    color: $color-text-sub;
  }
}

.bottom-safe {
  height: calc($tabbar-height + $safe-bottom);
}
</style>

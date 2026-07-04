<template>
  <view class="page">
    <!-- Top Bar -->
    <SocialTopBar
      :user-avatar="userAvatar"
      :has-notification="false"
      :pending-requests="pendingRequestsCount"
      @avatar-tap="onAvatarTap"
      @search-tap="onSearchTap"
      @add-friend-tap="showAddFriendMenu"
      @more-tap="onMoreTap"
    />

    <!-- Scrollable Content -->
    <scroll-view class="scroll-area" scroll-y>
      <!-- Quick Cards -->
      <SocialQuickCards
        :team-count="teams.length"
        team-desc="我的小队和发现小队"
        :pending-team-requests="pendingTeamRequests"
        :companion-count="activityCompanions.length"
        companion-desc="可能认识的活动同伴"
        :request-count="pendingRequestsCount"
        request-desc="待处理的好友申请"
        @teams-tap="goToTeams"
        @create-team-tap="goToCreateTeam"
        @companions-tap="goToActivityCompanions"
        @friend-requests-tap="goToFriendRequests"
      />

      <!-- Conversation List -->
      <view class="section-header">
        <text class="section-title">会话</text>
      </view>

      <view v-if="conversations.length === 0" class="empty-state">
        <EmptyState title="暂无会话" description="开始添加好友或加入小队，开启聊天吧！" />
      </view>

      <view v-else class="conversation-list">
        <ConversationCard
          v-for="conv in conversations"
          :key="conv.conversationId"
          :avatar="conv.avatar?.signedUrl || ''"
          :avatar-icon="conv.kind === 'team' ? '👥' : '👤'"
          :name="conv.title"
          :tag="conv.kind === 'team' ? '小队' : '好友'"
          :tag-type="conv.kind"
          :last-message="conv.lastMessagePreview || ''"
          :time="formatTime(conv.updatedAt)"
          :unread-count="conv.unreadCount"
          :is-online="false"
          :show-right-icon="true"
          @tap="openChat(conv)"
        />
      </view>

      <!-- Bottom padding for safe area -->
      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- Floating Create Button -->
    <FloatingCreateButton @tap="showCreateMenu" />

    <!-- Add Friend Action Sheet -->
    <uni-popup
      ref="addFriendPopup"
      type="bottom"
      :safe-area="true"
      @mask-click="closeAddFriendMenu"
    >
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">添加好友</text>
          <text class="action-sheet__close" @tap="closeAddFriendMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToAddFriend('qrCode')">
            <text class="action-sheet__icon">📷</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">扫码加好友</text>
              <text class="action-sheet__desc">扫描个人二维码</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToAddFriend('input')">
            <text class="action-sheet__icon">🔍</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">搜索用户</text>
              <text class="action-sheet__desc">输入用户名、昵称或邮箱</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToMyQRCode">
            <text class="action-sheet__icon">🎫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">我的二维码</text>
              <text class="action-sheet__desc">分享你的个人二维码</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToActivityCompanions">
            <text class="action-sheet__icon"></text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">活动同伴推荐</text>
              <text class="action-sheet__desc">来自相同活动的参与者</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToFriendRequests">
            <text class="action-sheet__icon">📨</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">好友申请</text>
              <text class="action-sheet__desc">{{ pendingRequestsCount }}条待处理</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- More Menu Action Sheet -->
    <uni-popup ref="moreMenuPopup" type="bottom" :safe-area="true" @mask-click="closeMoreMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">更多</text>
          <text class="action-sheet__close" @tap="closeMoreMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToFriends">
            <text class="action-sheet__icon">👫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">好友列表</text>
              <text class="action-sheet__desc">查看和管理所有好友</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToBlacklist">
            <text class="action-sheet__icon">🚫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">黑名单</text>
              <text class="action-sheet__desc">管理已屏蔽用户</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToFollows">
            <text class="action-sheet__icon">💖</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">关注与粉丝</text>
              <text class="action-sheet__desc">查看关注和粉丝列表</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- Create Menu Action Sheet -->
    <uni-popup ref="createMenuPopup" type="bottom" :safe-area="true" @mask-click="closeCreateMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">快速创建</text>
          <text class="action-sheet__close" @tap="closeCreateMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToCreateTeam">
            <text class="action-sheet__icon"></text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">创建小队</text>
              <text class="action-sheet__desc">创建兴趣小队并邀请成员</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="startNewChat">
            <text class="action-sheet__icon">💬</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">发起聊天</text>
              <text class="action-sheet__desc">选择好友开始新对话</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 社交总入口页面 - Social Hub
 *
 * 承载：好友单聊、小队群聊、好友申请、活动同伴推荐、小队入口
 */
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { useChatRealtime, type ChatRealtimeEvent } from '@/composables/useChatRealtime'
import SocialTopBar from '@/components/social/SocialTopBar.vue'
import SocialQuickCards from '@/components/social/SocialQuickCards.vue'
import ConversationCard from '@/components/social/ConversationCard.vue'
import FloatingCreateButton from '@/components/social/FloatingCreateButton.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { getReceivedFriendRequests } from '@/api/modules/social'
import { getTeams } from '@/api/modules/teams'
import { getConversations } from '@/api/modules/chat'
import { useAuthStore } from '@/stores/auth'
import { fetchActivityCompanions } from '@/utils/activity-companions'
import type { components } from '@/api/types/schema'

type FriendRequest = components['schemas']['Social.FriendRequest']
type TeamProfile = components['schemas']['Social.TeamProfile']
type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type MessageCreatedPayload = components['schemas']['Chat.MessageCreatedPayload']
type MessageForwardedPayload = components['schemas']['Chat.MessageForwardedPayload']

// User state
const userAvatar = ref('') // Will be loaded from user store
const loading = ref(false)

// Real data from API
const teams = ref<TeamProfile[]>([])
const pendingRequestsCount = ref(0)
const pendingTeamRequests = ref(0)
const activityCompanions = ref<{ userId: string }[]>([])

// Real conversations from chat API
const conversations = ref<ConversationSummary[]>([])

// Popup refs
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const addFriendPopup = ref<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const moreMenuPopup = ref<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createMenuPopup = ref<any>(null)

// Event handlers
function onAvatarTap() {
  uni.navigateTo({ url: '/pages/profile/index' })
}

function onSearchTap() {
  uni.showToast({ title: '搜索功能开发中', icon: 'none' })
}

function showAddFriendMenu() {
  // 互斥：关闭其他菜单
  moreMenuPopup.value?.close()
  createMenuPopup.value?.close()
  addFriendPopup.value?.open()
}

function closeAddFriendMenu() {
  addFriendPopup.value?.close()
}

function onMoreTap() {
  // 互斥：关闭其他菜单
  addFriendPopup.value?.close()
  createMenuPopup.value?.close()
  moreMenuPopup.value?.open()
}

function closeMoreMenu() {
  moreMenuPopup.value?.close()
}

function showCreateMenu() {
  // 互斥：关闭其他菜单
  addFriendPopup.value?.close()
  moreMenuPopup.value?.close()
  createMenuPopup.value?.open()
}

function closeCreateMenu() {
  createMenuPopup.value?.close()
}

/** 关闭所有底部菜单，避免遮罩层残留阻塞触摸 */
function closeAllPopups() {
  addFriendPopup.value?.close()
  moreMenuPopup.value?.close()
  createMenuPopup.value?.close()
}

// Navigation functions
function goToAddFriend(source: string) {
  closeAddFriendMenu()
  uni.navigateTo({ url: `/pages/social/add-friend?source=${source}` })
}

function goToMyQRCode() {
  closeAddFriendMenu()
  uni.showToast({ title: '二维码功能开发中', icon: 'none' })
}

function goToActivityCompanions() {
  closeAddFriendMenu()
  uni.navigateTo({ url: '/pages/social/activity-companions' })
}

function goToFriendRequests() {
  closeAddFriendMenu()
  uni.navigateTo({ url: '/pages/social/friend-requests' })
}

function goToFriends() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/friends' })
}

function goToBlacklist() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/blacklist' })
}

function goToFollows() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/follows' })
}

function goToTeams() {
  uni.navigateTo({ url: '/pages/teams/index' })
}

function goToCreateTeam() {
  closeCreateMenu()
  uni.navigateTo({ url: '/pages/teams/create' })
}

function openChat(conv: ConversationSummary) {
  uni.navigateTo({
    url: `/pages/messages/chat?conversationId=${conv.conversationId}&kind=${conv.kind}`,
  })
}

function startNewChat() {
  closeCreateMenu()
  uni.showToast({ title: '选择好友功能开发中', icon: 'none' })
}

/** 格式化时间 */
function formatTime(isoTime: string): string {
  const date = new Date(isoTime)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`

  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

/** 加载社交数据 */
async function loadSocialData() {
  loading.value = true
  const authStore = useAuthStore()
  const currentUserId = authStore.userId || '10001'

  try {
    // 并行加载请求、小队、会话、活动同伴
    const [requestsResult, teamsResult, conversationsResult, companionsResult] = await Promise.all([
      getReceivedFriendRequests().catch(() => []),
      getTeams().catch(() => []),
      getConversations().catch(() => []),
      fetchActivityCompanions(currentUserId).catch(() => []),
    ])

    activityCompanions.value = companionsResult

    // 处理好友请求 (只统计 pending 状态)
    const requestItems = Array.isArray(requestsResult)
      ? requestsResult
      : (((requestsResult as Record<string, unknown>).items as FriendRequest[]) ?? [])
    pendingRequestsCount.value = requestItems.filter(
      (req: FriendRequest) => req.status === 'pending',
    ).length

    // 处理小队列表
    const teamItems = Array.isArray(teamsResult)
      ? teamsResult
      : (((teamsResult as Record<string, unknown>).items as TeamProfile[]) ?? [])
    teams.value = teamItems

    // 处理会话列表
    const convItems = Array.isArray(conversationsResult)
      ? conversationsResult
      : (((conversationsResult as Record<string, unknown>).items as ConversationSummary[]) ?? [])
    conversations.value = convItems

    // TODO: 计算待处理的小队加入请求 (Phase 3)
    pendingTeamRequests.value = 0
  } catch (error) {
    console.error('Failed to load social data:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 根据实时聊天事件更新会话列表摘要 */
function handleChatRealtimeEvent(event: ChatRealtimeEvent) {
  if (event.kind !== 'messageCreated' && event.kind !== 'messageForwarded') return

  const payload = event.payload as MessageCreatedPayload | MessageForwardedPayload
  const preview =
    payload.message.text ??
    (payload.message.kind === 'image'
      ? '[图片]'
      : payload.message.kind === 'location'
        ? '[位置]'
        : '')

  const idx = conversations.value.findIndex((c) => c.conversationId === event.conversationId)
  if (idx >= 0) {
    const current = conversations.value[idx]
    conversations.value[idx] = {
      ...current,
      lastMessagePreview: preview || current.lastMessagePreview,
      unreadCount: payload.conversationUnreadCount,
      updatedAt: event.occurredAt,
    }
    conversations.value.sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    )
    return
  }

  void loadSocialData()
}

/** 收到好友申请实时推送时更新待处理数量 */
function handleSocialRealtimeEvent(request: FriendRequest) {
  if (request.status === 'pending') {
    pendingRequestsCount.value += 1
  }
}

const { connect: connectRealtime, close: closeRealtime } = useChatRealtime(
  handleChatRealtimeEvent,
  handleSocialRealtimeEvent,
)

onShow(() => {
  closeAllPopups()
  loadSocialData()
  connectRealtime()
})

onHide(() => {
  closeAllPopups()
  closeRealtime()
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

.scroll-area {
  flex: 1;
  height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* ===== Section Header ===== */
.section-header {
  padding: $spacing-lg $spacing-xl $spacing-sm;
}

.section-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

/* ===== Empty State ===== */
.empty-state {
  padding: $spacing-2xl;
}

/* ===== Conversation List ===== */
.conversation-list {
  padding-top: $spacing-sm;
}

/* ===== Bottom Padding ===== */
.bottom-padding {
  height: calc(72px + $safe-bottom);
}

/* ===== Action Sheet ===== */
.action-sheet {
  background: #ffffff;
  border-radius: $radius-xl $radius-xl 0 0;
  overflow: hidden;
  /* Tab 页底部菜单需抬高，避免被 tabBar 遮挡 */
  padding-bottom: calc($tabbar-height + $safe-bottom);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-lg $spacing-xl;
    border-bottom: 1px solid $color-border-light;
  }

  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
  }

  &__close {
    font-size: 24px;
    color: $color-text-muted;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;

    &:active {
      opacity: 0.7;
    }
  }

  &__items {
    padding: $spacing-md 0;
  }

  &__item {
    display: flex;
    align-items: center;
    padding: $spacing-md $spacing-xl;
    transition: background 0.2s ease;

    &:active {
      background: rgba(0, 0, 0, 0.03);
    }
  }

  &__icon {
    font-size: 24px;
    margin-right: $spacing-md;
    width: 32px;
    text-align: center;
  }

  &__content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__label {
    font-size: $font-base;
    font-weight: $weight-medium;
    color: $color-text;
  }

  &__desc {
    font-size: $font-xs;
    color: $color-text-sub;
  }
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

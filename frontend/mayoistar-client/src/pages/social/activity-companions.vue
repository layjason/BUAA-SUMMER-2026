<template>
  <view class="page">
    <AppNavbar title="活动同伴" />

    <scroll-view class="scroll-area" scroll-y>
      <view class="desc-section">
        <text class="desc-text">认识参加过相同活动的同伴，找到志同道合的朋友</text>
      </view>

      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="companions.length === 0" class="empty-state">
        <EmptyState
          title="暂无推荐"
          description="参加更多活动后，这里会推荐与你参加过相同活动的同伴"
        />
      </view>

      <view v-else class="list">
        <view
          v-for="item in companions"
          :key="item.userId"
          class="list-item"
          @tap="goToProfile(item.userId)"
        >
          <view class="item-avatar">
            <text class="item-avatar-placeholder">{{ item.nickname.charAt(0) }}</text>
          </view>

          <view class="item-info">
            <text class="item-name">{{ item.nickname }}</text>
            <text v-if="item.activityTitle" class="item-source"
              >来自「{{ item.activityTitle }}」</text
            >
          </view>

          <view
            class="item-action"
            :class="{ 'item-action--muted': !canTapAction(item.userId) }"
            @tap.stop="onAddFriendTap(item)"
          >
            <text class="action-text">{{ actionLabel(item.userId) }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动同伴推荐页面
 *
 * 基于「我的报名」+「活动参与者」推导同活动非好友用户（无独立 OpenAPI 路由）。
 */
import { ref, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { sendFriendRequest } from '@/api/modules/social'
import { resolveApiError } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'
import { fetchActivityCompanions, type ActivityCompanionItem } from '@/utils/activity-companions'
import {
  fetchBulkSocialRelationContext,
  resolveFriendListActionState,
  friendListActionLabel,
  canTapFriendListAction,
  type BulkSocialRelationContext,
} from '@/utils/social-relation'

const loading = ref(false)
const companions = ref<ActivityCompanionItem[]>([])
const relationCtx = ref<BulkSocialRelationContext | null>(null)

const authStore = useAuthStore()
const currentUserId = authStore.userId ?? ''

async function loadData() {
  if (!currentUserId) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  loading.value = true
  try {
    const [list, ctx] = await Promise.all([
      fetchActivityCompanions(currentUserId),
      fetchBulkSocialRelationContext(),
    ])
    companions.value = list
    relationCtx.value = ctx
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goToProfile(userId: string) {
  uni.navigateTo({
    url: `/pages/social/user-profile?id=${userId}&source=activityParticipants`,
  })
}

function actionLabel(userId: string): string {
  if (!relationCtx.value) return '加好友'
  const state = resolveFriendListActionState(relationCtx.value, userId, currentUserId)
  return friendListActionLabel(state)
}

function canTapAction(userId: string): boolean {
  if (!relationCtx.value) return true
  const state = resolveFriendListActionState(relationCtx.value, userId, currentUserId)
  return canTapFriendListAction(state)
}

function onAddFriendTap(item: ActivityCompanionItem) {
  if (!canTapAction(item.userId)) return
  if (!relationCtx.value) {
    sendFriendRequest_(item)
    return
  }
  const state = resolveFriendListActionState(relationCtx.value, item.userId, currentUserId)
  if (state === 'pending_received') {
    uni.navigateTo({ url: '/pages/social/friend-requests' })
    return
  }
  sendFriendRequest_(item)
}

async function sendFriendRequest_(item: ActivityCompanionItem) {
  try {
    await sendFriendRequest(
      item.userId,
      '你好，我们一起参加过活动，加个好友吧！',
      'activityParticipants',
    )
    relationCtx.value?.sentPendingIds.add(item.userId)
    uni.showToast({ title: '申请已发送', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: resolveApiError(error, '发送失败'), icon: 'none' })
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

.scroll-area {
  flex: 1;
  height: 0;
}

.desc-section {
  padding: $spacing-lg $spacing-xl $spacing-sm;
}

.desc-text {
  font-size: $font-sm;
  color: $color-text-sub;
  line-height: 1.5;
}

.loading-state {
  padding: $spacing-2xl;
  text-align: center;
}

.loading-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.empty-state {
  padding: $spacing-xl;
}

.list {
  padding: 0 $spacing-xl;
}

.list-item {
  display: flex;
  align-items: center;
  padding: $spacing-md 0;
  border-bottom: 1px solid $color-border-light;
}

.item-avatar {
  width: 44px;
  height: 44px;
  margin-right: $spacing-md;
  flex-shrink: 0;
}

.item-avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-primary-dark;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
  display: block;
}

.item-source {
  font-size: $font-xs;
  color: $color-text-sub;
  margin-top: 2px;
  display: block;
}

.item-action {
  padding: $spacing-xs $spacing-md;
  background: $color-primary;
  border-radius: $radius-full;
  flex-shrink: 0;

  &--muted {
    background: rgba(0, 0, 0, 0.08);

    .action-text {
      color: $color-text-sub;
    }
  }
}

.action-text {
  font-size: $font-sm;
  color: #ffffff;
  font-weight: $weight-medium;
}

.bottom-padding {
  height: calc($spacing-2xl + $safe-bottom);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

<template>
  <view class="page">
    <view v-if="loading" class="state-text">{{ t('加载中') }}</view>

    <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>

    <view v-else-if="items.length === 0" class="state-text">{{ t('暂无数据') }}</view>

    <scroll-view
      v-else
      class="scroll-area"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view v-for="item in items" :key="item.registrationId" class="card">
        <view class="card-left" @tap="goToProfile(item)">
          <UserAvatar
            size="md"
            :avatar="item.avatar"
            :name="item.nickname"
            :user-id="item.userId"
          />
          <text class="nickname">{{ item.nickname }}</text>
        </view>
        <view class="card-right">
          <text class="status-tag" :class="'status-' + item.registrationStatus">{{
            registrationStatusText(item.registrationStatus)
          }}</text>
          <view
            v-if="getActionState(item.userId) !== 'self'"
            class="add-friend-btn"
            :class="'add-friend-btn--' + getActionState(item.userId)"
            @tap="onFriendActionTap(item)"
          >
            <text class="add-friend-text">{{ getActionLabel(item.userId) }}</text>
          </view>
        </view>
      </view>

      <view v-if="loadingMore" class="load-more">{{ t('加载中') }}</view>
      <view v-else-if="noMore" class="load-more">{{ t('已加载全部') }}</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 参与者列表页
 *
 * 展示活动所有参与者及其报名/签到状态，支持分页加载和下拉刷新。
 * 前置条件：activityId 通过 query 传入
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { getActivityDetail, getParticipants, getParticipationState } from '@/api/modules/activities'
import { sendFriendRequest } from '@/api/modules/social'
import { useAuthStore } from '@/stores/auth'
import { canViewActivityParticipants } from '@/utils/activity-participants'
import type { components } from '@/api/types/schema'
import { getErrorMessage } from '@/utils/error'
import UserAvatar from '@/components/base/UserAvatar.vue'
import {
  fetchBulkSocialRelationContext,
  resolveFriendListActionState,
  friendListActionLabel,
  canTapFriendListAction,
  type BulkSocialRelationContext,
  type FriendListActionState,
} from '@/utils/social-relation'
const { t } = useI18n()

const activityId = ref('')
const loading = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)
const errorMsg = ref('')

interface ParticipantItem {
  registrationId: string
  userId: string
  nickname: string
  registrationStatus: string
  avatar?: components['schemas']['MediaFile']
}

const authStore = useAuthStore()
const currentUserId = authStore.userId ?? ''

const items = ref<ParticipantItem[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const noMore = ref(false)
const PAGE_SIZE = 20
const relationCtx = ref<BulkSocialRelationContext | null>(null)

const statusTextMap: Record<string, string> = {
  registered: t('myRegistrations.statusRegistered'),
  checkedIn: t('myRegistrations.statusCheckedIn'),
  canceled: t('myRegistrations.statusCanceled'),
  waiting: t('myRegistrations.statusWaiting'),
  waitingConfirmation: t('myRegistrations.statusWaitingConfirmation'),
}

function registrationStatusText(status: string): string {
  return statusTextMap[status] ?? status
}

/**
 * 加载参与者列表
 *
 * @param page 目标页码
 * @param reset 是否重置列表
 */
/**
 * 加载与参与者列表相关的好友/黑名单/申请关系索引
 */
async function loadRelationContext(): Promise<void> {
  try {
    relationCtx.value = await fetchBulkSocialRelationContext()
  } catch {
    relationCtx.value = null
  }
}

function getActionState(userId: string): FriendListActionState {
  if (!relationCtx.value) return userId === currentUserId ? 'self' : 'add'
  return resolveFriendListActionState(relationCtx.value, userId, currentUserId)
}

function getActionLabel(userId: string): string {
  return friendListActionLabel(getActionState(userId))
}

function canTapAction(userId: string): boolean {
  return canTapFriendListAction(getActionState(userId))
}

async function loadData(page: number, reset = false): Promise<void> {
  if (reset) {
    noMore.value = false
    loading.value = true
    await loadRelationContext()
  }

  try {
    const result = (await getParticipants(activityId.value, page, PAGE_SIZE)) as {
      items: ParticipantItem[]
      page: number
      totalPages: number
    }

    if (reset) {
      items.value = result.items
    } else {
      items.value.push(...result.items)
    }
    currentPage.value = result.page
    totalPages.value = result.totalPages
    noMore.value = result.page >= result.totalPages
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value =
        error.code === 20003 ? '请先报名参与活动后查看参与者列表' : getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载参与者列表失败')
    }
  } finally {
    loading.value = false
  }
}

/**
 * 下拉刷新
 */
async function onRefresh(): Promise<void> {
  refreshing.value = true
  errorMsg.value = ''
  await loadData(1, true)
  refreshing.value = false
}

/**
 * 上拉加载更多
 */
async function onLoadMore(): Promise<void> {
  if (loadingMore.value || noMore.value || loading.value) return
  loadingMore.value = true
  await loadData(currentPage.value + 1)
  loadingMore.value = false
}

onLoad(async (query) => {
  activityId.value = (query?.activityId as string) ?? ''
  if (!activityId.value) {
    errorMsg.value = '缺少活动标识'
    loading.value = false
    return
  }
  if (!authStore.isLoggedIn) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  try {
    const [detail, participation] = await Promise.all([
      getActivityDetail(activityId.value),
      getParticipationState(activityId.value),
    ])
    if (
      !canViewActivityParticipants({
        isLoggedIn: true,
        organizerId: detail.organizerId,
        userId: authStore.userId,
        participation,
      })
    ) {
      errorMsg.value = '请先报名参与活动后查看参与者列表'
      loading.value = false
      return
    }
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载活动信息失败')
    }
    loading.value = false
    return
  }
  await loadData(1, true)
})

/** 跳转到用户资料 */
function goToProfile(item: ParticipantItem) {
  if (item.userId) {
    uni.navigateTo({
      url: `/pages/social/user-profile?id=${item.userId}&source=activityParticipants`,
    })
  }
}

/** 处理参与者行的加好友相关操作 */
function onFriendActionTap(item: ParticipantItem) {
  if (!item.userId || !canTapAction(item.userId)) return
  const state = getActionState(item.userId)
  if (state === 'pending_received') {
    uni.navigateTo({ url: '/pages/social/friend-requests' })
    return
  }
  sendFriendRequest_(item)
}

/** 发送好友申请并更新本地关系索引 */
async function sendFriendRequest_(item: ParticipantItem) {
  if (!item.userId) return
  if (!currentUserId) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  try {
    await sendFriendRequest(
      item.userId,
      '你好，我们一起参加了活动，加个好友吧！',
      'activityParticipants',
    )
    relationCtx.value?.sentPendingIds.add(item.userId)
    uni.showToast({ title: '好友请求已发送', icon: 'success' })
  } catch (error) {
    const code = (error as { code?: number }).code ?? 0
    uni.showToast({ title: getErrorMessage(code, '发送失败'), icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  background-color: var(--q-color-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.state-text {
  text-align: center;
  font-size: 28rpx;
  color: var(--q-color-text-muted);
  padding-top: 120rpx;
}

.state-text--error {
  color: var(--q-color-danger);
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.card {
  background-color: var(--q-color-bg-card);
  margin: 16rpx 32rpx;
  padding: 24rpx 32rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.card-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.nickname {
  font-size: 28rpx;
  color: var(--q-color-text);
}

.status-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}

.status-registered {
  background-color: var(--q-color-primary-light);
  color: var(--q-color-primary);
}

.status-checkedIn {
  background-color: var(--q-color-primary-light);
  color: var(--q-color-success);
}

.status-canceled {
  background-color: var(--q-color-bg-soft);
  color: var(--q-color-text-muted);
}

.status-waiting,
.status-waitingConfirmation {
  background-color: var(--q-color-accent-light);
  color: var(--q-color-warning);
}

.add-friend-btn {
  padding: 6rpx 16rpx;
  border-radius: 6rpx;
}

.add-friend-btn--add,
.add-friend-btn--pending_received {
  background: var(--q-gradient-primary);
}

.add-friend-btn--friend {
  background: var(--q-color-primary-dark);
}

.add-friend-btn--pending_sent {
  background: var(--q-color-warning);
}

.add-friend-btn--blocked {
  background: #6b7280;
}

.add-friend-text {
  font-size: 22rpx;
  color: #ffffff;
  font-weight: 500;
}

.load-more {
  text-align: center;
  font-size: 24rpx;
  color: var(--q-color-text-muted);
  padding: 24rpx 0;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

<template>
  <view class="page">
    <scroll-view
      class="scroll-area"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="loading" class="loading-text">{{ t('加载中') }}</view>

      <view v-else-if="errorMsg" class="error-text">{{ errorMsg }}</view>

      <view v-else-if="items.length === 0" class="empty-text">{{ t('暂无数据') }}</view>

      <view v-else>
        <view
          v-for="item in items"
          :key="item.registrationId"
          class="card"
          :class="'card-' + item.registrationStatus"
          hover-class="card-hover"
          @click="goDetail(item.activityId)"
        >
          <view class="card-inner">
            <image
              v-if="item.coverImage?.signedUrl"
              class="card-cover"
              :src="getMediaUrl(item.coverImage.signedUrl)"
              mode="aspectFill"
            />
            <view v-else class="card-cover card-cover-placeholder">
              <text class="placeholder-icon">📋</text>
            </view>

            <view class="card-body">
              <view class="card-header-row">
                <text class="card-title">{{ item.title }}</text>
                <text class="status-tag" :class="'status-' + item.registrationStatus">{{
                  statusText(item.registrationStatus)
                }}</text>
              </view>

              <view v-if="item.tags.length > 0" class="card-tags">
                <text v-for="tag in item.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</text>
              </view>

              <view class="card-meta">
                <text class="meta-item">{{ formatDate(item.startAt) }}</text>
                <text class="meta-sep">|</text>
                <text class="meta-item">{{ item.location.city }}</text>
              </view>

              <view class="card-bottom">
                <view class="card-bottom-left">
                  <text class="fee" :class="{ free: !item.feeAmount }">{{
                    item.feeAmount ? '¥' + item.feeAmount : t('activityDetail.free')
                  }}</text>
                  <text class="registered">{{ item.registeredCount }}/{{ item.capacity }}人</text>
                </view>
                <text class="meta-item" style="font-size: 20rpx">{{
                  t('myRegistrations.registrationTime') + ' ' + formatDate(item.registeredAt)
                }}</text>
              </view>
            </view>
          </view>
        </view>
      </view></scroll-view
    >
  </view>
</template>

<script setup lang="ts">
/**
 * 我的报名
 *
 * 展示用户已报名的活动列表及报名状态。
 * API: GET /activities/registrations/mine
 * 前置条件：用户已登录
 * 后置条件：加载成功后展示报名数据
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { getMyRegistrations } from '@/api/modules/registrations'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'
import { toAbsoluteMediaUrl } from '@/utils/media-preview'

const { t } = useI18n()

const loading = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)
const errorMsg = ref('')
const currentPage = ref(1)
const noMoreData = ref(false)
const pageSize = 20

const statusMap: Record<string, string> = {
  registered: t('myRegistrations.statusRegistered'),
  checkedIn: t('myRegistrations.statusCheckedIn'),
  canceled: t('myRegistrations.statusCanceled'),
  waiting: t('myRegistrations.statusWaiting'),
  waitingConfirmation: t('myRegistrations.statusWaitingConfirmation'),
}

interface RegistrationItem {
  registrationId: string
  activityId: string
  title: string
  tags: string[]
  startAt: string
  endAt: string
  location: { city: string; address: string; placeName?: string }
  coverImage: { signedUrl: string; mediaId: string } | null
  feeAmount?: number
  capacity: number
  registeredCount: number
  registrationStatus: string
  registeredAt: string
  runtimeStatus: string
  waitingRank?: number
  confirmationDeadline?: string
}

const items = ref<RegistrationItem[]>([])

/**
 * 加载当前用户报名记录
 *
 * 前置条件：用户已登录，mock/真实请求层已初始化。
 * 后置条件：items 更新为 OpenAPI RegisteredActivitySummary 分页结果。
 * 不变量：页面只通过 registrations API 模块访问业务接口。
 */
async function loadData(page = 1, append = false): Promise<void> {
  if (!append) loading.value = true
  errorMsg.value = ''
  try {
    const result = await getMyRegistrations(page, pageSize)
    const nextItems = (result.items ?? []) as RegistrationItem[]
    items.value = append ? [...items.value, ...nextItems] : nextItems
    currentPage.value = result.page ?? page
    noMoreData.value = currentPage.value >= (result.totalPages ?? currentPage.value)
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  } finally {
    if (!append) loading.value = false
  }
}

/**
 * 下拉刷新报名记录
 *
 * 前置条件：用户触发 scroll-view refresher。
 * 后置条件：刷新成功时替换 items，失败时保留现有列表。
 * 不变量：刷新过程不改变当前页面路由。
 */
async function onRefresh(): Promise<void> {
  refreshing.value = true
  errorMsg.value = ''
  noMoreData.value = false
  try {
    await loadData(1, false)
  } catch {
    /* 静默 */
  } finally {
    refreshing.value = false
  }
}

/** 触底加载下一页报名记录 */
async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || noMoreData.value) return
  loadingMore.value = true
  try {
    await loadData(currentPage.value + 1, true)
  } finally {
    loadingMore.value = false
  }
}

onShow(() => {
  loadData()
})

/**
 * 获取报名状态展示文本
 *
 * 前置条件：status 来自 OpenAPI RegistrationStatus。
 * 后置条件：返回本地化展示文本或原始状态值。
 * 不变量：不修改报名状态。
 */
function statusText(status: string): string {
  return statusMap[status] ?? status
}

/**
 * 跳转活动详情页
 *
 * 前置条件：activityId 非空。
 * 后置条件：通过 uni.navigateTo 进入活动详情。
 * 不变量：不直接读取活动详情数据。
 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/** 获取 App 可直接渲染的媒体地址 */
function getMediaUrl(signedUrl: string): string {
  return toAbsoluteMediaUrl(signedUrl)
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

.loading-text,
.error-text,
.empty-text {
  text-align: center;
  font-size: 28rpx;
  color: var(--q-color-text-muted);
  padding-top: 120rpx;
}

.error-text {
  color: $color-danger;
}

.card {
  background-color: $color-bg-card;
  margin: 16rpx 32rpx;
  border: 1rpx solid $color-border-light;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.card-hover {
  opacity: 0.85;
}

.card-canceled {
  opacity: 0.65;
}

.card-checkedIn {
  border-left: 6rpx solid $color-primary;
}

.card-inner {
  display: flex;
  flex-direction: row;
}

.card-cover {
  width: 200rpx;
  height: 160rpx;
  flex-shrink: 0;
}

.card-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $color-bg-soft;
}

.placeholder-icon {
  font-size: 44rpx;
}

.card-body {
  flex: 1;
  padding: 16rpx 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8rpx;
}

.card-title {
  font-size: 28rpx;
  color: $color-text;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
}

.status-registered {
  background-color: $color-secondary-light;
  color: $color-info;
}

.status-checkedIn {
  background-color: $color-primary-light;
  color: $color-primary;
}

.status-canceled {
  background-color: $color-bg-soft;
  color: $color-text-muted;
}

.status-waiting,
.status-waitingConfirmation {
  background-color: var(--q-color-accent-light);
  color: var(--q-color-warning);
}

.card-tags {
  display: flex;
  gap: 6rpx;
  flex-wrap: wrap;
  margin-top: 6rpx;
}

.tag {
  font-size: 20rpx;
  color: $color-primary-dark;
  background-color: $color-primary-light;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 6rpx;
}

.meta-item {
  font-size: 22rpx;
  color: var(--q-color-text-muted);
}

.meta-sep {
  font-size: 18rpx;
  color: var(--q-color-text-muted);
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6rpx;
}

.card-bottom-left {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.fee {
  font-size: 24rpx;
  color: var(--q-color-danger);
  font-weight: 600;
}

.fee.free {
  color: var(--q-color-success);
}

.registered {
  font-size: 20rpx;
  color: var(--q-color-text-muted);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

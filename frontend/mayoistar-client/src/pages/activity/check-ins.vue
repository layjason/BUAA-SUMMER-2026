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
        <view class="card-left">
          <view class="avatar-placeholder">
            <text class="avatar-text">{{ item.nickname.charAt(0).toUpperCase() }}</text>
          </view>
          <view class="info-col">
            <text class="nickname">{{ item.nickname }}</text>
            <text v-if="item.checkedInAt" class="checkin-time">
              {{ formatDateTime(item.checkedInAt) }}
            </text>
          </view>
        </view>
        <text class="status-tag" :class="'status-' + item.registrationStatus">
          {{ registrationStatusText(item.registrationStatus) }}
        </text>
      </view>

      <view v-if="loadingMore" class="load-more">{{ t('加载中') }}</view>
      <view v-else-if="noMore" class="load-more">{{ t('已加载全部') }}</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 签到管理页
 *
 * 展示活动签到记录，支持分页加载和下拉刷新。
 * 前置条件：用户为活动发起人，activityId 通过 query 传入
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { getCheckIns } from '@/api/modules/checkin'
import { getErrorMessage } from '@/utils/error'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()

const activityId = ref('')
const loading = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)
const errorMsg = ref('')

interface CheckInItem {
  registrationId: string
  nickname: string
  registrationStatus: string
  checkedInAt?: string
  userId: string
}

const items = ref<CheckInItem[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const noMore = ref(false)

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
 * 加载签到列表
 *
 * 调用 getCheckIns API 获取活动的所有签到记录。
 * Mock 模式下一次性返回全部数据，不支持分页。
 */
async function loadData(): Promise<void> {
  noMore.value = false
  loading.value = true

  try {
    const result = (await getCheckIns(activityId.value)) as {
      items: CheckInItem[]
      page: number
      totalPages: number
    }

    items.value = result.items
    currentPage.value = result.page ?? 1
    totalPages.value = result.totalPages ?? 1
    noMore.value = true
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载签到记录失败')
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
  await loadData()
  refreshing.value = false
}

/**
 * 上拉加载更多（当前 mock 模式不支持分页）
 */
function onLoadMore(): void {
  // Mock 模式下一次性加载全部，无需分页
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  if (!activityId.value) {
    errorMsg.value = '缺少活动标识'
    loading.value = false
    return
  }
  loadData()
})
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.state-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.state-text--error {
  color: #ee0a24;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.card {
  background-color: #fff;
  margin: 16rpx 32rpx;
  padding: 24rpx 32rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar-placeholder {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: #5ec8a7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}

.info-col {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.nickname {
  font-size: 28rpx;
  color: #323233;
}

.checkin-time {
  font-size: 22rpx;
  color: #969799;
}

.status-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
  flex-shrink: 0;
}

.status-registered {
  background-color: rgba(94, 200, 167, 0.1);
  color: #5ec8a7;
}

.status-checkedIn {
  background-color: #ebf9e9;
  color: #07c160;
}

.status-canceled {
  background-color: #ebedf0;
  color: #969799;
}

.status-waiting,
.status-waitingConfirmation {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.load-more {
  text-align: center;
  font-size: 24rpx;
  color: #969799;
  padding: 24rpx 0;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

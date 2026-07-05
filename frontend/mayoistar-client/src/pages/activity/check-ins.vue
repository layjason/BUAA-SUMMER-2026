<template>
  <view class="page">
    <view class="filter-bar">
      <button
        v-for="option in filterOptions"
        :key="option.value"
        class="filter-chip"
        :class="{ 'filter-chip-active': statusFilter === option.value }"
        @click="setStatusFilter(option.value)"
      >
        {{ option.label }}
      </button>
    </view>

    <view v-if="loading" class="state-text">{{ t('加载中') }}</view>

    <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>

    <view v-else-if="filteredItems.length === 0" class="state-text">暂无匹配的签到记录</view>

    <scroll-view
      v-else
      class="scroll-area"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-num">{{ checkedInCount }}</text>
          <text class="summary-label">已签到</text>
        </view>
        <view class="summary-divider"></view>
        <view class="summary-item">
          <text class="summary-num">{{ registeredCount }}</text>
          <text class="summary-label">待签到</text>
        </view>
        <view class="summary-divider"></view>
        <view class="summary-item">
          <text class="summary-num">{{ waitingCount }}</text>
          <text class="summary-label">候补</text>
        </view>
      </view>

      <view v-for="item in filteredItems" :key="item.registrationId" class="card">
        <view class="card-left">
          <view class="avatar-placeholder">
            <text class="avatar-text">{{ avatarInitial(item.nickname) }}</text>
          </view>
          <view class="info-col">
            <text class="nickname">{{ item.nickname }}</text>
            <text class="meta-text">报名编号 {{ item.registrationId }}</text>
            <text v-if="item.checkedInAt" class="checkin-time">
              签到时间 {{ formatDateTime(item.checkedInAt) }}
            </text>
            <text v-else class="checkin-time checkin-time-muted">尚未签到</text>
          </view>
        </view>
        <text class="status-tag" :class="'status-' + item.registrationStatus">
          {{ registrationStatusText(item.registrationStatus) }}
        </text>
      </view>

      <view v-if="loadingMore" class="load-more">{{ t('加载中') }}</view>
      <view v-else-if="noMore" class="load-more">{{ t('已加载全部') }}</view>
    </scroll-view>

    <BottomActionBar>
      <button
        class="export-btn"
        :disabled="exporting || loading || Boolean(errorMsg)"
        :loading="exporting"
        @click="handleExport"
      >
        导出签到数据
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
/**
 * 签到管理页。
 *
 * 展示活动签到记录，支持状态筛选、分页刷新和导出签到数据。
 * 前置条件：用户为活动发起人，activityId 通过 query 传入。
 * 后置条件：列表与导出均通过 src/api/modules/checkin 访问 OpenAPI 路由。
 * 不变量：筛选只在前端展示层处理，不改变后端签到状态。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { exportCheckIns, getCheckIns } from '@/api/modules/checkin'
import { BottomActionBar } from '@/components'
import { getErrorMessage } from '@/utils/error'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()

const activityId = ref('')
const loading = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)
const exporting = ref(false)
const errorMsg = ref('')
const statusFilter = ref<CheckInFilter>('all')

interface CheckInItem {
  registrationId: string
  nickname: string
  registrationStatus: string
  checkedInAt?: string
  userId: string
}

type CheckInFilter = 'all' | 'checkedIn' | 'registered' | 'waiting'
type ExportPayload = string | { url?: string }

const items = ref<CheckInItem[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const noMore = ref(false)

const filterOptions: Array<{ label: string; value: CheckInFilter }> = [
  { label: '全部', value: 'all' },
  { label: '已签到', value: 'checkedIn' },
  { label: '待签到', value: 'registered' },
  { label: '候补', value: 'waiting' },
]

const statusTextMap: Record<string, string> = {
  registered: t('myRegistrations.statusRegistered'),
  checkedIn: t('myRegistrations.statusCheckedIn'),
  canceled: t('myRegistrations.statusCanceled'),
  waiting: t('myRegistrations.statusWaiting'),
  waitingConfirmation: t('myRegistrations.statusWaitingConfirmation'),
}

/** 筛选后的签到记录。 */
const filteredItems = computed(() => {
  if (statusFilter.value === 'all') return items.value
  if (statusFilter.value === 'waiting') {
    return items.value.filter(
      (item) =>
        item.registrationStatus === 'waiting' || item.registrationStatus === 'waitingConfirmation',
    )
  }
  return items.value.filter((item) => item.registrationStatus === statusFilter.value)
})

/** 已签到人数。 */
const checkedInCount = computed(
  () => items.value.filter((item) => item.registrationStatus === 'checkedIn').length,
)

/** 待签到人数。 */
const registeredCount = computed(
  () => items.value.filter((item) => item.registrationStatus === 'registered').length,
)

/** 候补人数。 */
const waitingCount = computed(
  () =>
    items.value.filter(
      (item) =>
        item.registrationStatus === 'waiting' || item.registrationStatus === 'waitingConfirmation',
    ).length,
)

/**
 * 获取报名状态文案。
 *
 * 前置条件：status 来自 OpenAPI RegistrationStatus。
 * 后置条件：返回中文展示文案。
 * 不变量：未知状态原样展示。
 */
function registrationStatusText(status: string): string {
  return statusTextMap[status] ?? status
}

/**
 * 获取头像占位首字。
 *
 * 前置条件：nickname 可为空。
 * 后置条件：返回一个可展示字符。
 * 不变量：不修改用户数据。
 */
function avatarInitial(nickname: string): string {
  return (nickname.trim().charAt(0) || '?').toUpperCase()
}

/**
 * 切换状态筛选。
 *
 * 前置条件：filter 来自页面定义的筛选枚举。
 * 后置条件：更新当前筛选条件。
 * 不变量：只影响本地列表展示。
 */
function setStatusFilter(filter: CheckInFilter): void {
  statusFilter.value = filter
}

/**
 * 加载签到列表。
 *
 * 前置条件：activityId 非空。
 * 后置条件：刷新签到列表、分页状态和空态。
 * 不变量：只读取 OpenAPI 签到列表接口。
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
 * 下拉刷新。
 *
 * 前置条件：页面已初始化 activityId。
 * 后置条件：重新加载签到列表。
 * 不变量：刷新不改变筛选条件。
 */
async function onRefresh(): Promise<void> {
  refreshing.value = true
  errorMsg.value = ''
  await loadData()
  refreshing.value = false
}

/**
 * 上拉加载更多。
 *
 * 前置条件：滚动到底部。
 * 后置条件：当前 mock 一次性返回全部数据，因此不追加请求。
 * 不变量：不改变现有列表。
 */
function onLoadMore(): void {
  // 当前 mock 模式下一次性加载全部，后续接入真实分页时再追加请求。
}

/**
 * 导出签到数据。
 *
 * 前置条件：activityId 非空。
 * 后置条件：URL 导出尝试下载并打开；CSV 文本导出复制到剪贴板供演示使用。
 * 不变量：只调用 OpenAPI 定义的导出 path，不构造额外正式业务路由。
 */
async function handleExport(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const payload = (await exportCheckIns(activityId.value)) as ExportPayload
    if (typeof payload === 'string' && /^https?:\/\//.test(payload)) {
      await downloadExportFile(payload)
      return
    }
    if (typeof payload !== 'string' && payload.url) {
      await downloadExportFile(payload.url)
      return
    }
    const csvContent = typeof payload === 'string' ? payload : buildLocalCsv()
    await uni.setClipboardData({ data: csvContent })
    uni.showToast({ title: '签到数据已复制，可粘贴保存', icon: 'none' })
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '导出失败，请稍后重试', icon: 'none' })
    }
  } finally {
    exporting.value = false
  }
}

/**
 * 下载并打开导出文件。
 *
 * 前置条件：url 为 HTTP(S) 文件地址。
 * 后置条件：支持文件系统的平台打开临时文件。
 * 不变量：失败时由调用方统一提示。
 */
async function downloadExportFile(url: string): Promise<void> {
  const downloadResult = await uni.downloadFile({ url })
  if (downloadResult.statusCode && downloadResult.statusCode >= 400) {
    throw new Error('download failed')
  }
  await uni.openDocument({
    filePath: downloadResult.tempFilePath,
    showMenu: true,
  })
}

/**
 * 构造本地 CSV 兜底内容。
 *
 * 前置条件：items 为当前已加载签到记录。
 * 后置条件：返回 CSV 文本。
 * 不变量：字段只来自 CheckInRecord 展示数据。
 */
function buildLocalCsv(): string {
  const header = ['registrationId', 'userId', 'nickname', 'registrationStatus', 'checkedInAt']
  const rows = items.value.map((item) =>
    [
      item.registrationId,
      item.userId,
      item.nickname,
      item.registrationStatus,
      item.checkedInAt ?? '',
    ]
      .map((cell) => `"${cell.replace(/"/g, '""')}"`)
      .join(','),
  )
  return [header.join(','), ...rows].join('\n')
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  if (!activityId.value) {
    errorMsg.value = '缺少活动标识'
    loading.value = false
    return
  }
  void loadData()
})
</script>

<style scoped>
.page {
  background-color: var(--q-color-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-bar {
  flex: none;
  display: flex;
  gap: 12rpx;
  padding: 20rpx 32rpx 12rpx;
  background-color: var(--q-color-bg);
  box-sizing: border-box;
}

.filter-chip {
  height: 60rpx;
  line-height: 60rpx;
  margin: 0;
  padding: 0 24rpx;
  border: none;
  border-radius: 30rpx;
  background-color: var(--q-color-bg-card);
  color: var(--q-color-text-sub);
  font-size: 24rpx;
}

.filter-chip-active {
  background: var(--q-gradient-primary);
  color: var(--q-color-bg-card);
  font-weight: 600;
}

.state-text {
  text-align: center;
  font-size: 28rpx;
  color: var(--q-color-text-muted);
  padding-top: 120rpx;
  flex: 1;
}

.state-text--error {
  color: var(--q-color-danger);
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  box-sizing: border-box;
  padding-bottom: 24rpx;
}

.summary-card {
  background-color: var(--q-color-bg-card);
  margin: 16rpx 32rpx;
  padding: 28rpx 16rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
}

.summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.summary-num {
  font-size: 36rpx;
  color: var(--q-color-text);
  font-weight: 700;
}

.summary-label {
  font-size: 24rpx;
  color: var(--q-color-text-muted);
}

.summary-divider {
  width: 1rpx;
  height: 52rpx;
  background-color: var(--q-color-bg-soft);
}

.card {
  background-color: var(--q-color-bg-card);
  margin: 16rpx 32rpx;
  padding: 24rpx 32rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
}

.card-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-width: 0;
}

.avatar-placeholder {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: var(--q-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-text {
  font-size: 28rpx;
  color: var(--q-color-bg-card);
  font-weight: 600;
}

.info-col {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}

.nickname {
  font-size: 28rpx;
  color: var(--q-color-text);
  font-weight: 600;
}

.meta-text,
.checkin-time {
  font-size: 22rpx;
  color: var(--q-color-text-muted);
}

.checkin-time-muted {
  color: var(--q-color-text-muted);
}

.status-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
  flex-shrink: 0;
}

.status-registered {
  background-color: rgba(94, 200, 167, 0.1);
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

.load-more {
  text-align: center;
  font-size: 24rpx;
  color: var(--q-color-text-muted);
  padding: 24rpx 0;
}

.export-btn {
  width: 100%;
  height: 76rpx;
  line-height: 76rpx;
  margin: 0;
  border: none;
  border-radius: 8rpx;
  background: var(--q-gradient-primary);
  color: var(--q-color-bg-card);
  font-size: 28rpx;
  font-weight: 600;
}

.export-btn[disabled] {
  background-color: var(--q-color-text-muted);
  color: var(--q-color-bg-card);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

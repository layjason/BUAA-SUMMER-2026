<script setup lang="ts">
/**
 * 我创建的活动页。
 *
 * 前置条件：用户已登录，可访问我的活动与草稿接口。
 * 后置条件：已发布 Tab 展示审核通过活动，未发布 Tab 展示草稿和仍在审核流程中的活动。
 * 不变量：草稿入口页与未发布 Tab 的定位不同，本页会保留审核中活动供用户追踪流程。
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { BusinessError } from '@/api'
import {
  getDrafts,
  getMyActivities,
  type ActivityDraftSummary,
  type ActivitySummary,
} from '@/api/modules/activities'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'
import { reviewStatusText, runtimeStatusText } from '@/utils/status'

type ActivityTab = 'published' | 'unpublished'
type UnpublishedSource = 'draft' | 'activity'
type UnpublishedActivityItem = {
  activityId: string
  title?: string
  reviewStatus: ActivityDraftSummary['reviewStatus']
  createdAt: string
  updatedAt: string
  source: UnpublishedSource
  runtimeStatus?: ActivitySummary['runtimeStatus']
  startAt?: string
}

const activeTab = ref<ActivityTab>('published')
const loading = ref(true)
const refreshing = ref(false)
const errorMsg = ref('')
const activities = ref<ActivitySummary[]>([])
const unpublishedActivities = ref<UnpublishedActivityItem[]>([])

const unpublishedCountText = computed(() => {
  const draftCount = unpublishedActivities.value.filter(
    (item) => item.reviewStatus === 'draft',
  ).length
  const reviewCount = unpublishedActivities.value.length - draftCount
  if (unpublishedActivities.value.length === 0) return ''
  return `${draftCount} 个草稿 · ${reviewCount} 个审核中/待处理`
})

/**
 * 简易状态翻译函数。
 *
 * 前置条件：状态工具传入固定 i18n key。
 * 后置条件：返回中文展示文案。
 * 不变量：不依赖页面外 i18n 资源，避免旧资源乱码影响展示。
 *
 * @param key 状态文案 key
 * @returns 中文展示文案
 */
function translateStatus(key: string): string {
  const statusMap: Record<string, string> = {
    'myActivities.statusDraft': '草稿',
    'myActivities.statusPending': '审核中',
    'myActivities.statusApproved': '已发布',
    'myActivities.statusRejected': '已驳回',
    'myActivities.statusChangeRequired': '需修改',
    'myActivities.statusNotStarted': '未开始',
    'myActivities.statusRegistering': '报名中',
    'myActivities.statusRegistrationClosed': '报名截止',
    'myActivities.statusOngoing': '进行中',
    'myActivities.statusEnded': '已结束',
    'myActivities.statusTakenDown': '已下架',
  }
  return statusMap[key] ?? key
}

/**
 * 加载已发布活动列表。
 *
 * 前置条件：我的活动接口返回 ActivitySummary 分页。
 * 后置条件：只保留 reviewStatus 为 approved 的活动。
 * 不变量：不会把审核中或草稿活动展示到已发布 Tab。
 */
async function loadPublishedActivities(): Promise<void> {
  try {
    const result = await getMyActivities()
    activities.value = (result.items ?? []).filter((item) => item.reviewStatus === 'approved')
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  }
}

/**
 * 加载未发布活动列表。
 *
 * 前置条件：草稿接口返回 ActivityDraftSummary 分页。
 * 后置条件：展示所有非 approved 状态，包含草稿、审核中、需修改和已驳回。
 * 不变量：已发布活动不会出现在未发布 Tab。
 */
async function loadUnpublishedActivities(): Promise<void> {
  try {
    const [draftResult, activityResult] = await Promise.all([getDrafts(), getMyActivities()])
    const draftItems: UnpublishedActivityItem[] = (draftResult.items ?? [])
      .filter((item) => item.reviewStatus === 'draft')
      .map((item) => ({
        activityId: item.activityId,
        title: item.title,
        reviewStatus: item.reviewStatus,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
        source: 'draft',
      }))
    const reviewingItems: UnpublishedActivityItem[] = (activityResult.items ?? [])
      .filter((item) => item.reviewStatus !== 'approved')
      .map((item) => ({
        activityId: item.activityId,
        title: item.title,
        reviewStatus: item.reviewStatus,
        createdAt: item.startAt,
        updatedAt: item.startAt,
        source: 'activity',
        runtimeStatus: item.runtimeStatus,
        startAt: item.startAt,
      }))
    unpublishedActivities.value = [...draftItems, ...reviewingItems].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    )
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  }
}

/**
 * 加载当前 Tab 数据。
 *
 * 前置条件：activeTab 为合法 Tab。
 * 后置条件：刷新当前 Tab 数据并结束 loading。
 * 不变量：错误信息会在每次加载前清空。
 */
async function loadCurrentTab(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    if (activeTab.value === 'published') {
      await loadPublishedActivities()
    } else {
      await loadUnpublishedActivities()
    }
  } finally {
    loading.value = false
  }
}

/**
 * 切换 Tab。
 *
 * 前置条件：tab 为页面声明的 Tab。
 * 后置条件：激活对应 Tab 并重新加载数据。
 * 不变量：不会保留上一次错误提示。
 *
 * @param tab 目标 Tab
 */
function switchTab(tab: ActivityTab): void {
  if (activeTab.value === tab) return
  activeTab.value = tab
  void loadCurrentTab()
}

/**
 * 下拉刷新当前列表。
 *
 * 前置条件：当前页面可访问接口。
 * 后置条件：刷新当前 Tab 数据。
 * 不变量：刷新失败只显示错误，不改变 Tab。
 */
async function onRefresh(): Promise<void> {
  refreshing.value = true
  errorMsg.value = ''
  try {
    if (activeTab.value === 'published') {
      await loadPublishedActivities()
    } else {
      await loadUnpublishedActivities()
    }
  } finally {
    refreshing.value = false
  }
}

/**
 * 格式化标签列表。
 *
 * 前置条件：tags 为 OpenAPI 返回的字符串数组。
 * 后置条件：返回适合列表展示的文本。
 * 不变量：不会修改原数组。
 *
 * @param tags 标签数组
 * @returns 展示文本
 */
function formatTags(tags: string[]): string {
  return tags.length > 0 ? tags.join(' · ') : '未设置标签'
}

/**
 * 获取审核状态展示文案。
 *
 * 前置条件：status 为审核状态枚举。
 * 后置条件：返回中文文案。
 * 不变量：未知状态原样返回。
 *
 * @param status 审核状态
 * @returns 中文文案
 */
function getReviewStatusText(status: string): string {
  return reviewStatusText(status, translateStatus)
}

/**
 * 获取运行状态展示文案。
 *
 * 前置条件：status 为运行状态枚举。
 * 后置条件：返回中文文案。
 * 不变量：未知状态原样返回。
 *
 * @param status 运行状态
 * @returns 中文文案
 */
function getRuntimeStatusText(status: string): string {
  return runtimeStatusText(status, translateStatus)
}

/**
 * 打开未发布活动。
 *
 * 前置条件：activityId 来自未发布列表。
 * 后置条件：进入编辑页，供草稿继续编辑或查看审核结果。
 * 不变量：不直接改变审核状态。
 *
 * @param item 未发布列表项
 */
function goUnpublished(item: UnpublishedActivityItem): void {
  if (item.source === 'draft') {
    uni.navigateTo({ url: `/pages/activity/edit?activityId=${item.activityId}` })
    return
  }
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${item.activityId}` })
}

/**
 * 获取未发布活动的操作文案。
 *
 * 前置条件：item 来自未发布列表。
 * 后置条件：返回与当前审核状态匹配的简短操作提示。
 * 不变量：不改变活动状态。
 *
 * @param item 未发布列表项
 * @returns 操作文案
 */
function getUnpublishedActionText(item: UnpublishedActivityItem): string {
  if (item.source === 'draft') return '继续编辑'
  if (item.reviewStatus === 'pending') return '查看进度'
  if (item.reviewStatus === 'changeRequired') return '查看修改意见'
  if (item.reviewStatus === 'rejected') return '查看驳回原因'
  return '查看详情'
}

/**
 * 获取未发布活动的辅助时间文案。
 *
 * 前置条件：item 来自未发布列表。
 * 后置条件：草稿展示更新时间，审核流活动展示活动时间。
 * 不变量：不会修改时间值。
 *
 * @param item 未发布列表项
 * @returns 时间文案
 */
function getUnpublishedTimeText(item: UnpublishedActivityItem): string {
  if (item.source === 'draft') return `更新于 ${formatDate(item.updatedAt)}`
  return item.startAt
    ? `活动时间 ${formatDate(item.startAt)}`
    : `创建于 ${formatDate(item.createdAt)}`
}

/**
 * 打开已发布活动详情。
 *
 * 前置条件：activityId 来自已发布列表。
 * 后置条件：进入活动详情页。
 * 不变量：不修改活动数据。
 *
 * @param activityId 活动标识
 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

onShow(() => {
  void loadCurrentTab()
})
</script>

<template>
  <view class="page">
    <view class="tab-bar">
      <view
        class="tab"
        :class="{ active: activeTab === 'published' }"
        @click="switchTab('published')"
      >
        <text>已发布</text>
      </view>
      <view
        class="tab"
        :class="{ active: activeTab === 'unpublished' }"
        @click="switchTab('unpublished')"
      >
        <text>未发布</text>
      </view>
    </view>

    <scroll-view
      class="scroll-area"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="loading" class="loading-text">加载中...</view>

      <view v-else-if="errorMsg" class="error-text">{{ errorMsg }}</view>

      <view v-else-if="activeTab === 'published'" class="list-content">
        <view v-if="activities.length === 0" class="empty-text">暂无已发布活动</view>
        <view
          v-for="item in activities"
          :key="item.activityId"
          class="card"
          hover-class="card-hover"
          @click="goDetail(item.activityId)"
        >
          <view class="card-header">
            <text class="card-title">{{ item.title }}</text>
            <text class="status-tag" :class="'runtime-' + item.runtimeStatus">
              {{ getRuntimeStatusText(item.runtimeStatus) }}
            </text>
          </view>
          <view class="card-row">
            <text class="tag">{{ formatTags(item.tags) }}</text>
          </view>
          <view class="card-row">
            <text class="meta">{{ formatDate(item.startAt) }}</text>
            <text class="meta">{{ item.registeredCount }}/{{ item.capacity }}人</text>
          </view>
        </view>
      </view>

      <view v-else class="list-content">
        <view v-if="unpublishedCountText" class="list-hint">{{ unpublishedCountText }}</view>
        <view v-if="unpublishedActivities.length === 0" class="empty-text">暂无未发布活动</view>
        <view
          v-for="item in unpublishedActivities"
          :key="item.activityId"
          class="card card-unpublished"
          hover-class="card-hover"
          @click="goUnpublished(item)"
        >
          <view class="card-header">
            <text class="card-title">{{ item.title || '未命名活动' }}</text>
            <text class="status-tag" :class="'review-' + item.reviewStatus">
              {{ getReviewStatusText(item.reviewStatus) }}
            </text>
          </view>
          <text class="unpublished-desc">
            {{
              item.reviewStatus === 'draft'
                ? '尚未提交审核，可以继续编辑'
                : item.reviewStatus === 'pending'
                  ? '活动已提交，正在等待审核结果'
                  : item.reviewStatus === 'changeRequired'
                    ? '审核要求修改，请调整后重新提交'
                    : '审核未通过，可修改后重新提交'
            }}
          </text>
          <view class="card-row">
            <text class="meta">{{ getUnpublishedTimeText(item) }}</text>
          </view>
          <view class="card-row">
            <text class="meta">{{ item.source === 'draft' ? '草稿箱' : '审核流程' }}</text>
            <text class="edit-action">{{ getUnpublishedActionText(item) }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: $color-bg;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.tab-bar {
  display: flex;
  flex-shrink: 0;
  padding: 12rpx 32rpx;
  background-color: $color-bg-card;
  border-bottom: 1rpx solid $color-border-light;
}

.tab {
  flex: 1;
  height: 64rpx;
  border-radius: 32rpx;
  color: $color-text-sub;
  font-size: 28rpx;
  line-height: 64rpx;
  text-align: center;
}

.tab.active {
  background-color: $color-primary-light;
  color: $color-primary;
  font-weight: 700;
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

.list-content {
  padding: 16rpx 0 32rpx;
}

.list-hint {
  margin: 8rpx 32rpx 4rpx;
  color: $color-text-muted;
  font-size: 24rpx;
}

.card {
  margin: 16rpx 32rpx;
  padding: 28rpx 32rpx;
  border: 1rpx solid $color-border-light;
  border-radius: 24rpx;
  background-color: $color-bg-card;
  box-shadow: $shadow-sm;
}

.card-hover {
  opacity: 0.85;
}

.card-unpublished {
  border-left: 6rpx solid $color-primary;
}

.card-header,
.card-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.card-row {
  margin-top: 12rpx;
}

.card-title {
  flex: 1;
  min-width: 0;
  color: $color-text;
  font-size: 30rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag {
  flex: 1;
  min-width: 0;
  color: $color-primary;
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  min-width: 0;
  color: $color-text-muted;
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.runtime-registering,
.runtime-ongoing {
  background-color: $color-primary-light;
  color: $color-primary;
}

.review-draft,
.runtime-notStarted {
  background-color: $color-bg-soft;
  color: $color-text-sub;
}

.review-pending,
.runtime-registrationClosed {
  background-color: var(--q-color-accent-light);
  color: var(--q-color-warning);
}

.review-changeRequired {
  background-color: var(--q-color-accent-light);
  color: var(--q-color-warning);
}

.review-rejected,
.runtime-takenDown {
  background-color: rgba(220, 38, 38, 0.08);
  color: $color-danger;
}

.runtime-ended {
  background-color: $color-bg-soft;
  color: $color-text-muted;
}

.unpublished-desc {
  display: block;
  margin-top: 12rpx;
  color: var(--q-color-text-sub);
  font-size: 24rpx;
  line-height: 1.5;
}

.edit-action {
  flex-shrink: 0;
  color: var(--q-color-primary);
  font-size: 24rpx;
  font-weight: 700;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

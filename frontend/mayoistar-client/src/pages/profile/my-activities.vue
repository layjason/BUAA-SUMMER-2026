<template>
  <view class="page">
    <view class="tab-bar">
      <view
        class="tab"
        :class="{ active: activeTab === 'published' }"
        @click="activeTab = 'published'"
      >
        <text>{{ t('myActivities.tabPublished') }}</text>
      </view>
      <view class="tab" :class="{ active: activeTab === 'drafts' }" @click="activeTab = 'drafts'">
        <text>{{ t('myActivities.tabDrafts') }}</text>
      </view>
    </view>

    <view class="tab-bar-spacer" />

    <view v-if="loading" class="loading-text">{{ t('加载中') }}</view>

    <view v-else-if="errorMsg" class="error-text">{{ errorMsg }}</view>

    <view v-else-if="activeTab === 'published'">
      <view v-if="activities.length === 0" class="empty-text">{{ t('暂无数据') }}</view>
      <view
        v-for="item in activities"
        :key="item.activityId"
        class="card"
        hover-class="card-hover"
        @click="goDetail(item.activityId)"
      >
        <text class="card-title">{{ item.title }}</text>
        <view class="card-row">
          <text class="tag">{{ formatTags(item.tags) }}</text>
          <text class="status-tag" :class="'status-' + item.runtimeStatus">{{
            runtimeStatusText(item.runtimeStatus)
          }}</text>
        </view>
        <view class="card-row">
          <text class="meta">{{ formatDate(item.startAt) }}</text>
          <text class="meta">{{
            t('myActivities.participants', { count: item.registeredCount })
          }}</text>
        </view>
      </view>
    </view>

    <view v-else>
      <view v-if="drafts.length === 0" class="empty-text">{{ t('暂无数据') }}</view>
      <view
        v-for="item in drafts"
        :key="item.activityId"
        class="card card-draft"
        hover-class="card-hover"
        @click="goEdit(item.activityId)"
      >
        <text class="card-title">{{ item.title }}</text>
        <view class="card-row">
          <text class="tag draft-status" :class="'draft-' + item.reviewStatus">{{
            reviewStatusText(item.reviewStatus)
          }}</text>
          <text class="meta">{{ formatDate(item.updatedAt) }}</text>
        </view>
      </view>
    </view>
  </view>
  <view class="bottom-safe" />
</template>

<script setup lang="ts">
/**
 * 我创建的活动
 *
 * Tab 切换已发布和草稿两个列表。
 * 前置条件：用户已登录
 * 后置条件：加载成功后展示活动数据
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'

const { t } = useI18n()

const activeTab = ref<'published' | 'drafts'>('published')
const loading = ref(true)
const errorMsg = ref('')

/** 运行时状态文本映射 */
const runtimeStatusMap: Record<string, string> = {
  notStarted: t('myActivities.statusNotStarted'),
  registering: t('myActivities.statusRegistering'),
  registrationClosed: t('myActivities.statusRegistrationClosed'),
  ongoing: t('myActivities.statusOngoing'),
  ended: t('myActivities.statusEnded'),
  takenDown: t('myActivities.statusTakenDown'),
}

/** 审核状态文本映射 */
const reviewStatusMap: Record<string, string> = {
  draft: t('myActivities.statusDraft'),
  pending: t('myActivities.statusPending'),
  approved: t('myActivities.statusApproved'),
  rejected: t('myActivities.statusRejected'),
}

interface ActivityItem {
  activityId: string
  title: string
  registeredCount: number
  tags: string[]
  runtimeStatus: string
  startAt: string
}

const activities = ref<ActivityItem[]>([])

interface DraftItem {
  activityId: string
  title: string
  updatedAt: string
  reviewStatus: string
}

const drafts = ref<DraftItem[]>([])

/**
 * 加载已发布活动列表
 */
async function loadActivities(): Promise<void> {
  try {
    const result = await api.get('/activities/mine')
    activities.value = result.items as ActivityItem[]
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  }
}

/**
 * 加载草稿列表
 */
async function loadDrafts(): Promise<void> {
  try {
    const result = await api.get('/activities/drafts')
    drafts.value = result.items as DraftItem[]
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  }
}

/**
 * 每次进入页面时重新加载
 */
onShow(async () => {
  loading.value = true
  errorMsg.value = ''
  await Promise.all([loadActivities(), loadDrafts()])
  loading.value = false
})

/**
 * 格式化标签列表为展示文本
 *
 * @param tags 标签数组
 * @returns 逗号分隔的标签文本
 */
function formatTags(tags: string[]): string {
  return tags.join(' · ')
}

/**
 * 获取运行时状态中文展示文本
 *
 * @param status 运行时状态值
 * @returns 中文文本
 */
function runtimeStatusText(status: string): string {
  return runtimeStatusMap[status] ?? status
}

/**
 * 获取审核状态中文展示文本
 *
 * @param status 审核状态值
 * @returns 中文文本
 */
function reviewStatusText(status: string): string {
  return reviewStatusMap[status] ?? status
}

/**
 * 跳转到活动编辑页
 *
 * @param activityId 草稿活动标识
 */
function goEdit(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/edit?activityId=${activityId}` })
}

/**
 * 跳转到活动详情页
 *
 * @param activityId 活动标识
 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
}

.tab-bar {
  position: fixed;
  top: var(--window-top, 44px);
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  background-color: #fff;
  padding: 0 32rpx;
  border-bottom: 1rpx solid #ebedf0;
}

.tab-bar-spacer {
  height: 104rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #646566;
  border-bottom: 4rpx solid transparent;
}

.tab.active {
  color: #1989fa;
  border-bottom-color: #1989fa;
  font-weight: 600;
}

.loading-text,
.error-text,
.empty-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.error-text {
  color: #ee0a24;
}

.card {
  background-color: #fff;
  margin: 16rpx 32rpx;
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
}

.card-hover {
  opacity: 0.85;
}

.card-draft {
  border-left: 6rpx solid #ed6a0c;
}

.card-title {
  display: block;
  font-size: 30rpx;
  color: #323233;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8rpx;
}

.tag {
  font-size: 24rpx;
  color: #1989fa;
}

.draft-tag {
  color: #969799;
}

.draft-status {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}

.draft-draft {
  background-color: #ebedf0;
  color: #969799;
}

.draft-rejected {
  background-color: #fff2f0;
  color: #ee0a24;
}

.draft-pending {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.draft-approved {
  background-color: #ebf9e9;
  color: #07c160;
}

.status-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}

.status-registering {
  background-color: #e6f0fe;
  color: #1989fa;
}

.status-registrationClosed {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.status-ongoing {
  background-color: #ebf9e9;
  color: #07c160;
}

.status-ended {
  background-color: #f2f3f5;
  color: #c8c9cc;
}

.status-notStarted {
  background-color: #ebedf0;
  color: #969799;
}

.status-takenDown {
  background-color: #fff2f0;
  color: #ee0a24;
}

.meta {
  font-size: 24rpx;
  color: #969799;
}

.bottom-safe {
  height: 48rpx;
}
</style>

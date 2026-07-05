<script setup lang="ts">
/**
 * 克隆已有活动页。
 *
 * 前置条件：用户已登录，可读取自己创建过的活动。
 * 后置条件：选择活动后调用克隆接口生成新草稿，并进入编辑页。
 * 不变量：本页只展示可作为克隆来源的已创建活动，不跳转到“我创建的活动”承载流程。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { BusinessError } from '@/api'
import { cloneActivity, getMyActivities, type ActivitySummary } from '@/api/modules/activities'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'
import { runtimeStatusText } from '@/utils/status'
import { MOCK_IMAGE_BASE_URL } from '@/config/env'

const loading = ref(true)
const actioningId = ref('')
const activities = ref<ActivitySummary[]>([])

const cloneableActivities = computed(() =>
  activities.value.filter((activity) => activity.reviewStatus === 'approved'),
)

/**
 * 简易翻译函数，克隆页只需要状态兜底文案。
 *
 * 前置条件：传入状态工具定义的 key。
 * 后置条件：返回对应中文文案。
 * 不变量：不会依赖全局 i18n 资源是否存在。
 *
 * @param key 状态文案 key
 * @returns 中文状态文案
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
 * 加载我的已创建活动，供克隆来源选择。
 *
 * 前置条件：活动 mine 接口返回 ActivitySummary 分页。
 * 后置条件：成功时刷新列表，失败时展示空态。
 * 不变量：只读取数据，不修改活动状态。
 */
async function loadActivities(): Promise<void> {
  try {
    const result = await getMyActivities()
    activities.value = result.items ?? []
  } catch {
    activities.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 克隆活动为新草稿。
 *
 * 前置条件：activityId 来自已发布活动列表。
 * 后置条件：成功后进入新草稿编辑页。
 * 不变量：同一时刻只允许一个克隆请求执行。
 *
 * @param activity 活动列表项
 */
async function cloneExistingActivity(activity: ActivitySummary): Promise<void> {
  if (actioningId.value) return
  actioningId.value = activity.activityId
  try {
    uni.showLoading({ title: '克隆中' })
    const draft = await cloneActivity(activity.activityId)
    uni.hideLoading()
    uni.redirectTo({
      url: `/pages/activity/edit?activityId=${draft.activityId}`,
    })
  } catch (error) {
    uni.hideLoading()
    const title = error instanceof BusinessError ? getErrorMessage(error.code) : '克隆失败'
    uni.showToast({ title, icon: 'none' })
  } finally {
    actioningId.value = ''
  }
}

/**
 * 跳转到空白创建页。
 *
 * 前置条件：当前处于克隆活动页。
 * 后置条件：进入活动编辑页。
 * 不变量：不调用克隆接口。
 */
function createBlankActivity(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}

/**
 * 格式化地点展示文本。
 *
 * 前置条件：活动包含 OpenAPI LocationInfo。
 * 后置条件：优先返回城市与地点名称，其次返回地址。
 * 不变量：不会写入活动数据。
 *
 * @param activity 活动列表项
 * @returns 地点展示文本
 */
function formatLocation(activity: ActivitySummary): string {
  const place = activity.location.placeName || activity.location.address
  return [activity.location.city, place].filter(Boolean).join(' · ')
}

/**
 * 获取克隆来源活动封面图。
 *
 * 前置条件：activity 来自我的活动列表。
 * 后置条件：优先使用接口返回的封面图，无封面时使用稳定的活动图片兜底。
 * 不变量：只生成展示 URL，不修改活动数据。
 *
 * @param activity 活动列表项
 * @returns 图片地址
 */
function getActivityCoverUrl(activity: ActivitySummary): string {
  return (
    activity.coverImage?.signedUrl ||
    `${MOCK_IMAGE_BASE_URL}/seed/activity${activity.activityId}/400/225`
  )
}

onLoad(() => {
  void loadActivities()
})
</script>

<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="container">
        <view class="header">
          <text class="title">克隆已有活动</text>
          <text class="subtitle">复制历史活动的基础信息，生成草稿后再调整时间、地点和报名设置</text>
        </view>

        <view v-if="loading" class="state-text">加载中...</view>

        <view v-else-if="cloneableActivities.length === 0" class="empty-state">
          <text class="empty-title">暂无可克隆活动</text>
          <text class="empty-desc">已发布活动会出现在这里，方便下次快速复用</text>
          <button class="empty-button" @click="createBlankActivity">先创建一个活动</button>
        </view>

        <view v-else class="activity-list">
          <view
            v-for="activity in cloneableActivities"
            :key="activity.activityId"
            class="activity-card"
            hover-class="card-hover"
            @click="cloneExistingActivity(activity)"
          >
            <image class="activity-cover" :src="getActivityCoverUrl(activity)" mode="aspectFill" />

            <view class="activity-main">
              <view class="title-row">
                <text class="activity-title">{{ activity.title }}</text>
                <text class="runtime-tag">{{
                  runtimeStatusText(activity.runtimeStatus, translateStatus)
                }}</text>
              </view>
              <text class="meta">{{ formatDate(activity.startAt) }}</text>
              <text class="meta">{{ formatLocation(activity) }}</text>
              <view class="footer-row">
                <text class="clone-action">
                  {{ actioningId === activity.activityId ? '克隆中' : '克隆为草稿' }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page {
  height: 100%;
  overflow: hidden;
  background-color: var(--q-color-bg);
}

.scroll-area {
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.container {
  padding: 32rpx;
}

.header {
  margin-bottom: 28rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--q-color-text);
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--q-color-text-muted);
}

.state-text,
.empty-state {
  padding-top: 120rpx;
  text-align: center;
  color: var(--q-color-text-muted);
}

.empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-color-text);
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--q-color-text-muted);
}

.empty-button {
  margin-top: 28rpx;
  width: 320rpx;
  height: 76rpx;
  border-radius: 999rpx;
  background: var(--q-gradient-primary);
  color: var(--q-color-bg-card);
  font-size: 28rpx;
  line-height: 76rpx;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.activity-card {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background-color: var(--q-color-bg-card);
  box-shadow: 0 12rpx 32rpx rgba(50, 50, 51, 0.06);
}

.card-hover {
  opacity: 0.86;
}

.activity-cover {
  flex-shrink: 0;
  width: 168rpx;
  height: 168rpx;
  border-radius: 12rpx;
  background-color: var(--q-color-primary-light);
}

.activity-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.title-row,
.footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.activity-title {
  flex: 1;
  min-width: 0;
  color: var(--q-color-text);
  font-size: 30rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-tag {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background-color: var(--q-color-primary-light);
  color: var(--q-color-primary);
  font-size: 22rpx;
  font-weight: 600;
}

.meta {
  display: block;
  margin-top: 8rpx;
  color: var(--q-color-text-muted);
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-row {
  margin-top: auto;
  justify-content: flex-end;
}

.clone-action {
  flex-shrink: 0;
  color: var(--q-color-primary);
  font-size: 24rpx;
  font-weight: 600;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

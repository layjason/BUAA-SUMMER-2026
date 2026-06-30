<template>
  <view class="page">
    <view v-if="loading" class="loading-text">{{ t('activityDetail.loading') }}</view>

    <view v-else-if="errorMsg" class="error-text">{{ errorMsg }}</view>

    <template v-else>
      <scroll-view class="scroll-area" scroll-y>
        <!-- 图片轮播 -->
        <view v-if="activity.images.length > 0" class="swiper-wrapper">
          <swiper
            v-if="activity.images.length > 1"
            class="swiper"
            indicator-dots
            indicator-color="rgba(255,255,255,0.5)"
            indicator-active-color="#fff"
            autoplay
            interval="4000"
            circular
          >
            <swiper-item v-for="img in activity.images" :key="img.mediaId">
              <image class="swiper-image" :src="img.url" mode="aspectFill" />
            </swiper-item>
          </swiper>
          <image v-else class="single-image" :src="activity.images[0].url" mode="aspectFill" />
        </view>
        <view v-else class="image-placeholder">
          <text class="placeholder-icon">📷</text>
        </view>

        <!-- 标题 + 状态 -->
        <view class="section card">
          <view class="title-row">
            <text class="title">{{ activity.title }}</text>
            <text class="status-tag" :class="'status-' + activity.runtimeStatus">{{
              runtimeStatusText(activity.runtimeStatus)
            }}</text>
          </view>
        </view>

        <!-- 活动信息 -->
        <view class="section card info-card">
          <view class="info-row">
            <text class="info-label">{{ t('activityDetail.time') }}</text>
            <text class="info-value">{{ formatTimeRange(activity.startAt, activity.endAt) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ t('activityDetail.location') }}</text>
            <text class="info-value">{{ activity.location.address }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ t('activityDetail.organizer') }}</text>
            <text class="info-value">{{ activity.organizerName }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ t('activityDetail.fee') }}</text>
            <text class="info-value">{{ feeText }}</text>
          </view>
          <view v-if="activity.tags.length" class="info-row">
            <text class="info-label">{{ tagsLabel }}</text>
            <view class="tag-row">
              <text v-for="tag in activity.tags" :key="tag" class="tag-chip">{{ tag }}</text>
            </view>
          </view>
        </view>

        <!-- 活动简介 -->
        <view class="section card">
          <text class="section-title">{{ t('activityDetail.introduction') }}</text>
          <text class="section-body">{{ activity.introduction }}</text>
        </view>

        <!-- 安全须知（可折叠） -->
        <view class="section card">
          <view class="section-header" @click="safetyExpanded = !safetyExpanded">
            <text class="section-title">{{ t('activityDetail.safetyNotice') }}</text>
            <text class="expand-icon">{{ safetyExpanded ? '收起' : '展开' }}</text>
          </view>
          <text v-if="safetyExpanded" class="section-body">{{ activity.safetyNotice }}</text>
        </view>

        <view class="bottom-spacer" />
      </scroll-view>

      <!-- 底部操作按钮 -->
      <view class="action-bar">
        <button
          class="action-btn"
          :class="{ disabled: buttonDisabled }"
          :disabled="buttonDisabled"
          @click="handleAction"
        >
          {{ buttonText }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动详情页
 *
 * 展示活动的完整信息，并根据用户报名状态显示对应的操作按钮。
 * 前置条件：用户已登录，activityId 通过 query 传入
 * 后置条件：加载成功后展示活动详情与操作按钮
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatTimeRange } from '@/utils/date'

const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')
const activityId = ref('')
const safetyExpanded = ref(false)

interface ActivityDetail {
  activityId: string
  title: string
  tags: string[]
  introduction: string
  safetyNotice: string
  startAt: string
  endAt: string
  location: {
    address: string
    city: string
    placeName?: string
    point: { longitude: number; latitude: number }
  }
  coverImage: { url?: string; mediaId: string } | null
  images: { url?: string; mediaId: string }[]
  feeAmount?: number
  capacity: number
  registeredCount: number
  waitingCount: number
  registrationDeadline: string
  organizerId: string
  organizerName: string
  reviewStatus: string
  runtimeStatus: string
}

interface ParticipationState {
  canRegister: boolean
  status: string | null
  waitingRank?: number
  confirmationDeadline?: string
  canCancelRegistration: boolean
  canConfirmWaitingSeat: boolean
  canCheckIn: boolean
}

const activity = ref<ActivityDetail>(null!)
const participation = ref<ParticipationState>(null!)

const runtimeStatusMap: Record<string, string> = {
  notStarted: t('myActivities.statusNotStarted'),
  registering: t('myActivities.statusRegistering'),
  registrationClosed: t('myActivities.statusRegistrationClosed'),
  ongoing: t('myActivities.statusOngoing'),
  ended: t('myActivities.statusEnded'),
  takenDown: t('myActivities.statusTakenDown'),
}

/**
 * 获取运行时状态中文展示文本
 */
function runtimeStatusText(status: string): string {
  return runtimeStatusMap[status] ?? status
}

/** 费用展示文本 */
const feeText = computed(() => {
  if (activity.value.feeAmount == null || activity.value.feeAmount === 0) {
    return t('activityDetail.free')
  }
  return t('activityDetail.feeAmount', { amount: activity.value.feeAmount })
})

/** 标签行标签 */
const tagsLabel = computed(() => {
  return t('activityDetail.tags')
})

/** 是否已满 */
const isFull = computed(() => {
  return activity.value.registeredCount >= activity.value.capacity
})

/** 按钮文案 */
const buttonText = computed(() => {
  const p = participation.value
  if (!p) return ''

  if (p.canCheckIn) return t('activityDetail.checkedInTag')
  if (p.canConfirmWaitingSeat) return t('activityDetail.confirmWaiting')
  if (p.canRegister) {
    if (isFull.value) {
      return t('activityDetail.registerFull', { count: activity.value.waitingCount })
    }
    return t('activityDetail.registerNow', {
      count: activity.value.registeredCount,
      total: activity.value.capacity,
    })
  }
  if (p.status === 'registered') return t('activityDetail.cancelRegistration')
  if (p.status === 'waiting') {
    return t('activityDetail.waitingRank', { rank: p.waitingRank ?? '?' })
  }
  if (p.status === 'checkedIn') return t('activityDetail.checkedInTag')
  return t('activityDetail.disabledTag')
})

/** 按钮是否禁用 */
const buttonDisabled = computed(() => {
  const p = participation.value
  if (!p) return true
  if (p.canCheckIn) return false
  if (p.canConfirmWaitingSeat) return false
  if (p.canRegister) return false
  if (p.status === 'registered') return false
  return true
})

/**
 * 处理按钮点击
 */
function handleAction(): void {
  const p = participation.value
  if (!p) return
  if (p.canCheckIn) {
    // 签到功能待实现
    uni.showToast({ title: '签到功能即将上线', icon: 'none' })
    return
  }
  if (p.canConfirmWaitingSeat) {
    handleConfirmWaiting()
    return
  }
  if (p.canRegister) {
    handleRegister()
    return
  }
  if (p.status === 'registered') {
    handleCancelRegistration()
    return
  }
}

/**
 * 报名
 */
async function handleRegister(): Promise<void> {
  try {
    await api.post(`/activities/${activityId.value}/registrations`)
    uni.showToast({ title: '报名成功', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '报名失败，请稍后重试', icon: 'none' })
    }
  }
}

/**
 * 取消报名
 */
async function handleCancelRegistration(): Promise<void> {
  try {
    await api.post(`/activities/${activityId.value}/registrations/cancel`)
    uni.showToast({ title: '已取消报名', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '取消失败，请稍后重试', icon: 'none' })
    }
  }
}

/**
 * 确认候补名额
 */
async function handleConfirmWaiting(): Promise<void> {
  try {
    await api.post(`/activities/${activityId.value}/waiting-confirmations`)
    uni.showToast({ title: '已确认名额', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '确认失败，请稍后重试', icon: 'none' })
    }
  }
}

/**
 * 加载活动详情 + 用户参与状态
 */
async function loadData(): Promise<void> {
  try {
    const [act, state] = await Promise.all([
      api.get(`/activities/${activityId.value}`),
      api.get(`/activities/${activityId.value}/participation-state`),
    ])
    activity.value = act as ActivityDetail
    participation.value = state as ParticipationState
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载活动详情失败')
    }
  } finally {
    loading.value = false
  }
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
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.loading-text,
.error-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.error-text {
  color: #ee0a24;
}

/* ---- 滚动区域 ---- */
.scroll-area {
  flex: 1;
  overflow-y: auto;
}

/* ---- 图片轮播 ---- */
.swiper-wrapper {
  width: 100%;
  height: 420rpx;
}

.swiper,
.swiper-image {
  width: 100%;
  height: 420rpx;
}

.single-image {
  width: 100%;
  height: 420rpx;
  display: block;
}

.image-placeholder {
  width: 100%;
  height: 420rpx;
  background-color: #ebedf0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-icon {
  font-size: 80rpx;
}

/* ---- 卡片 ---- */
.section {
  margin: 16rpx 32rpx;
}

.card {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 28rpx 32rpx;
}

/* ---- 标题 ---- */
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.title {
  font-size: 36rpx;
  font-weight: 700;
  color: #323233;
  flex: 1;
  line-height: 1.4;
}

.status-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 4rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
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

/* ---- 信息行 ---- */
.info-card {
  padding: 20rpx 32rpx;
}

.info-row {
  display: flex;
  align-items: flex-start;
  padding: 14rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 26rpx;
  color: #969799;
  width: 120rpx;
  flex-shrink: 0;
}

.info-value {
  font-size: 26rpx;
  color: #323233;
  flex: 1;
  line-height: 1.4;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  flex: 1;
}

.tag-chip {
  font-size: 22rpx;
  color: #1989fa;
  background-color: #e6f0fe;
  padding: 4rpx 14rpx;
  border-radius: 4rpx;
}

/* ---- 简介 / 安全须知 ---- */
.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-header .section-title {
  margin-bottom: 0;
}

.expand-icon {
  font-size: 24rpx;
  color: #1989fa;
}

.section-body {
  display: block;
  font-size: 26rpx;
  color: #646566;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* ---- 底部间距 ---- */
.bottom-spacer {
  height: 48rpx;
}

/* ---- 底部操作按钮 ---- */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #fff;
  border-top: 2rpx solid #ebedf0;
  box-sizing: border-box;
}

.action-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #fff;
  background-color: #1989fa;
  border-radius: 12rpx;
  border: none;
}

.action-btn.disabled {
  background-color: #c8c9cc;
  color: #fff;
}
</style>

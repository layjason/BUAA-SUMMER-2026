<template>
  <view class="page">
    <view v-if="loading" class="state-text">{{ t('activityDetail.loading') }}</view>

    <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>

    <template v-else-if="activity">
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
              <image class="swiper-image" :src="img.signedUrl" mode="aspectFill" />
            </swiper-item>
          </swiper>
          <image
            v-else
            class="single-image"
            :src="activity.images[0].signedUrl"
            mode="aspectFill"
          />
        </view>
        <view v-else class="image-placeholder">
          <text class="placeholder-icon">📷</text>
        </view>

        <!-- 标题 + 状态 -->
        <view class="section card">
          <view class="title-row">
            <text class="title">{{ activity.title }}</text>
            <text class="status-tag" :class="'status-' + activity.runtimeStatus">{{
              getRuntimeStatusText(activity.runtimeStatus)
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
          <view class="info-row">
            <text class="info-label">{{ t('activityDetail.registrationDeadline') }}</text>
            <text class="info-value">{{ formatDateTime(activity.registrationDeadline) }}</text>
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

        <!-- 安全须知 -->
        <view class="section card">
          <text class="section-title">{{ t('activityDetail.safetyNotice') }}</text>
          <text class="section-body">{{ activity.safetyNotice }}</text>
        </view>

        <!-- AI 内容审核 -->
        <view v-if="activity.aiContentReview" class="section card">
          <text class="section-title">{{ t('aiReview.title') }}</text>
          <view class="info-row">
            <text class="info-label">{{ t('aiReview.riskLevel') }}</text>
            <text class="info-value" :class="'risk-' + activity.aiContentReview.riskLevel">{{
              riskLevelText(activity.aiContentReview.riskLevel)
            }}</text>
          </view>
          <view v-if="activity.aiContentReview.reasons.length" class="info-row">
            <text class="info-label">{{ t('aiReview.reason') }}</text>
            <text class="info-value">{{ activity.aiContentReview.reasons.join('; ') }}</text>
          </view>
        </view>

        <!-- 活动总结 -->
        <view v-if="publishedSummaries.length > 0" class="section card">
          <text class="section-title">{{ t('activityDetail.summarySection') }}</text>
          <view v-for="item in publishedSummaries" :key="item.summaryId" class="published-block">
            <text class="published-title">{{ item.title }}</text>
            <text class="published-body">{{ item.content }}</text>
          </view>
        </view>

        <!-- 活动评价 -->
        <view v-if="publishedReviews.length > 0" class="section card">
          <text class="section-title">{{
            t('activityDetail.reviewsSection', { count: publishedReviews.length })
          }}</text>
          <view v-for="item in publishedReviews" :key="item.reviewId" class="published-block">
            <view class="review-header">
              <text class="published-title">{{ item.nickname }}</text>
              <text class="review-rating">{{
                t('activityDetail.reviewStars', { rating: item.rating })
              }}</text>
            </view>
            <view v-if="item.tags.length > 0" class="tag-row">
              <text v-for="tag in item.tags" :key="tag" class="tag-chip">{{ tag }}</text>
            </view>
            <text v-if="item.content" class="published-body">{{ item.content }}</text>
          </view>
        </view>

        <!-- 参与者菜单 -->
        <view class="section">
          <view class="menu-card" hover-class="menu-hover" @click="goParticipants">
            <text class="menu-text">{{ t('activityDetail.viewParticipants') }}</text>
            <view class="menu-right">
              <text class="menu-count">{{ activity.occupiedCount }}/{{ activity.capacity }}</text>
              <text class="menu-arrow">&gt;</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 底部操作按钮 -->
      <BottomActionBar>
        <view class="detail-action-stack">
          <button
            class="bar-btn bar-btn-primary"
            :class="{ 'bar-btn-disabled': buttonDisabled }"
            :disabled="buttonDisabled"
            :loading="actioning"
            @click="handleAction"
          >
            {{ buttonText }}
          </button>
          <view class="action-row">
            <button
              v-if="
                isOrganizer &&
                (activity.runtimeStatus === 'registering' || activity.runtimeStatus === 'ongoing')
              "
              class="action-btn-sm"
              @click="handleGenerateQrCode"
            >
              {{ t('activityDetail.generateQrCode') }}
            </button>
            <button v-if="isOrganizer" class="action-btn-sm" @click="goCheckIns">
              {{ t('activityDetail.checkInManagement') }}
            </button>
            <view v-if="canReview" class="review-action-block">
              <button class="action-btn-sm" @click="goReview">
                {{ t('activityDetail.writeReview') }}
              </button>
              <text v-if="reviewDeadlineText" class="review-deadline-text">{{
                reviewDeadlineText
              }}</text>
            </view>
            <text v-else-if="showReviewedStatus" class="action-status-chip">{{
              t('activityDetail.reviewSubmitted')
            }}</text>
            <button v-if="canPostSummary" class="action-btn-sm" @click="goSummary">
              {{ t('activityDetail.writeSummary') }}
            </button>
            <text v-else-if="showSummaryPostedStatus" class="action-status-chip">{{
              t('activityDetail.summarySubmitted')
            }}</text>
            <button
              v-if="
                isOrganizer &&
                (activity.runtimeStatus === 'registering' || activity.runtimeStatus === 'ongoing')
              "
              class="action-btn-sm"
              @click="handleExportCheckIns"
            >
              {{ t('checkInExport.export') }}
            </button>
          </view>
        </view>
      </BottomActionBar>
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
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import {
  getActivityDetail,
  getActivityReviews,
  getActivitySummaries,
  getMyActivityReview,
  getParticipationState as fetchParticipationState,
  type ActivityDetail,
  type ActivityParticipationState,
  type ActivityReviewListItem,
  type ActivitySummaryPost,
  type RegistrationResult,
} from '@/api/modules/activities'
import {
  registerForActivity,
  cancelRegistration,
  confirmWaitlist,
} from '@/api/modules/registrations'
import { generateCheckInQrCode, checkIn, exportCheckIns } from '@/api/modules/checkin'
import { BottomActionBar } from '@/components'
import { isActivityAtCapacity } from '@/utils/activity-capacity'
import { getErrorMessage } from '@/utils/error'
import { formatDateTime, formatTimeRange } from '@/utils/date'
import { runtimeStatusText } from '@/utils/status'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(true)
const errorMsg = ref('')
const actioning = ref(false)
const activityId = ref('')

const activity = ref<ActivityDetail | null>(null)
const participation = ref<ActivityParticipationState | null>(null)

const publishedSummaries = ref<ActivitySummaryPost[]>([])
const publishedReviews = ref<ActivityReviewListItem[]>([])
const hasReviewed = ref(false)

function getRuntimeStatusText(status: string): string {
  return runtimeStatusText(status, t)
}

/**
 * 风险等级文本映射
 *
 * @param level 风险等级值
 * @returns 中文文本
 */
function riskLevelText(level: string): string {
  const map: Record<string, string> = {
    low: t('aiReview.riskLow'),
    medium: t('aiReview.riskMedium'),
    high: t('aiReview.riskHigh'),
    uncertain: t('aiReview.riskUncertain'),
  }
  return map[level] ?? level
}

const feeText = computed(() => {
  if (!activity.value) return ''
  if (activity.value.feeAmount == null || activity.value.feeAmount === 0) {
    return t('activityDetail.free')
  }
  return t('activityDetail.feeAmount', { amount: activity.value.feeAmount })
})

const tagsLabel = computed(() => {
  return t('activityDetail.tags')
})

/** 当前用户是否为活动发起人 */
const isOrganizer = computed(() => {
  return activity.value?.organizerId === authStore.userId
})

/** 当前用户是否可以评价，由后端根据活动状态、签到、评价窗口等规则统一计算 */
const canReview = computed(() => {
  return participation.value?.canReview === true
})

/** 是否展示「已评价」状态 */
const showReviewedStatus = computed(() => {
  return hasReviewed.value
})

/** 评价入口截止时间文案，空字符串表示后端未返回截止时间 */
const reviewDeadlineText = computed(() => {
  const endsAt = participation.value?.reviewWindowEndsAt
  if (!endsAt) return ''
  return t('activityDetail.reviewDeadline', { time: formatDateTime(endsAt) })
})

/** 当前用户是否可以发布总结（发起人、活动已结束且活动尚无总结） */
const canPostSummary = computed(() => {
  return (
    isOrganizer.value &&
    activity.value?.runtimeStatus === 'ended' &&
    publishedSummaries.value.length === 0
  )
})

/** 是否展示「已发布总结」状态 */
const showSummaryPostedStatus = computed(() => {
  return (
    isOrganizer.value &&
    activity.value?.runtimeStatus === 'ended' &&
    publishedSummaries.value.length > 0
  )
})

/** 活动是否已满员（依据 API 返回的 occupiedCount） */
const isAtCapacity = computed(() => {
  const act = activity.value
  if (!act) return false
  return isActivityAtCapacity(act.occupiedCount, act.capacity)
})

/** 跳转到评价页 */
function goReview(): void {
  uni.navigateTo({ url: `/pages/activity/review?activityId=${activityId.value}` })
}

/** 跳转到总结页 */
function goSummary(): void {
  uni.navigateTo({ url: `/pages/activity/summary?activityId=${activityId.value}` })
}

/**
 * 导出签到数据
 *
 * 下载签到数据文件并打开。
 */
async function handleExportCheckIns(): Promise<void> {
  try {
    uni.showLoading({ title: t('checkInExport.exporting') })
    /*
    UNKNOWN-TYPE: mock 导出返回下载 URL，OpenAPI 定义为 application/octet-stream
    */
    const result = (await exportCheckIns(activityId.value)) as { url: string }

    const downloadResult = await uni.downloadFile({ url: result.url })
    uni.hideLoading()
    await uni.openDocument({ filePath: downloadResult.tempFilePath })
    uni.showToast({ title: t('checkInExport.exportSuccess'), icon: 'success' })
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: t('checkInExport.exportFailed'), icon: 'none' })
    }
  }
}

const buttonText = computed(() => {
  const p = participation.value
  if (!p) return ''

  if (p.status === 'registered' && p.canCancelRegistration)
    return t('activityDetail.cancelRegistration')
  if (p.status === 'waiting' && p.canCancelRegistration)
    return t('activityDetail.cancelRegistration')
  if (p.canConfirmWaitingSeat) return t('activityDetail.confirmWaiting')
  if (p.canCheckIn) return t('activityDetail.checkIn')
  if (p.canRegister) {
    const act = activity.value!
    if (isAtCapacity.value) {
      return t('activityDetail.registerFull', { count: act.waitingCount ?? 0 })
    }
    return t('activityDetail.registerNow', {
      count: act.occupiedCount,
      total: act.capacity,
    })
  }
  if (p.status === 'waiting') {
    return t('activityDetail.waitingRank', { rank: p.waitingRank ?? '?' })
  }
  if (p.status === 'checkedIn') return t('activityDetail.checkedInTag')
  return t('activityDetail.disabledTag')
})

const buttonDisabled = computed(() => {
  if (actioning.value) return true
  const p = participation.value
  if (!p) return true
  if (p.canCheckIn) return false
  if (p.canConfirmWaitingSeat) return false
  if (p.canRegister) return false
  if (p.status === 'registered') return !p.canCancelRegistration
  if (p.status === 'waiting') return !p.canCancelRegistration
  return true
})

function handleAction(): void {
  const p = participation.value
  if (!p || buttonDisabled.value) return
  if ((p.status === 'registered' || p.status === 'waiting') && p.canCancelRegistration) {
    showCancelConfirm()
    return
  }
  if (p.canConfirmWaitingSeat) {
    handleConfirmWaiting()
    return
  }
  if (p.canCheckIn) {
    handleCheckIn()
    return
  }
  if (p.canRegister) {
    showSafetyConfirm()
    return
  }
}

/**
 * 取消报名确认弹窗
 */
function showCancelConfirm(): void {
  actioning.value = true
  uni.showModal({
    title: '取消报名',
    content: '确定要取消报名吗？取消后可能需要重新排队',
    confirmText: t('确定'),
    cancelText: t('取消'),
    success: (res) => {
      if (res.confirm) {
        handleCancelRegistration()
      } else {
        actioning.value = false
      }
    },
    fail: () => {
      actioning.value = false
    },
  })
}

/**
 * 弹出安全须知确认弹窗，确认后再报名
 */
function showSafetyConfirm(): void {
  actioning.value = true
  uni.showModal({
    title: t('activityDetail.safetyNotice'),
    content: activity.value?.safetyNotice ?? '',
    confirmText: '我已阅读并同意',
    cancelText: '取消报名',
    success: (res) => {
      if (res.confirm) {
        handleRegister()
      } else {
        actioning.value = false
      }
    },
    fail: () => {
      actioning.value = false
    },
  })
}

/**
 * 扫码签到
 *
 * 尝试调用设备摄像头扫码，失败时回退到手动输入签到码。
 */
async function handleCheckIn(): Promise<void> {
  actioning.value = true
  let qrCodeToken = ''

  try {
    const scanResult = await uni.scanCode({})
    qrCodeToken = scanResult.result
  } catch {
    const inputResult = await new Promise<string | null>((resolve) => {
      uni.showModal({
        title: t('activityDetail.checkIn'),
        content: t('activityDetail.enterQrCode'),
        editable: true,
        confirmText: t('确定'),
        cancelText: t('取消'),
        success: (res) => {
          resolve(res.confirm ? (res.content as string)?.trim() || null : null)
        },
      })
    })
    qrCodeToken = inputResult ?? ''
  }

  if (!qrCodeToken) {
    actioning.value = false
    return
  }

  try {
    await checkIn(activityId.value, qrCodeToken)
    uni.showToast({ title: '签到成功', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '签到失败，请稍后重试', icon: 'none' })
    }
  } finally {
    actioning.value = false
  }
}

/**
 * 生成签到二维码
 *
 * 下载二维码图片到本地后提供「查看」和「保存到相册」两种操作。
 */
async function handleGenerateQrCode(): Promise<void> {
  try {
    uni.showLoading({ title: '生成中...' })
    const result = await generateCheckInQrCode(activityId.value)
    const qrImageUrl =
      'https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=' +
      encodeURIComponent(result.qrCodeToken)

    const dl = await uni.downloadFile({ url: qrImageUrl })
    uni.hideLoading()

    uni.showActionSheet({
      itemList: ['查看二维码', '保存到相册'],
      success: (action) => {
        if (action.tapIndex === 0) {
          uni.previewImage({ urls: [dl.tempFilePath] })
        } else {
          uni.saveImageToPhotosAlbum({
            filePath: dl.tempFilePath,
            success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
          })
        }
      },
    })
    uni.showToast({
      title: `${t('activityDetail.qrCodeExpires')}: ${formatDateTime(result.expiresAt)}`,
      icon: 'none',
      duration: 3000,
    })
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '生成二维码失败', icon: 'none' })
    }
  }
}

/**
 * 跳转到参与者列表页
 */
function goParticipants(): void {
  uni.navigateTo({ url: `/pages/activity/participants?activityId=${activityId.value}` })
}

/**
 * 跳转到签到管理页
 */
function goCheckIns(): void {
  uni.navigateTo({ url: `/pages/activity/check-ins?activityId=${activityId.value}` })
}

async function handleRegister(): Promise<void> {
  actioning.value = true
  try {
    const result: RegistrationResult = await registerForActivity(activityId.value, {
      acceptedSafetyNotice: true,
    })
    const message = result.status === 'waiting' ? '已加入候补' : '报名成功'
    uni.showToast({ title: message, icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '报名失败，请稍后重试', icon: 'none' })
    }
  } finally {
    actioning.value = false
  }
}

async function handleCancelRegistration(): Promise<void> {
  actioning.value = true
  try {
    await cancelRegistration(activityId.value)
    uni.showToast({ title: '已取消报名', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '取消失败，请稍后重试', icon: 'none' })
    }
  } finally {
    actioning.value = false
  }
}

async function handleConfirmWaiting(): Promise<void> {
  actioning.value = true
  try {
    await confirmWaitlist(activityId.value)
    uni.showToast({ title: '已确认名额', icon: 'success' })
    await loadData()
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '确认失败，请稍后重试', icon: 'none' })
    }
  } finally {
    actioning.value = false
  }
}

async function loadData(): Promise<void> {
  try {
    const [act, state, summariesRes, reviewsRes, myReviewRes] = await Promise.all([
      getActivityDetail(activityId.value),
      fetchParticipationState(activityId.value),
      getActivitySummaries(activityId.value, 1, 5),
      getActivityReviews(activityId.value, 1, 10),
      getMyActivityReview(activityId.value),
    ])
    activity.value = act
    participation.value = state

    publishedSummaries.value = summariesRes.items ?? []
    publishedReviews.value = reviewsRes.items ?? []
    hasReviewed.value = Boolean(myReviewRes.review)
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

onShow(() => {
  if (activityId.value && activity.value) {
    void loadData()
  }
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
  box-sizing: border-box;
  padding-bottom: calc(260rpx + env(safe-area-inset-bottom));
}

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

.section {
  margin: 16rpx 32rpx;
}

.card {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 28rpx 32rpx;
}

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
  background-color: #e8f7f0;
  color: #5ec8a7;
}

.status-registrationClosed {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.status-ongoing {
  background-color: #e8f7f0;
  color: #5ec8a7;
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
  color: #5ec8a7;
  background-color: #e8f7f0;
  padding: 4rpx 14rpx;
  border-radius: 4rpx;
}

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
  color: #5ec8a7;
}

.section-body {
  display: block;
  font-size: 26rpx;
  color: #646566;
  line-height: 1.7;
  white-space: pre-wrap;
}

.published-block {
  margin-top: 16rpx;
}

.published-block + .published-block {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #ebedf0;
}

.published-title {
  display: block;
  font-size: 28rpx;
  color: #323233;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.published-body {
  display: block;
  font-size: 26rpx;
  color: #646566;
  line-height: 1.6;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.review-rating {
  font-size: 24rpx;
  color: #ff9800;
}

/* AI 内容审核风险等级 */
.risk-high {
  color: #ee0a24;
  font-weight: 600;
}

.risk-medium {
  color: #ed6a0c;
}

.risk-low {
  color: #07c160;
}

.risk-uncertain {
  color: #969799;
}

.bar-btn-disabled {
  background-color: #c8c9cc;
  color: #fff;
  border-color: #c8c9cc;
}

.detail-action-stack {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.detail-action-stack .bar-btn {
  width: 100%;
  flex: none;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.action-btn-sm {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 160rpx;
  height: 64rpx;
  line-height: 64rpx;
  margin: 0;
  text-align: center;
  font-size: 24rpx;
  color: #5ec8a7;
  background-color: #e8f7f0;
  border-radius: 8rpx;
  border: none;
  padding: 0 12rpx;
  box-sizing: border-box;
}

.review-action-block {
  flex: 1;
  min-width: 200rpx;
}

.review-action-block .action-btn-sm {
  width: 100%;
}

.review-deadline-text {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  line-height: 1.3;
  color: #ed6a0c;
  text-align: center;
}

.action-status-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 160rpx;
  height: 64rpx;
  font-size: 24rpx;
  color: #969799;
  background-color: #f2f3f5;
  border-radius: 8rpx;
  padding: 0 12rpx;
  box-sizing: border-box;
}

/* ---- 参与者菜单 ---- */
.menu-card {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 28rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.menu-hover {
  opacity: 0.85;
}

.menu-text {
  font-size: 28rpx;
  color: #323233;
}

.menu-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.menu-count {
  font-size: 26rpx;
  color: #969799;
}

.menu-arrow {
  font-size: 28rpx;
  color: #c8c9cc;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

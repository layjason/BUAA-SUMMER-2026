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

        <!-- 参与者菜单 -->
        <view class="section">
          <view class="menu-card" hover-class="menu-hover" @click="goParticipants">
            <text class="menu-text">{{ t('activityDetail.viewParticipants') }}</text>
            <view class="menu-right">
              <text class="menu-count">{{ activity.registeredCount }}/{{ activity.capacity }}</text>
              <text class="menu-arrow">&gt;</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 底部操作按钮 -->
      <view class="action-bar">
        <button
          class="action-btn"
          :class="{ disabled: buttonDisabled }"
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
          <button v-if="isOrganizer" class="action-btn-sm" @click="handleViewCheckIns">
            {{ t('activityDetail.checkInManagement') }}
          </button>
          <button v-if="canReview" class="action-btn-sm" @click="goReview">
            {{ t('activityDetail.writeReview') }}
          </button>
          <button
            v-if="isOrganizer && activity.runtimeStatus === 'ended'"
            class="action-btn-sm"
            @click="goSummary"
          >
            {{ t('activityDetail.writeSummary') }}
          </button>
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
import { formatDateTime, formatTimeRange } from '@/utils/date'
import { runtimeStatusText } from '@/utils/status'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(true)
const errorMsg = ref('')
const actioning = ref(false)
const activityId = ref('')

interface MediaFile {
  mediaId: string
  fileName: string
  contentType: string
  sizeBytes: number
  usage: string
  url?: string
  uploadedAt: string
}

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
  coverImage: MediaFile | null
  images: MediaFile[]
  feeAmount?: number
  feeDescription?: string
  minAge?: number
  capacity: number
  registeredCount: number
  waitingCount: number
  registrationDeadline: string
  organizerId: string
  organizerName: string
  reviewStatus: string
  runtimeStatus: string
  manualReviewRequired: boolean
  reviewRecords: {
    reviewId: string
    result: string
    reason?: string
    reviewerId?: string
    reviewedAt: string
  }[]
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

const activity = ref<ActivityDetail | null>(null)
const participation = ref<ParticipationState | null>(null)

function getRuntimeStatusText(status: string): string {
  return runtimeStatusText(status, t)
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

/** 当前用户是否可以评价（已签到且活动已结束） */
const canReview = computed(() => {
  return participation.value?.status === 'checkedIn' && activity.value?.runtimeStatus === 'ended'
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
    const result = (await api.get('/activities/{activityId}/check-ins/export', {
      path: { activityId: activityId.value },
    })) as { url: string }

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

const isFull = computed(() => {
  if (!activity.value) return false
  return activity.value.registeredCount >= activity.value.capacity
})

const buttonText = computed(() => {
  const p = participation.value
  if (!p) return ''

  if (p.canCheckIn) return t('activityDetail.checkIn')
  if (p.canConfirmWaitingSeat) return t('activityDetail.confirmWaiting')
  if (p.canRegister) {
    if (isFull.value) {
      return t('activityDetail.registerFull', { count: activity.value!.waitingCount })
    }
    return t('activityDetail.registerNow', {
      count: activity.value!.registeredCount,
      total: activity.value!.capacity,
    })
  }
  if (p.status === 'registered' && p.canCancelRegistration)
    return t('activityDetail.cancelRegistration')
  if (p.status === 'waiting' && p.canCancelRegistration)
    return t('activityDetail.cancelRegistration')
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
  if (p.canCheckIn) {
    handleCheckIn()
    return
  }
  if (p.canConfirmWaitingSeat) {
    handleConfirmWaiting()
    return
  }
  if (p.canRegister) {
    showSafetyConfirm()
    return
  }
  if ((p.status === 'registered' || p.status === 'waiting') && p.canCancelRegistration) {
    handleCancelRegistration()
    return
  }
}

/**
 * 弹出安全须知确认弹窗，确认后再报名
 */
function showSafetyConfirm(): void {
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
    await api.post('/activities/{activityId}/check-ins', {
      path: { activityId: activityId.value },
      body: { qrCodeToken },
    })
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
    const result = (await api.post('/activities/{activityId}/check-in-qrcode', {
      path: { activityId: activityId.value },
    })) as { qrCodeToken: string; expiresAt: string }
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
 * 查看签到管理列表
 */
async function handleViewCheckIns(): Promise<void> {
  try {
    const result = (await api.get('/activities/{activityId}/check-ins', {
      path: { activityId: activityId.value },
    })) as { items: { nickname: string; registrationStatus: string }[] }
    const list = result.items
      .map((p) => `${p.nickname}（${p.registrationStatus === 'checkedIn' ? '已签到' : '未签到'}）`)
      .join('\n')
    uni.showModal({
      title: t('activityDetail.checkInManagement'),
      content: list || '暂无签到记录',
      showCancel: false,
    })
  } catch {
    uni.showModal({
      title: t('activityDetail.checkInManagement'),
      content: '加载失败',
      showCancel: false,
    })
  }
}

async function handleRegister(): Promise<void> {
  actioning.value = true
  try {
    await api.post('/activities/{activityId}/registrations', {
      path: { activityId: activityId.value },
      body: { acceptedSafetyNotice: true },
    })
    uni.showToast({ title: '报名成功', icon: 'success' })
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
    await api.post('/activities/{activityId}/registrations/cancel', {
      path: { activityId: activityId.value },
    })
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
    await api.post('/activities/{activityId}/waiting-confirmations', {
      path: { activityId: activityId.value },
      body: { confirmed: true },
    })
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
    const [act, state] = await Promise.all([
      api.get('/activities/{activityId}', { path: { activityId: activityId.value } }),
      api.get('/activities/{activityId}/participation-state', {
        path: { activityId: activityId.value },
      }),
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

.action-bar {
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #fff;
  border-top: 2rpx solid #ebedf0;
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

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.action-btn-sm {
  flex: 1;
  min-width: 160rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  font-size: 24rpx;
  color: #1989fa;
  background-color: #f0f6ff;
  border-radius: 8rpx;
  border: none;
  padding: 0 12rpx;
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

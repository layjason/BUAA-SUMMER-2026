<script setup lang="ts">
/**
 * 活动详情页。
 *
 * 前置条件：通过页面 query 传入 activityId，且当前用户已登录。
 * 后置条件：展示活动详情、审核进度、参与状态，并按 OpenAPI 状态执行报名、候补、签到、评价、总结等操作。
 * 不变量：页面只使用 OpenAPI 已定义字段，不新增正式业务状态或接口结构。
 */
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { BusinessError } from '@/api'
import {
  getActivityDetail,
  getActivityReviews,
  getActivitySummaries,
  getMyActivityReview,
  getParticipationState as fetchParticipationState,
  type ActivityDetail,
  type CheckInQrCode,
  type ActivityParticipationState,
  type ActivityReviewListItem,
  type ActivitySummaryPost,
  type RegistrationResult,
} from '@/api/modules/activities'
import {
  cancelRegistration,
  confirmWaitlist,
  registerForActivity,
} from '@/api/modules/registrations'
import { checkIn, generateCheckInQrCode } from '@/api/modules/checkin'
import { BottomActionBar } from '@/components'
import { isActivityAtCapacity } from '@/utils/activity-capacity'
import { formatDateTime, formatTimeRange } from '@/utils/date'
import { getErrorMessage } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

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
const checkInQrCode = ref<CheckInQrCode | null>(null)
const generatingQrCode = ref(false)
const savingQrCode = ref(false)

const runtimeStatusMap: Record<string, string> = {
  notStarted: '未开始',
  registering: '报名中',
  registrationClosed: '报名截止',
  ongoing: '进行中',
  ended: '已结束',
  takenDown: '已下架',
}

const reviewStatusMap: Record<string, string> = {
  draft: '草稿',
  pending: '审核中',
  approved: '审核通过',
  rejected: '已驳回',
  changeRequired: '需修改',
}

const riskLevelMap: Record<string, string> = {
  low: '低风险',
  medium: '中风险',
  high: '高风险',
  uncertain: '需人工判断',
}

/** 当前用户是否是活动发起人 */
const isOrganizer = computed(() => activity.value?.organizerId === authStore.userId)

/** 活动是否满员 */
const isAtCapacity = computed(() => {
  const act = activity.value
  if (!act) return false
  return isActivityAtCapacity(act.occupiedCount, act.capacity)
})

/** 费用展示文本，空费用不渲染。 */
const feeText = computed(() => {
  const fee = activity.value?.feeAmount
  if (fee == null) return ''
  return fee === 0 ? '免费' : `¥${fee}`
})

/** 报名人数展示文本。 */
const participantCountText = computed(() => {
  const act = activity.value
  if (!act) return ''
  return `${act.registeredCount}/${act.capacity}人`
})

/** 是否展示审核进度，仅发起人可见。 */
const showReviewProgress = computed(() => {
  return Boolean(isOrganizer.value && activity.value?.reviewStatus)
})

/** 审核原因，仅驳回和要求修改时展示。 */
const reviewReason = computed(() => {
  const act = activity.value
  if (!act || (act.reviewStatus !== 'rejected' && act.reviewStatus !== 'changeRequired')) return ''
  const records = act.reviewRecords ?? []
  return records[records.length - 1]?.reason ?? ''
})

/** 是否展示审核原因。 */
const showReviewReason = computed(() => Boolean(reviewReason.value))

/** 审核原因标题。 */
const reasonLabel = computed(() => {
  if (activity.value?.reviewStatus === 'changeRequired') return '修改意见'
  return '驳回原因'
})

/** 审核步骤连接线样式。 */
const stepConnectorClass = computed(() => {
  return activity.value?.reviewStatus === 'pending'
    ? 'step-connector step-connector-active'
    : 'step-connector step-connector-done'
})

/** 审核第二步图标。 */
const step2Icon = computed(() => {
  return activity.value?.reviewStatus === 'pending' ? '•' : '✓'
})

/** 审核结果图标。 */
const step3Icon = computed(() => {
  const status = activity.value?.reviewStatus
  if (status === 'approved') return '✓'
  if (status === 'rejected' || status === 'changeRequired') return '×'
  return ''
})

/** 审核结果节点样式。 */
const step3DotClass = computed(() => {
  const status = activity.value?.reviewStatus
  if (status === 'approved') return 'step-dot step-dot-done'
  if (status === 'rejected' || status === 'changeRequired') return 'step-dot step-dot-error'
  return 'step-dot'
})

/** 审核结果标签样式。 */
const step3LabelClass = computed(() => {
  const status = activity.value?.reviewStatus
  if (status === 'approved') return 'step-label step-label-done'
  if (status === 'rejected' || status === 'changeRequired') return 'step-label step-label-error'
  return 'step-label'
})

/** 当前用户是否满足评价条件。 */
const meetsReviewCondition = computed(() => {
  return participation.value?.status === 'checkedIn' && activity.value?.runtimeStatus === 'ended'
})

/** 当前用户是否可以评价。 */
const canReview = computed(() => meetsReviewCondition.value && !hasReviewed.value)

/** 是否展示已评价主按钮状态。 */
const showReviewedStatus = computed(() => meetsReviewCondition.value && hasReviewed.value)

/** 发起人是否可以写总结。 */
const canPostSummary = computed(() => {
  return (
    isOrganizer.value &&
    activity.value?.runtimeStatus === 'ended' &&
    publishedSummaries.value.length === 0
  )
})

/** 是否展示已发布总结状态。 */
const showSummaryPostedStatus = computed(() => {
  return (
    isOrganizer.value &&
    activity.value?.runtimeStatus === 'ended' &&
    publishedSummaries.value.length > 0
  )
})

/** 活动驳回或需修改时是否可返回编辑页。 */
const canResubmit = computed(() => {
  return (
    isOrganizer.value &&
    (activity.value?.reviewStatus === 'rejected' ||
      activity.value?.reviewStatus === 'changeRequired')
  )
})

/** 是否展示人工审核等待提示。 */
const showManualReviewTip = computed(() => {
  return Boolean(activity.value?.manualReviewRequired && activity.value.reviewStatus === 'pending')
})

/** 发起人是否可以生成签到二维码。 */
const canGenerateQrCode = computed(() => {
  const status = activity.value?.runtimeStatus
  return Boolean(
    isOrganizer.value &&
      activity.value?.reviewStatus === 'approved' &&
      (status === 'registering' || status === 'ongoing'),
  )
})

/** 发起人是否可以进入签到管理页。 */
const canViewCheckIns = computed(() => {
  return Boolean(isOrganizer.value && activity.value?.reviewStatus === 'approved')
})

/** 是否显示次级操作区域。 */
const hasSecondaryActions = computed(() => {
  return canPostSummary.value || showSummaryPostedStatus.value
})

/** 发起人签到管理卡片是否可见。 */
const showCheckInCard = computed(() => {
  return Boolean(isOrganizer.value && activity.value)
})

/** 签到二维码图片地址。 */
const checkInQrImageUrl = computed(() => {
  const token = checkInQrCode.value?.qrCodeToken
  if (!token) return ''
  return (
    'https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=' + encodeURIComponent(token)
  )
})

/** 签到二维码有效期展示文本。 */
const checkInQrExpiresText = computed(() => {
  const expiresAt = checkInQrCode.value?.expiresAt
  return expiresAt ? formatDateTime(expiresAt) : '生成后 10 分钟内有效'
})

/** 底部主按钮文案。 */
const buttonText = computed(() => {
  if (canResubmit.value) return '前往修改'
  const p = participation.value
  const act = activity.value
  if (!p || !act) return ''

  if (canReview.value) return '写评价'
  if (showReviewedStatus.value) return '已评价'
  if (p.canCheckIn) return '扫码签到'
  if (p.status === 'checkedIn') return '已签到'
  if (p.status === 'registered' && p.canCancelRegistration) {
    return `取消报名 (${participantCountText.value})`
  }
  if (p.status === 'registered') return `待签到 (${participantCountText.value})`
  if (p.status === 'waiting' && p.canCancelRegistration) {
    return `取消候补 (候补${act.waitingCount}人)`
  }
  if (p.canConfirmWaitingSeat) {
    const deadline = p.confirmationDeadline
      ? ` · ${formatDateTime(p.confirmationDeadline)}前确认`
      : ''
    return `候补确认${deadline}`
  }
  if (p.status === 'waitingConfirmation') {
    const deadline = p.confirmationDeadline
      ? ` · ${formatDateTime(p.confirmationDeadline)}前确认`
      : ''
    return `候补待确认${deadline}`
  }
  if (p.canRegister) {
    if (isAtCapacity.value) {
      return `加入候补 (${participantCountText.value} · 候补${act.waitingCount}人)`
    }
    return `立即报名 (${participantCountText.value})`
  }
  if (p.status === 'waiting') {
    return `候补中 (第${p.waitingRank ?? '?'}位 · 候补${act.waitingCount}人)`
  }
  if (act.runtimeStatus === 'takenDown') return '活动已下架'
  if (act.reviewStatus !== 'approved') return '活动尚未发布'
  if (act.runtimeStatus === 'notStarted') return '报名暂未开始'
  if (act.runtimeStatus === 'registrationClosed') return '报名已截止'
  if (act.runtimeStatus === 'ended') return '活动已结束'
  return '暂不可报名'
})

/** 底部主按钮是否禁用。 */
const buttonDisabled = computed(() => {
  if (actioning.value) return true
  if (canResubmit.value) return false
  const p = participation.value
  if (canReview.value) return false
  if (!p) return true
  if (p.canCheckIn) return false
  if (p.canConfirmWaitingSeat) return false
  if (p.canRegister) return false
  if (p.status === 'registered') return !p.canCancelRegistration
  if (p.status === 'waiting') return !p.canCancelRegistration
  return true
})

/** 是否展示底部主按钮。 */
const showPrimaryAction = computed(() => {
  if (canResubmit.value) return true
  const p = participation.value
  const act = activity.value
  if (!p || !act) return false
  if (canReview.value || showReviewedStatus.value) return true
  if (p.status === 'registered' || p.status === 'waiting' || p.status === 'waitingConfirmation') {
    return true
  }
  if (p.status === 'checkedIn') return true
  if (p.canRegister || p.canCheckIn || p.canConfirmWaitingSeat) return true
  if (act.runtimeStatus === 'registrationClosed' || act.runtimeStatus === 'ongoing') return false
  if (act.runtimeStatus === 'ended') return false
  return true
})

/** 活动评价区域标题。 */
const reviewsSectionText = computed(() => {
  return `活动评价（${publishedReviews.value.length}）`
})

/**
 * 获取活动状态文案。
 *
 * 前置条件：status 来自 ActivityRuntimeStatus。
 * 后置条件：返回中文展示文案。
 * 不变量：未知状态原样展示。
 *
 * @param status 运行状态
 */
function getRuntimeStatusText(status: string): string {
  return runtimeStatusMap[status] ?? status
}

/**
 * 获取审核状态文案。
 *
 * 前置条件：status 来自 ActivityReviewStatus。
 * 后置条件：返回中文展示文案。
 * 不变量：未知状态原样展示。
 *
 * @param status 审核状态
 */
function getReviewStatusText(status: string): string {
  return reviewStatusMap[status] ?? status
}

/**
 * 获取 AI 风险等级文案。
 *
 * 前置条件：level 来自 AI 审核结果。
 * 后置条件：返回中文展示文案。
 * 不变量：未知等级原样展示。
 *
 * @param level 风险等级
 */
function riskLevelText(level: string): string {
  return riskLevelMap[level] ?? level
}

/**
 * 格式化评价分数。
 *
 * 前置条件：rating 为 OpenAPI 返回的数值评分。
 * 后置条件：返回可读评分文案。
 * 不变量：不修改评价数据。
 *
 * @param rating 评分
 */
function formatReviewRating(rating: number): string {
  return `${rating} 分`
}

/**
 * 执行底部主按钮动作。
 *
 * 前置条件：当前按钮未禁用。
 * 后置条件：根据当前参与状态发起对应操作。
 * 不变量：不会绕过 OpenAPI 状态字段自行构造业务状态。
 */
function handleAction(): void {
  if (canResubmit.value) {
    goEdit()
    return
  }
  const p = participation.value
  if (!p || buttonDisabled.value) return
  if ((p.status === 'registered' || p.status === 'waiting') && p.canCancelRegistration) {
    showCancelConfirm()
    return
  }
  if (p.canConfirmWaitingSeat) {
    void handleConfirmWaiting()
    return
  }
  if (p.canCheckIn) {
    void handleCheckIn()
    return
  }
  if (canReview.value) {
    goReview()
    return
  }
  if (p.canRegister) {
    showSafetyConfirm()
  }
}

/**
 * 显示取消报名/候补确认弹窗。
 *
 * 前置条件：当前用户可取消正式报名或候补。
 * 后置条件：确认后调用取消报名接口。
 * 不变量：取消正式报名和取消候补共用 OpenAPI cancel path。
 */
function showCancelConfirm(): void {
  const isWaiting = participation.value?.status === 'waiting'
  actioning.value = true
  uni.showModal({
    title: isWaiting ? '取消候补' : '取消报名',
    content: isWaiting ? '确定要取消候补吗？' : '确定要取消报名吗？取消后可能需要重新排队。',
    confirmText: '确定',
    cancelText: '再想想',
    success: (res) => {
      if (res.confirm) {
        void handleCancelRegistration()
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
 * 显示报名安全须知确认弹窗。
 *
 * 前置条件：当前用户可报名或加入候补。
 * 后置条件：确认后提交报名请求。
 * 不变量：request body 与 RegisterActivityRequest 对齐。
 */
function showSafetyConfirm(): void {
  actioning.value = true
  uni.showModal({
    title: '报名确认',
    content: activity.value?.safetyNotice || '请确认已了解活动安排与安全须知。',
    confirmText: '我已阅读并同意',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) {
        void handleRegister()
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
 * 扫码签到。
 *
 * 前置条件：参与状态允许签到。
 * 后置条件：扫码取得 token，必要时附带 uni.getLocation 获取的当前位置提交签到。
 * 不变量：位置校验仅在活动 requireLocationCheck 为 true 时提交 currentLocation。
 */
async function handleCheckIn(): Promise<void> {
  actioning.value = true
  try {
    const scanResult = await uni.scanCode({})
    const token = scanResult.result
    if (!token) {
      actioning.value = false
      return
    }
    const currentLocation = activity.value?.requireLocationCheck
      ? await getCheckInLocation()
      : undefined
    await checkIn(activityId.value, token, currentLocation)
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
 * 获取签到位置。
 *
 * 前置条件：活动要求位置校验。
 * 后置条件：返回 OpenAPI GeoPoint 形态。
 * 不变量：只使用 uni-app 定位能力。
 */
async function getCheckInLocation(): Promise<{ longitude: number; latitude: number }> {
  const location = await uni.getLocation({ type: 'gcj02' })
  return {
    longitude: location.longitude,
    latitude: location.latitude,
  }
}

/**
 * 生成签到二维码。
 *
 * 前置条件：当前用户为发起人，且活动处于报名中或进行中。
 * 后置条件：展示可扫码的二维码图片。
 * 不变量：只调用 OpenAPI 定义的二维码 token 接口。
 */
async function handleGenerateQrCode(): Promise<void> {
  if (!canGenerateQrCode.value || generatingQrCode.value) return
  generatingQrCode.value = true
  try {
    checkInQrCode.value = await generateCheckInQrCode(activityId.value)
    uni.showToast({ title: '二维码已生成', icon: 'success' })
  } catch (error) {
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '生成二维码失败', icon: 'none' })
    }
  } finally {
    generatingQrCode.value = false
  }
}

/**
 * 保存签到二维码到本地相册。
 *
 * 前置条件：二维码图片地址已生成。
 * 后置条件：支持相册写入的平台保存图片；不支持的平台给出提示。
 * 不变量：保存内容仅来自二维码 token 渲染出的图片，不额外构造业务数据。
 */
async function saveCheckInQrCode(): Promise<void> {
  const url = checkInQrImageUrl.value
  if (!url || savingQrCode.value) return
  savingQrCode.value = true
  try {
    // #ifdef APP-PLUS
    // App 端：直接将网络 URL 传给 plus.gallery.save，由 SDK 内部处理下载，
    // 避免 uni.downloadFile 下载后文件无扩展名导致相册无法识别图片的问题。
    await saveImageFileToAlbum(url)
    // #endif
    // #ifndef APP-PLUS
    const downloadResult = await uni.downloadFile({ url })
    if (downloadResult.statusCode && downloadResult.statusCode >= 400) {
      throw new Error('download failed')
    }
    await saveImageFileToAlbum(downloadResult.tempFilePath)
    // #endif
    uni.showToast({ title: '已保存到相册', icon: 'success' })
  } catch {
    uni.showToast({ title: '保存失败，可长按二维码手动保存', icon: 'none' })
  } finally {
    savingQrCode.value = false
  }
}

/**
 * 将图片写入系统相册。
 *
 * 前置条件：filePath 为本地图片路径或网络图片 URL。App 端支持本地路径和网络
 * URL；非 App 端仅支持 uni.downloadFile 生成的本地临时图片路径。
 * 后置条件：图片写入系统相册，可被相册应用浏览。
 * 不变量：只保存已生成的二维码图片文件，不读取或修改业务状态。
 */
function saveImageFileToAlbum(filePath: string): Promise<void> {
  return new Promise((resolve, reject) => {
    // #ifdef APP-PLUS
    // 若是本地路径，转换为绝对路径后再保存，确保 plus.gallery 能正确访问文件
    const savePath = filePath.startsWith('http')
      ? filePath
      : plus.io.convertLocalFileSystemURL(filePath)
    plus.gallery.save(
      savePath,
      () => resolve(),
      (err) => reject(new Error(`gallery save failed: ${JSON.stringify(err)}`)),
    )
    // #endif

    // #ifndef APP-PLUS
    uni.saveImageToPhotosAlbum({
      filePath,
      success: () => resolve(),
      fail: (err) => reject(new Error(`save image failed: ${JSON.stringify(err)}`)),
    })
    // #endif
  })
}

/** 跳转评价页。 */
function goReview(): void {
  uni.navigateTo({ url: `/pages/activity/review?activityId=${activityId.value}` })
}

/** 跳转编辑页。 */
function goEdit(): void {
  uni.navigateTo({ url: `/pages/activity/edit?activityId=${activityId.value}` })
}

/** 跳转总结页。 */
function goSummary(): void {
  uni.navigateTo({ url: `/pages/activity/summary?activityId=${activityId.value}` })
}

/** 跳转参与者列表页。 */
function goParticipants(): void {
  uni.navigateTo({ url: `/pages/activity/participants?activityId=${activityId.value}` })
}

/** 跳转签到管理页。 */
function goCheckIns(): void {
  if (!canViewCheckIns.value) return
  uni.navigateTo({ url: `/pages/activity/check-ins?activityId=${activityId.value}` })
}

/**
 * 提交报名或加入候补。
 *
 * 前置条件：参与状态允许报名。
 * 后置条件：调用报名接口并刷新详情页。
 * 不变量：满员时是否进入候补以后端 RegistrationResult 为准。
 */
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

/**
 * 取消报名或候补。
 *
 * 前置条件：参与状态允许取消。
 * 后置条件：调用取消接口并刷新详情页。
 * 不变量：候补递补逻辑由后端模拟真实状态流转。
 */
async function handleCancelRegistration(): Promise<void> {
  actioning.value = true
  try {
    await cancelRegistration(activityId.value)
    uni.showToast({ title: '已取消', icon: 'success' })
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

/**
 * 确认候补释放名额。
 *
 * 前置条件：参与状态允许确认候补名额。
 * 后置条件：调用候补确认接口并刷新详情页。
 * 不变量：请求体与 WaitingConfirmationRequest 对齐。
 */
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

/**
 * 加载详情页全部数据。
 *
 * 前置条件：activityId 已存在。
 * 后置条件：同步活动详情、参与状态、总结、评价和我的评价状态。
 * 不变量：不改变任何业务状态。
 */
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
  void loadData()
})

onShow(() => {
  if (activityId.value && activity.value) {
    void loadData()
  }
})
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state-text">加载中...</view>
    <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>

    <template v-else-if="activity">
      <scroll-view class="scroll-area" scroll-y>
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

        <view class="section card">
          <view class="title-row">
            <text class="title">{{ activity.title }}</text>
            <text class="status-tag" :class="'status-' + activity.runtimeStatus">
              {{ getRuntimeStatusText(activity.runtimeStatus) }}
            </text>
          </view>
        </view>

        <view class="section card info-card">
          <view v-if="activity.startAt && activity.endAt" class="info-row">
            <text class="info-label">时间</text>
            <text class="info-value">{{ formatTimeRange(activity.startAt, activity.endAt) }}</text>
          </view>
          <view v-if="activity.location?.address" class="info-row">
            <text class="info-label">地点</text>
            <text class="info-value">{{ activity.location.address }}</text>
          </view>
          <view v-if="activity.organizerName" class="info-row">
            <text class="info-label">发起人</text>
            <text class="info-value">{{ activity.organizerName }}</text>
          </view>
          <view v-if="feeText" class="info-row">
            <text class="info-label">费用</text>
            <text class="info-value">{{ feeText }}</text>
          </view>
          <view v-if="activity.registrationDeadline" class="info-row">
            <text class="info-label">截止</text>
            <text class="info-value">{{ formatDateTime(activity.registrationDeadline) }}</text>
          </view>
          <view v-if="activity.tags.length" class="info-row">
            <text class="info-label">标签</text>
            <view class="tag-row">
              <text v-for="tag in activity.tags" :key="tag" class="tag-chip">{{ tag }}</text>
            </view>
          </view>
        </view>

        <view v-if="activity.introduction" class="section card">
          <text class="section-title">活动简介</text>
          <text class="section-body">{{ activity.introduction }}</text>
        </view>

        <view v-if="activity.safetyNotice" class="section card">
          <text class="section-title">安全须知</text>
          <text class="section-body">{{ activity.safetyNotice }}</text>
        </view>

        <view v-if="showReviewProgress" class="section card review-progress-card">
          <text class="section-title">审核进度</text>
          <view class="review-steps">
            <view class="review-step">
              <view class="step-dot step-dot-done">
                <text class="step-dot-icon">✓</text>
              </view>
              <text class="step-label step-label-done">提交审核</text>
            </view>
            <view class="step-connector" :class="stepConnectorClass"></view>
            <view class="review-step">
              <view
                class="step-dot"
                :class="activity.reviewStatus === 'pending' ? 'step-dot-active' : 'step-dot-done'"
              >
                <text class="step-dot-icon">{{ step2Icon }}</text>
              </view>
              <text
                class="step-label"
                :class="
                  activity.reviewStatus === 'pending' ? 'step-label-active' : 'step-label-done'
                "
              >
                内容审核
              </text>
            </view>
            <view class="step-connector" :class="stepConnectorClass"></view>
            <view class="review-step">
              <view class="step-dot" :class="step3DotClass">
                <text class="step-dot-icon">{{ step3Icon }}</text>
              </view>
              <text class="step-label" :class="step3LabelClass">审核结果</text>
            </view>
          </view>

          <view class="review-status-row">
            <text class="review-status-badge" :class="'review-badge-' + activity.reviewStatus">
              {{ getReviewStatusText(activity.reviewStatus) }}
            </text>
          </view>

          <view v-if="activity.aiContentReview" class="ai-review-summary">
            <view class="ai-review-header">
              <text class="ai-review-section-title">AI 审核结果</text>
              <text
                class="review-risk-tag"
                :class="'review-risk-' + activity.aiContentReview.riskLevel"
              >
                {{ riskLevelText(activity.aiContentReview.riskLevel) }}
              </text>
            </view>
            <view v-if="activity.aiContentReview.reasons.length" class="ai-review-detail">
              <text class="review-risk-reason">
                {{ activity.aiContentReview.reasons.join('；') }}
              </text>
            </view>
          </view>

          <view v-if="showManualReviewTip" class="review-tip">
            <text class="tip-icon">⏳</text>
            <text class="tip-text">该活动需要人工审核，请等待处理结果</text>
          </view>

          <view v-if="showReviewReason" class="review-reason-card">
            <text class="reason-label">{{ reasonLabel }}</text>
            <text class="reason-text">{{ reviewReason }}</text>
          </view>
        </view>

        <view v-if="publishedSummaries.length > 0" class="section card">
          <text class="section-title">活动总结</text>
          <view v-for="item in publishedSummaries" :key="item.summaryId" class="published-block">
            <text class="published-title">{{ item.title }}</text>
            <text class="published-body">{{ item.content }}</text>
          </view>
        </view>

        <view v-if="publishedReviews.length > 0" class="section card">
          <text class="section-title">{{ reviewsSectionText }}</text>
          <view v-for="item in publishedReviews" :key="item.reviewId" class="published-block">
            <view class="review-header">
              <text class="published-title">{{ item.nickname }}</text>
              <text class="review-rating">{{ formatReviewRating(item.rating) }}</text>
            </view>
            <view v-if="item.tags.length > 0" class="tag-row">
              <text v-for="tag in item.tags" :key="tag" class="tag-chip">{{ tag }}</text>
            </view>
            <text v-if="item.content" class="published-body">{{ item.content }}</text>
          </view>
        </view>

        <view class="section">
          <view class="menu-card" hover-class="menu-hover" @click="goParticipants">
            <text class="menu-text">参与者</text>
            <view class="menu-right">
              <text class="menu-count">{{ activity.occupiedCount }}/{{ activity.capacity }}</text>
              <text class="menu-arrow">&gt;</text>
            </view>
          </view>
        </view>

        <view v-if="showCheckInCard" class="section card checkin-card">
          <view class="checkin-card-header">
            <view>
              <text class="section-title">签到管理</text>
              <text class="checkin-desc"> 生成现场签到二维码，并查看参与者签到情况 </text>
            </view>
            <text v-if="activity.requireLocationCheck" class="checkin-location-tag">
              需位置校验
            </text>
          </view>

          <view v-if="checkInQrImageUrl" class="qr-panel">
            <image class="qr-image" :src="checkInQrImageUrl" mode="aspectFit" />
            <view class="qr-info">
              <text class="qr-title">签到二维码已生成</text>
              <text class="qr-expire">有效期至 {{ checkInQrExpiresText }}</text>
            </view>
            <button
              class="checkin-action checkin-action-ghost"
              :loading="savingQrCode"
              @click="saveCheckInQrCode"
            >
              保存到相册
            </button>
          </view>

          <view v-else class="qr-empty">
            <text class="qr-empty-text">还没有生成签到二维码</text>
            <text class="qr-empty-sub">二维码短期有效，建议活动开始前生成</text>
          </view>

          <view class="checkin-action-row">
            <button
              class="checkin-action"
              :disabled="!canGenerateQrCode"
              :loading="generatingQrCode"
              @click="handleGenerateQrCode"
            >
              {{ checkInQrImageUrl ? '重新生成二维码' : '生成签到二维码' }}
            </button>
            <button
              class="checkin-action checkin-action-ghost"
              :disabled="!canViewCheckIns"
              @click="goCheckIns"
            >
              查看签到情况
            </button>
          </view>
        </view>
      </scroll-view>

      <BottomActionBar v-if="showPrimaryAction || hasSecondaryActions">
        <view class="detail-action-stack">
          <button
            v-if="showPrimaryAction"
            class="bar-btn bar-btn-primary"
            :class="{ 'bar-btn-disabled': buttonDisabled }"
            :disabled="buttonDisabled"
            :loading="actioning"
            @click="handleAction"
          >
            {{ buttonText }}
          </button>
          <view v-if="hasSecondaryActions" class="action-row">
            <button v-if="canPostSummary" class="action-btn-sm" @click="goSummary">写总结</button>
            <text v-else-if="showSummaryPostedStatus" class="action-status-chip">已发布总结</text>
          </view>
        </view>
      </BottomActionBar>
    </template>
  </view>
</template>

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
  padding-bottom: 24rpx;
}

.swiper-wrapper,
.swiper,
.swiper-image,
.single-image,
.image-placeholder {
  width: 100%;
  height: 420rpx;
}

.single-image {
  display: block;
}

.image-placeholder {
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

.status-registering,
.status-ongoing {
  background-color: #e8f7f0;
  color: #5ec8a7;
}

.status-registrationClosed {
  background-color: #fff7e6;
  color: #ed6a0c;
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

.action-btn-warning {
  color: #ed6a0c;
  background-color: #fff7e6;
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

.checkin-card {
  padding: 28rpx 32rpx;
}

.checkin-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.checkin-desc {
  display: block;
  font-size: 24rpx;
  color: #969799;
  line-height: 1.5;
}

.checkin-location-tag {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #ed6a0c;
  background-color: #fff7e6;
  border-radius: 4rpx;
  padding: 6rpx 14rpx;
}

.qr-panel {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 12rpx;
  background-color: #f8f9fc;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-image {
  width: 320rpx;
  height: 320rpx;
  background-color: #fff;
  border-radius: 8rpx;
}

.qr-info {
  margin: 18rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.qr-title {
  font-size: 28rpx;
  color: #323233;
  font-weight: 600;
}

.qr-expire {
  font-size: 24rpx;
  color: #969799;
}

.qr-empty {
  margin-top: 24rpx;
  padding: 28rpx 24rpx;
  border-radius: 12rpx;
  background-color: #f8f9fc;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.qr-empty-text {
  font-size: 28rpx;
  color: #323233;
  font-weight: 600;
}

.qr-empty-sub {
  font-size: 24rpx;
  color: #969799;
}

.checkin-action-row {
  margin-top: 20rpx;
  display: flex;
  gap: 16rpx;
}

.checkin-action {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0;
  border: none;
  border-radius: 8rpx;
  background-color: #5ec8a7;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
}

.checkin-action[disabled] {
  background-color: #c8c9cc;
  color: #fff;
}

.checkin-action-ghost {
  background-color: #e8f7f0;
  color: #5ec8a7;
}

.review-progress-card {
  margin-top: 16rpx;
}

.review-steps {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  margin: 20rpx 0;
  padding: 0 20rpx;
}

.review-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.step-dot {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background-color: #ebedf0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.step-dot-done {
  background-color: #07c160;
}

.step-dot-active {
  background-color: #5ec8a7;
}

.step-dot-error {
  background-color: #ee0a24;
}

.step-dot-icon {
  font-size: 20rpx;
  color: #fff;
  font-weight: 700;
}

.step-label {
  font-size: 22rpx;
  color: #969799;
  text-align: center;
}

.step-label-done {
  color: #07c160;
}

.step-label-active {
  color: #5ec8a7;
}

.step-label-error {
  color: #ee0a24;
}

.step-connector {
  flex: 0.6;
  height: 2rpx;
  background-color: #ebedf0;
  margin-top: 20rpx;
  align-self: flex-start;
}

.step-connector-done {
  background-color: #07c160;
}

.step-connector-active {
  background-color: #5ec8a7;
}

.review-status-row {
  text-align: center;
  margin: 16rpx 0 8rpx;
}

.review-status-badge {
  display: inline-block;
  font-size: 24rpx;
  padding: 6rpx 20rpx;
  border-radius: 4rpx;
  font-weight: 600;
}

.review-badge-pending {
  background-color: #e8f7f0;
  color: #5ec8a7;
}

.review-badge-approved {
  background-color: #e8f7f0;
  color: #07c160;
}

.review-badge-rejected {
  background-color: #fff2f0;
  color: #ee0a24;
}

.review-badge-changeRequired {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.review-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin: 12rpx 0;
  padding: 12rpx 20rpx;
  background-color: #f0f9ff;
  border-radius: 8rpx;
}

.tip-icon {
  font-size: 28rpx;
}

.tip-text {
  font-size: 24rpx;
  color: #5ec8a7;
}

.review-reason-card {
  margin-top: 12rpx;
  padding: 16rpx 20rpx;
  background-color: #fff7e6;
  border-radius: 8rpx;
}

.reason-label {
  display: block;
  font-size: 22rpx;
  color: #ed6a0c;
  font-weight: 600;
  margin-bottom: 6rpx;
}

.reason-text {
  display: block;
  font-size: 24rpx;
  color: #323233;
  line-height: 1.5;
}

.ai-review-summary {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  background-color: #f8f9fc;
  border-radius: 8rpx;
}

.ai-review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.ai-review-section-title {
  font-size: 24rpx;
  color: #969799;
  font-weight: 600;
}

.review-risk-tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 4rpx;
  font-weight: 600;
}

.review-risk-low {
  background-color: #e8f7f0;
  color: #07c160;
}

.review-risk-medium {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.review-risk-high {
  background-color: #fff2f0;
  color: #ee0a24;
}

.review-risk-uncertain {
  background-color: #f2f3f5;
  color: #969799;
}

.ai-review-detail {
  margin-top: 4rpx;
}

.review-risk-reason {
  font-size: 24rpx;
  color: #646566;
  line-height: 1.6;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

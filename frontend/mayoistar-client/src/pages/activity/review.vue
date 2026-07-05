<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="form-container">
        <text class="title">{{ t('activityReview.title') }}</text>

        <view v-if="reviewDeadlineText" class="deadline-card">
          <text class="deadline-text">{{ reviewDeadlineText }}</text>
        </view>

        <view class="star-section">
          <text class="label">{{ t('activityReview.rating') }}</text>
          <view class="star-row">
            <text
              v-for="n in 5"
              :key="n"
              class="star"
              :class="{ active: n <= rating }"
              @click="rating = n"
            >
              {{ n <= rating ? '★' : '☆' }}
            </text>
          </view>
        </view>

        <!-- 评价标签 -->
        <view class="form-item">
          <text class="label">评价标签</text>
          <view class="tag-options">
            <text
              v-for="tag in availableTags"
              :key="tag"
              class="tag-option"
              :class="{ 'tag-selected': selectedTags.includes(tag) }"
              @click="toggleTag(tag)"
              >{{ tag }}</text
            >
          </view>
        </view>

        <view class="form-item">
          <text class="label">{{ t('activityReview.content') }}</text>
          <textarea
            v-model="content"
            class="textarea"
            :placeholder="t('activityReview.contentPlaceholder')"
            :maxlength="2000"
            auto-height
          />
        </view>

        <view class="form-item">
          <text class="label">{{ t('reviewImages.addImage') }}</text>
          <view class="image-grid">
            <view
              v-for="(img, idx) in imageUrls"
              :key="idx"
              class="image-item"
              @click="removeReviewImage(idx)"
            >
              <image class="review-image" :src="img" mode="aspectFill" />
              <text class="image-remove">×</text>
            </view>
            <view class="image-add-btn" @click="handleAddReviewImage">
              <text class="add-icon">+</text>
            </view>
          </view>
        </view>

        <FormError :message="formError" />
      </view>
    </scroll-view>

    <BottomActionBar>
      <button
        class="bar-btn bar-btn-primary"
        :loading="submitting"
        :disabled="rating === 0"
        @click="handleSubmit"
      >
        {{ rating === 0 ? '请先打分' : t('activityReview.submit') }}
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动评价页
 *
 * 参与者对已结束活动进行评价。
 * 前置条件：用户已登录，activityId 通过 query 传入，活动已结束且用户已签到
 * 后置条件：提交成功后返回上一页
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'

import {
  createActivityReview,
  getParticipationState,
  getMyActivityReview,
  uploadReviewImages,
  type ActivityParticipationState,
  type MyActivityReviewResult,
} from '@/api/modules/activities'
import { getErrorMessage } from '@/utils/error'
import { formatDateTime } from '@/utils/date'
import { FormError, BottomActionBar } from '@/components'

const { t } = useI18n()

const activityId = ref('')
const rating = ref(0)
const content = ref('')
const submitting = ref(false)
const formError = ref('')
const imageUrls = ref<string[]>([])
const reviewMarkdownImageUrls = ref<string[]>([])
const uploadingImage = ref(false)
const participation = ref<ActivityParticipationState | null>(null)

// 预设评价标签
const availableTags = ['组织有序', '氛围很好', '收获满满', '场地不错', '时间合理', '物超所值']
const selectedTags = ref<string[]>([])

/** 评价入口截止时间文案，空字符串表示后端未返回截止时间 */
const reviewDeadlineText = computed(() => {
  const endsAt = participation.value?.reviewWindowEndsAt
  if (!endsAt) return ''
  return t('activityReview.deadline', { time: formatDateTime(endsAt) })
})

/** 当前评价窗口是否已经在本地时间中过期，最终结果仍以后端提交接口校验为准 */
const isReviewWindowExpired = computed(() => {
  const endsAt = participation.value?.reviewWindowEndsAt
  return endsAt ? new Date(endsAt).getTime() <= Date.now() : false
})

/** 切换标签选中状态 */
function toggleTag(tag: string): void {
  const idx = selectedTags.value.indexOf(tag)
  if (idx >= 0) {
    selectedTags.value.splice(idx, 1)
  } else {
    selectedTags.value.push(tag)
  }
}

/**
 * 添加评价图片
 *
 * 上传图片后自动将 Markdown 图片链接插入到评价正文末尾。
 */
async function handleAddReviewImage(): Promise<void> {
  if (uploadingImage.value) return
  try {
    const res = await uni.chooseImage({
      count: 9 - imageUrls.value.length,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    uploadingImage.value = true
    try {
      const selectedPaths = res.tempFilePaths as string[]
      const results = await uploadReviewImages(selectedPaths)
      for (const [index, r] of results.entries()) {
        const previewUrl = selectedPaths[index]
        const markdownUrl = r.signedUrl || ''
        if (!previewUrl) continue
        imageUrls.value.push(previewUrl)
        reviewMarkdownImageUrls.value.push(markdownUrl)
        if (markdownUrl) {
          if (content.value) content.value += '\n'
          content.value += `![评价图片](${markdownUrl})`
        }
      }
    } catch {
      formError.value = '图片上传失败'
    }
  } catch {
    /* 用户取消选择 */
  } finally {
    uploadingImage.value = false
  }
}

/**
 * 删除已上传的评价图片
 *
 * 同时从正文中移除对应的 Markdown 图片链接。
 */
function removeReviewImage(index: number): void {
  const removedUrl = reviewMarkdownImageUrls.value[index]
  imageUrls.value.splice(index, 1)
  reviewMarkdownImageUrls.value.splice(index, 1)
  if (removedUrl) content.value = content.value.replace(`![评价图片](${removedUrl})`, '').trim()
}

interface PageRouteSnapshot {
  route?: string
  options?: Record<string, string | undefined>
}

/** 返回活动详情页
 *
 * 前置条件：activityId 已由页面 query 初始化。
 * 后置条件：若上一页是同一活动详情页则后退，否则当前评价页被活动详情页替换。
 * 不变量：最终目标始终为当前活动详情页，且不重复堆叠同一详情页。
 */
function returnToActivityDetail(): void {
  const pages = getCurrentPages() as PageRouteSnapshot[]
  const previousPage = pages[pages.length - 2]
  if (
    previousPage?.route === 'pages/activity/detail' &&
    previousPage.options?.activityId === activityId.value
  ) {
    uni.navigateBack()
    return
  }

  uni.redirectTo({ url: `/pages/activity/detail?activityId=${activityId.value}` })
}

async function handleSubmit(): Promise<void> {
  if (submitting.value || rating.value === 0) return
  formError.value = ''
  if (isReviewWindowExpired.value) {
    formError.value = t('activityReview.windowExpired')
    return
  }
  submitting.value = true

  try {
    await createActivityReview(activityId.value, {
      rating: rating.value,
      content: content.value.trim() || undefined,
      tags: selectedTags.value,
    })
    uni.showToast({ title: t('activityReview.success'), icon: 'success' })
    setTimeout(returnToActivityDetail, 1500)
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, t('activityReview.failed'))
    }
  } finally {
    submitting.value = false
  }
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  if (!activityId.value) {
    uni.showToast({ title: '缺少活动标识', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 1000)
    return
  }
  void (async () => {
    try {
      const [state, result]: [ActivityParticipationState, MyActivityReviewResult] =
        await Promise.all([
          getParticipationState(activityId.value),
          getMyActivityReview(activityId.value),
        ])
      participation.value = state
      if (result.review) {
        uni.showToast({ title: t('activityDetail.alreadyReviewed'), icon: 'none' })
        setTimeout(returnToActivityDetail, 1000)
        return
      }
      if (!state.canReview) {
        uni.showToast({ title: t('activityReview.windowClosed'), icon: 'none' })
        setTimeout(returnToActivityDetail, 1000)
      }
    } catch {
      /* 查询失败不阻塞填写 */
    }
  })()
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

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.form-container {
  padding: 32rpx;
}

.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-color-text);
  margin-bottom: 40rpx;
}

.deadline-card {
  margin-bottom: 32rpx;
  padding: 20rpx 24rpx;
  border-radius: 12rpx;
  background-color: var(--q-color-accent-light);
}

.deadline-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--q-color-warning);
}

.star-section {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: var(--q-color-text);
  margin-bottom: 16rpx;
}

.star-row {
  display: flex;
  gap: 16rpx;
}

.star {
  font-size: 56rpx;
  color: var(--q-color-text-muted);
  line-height: 1;
}

.star.active {
  color: var(--q-color-warning);
}

.form-item {
  margin-bottom: 32rpx;
}

.textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  font-size: 28rpx;
  color: var(--q-color-text);
  box-sizing: border-box;
}

.submit-btn {
  display: none;
}

/* ---- 评价标签 ---- */
.tag-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-option {
  font-size: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 8rpx;
  background-color: var(--q-color-bg-soft);
  color: var(--q-color-text-sub);
}

.tag-selected {
  background-color: var(--q-color-primary-light);
  color: var(--q-color-primary);
  font-weight: 500;
}

/* ---- 评价图片 ---- */
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  overflow: hidden;
}

.review-image {
  width: 100%;
  height: 100%;
}

.image-remove {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  background-color: rgba(0, 0, 0, 0.5);
  color: var(--q-color-bg-card);
  border-radius: 50%;
  font-size: 24rpx;
}

.image-add-btn {
  width: 160rpx;
  height: 160rpx;
  border: 2rpx dashed var(--q-color-text-muted);
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--q-color-bg-card);
}

.image-add-btn .add-icon {
  font-size: 48rpx;
  color: var(--q-color-text-muted);
  line-height: 1;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

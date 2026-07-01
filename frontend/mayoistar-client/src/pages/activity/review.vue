<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="form-container">
        <text class="title">{{ t('activityReview.title') }}</text>

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

        <button
          class="submit-btn"
          :loading="submitting"
          :disabled="rating === 0"
          @click="handleSubmit"
        >
          {{ rating === 0 ? '请先打分' : t('activityReview.submit') }}
        </button>
      </view>
    </scroll-view>
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
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { FormError } from '@/components'

const { t } = useI18n()

const activityId = ref('')
const rating = ref(0)
const content = ref('')
const submitting = ref(false)
const formError = ref('')
const imageUrls = ref<string[]>([])
const uploadingImage = ref(false)

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
    for (const tempPath of res.tempFilePaths) {
      try {
        const result = (await api.upload('/activities/media/review-images', tempPath)) as {
          url?: string
          mediaId: string
        }
        const imageUrl = result.url || tempPath
        imageUrls.value.push(imageUrl)
        if (content.value) content.value += '\n'
        content.value += `![评价图片](${imageUrl})`
      } catch {
        formError.value = '图片上传失败'
      }
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
  const removedUrl = imageUrls.value[index]
  imageUrls.value.splice(index, 1)
  content.value = content.value.replace(`![评价图片](${removedUrl})`, '').trim()
}

async function handleSubmit(): Promise<void> {
  if (submitting.value || rating.value === 0) return
  formError.value = ''
  submitting.value = true

  try {
    await api.post('/activities/{activityId}/reviews', {
      path: { activityId: activityId.value },
      body: {
        rating: rating.value,
        content: content.value.trim() || undefined,
        tags: [],
      },
    })
    uni.showToast({ title: t('activityReview.success'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
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
  color: #323233;
  margin-bottom: 40rpx;
}

.star-section {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 16rpx;
}

.star-row {
  display: flex;
  gap: 16rpx;
}

.star {
  font-size: 56rpx;
  color: #c8c9cc;
  line-height: 1;
}

.star.active {
  color: #ff9800;
}

.form-item {
  margin-bottom: 32rpx;
}

.textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

.submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #fff;
  background-color: #1989fa;
  border-radius: 12rpx;
  border: none;
  margin-top: 16rpx;
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
  color: #fff;
  border-radius: 50%;
  font-size: 24rpx;
}

.image-add-btn {
  width: 160rpx;
  height: 160rpx;
  border: 2rpx dashed #c8c9cc;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fff;
}

.image-add-btn .add-icon {
  font-size: 48rpx;
  color: #c8c9cc;
  line-height: 1;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

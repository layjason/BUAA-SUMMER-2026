<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="form-container">
        <text class="title">{{ t('activitySummary.title') }}</text>

        <!-- 活动总结图片 -->
        <view class="form-item">
          <text class="label">{{ t('activitySummary.images') }}</text>
          <view class="image-grid">
            <view
              v-for="(img, idx) in imagePreviews"
              :key="idx"
              class="image-preview-item"
              @click="removeImage(idx)"
            >
              <image class="preview-image" :src="img" mode="aspectFill" />
              <text class="image-remove">×</text>
            </view>
            <view class="image-add-btn" @click="handleAddImage">
              <text class="add-icon">+</text>
            </view>
          </view>
        </view>

        <!-- 总结标题 -->
        <view class="form-item">
          <text class="label">{{ t('activitySummary.summaryTitle') }}</text>
          <input
            v-model="summaryTitle"
            class="input"
            :placeholder="t('activitySummary.summaryTitlePlaceholder')"
          />
        </view>

        <!-- 总结正文 -->
        <view class="form-item">
          <text class="label">{{ t('activitySummary.content') }}</text>
          <textarea
            v-model="summaryContent"
            class="textarea"
            :placeholder="t('activitySummary.contentPlaceholder')"
            :maxlength="5000"
            auto-height
          />
        </view>

        <FormError :message="formError" />

        <button class="submit-btn" :loading="submitting" @click="handleSubmit">
          {{ t('activitySummary.submit') }}
        </button>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动图文总结页
 *
 * 发起人在活动结束后发布图文总结。
 * 前置条件：用户已登录且为活动发起人，activityId 通过 query 传入
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
const summaryTitle = ref('')
const summaryContent = ref('')
const imagePreviews = ref<string[]>([])
const imageIds = ref<string[]>([])
const submitting = ref(false)
const formError = ref('')

async function handleAddImage(): Promise<void> {
  try {
    const res = await uni.chooseImage({
      count: 9 - imagePreviews.value.length,
      sizeType: ['compressed'],
    })
    for (const tempPath of res.tempFilePaths) {
      try {
        const result = (await api.upload('/activities/media/images', tempPath)) as {
          mediaId: string
          url?: string
        }
        imageIds.value.push(result.mediaId)
        imagePreviews.value.push(result.url || tempPath)
      } catch {
        formError.value = '图片上传失败'
      }
    }
  } catch {
    /* 用户取消选择 */
  }
}

function removeImage(index: number): void {
  imageIds.value.splice(index, 1)
  imagePreviews.value.splice(index, 1)
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  if (!summaryTitle.value.trim()) {
    formError.value = t('activitySummary.titleRequired')
    return
  }
  if (!summaryContent.value.trim()) {
    formError.value = t('activitySummary.contentRequired')
    return
  }
  formError.value = ''
  submitting.value = true

  try {
    await api.post('/activities/{activityId}/summaries', {
      path: { activityId: activityId.value },
      body: {
        title: summaryTitle.value.trim(),
        content: summaryContent.value.trim(),
        imageIds: [...imageIds.value],
        confirmedImageTags: imageIds.value.map((id) => ({ mediaId: id, tags: [] })),
      },
    })
    uni.showToast({ title: t('activitySummary.success'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, t('activitySummary.failed'))
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
  padding: 32rpx 32rpx calc(80rpx + env(safe-area-inset-bottom));
}

.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #323233;
  margin-bottom: 40rpx;
}

.form-item {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

.textarea {
  width: 100%;
  min-height: 300rpx;
  padding: 20rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-preview-item {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  border-radius: 8rpx;
  overflow: hidden;
}

.preview-image {
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
  width: 180rpx;
  height: 180rpx;
  border: 2rpx dashed #c8c9cc;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fff;
}

.add-icon {
  font-size: 48rpx;
  color: #c8c9cc;
  line-height: 1;
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
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

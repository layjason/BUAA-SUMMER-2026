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

        <!-- AI 图片分类确认 -->
        <view v-if="imageClassifications.size > 0" class="form-item">
          <text class="label">AI 图片分类（可编辑确认）</text>
          <view
            v-for="[mediaId, tags] in imageClassifications"
            :key="mediaId"
            class="classification-row"
          >
            <text class="classification-media">{{
              mediaId ? mediaId.slice(0, 8) + '...' : '—'
            }}</text>
            <view class="classification-tags">
              <text v-for="tag in tags" :key="tag" class="tag-chip">{{ tag }}</text>
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
      </view>
    </scroll-view>

    <BottomActionBar>
      <button class="bar-btn bar-btn-primary" :loading="submitting" @click="handleSubmit">
        {{ t('activitySummary.submit') }}
      </button>
    </BottomActionBar>
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
import { BusinessError } from '@/api'
import {
  createActivitySummary,
  getMyActivitySummary,
  uploadActivityImages,
  type MyActivitySummaryResult,
} from '@/api/modules/activities'
import { classifyImages, type ImageClassificationResult } from '@/api/modules/ai'
import { getErrorMessage } from '@/utils/error'
import { FormError, BottomActionBar } from '@/components'

const { t } = useI18n()

const activityId = ref('')
const summaryTitle = ref('')
const summaryContent = ref('')
const imagePreviews = ref<string[]>([])
const imageIds = ref<string[]>([])
const submitting = ref(false)
const formError = ref('')
// AI 图片分类结果：mediaId -> tags
const imageClassifications = ref<Map<string, string[]>>(new Map())

/**
 * 添加图片并调用 AI 分类
 *
 * 批量上传图片后，调用 AI 接口获取分类标签供用户确认。
 */
async function handleAddImage(): Promise<void> {
  try {
    const res = await uni.chooseImage({
      count: 9 - imagePreviews.value.length,
      sizeType: ['compressed'],
      extension: ['jpg', 'jpeg', 'png'],
    })
    try {
      const results = await uploadActivityImages(res.tempFilePaths as string[])
      const newMediaIds: string[] = []
      for (const r of results) {
        const mediaId = r.mediaId
        const url = r.signedUrl
        if (!mediaId) continue
        imageIds.value.push(mediaId)
        imagePreviews.value.push(url || '')
        newMediaIds.push(mediaId)
      }
      // 调用 AI 图片分类
      try {
        const classification: ImageClassificationResult = await classifyImages(newMediaIds)
        if (classification.status === 'succeeded' && classification.items) {
          for (const item of classification.items) {
            imageClassifications.value.set(item.mediaId, item.suggestedTags)
          }
        }
      } catch {
        // 分类失败不阻塞
      }
    } catch {
      formError.value = '图片上传失败'
    }
  } catch {
    /* 用户取消选择 */
  }
}

/**
 * 移除已上传的图片及其分类结果
 *
 * @param index 图片在列表中的索引
 */
function removeImage(index: number): void {
  const removedId = imageIds.value[index]
  imageIds.value.splice(index, 1)
  imagePreviews.value.splice(index, 1)
  imageClassifications.value.delete(removedId)
}

/**
 * 提交活动总结
 *
 * 将用户确认的图片分类标签作为 confirmedImageTags 提交。
 */
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
    // 构建 confirmedImageTags：优先使用 AI 分类结果，否则空标签
    const confirmedImageTags = imageIds.value.map((id) => ({
      mediaId: id,
      tags: imageClassifications.value.get(id) ?? [],
    }))

    await createActivitySummary(activityId.value, {
      title: summaryTitle.value.trim(),
      content: summaryContent.value.trim(),
      imageIds: [...imageIds.value],
      confirmedImageTags,
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
    return
  }
  void (async () => {
    try {
      const result: MyActivitySummaryResult = await getMyActivitySummary(activityId.value)
      if (result.summary) {
        uni.showToast({ title: t('activityDetail.alreadySummarized'), icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1000)
      }
    } catch {
      /* 查询失败不阻塞填写 */
    }
  })()
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
  padding: 32rpx 32rpx calc(240rpx + env(safe-area-inset-bottom));
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

/* AI 图片分类 */
.classification-row {
  display: flex;
  align-items: center;
  background-color: #fff;
  border-radius: 8rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 12rpx;
}

.classification-media {
  font-size: 22rpx;
  color: #969799;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.classification-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.tag-chip {
  font-size: 22rpx;
  color: #5ec8a7;
  background-color: #e8f7f0;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

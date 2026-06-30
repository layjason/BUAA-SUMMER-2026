<template>
  <view class="page">
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

      <FormError :message="formError" />

      <button class="submit-btn" :loading="submitting" @click="handleSubmit">
        {{ t('activityReview.submit') }}
      </button>
    </view>
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
  min-height: 100vh;
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
</style>

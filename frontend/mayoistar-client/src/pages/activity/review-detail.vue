<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading" class="state-text">{{ t('activityDetail.loading') }}</view>
      <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>
      <view v-else-if="review" class="detail-container">
        <view class="card">
          <view class="header-row">
            <text class="nickname">{{ review.nickname }}</text>
            <text class="rating">{{
              t('activityDetail.reviewStars', { rating: review.rating })
            }}</text>
          </view>
          <text class="meta">{{ formatDateTime(review.createdAt) }}</text>

          <view v-if="review.tags.length > 0" class="tag-row">
            <text v-for="tag in review.tags" :key="tag" class="tag-chip">{{ tag }}</text>
          </view>

          <text v-if="reviewTextContent" class="content">{{ reviewTextContent }}</text>
          <text v-else class="empty-content">该用户未填写文字评价</text>

          <view v-if="reviewImageUrls.length > 0" class="review-image-grid">
            <image
              v-for="url in reviewImageUrls"
              :key="url"
              class="review-image"
              :src="url"
              mode="aspectFill"
            />
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动评价详情页
 *
 * 通过活动评价列表接口按 reviewId 回查单条评价详情。
 * 前置条件：activityId 和 reviewId 通过 query 传入。
 * 后置条件：成功时展示评价人、评分、标签、正文和创建时间。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getActivityReviews, type ActivityReviewListItem } from '@/api/modules/activities'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')
const activityId = ref('')
const reviewId = ref('')
const review = ref<ActivityReviewListItem | null>(null)

const markdownImagePattern = /!\[[^\]]*]\(([^)]+)\)/g

/** 评价正文中的 Markdown 图片 URL 列表 */
const reviewImageUrls = computed(() => {
  const content = review.value?.content ?? ''
  return [...content.matchAll(markdownImagePattern)].map((match) => match[1]).filter(Boolean)
})

/** 去除 Markdown 图片后的评价文本 */
const reviewTextContent = computed(() => {
  const content = review.value?.content ?? ''
  return content.replace(markdownImagePattern, '').trim()
})

/** 加载活动评价详情
 *
 * 前置条件：activityId 和 reviewId 不为空。
 * 后置条件：review 指向匹配的活动评价；未找到时写入 errorMsg。
 * 不变量：不修改远端数据，仅执行只读查询。
 */
async function loadReviewDetail(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await getActivityReviews(activityId.value, 1, 50)
    review.value = (result.items ?? []).find((item) => item.reviewId === reviewId.value) ?? null
    if (!review.value) errorMsg.value = '未找到活动评价'
  } catch {
    errorMsg.value = '加载活动评价失败'
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  reviewId.value = (query?.reviewId as string) ?? ''
  if (!activityId.value || !reviewId.value) {
    errorMsg.value = '缺少活动评价标识'
    loading.value = false
    return
  }
  void loadReviewDetail()
})
</script>

<style scoped>
.page {
  height: 100%;
  background-color: #f7f8fa;
  overflow: hidden;
}

.scroll-area {
  height: 100%;
}

.detail-container {
  padding: 24rpx 24rpx 48rpx;
}

.card {
  padding: 28rpx;
  border-radius: 16rpx;
  background-color: #fff;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.nickname {
  flex: 1;
  font-size: 32rpx;
  font-weight: 700;
  color: #323233;
}

.rating {
  font-size: 26rpx;
  color: #ff9800;
}

.meta {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #969799;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.tag-chip {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background-color: #f0faf7;
  color: #5ec8a7;
  font-size: 24rpx;
}

.content {
  display: block;
  margin-top: 28rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #323233;
  white-space: pre-wrap;
}

.review-image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 28rpx;
}

.review-image {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background-color: #f2f3f5;
}

.empty-content {
  display: block;
  margin-top: 28rpx;
  font-size: 26rpx;
  color: #969799;
}

.state-text {
  padding: 80rpx 32rpx;
  text-align: center;
  color: #969799;
  font-size: 28rpx;
}

.state-text--error {
  color: #ee0a24;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

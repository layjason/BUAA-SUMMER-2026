<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading" class="state-text">{{ t('activityDetail.loading') }}</view>
      <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>
      <view v-else-if="summary" class="detail-container">
        <view class="card">
          <text class="title">{{ summary.title }}</text>
          <text class="meta">{{ formatDateTime(summary.createdAt) }}</text>
          <text class="content">{{ summary.content }}</text>
        </view>

        <view v-if="summary.images.length > 0" class="card">
          <text class="section-title">{{ t('activitySummary.images') }}</text>
          <view class="image-grid">
            <image
              v-for="image in summary.images"
              :key="image.mediaId"
              class="summary-image"
              :src="image.signedUrl"
              mode="aspectFill"
            />
          </view>
        </view>

        <view v-if="summary.imageTags.length > 0" class="card">
          <text class="section-title">图片分类确认结果</text>
          <view v-for="item in summary.imageTags" :key="item.mediaId" class="tag-block">
            <text class="media-id">{{ item.mediaId }}</text>
            <view class="tag-row">
              <text v-for="tag in item.tags" :key="tag" class="tag-chip">{{ tag }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动总结详情页
 *
 * 通过活动总结列表接口按 summaryId 回查单条总结详情。
 * 前置条件：activityId 和 summaryId 通过 query 传入。
 * 后置条件：成功时展示总结正文、图片和人工确认后的图片标签。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getActivitySummaries, type ActivitySummaryPost } from '@/api/modules/activities'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')
const activityId = ref('')
const summaryId = ref('')
const summary = ref<ActivitySummaryPost | null>(null)

/** 加载活动总结详情
 *
 * 前置条件：activityId 和 summaryId 不为空。
 * 后置条件：summary 指向匹配的活动总结；未找到时写入 errorMsg。
 * 不变量：不修改远端数据，仅执行只读查询。
 */
async function loadSummaryDetail(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await getActivitySummaries(activityId.value, 1, 50)
    summary.value = (result.items ?? []).find((item) => item.summaryId === summaryId.value) ?? null
    if (!summary.value) errorMsg.value = '未找到活动总结'
  } catch {
    errorMsg.value = '加载活动总结失败'
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  summaryId.value = (query?.summaryId as string) ?? ''
  if (!activityId.value || !summaryId.value) {
    errorMsg.value = '缺少活动总结标识'
    loading.value = false
    return
  }
  void loadSummaryDetail()
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
  margin-bottom: 24rpx;
  border-radius: 16rpx;
  background-color: #fff;
}

.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #323233;
  line-height: 1.4;
}

.meta {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #969799;
}

.content {
  display: block;
  margin-top: 28rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #323233;
  white-space: pre-wrap;
}

.section-title {
  display: block;
  margin-bottom: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #323233;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.summary-image {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background-color: #f2f3f5;
}

.tag-block + .tag-block {
  margin-top: 20rpx;
}

.media-id {
  display: block;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  color: #969799;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-chip {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background-color: #f0faf7;
  color: #5ec8a7;
  font-size: 24rpx;
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

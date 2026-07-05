<script setup lang="ts">
/**
 * 我的草稿页。
 *
 * 前置条件：用户已登录，草稿接口返回 ActivityDraftSummary 列表。
 * 后置条件：仅展示 reviewStatus 为 draft 的活动。
 * 不变量：审核中、需修改、已驳回和已发布活动不在本页展示。
 */
import { computed, onMounted, ref } from 'vue'
import { getDrafts, type ActivityDraftSummary } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'

const drafts = ref<ActivityDraftSummary[]>([])
const isLoading = ref(false)

const visibleDrafts = computed(() => drafts.value.filter((draft) => draft.reviewStatus === 'draft'))

/**
 * 加载草稿列表。
 *
 * 前置条件：草稿接口按 OpenAPI 返回 items。
 * 后置条件：成功时刷新本地列表，失败时置空。
 * 不变量：展示层会继续只过滤 draft 状态。
 */
async function loadDrafts(): Promise<void> {
  isLoading.value = true
  try {
    const result = await getDrafts()
    drafts.value = result.items ?? []
  } catch {
    drafts.value = []
  } finally {
    isLoading.value = false
  }
}

/**
 * 打开草稿编辑页。
 *
 * 前置条件：activityId 来自可见草稿列表。
 * 后置条件：跳转到活动编辑页并回显草稿。
 * 不变量：不修改草稿状态。
 *
 * @param activityId 草稿活动标识
 */
function openDraft(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/edit?activityId=${activityId}` })
}

/**
 * 新建空白活动。
 *
 * 前置条件：无。
 * 后置条件：跳转到活动编辑页。
 * 不变量：不立即保存草稿。
 */
function createNew(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}

onMounted(() => {
  void loadDrafts()
})
</script>

<template>
  <view class="drafts-page">
    <view v-if="isLoading" class="loading-state">
      <text>加载中...</text>
    </view>

    <view v-else-if="visibleDrafts.length === 0" class="empty-state">
      <text class="empty-title">暂无草稿</text>
      <text class="empty-desc">只有未提交审核的活动会显示在这里</text>
      <view class="create-btn" @tap="createNew">
        <text>创建第一个活动</text>
      </view>
    </view>

    <view v-else class="draft-list">
      <view
        v-for="draft in visibleDrafts"
        :key="draft.activityId"
        class="draft-card"
        @tap="openDraft(draft.activityId)"
      >
        <view class="draft-header">
          <text class="draft-title">{{ draft.title || '未命名草稿' }}</text>
          <text class="draft-status">草稿</text>
        </view>
        <view class="draft-footer">
          <text class="draft-time">更新于 {{ formatDate(draft.updatedAt) }}</text>
          <text class="draft-action">继续编辑</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.drafts-page {
  min-height: 100vh;
  padding: $spacing-lg;
  padding-bottom: calc(#{$safe-bottom} + #{$spacing-2xl});
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 60px;

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px $spacing-xl;
  text-align: center;
}

.empty-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  margin-bottom: $spacing-sm;
}

.empty-desc {
  font-size: $font-sm;
  color: $color-text-sub;
  margin-bottom: $spacing-xl;
}

.create-btn {
  padding: $spacing-sm $spacing-xl;
  background: $color-primary;
  border-radius: $radius-full;

  text {
    color: $color-text-inverse;
    font-size: $font-base;
    font-weight: $weight-semibold;
  }

  &:active {
    opacity: 0.85;
  }
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.draft-card {
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-left: 3px solid $color-primary;
  border-radius: $radius-xl;
  padding: $spacing-lg;

  &:active {
    background: $color-bg-glass-heavy;
  }
}

.draft-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.draft-title {
  flex: 1;
  min-width: 0;
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-status {
  margin-left: $spacing-sm;
  padding: 2px 8px;
  border-radius: $radius-full;
  background: rgba(94, 200, 167, 0.12);
  color: $color-primary;
  font-size: $font-xs;
  font-weight: $weight-semibold;
}

.draft-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
}

.draft-time {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-action {
  flex-shrink: 0;
  font-size: $font-xs;
  font-weight: $weight-semibold;
  color: $color-primary;
}
</style>

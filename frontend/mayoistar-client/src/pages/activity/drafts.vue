<script setup lang="ts">
/**
 * 草稿列表页
 *
 * 展示用户创建的活动草稿，支持打开编辑和新建。
 *
 * 前置条件：用户已登录
 * 后置条件：展示草稿列表，点击跳转编辑
 */
import { ref, onMounted } from 'vue'
import { getDrafts } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'

/** 草稿列表条目 */
interface DraftItem {
  activityId: string
  title?: string
  reviewStatus: string
  createdAt: string
  updatedAt: string
}

const drafts = ref<DraftItem[]>([])
const isLoading = ref(false)

onMounted(async () => {
  await loadDrafts()
})

/**
 * 加载草稿列表
 *
 * 调用 getDrafts API 获取当前用户的所有草稿
 */
async function loadDrafts(): Promise<void> {
  isLoading.value = true
  try {
    const result = await getDrafts()
    drafts.value = ((result as Record<string, unknown>).items ?? []) as DraftItem[]
  } catch {
    drafts.value = []
  } finally {
    isLoading.value = false
  }
}

/** 打开草稿编辑 */
function openDraft(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/edit?activityId=${activityId}` })
}

/** 新建空白草稿 */
function createNew(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}
</script>

<template>
  <view class="drafts-page">
    <view v-if="isLoading" class="loading-state">
      <text>加载中...</text>
    </view>

    <view v-else-if="drafts.length === 0" class="empty-state">
      <text class="empty-icon">📝</text>
      <text class="empty-title">暂无草稿</text>
      <text class="empty-desc">创建活动时会自动保存为草稿</text>
      <view class="create-btn" @tap="createNew">
        <text>创建第一个活动</text>
      </view>
    </view>

    <view v-else class="draft-list">
      <view
        v-for="draft in drafts"
        :key="draft.activityId"
        class="draft-card"
        @tap="openDraft(draft.activityId)"
      >
        <view class="draft-header">
          <text class="draft-title">{{ draft.title || '未命名草稿' }}</text>
          <text class="draft-status" :class="`status-${draft.reviewStatus}`">
            {{
              draft.reviewStatus === 'draft'
                ? '草稿'
                : draft.reviewStatus === 'rejected'
                  ? '已驳回'
                  : draft.reviewStatus === 'pending'
                    ? '审核中'
                    : '待修改'
            }}
          </text>
        </view>
        <view class="draft-footer">
          <text class="draft-time">{{ formatDate(draft.updatedAt) }}</text>
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
}

.empty-icon {
  font-size: 48px;
  margin-bottom: $spacing-lg;
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
  border-left: 3px solid $color-accent;
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
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-status {
  font-size: $font-xs;
  padding: 2px 8px;
  border-radius: $radius-full;
  margin-left: $spacing-sm;
}

.status-draft {
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.1);
}

.status-rejected {
  color: $color-danger;
  background: rgba(242, 156, 163, 0.1);
}

.status-changeRequired {
  color: $color-accent;
  background: $color-accent-light;
}

.draft-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.draft-source,
.draft-time {
  font-size: $font-xs;
  color: $color-text-muted;
}
</style>

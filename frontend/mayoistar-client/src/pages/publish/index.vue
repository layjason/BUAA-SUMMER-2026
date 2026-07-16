<script setup lang="ts">
/**
 * 发布入口页。
 *
 * 前置条件：用户已登录并进入发布 Tab。
 * 后置条件：根据用户选择跳转到对应创建流程。
 * 不变量：入口页不直接创建、提交或克隆活动。
 */
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { ensureAuthenticatedAccess } from '@/utils/auth-guard'

const authStore = useAuthStore()

/**
 * 检查发布 Tab 登录态。
 *
 * 前置条件：页面进入显示阶段。
 * 后置条件：未登录用户被引导到登录页。
 * 不变量：已登录用户不受影响。
 */
onShow(() => {
  ensureAuthenticatedAccess('/pages/publish/index', () => authStore.isLoggedIn)
})

/**
 * 跳转到空白活动创建页。
 *
 * 前置条件：无。
 * 后置条件：打开活动编辑页。
 * 不变量：不携带活动标识。
 */
function goToCreate(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}

/**
 * 跳转到模板选择页。
 *
 * 前置条件：无。
 * 后置条件：打开单独的模板创建页。
 * 不变量：不混入克隆流程。
 */
function goToTemplates(): void {
  uni.navigateTo({ url: '/pages/activity/templates' })
}

/**
 * 跳转到草稿列表页。
 *
 * 前置条件：无。
 * 后置条件：打开仅展示草稿状态的列表。
 * 不变量：不展示审核中活动。
 */
function goToDrafts(): void {
  uni.navigateTo({ url: '/pages/activity/drafts' })
}

/**
 * 跳转到克隆已有活动页。
 *
 * 前置条件：无。
 * 后置条件：打开独立克隆页。
 * 不变量：不跳转到我创建的活动页复用流程。
 */
function goToClone(): void {
  uni.navigateTo({ url: '/pages/activity/clone' })
}

/**
 * 跳转到 AI 活动生成页。
 *
 * 前置条件：无。
 * 后置条件：打开 AI 生成流程。
 * 不变量：不修改现有草稿。
 */
function goToAiDraft(): void {
  uni.navigateTo({ url: '/pages/activity/ai-draft' })
}
</script>

<template>
  <view class="publish-page">
    <view class="header">
      <text class="header-title">发布活动</text>
      <text class="header-sub">选择一种方式开始创建，之后都可以继续编辑</text>
    </view>

    <view class="options">
      <view class="option-card main-option" @tap="goToCreate">
        <view class="option-icon">✏️</view>
        <view class="option-content">
          <text class="option-title">手动创建</text>
          <text class="option-desc">从空白表单开始，完整填写活动信息</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <view class="option-card" @tap="goToTemplates">
        <view class="option-icon">📋</view>
        <view class="option-content">
          <text class="option-title">模板创建</text>
          <text class="option-desc">选择活动模板，快速生成可编辑草稿</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <view class="option-card" @tap="goToAiDraft">
        <view class="option-icon">🤖</view>
        <view class="option-content">
          <text class="option-title">AI 智能生成</text>
          <text class="option-desc">输入主题，让 AI 先规划一版活动方案</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <view class="option-card" @tap="goToClone">
        <view class="option-icon">🔄</view>
        <view class="option-content">
          <text class="option-title">克隆已有活动</text>
          <text class="option-desc">复用已发布活动信息，生成新草稿</text>
        </view>
        <text class="option-arrow">›</text>
      </view>
    </view>

    <view class="draft-section" @tap="goToDrafts">
      <view class="draft-card">
        <text class="draft-icon">📝</text>
        <text class="draft-text">我的草稿</text>
        <text class="draft-arrow">查看 ›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.publish-page {
  min-height: 100vh;
  padding: $spacing-xl;
  padding-bottom: calc(#{$safe-bottom} + #{$tabbar-height} + #{$spacing-xl});
}

.header {
  margin-bottom: $spacing-2xl;

  &-title {
    display: block;
    font-size: $font-2xl;
    font-weight: $weight-bold;
    color: $color-text;
  }

  &-sub {
    display: block;
    font-size: $font-sm;
    color: $color-text-sub;
    margin-top: $spacing-xs;
  }
}

.options {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.option-card {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;

  &:active {
    background: $color-bg-glass-heavy;
    transform: scale(0.99);
  }
}

.main-option {
  background: $color-primary-light;
  border-color: rgba(22, 160, 133, 0.18);
}

.option-icon {
  font-size: 28px;
  margin-right: $spacing-lg;
}

.option-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.option-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.option-desc {
  font-size: $font-sm;
  color: $color-text-sub;
  margin-top: 2px;
}

.option-arrow {
  font-size: $font-xl;
  color: $color-text-muted;
}

.draft-section {
  margin-top: $spacing-2xl;
}

.draft-card {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  background: $color-bg-card;
  border: 1px dashed rgba(22, 160, 133, 0.3);
  border-radius: $radius-xl;

  &:active {
    background: $color-primary-light;
  }
}

.draft-icon {
  font-size: 24px;
  margin-right: $spacing-md;
}

.draft-text {
  flex: 1;
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
}

.draft-arrow {
  font-size: $font-sm;
  color: $color-primary;
}
</style>

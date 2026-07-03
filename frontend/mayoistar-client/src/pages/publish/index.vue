<script setup lang="ts">
/**
 * 发布入口页
 *
 * TabBar 第三个 tab，提供活动创建的多种入口：
 * 手动创建、模板创建、AI 生成、克隆已有活动。
 */

/** 跳转到空白创建活动 */
function goToCreate(): void {
  uni.navigateTo({ url: '/pages/activity/edit' })
}

/** 跳转到模板选择页 */
function goToTemplates(): void {
  uni.navigateTo({ url: '/pages/activity/templates' })
}

/** 跳转到草稿列表页 */
function goToDrafts(): void {
  uni.navigateTo({ url: '/pages/activity/drafts' })
}

/** 跳转到我的活动（可从中克隆） */
function goToMyActivities(): void {
  uni.navigateTo({ url: '/pages/profile/my-activities' })
}
</script>

<template>
  <view class="publish-page">
    <view class="header">
      <text class="header-title">发布活动</text>
      <text class="header-sub">选择你喜欢的创建方式</text>
    </view>

    <view class="options">
      <!-- 手动创建 -->
      <view class="option-card main-option" @tap="goToCreate">
        <view class="option-icon">✏️</view>
        <view class="option-content">
          <text class="option-title">手动创建</text>
          <text class="option-desc">从零开始，自由填写活动信息</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <!-- 模板创建 -->
      <view class="option-card" @tap="goToTemplates">
        <view class="option-icon">📋</view>
        <view class="option-content">
          <text class="option-title">模板创建</text>
          <text class="option-desc">选择活动模板，快速生成草稿</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <!-- AI 生成 -->
      <view class="option-card" @tap="goToCreate">
        <view class="option-icon">🤖</view>
        <view class="option-content">
          <text class="option-title">AI 智能生成</text>
          <text class="option-desc">输入主题，AI 为你规划活动方案</text>
        </view>
        <text class="option-arrow">›</text>
      </view>

      <!-- 克隆活动 -->
      <view class="option-card" @tap="goToMyActivities">
        <view class="option-icon">🔄</view>
        <view class="option-content">
          <text class="option-title">克隆已有活动</text>
          <text class="option-desc">从历史活动复制，修改后发布</text>
        </view>
        <text class="option-arrow">›</text>
      </view>
    </view>

    <!-- 草稿入口 -->
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
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;

  &:active {
    background: $color-bg-glass-heavy;
    transform: scale(0.99);
  }
}

.main-option {
  background: linear-gradient(135deg, $color-primary-light 0%, $color-bg-glass 100%);
  border-color: rgba(94, 200, 167, 0.15);
}

.option-icon {
  font-size: 28px;
  margin-right: $spacing-lg;
}

.option-content {
  flex: 1;
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
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px dashed rgba(94, 200, 167, 0.3);
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

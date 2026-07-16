<template>
  <view class="status-badge" :class="[`status-badge--${variant}`, `status-badge--${size}`]">
    <text class="status-badge__text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 状态徽章组件
 *
 * 小药丸形状的状态指示器，支持多种颜色变体和两种尺寸。
 * 颜色方案与平台主题的功能色保持一致。
 *
 * 前置条件：无
 * 后置条件：渲染带颜色背景的文本徽章
 */

/** 徽章颜色变体 */
type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'muted'

/** 徽章尺寸 */
type BadgeSize = 'sm' | 'md'

interface Props {
  /** 显示文本 */
  text: string
  /** 颜色变体 */
  variant?: BadgeVariant
  /** 尺寸 */
  size?: BadgeSize
}

withDefaults(defineProps<Props>(), {
  variant: 'info',
  size: 'sm',
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  white-space: nowrap;
  box-sizing: border-box;

  /* ===== 尺寸 ===== */

  &--sm {
    padding: 2px 8px;
    min-height: 20px;
  }

  &--md {
    padding: 4px 12px;
    min-height: 26px;
  }

  /* ===== 颜色变体 ===== */

  /* 成功：薄荷绿 */
  &--success {
    background: $color-primary-light;
    color: $color-primary-dark;
  }

  /* 警告：暖黄 */
  &--warning {
    background: rgba(246, 197, 111, 0.12);
    color: var(--q-color-warning);
  }

  /* 危险：柔粉 */
  &--danger {
    background: rgba(220, 38, 38, 0.08);
    color: var(--q-color-danger);
  }

  /* 信息：柔蓝 */
  &--info {
    background: $color-secondary-light;
    color: var(--q-color-info);
  }

  /* 弱化：灰色 */
  &--muted {
    background: rgba(160, 166, 178, 0.1);
    color: $color-text-muted;
  }

  /* ===== 文本 ===== */
  &__text {
    font-size: $font-xs;
    font-weight: $weight-medium;
    line-height: 1.4;
  }
}
</style>

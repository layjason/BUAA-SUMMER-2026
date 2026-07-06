<script setup lang="ts">
/**
 * 底部操作栏组件
 *
 * 放置在页面底部的操作栏，支持安全区域适配和稳定白底样式。
 * 用于活动创建、草稿编辑、评价提交等长表单页面。
 *
 * 使用方式：在 flex column 页面中作为 scroll-view 的兄弟节点放置，通过默认插槽传入按钮内容。
 * 默认参与普通布局；确实需要覆盖页面时再传入 fixed。
 */
defineProps<{
  /** 操作栏上方是否有阴影，默认 true */
  shadow?: boolean
  /** 是否固定到视口底部，默认 false */
  fixed?: boolean
}>()
</script>

<template>
  <view
    class="bottom-action-bar"
    :class="{ 'bar-shadow': shadow !== false, 'bottom-action-bar--fixed': fixed }"
  >
    <view class="bar-inner">
      <slot />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.bottom-action-bar {
  flex-shrink: 0;
  background: $color-bg-card;
  border-top: 1px solid $color-border;

  /* 兼容 iOS 11.0-11.2 */
  padding-bottom: constant(safe-area-inset-bottom);
  /* 兼容 iOS 11.2+ */
  padding-bottom: env(safe-area-inset-bottom);
}

.bottom-action-bar--fixed {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
}

.bar-shadow {
  box-shadow: 0 -2px 12px rgba(17, 24, 39, 0.06);
}

.bar-inner {
  display: flex;
  gap: 12px;
  padding: 10px 16px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  padding-bottom: calc(10px + constant(safe-area-inset-bottom));
}

/* 按钮通用样式 — 通过 :deep() 作用于插槽内容 */
:deep(.bar-btn) {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  line-height: 44px;
  margin: 0;
  padding: 0;
  text-align: center;
  font-size: $font-base;
  font-weight: $weight-semibold;
  border-radius: $radius-full;
  border: none;
  box-sizing: border-box;
  cursor: pointer;
  transition:
    opacity 0.2s,
    transform 0.1s;

  &:active {
    opacity: 0.85;
    transform: scale(0.98);
  }

  &::after {
    display: none;
  }
}

:deep(.bar-btn-primary) {
  background: $gradient-primary;
  color: $color-text-inverse;
  box-shadow: 0 8px 18px rgba(22, 160, 133, 0.2);
}

:deep(.bar-btn-secondary) {
  background: $color-bg-card;
  color: $color-primary;
  border: 1px solid rgba(22, 160, 133, 0.35);
}

:deep(.bar-btn-danger) {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: $color-text-inverse;
  box-shadow: 0 8px 18px rgba(220, 38, 38, 0.16);
}

:deep(.bar-btn[disabled]),
:deep(.bar-btn-disabled) {
  background: $color-bg-soft;
  color: $color-text-muted;
  border: 1px solid $color-border-light;
  box-shadow: none;
  opacity: 1;

  &:active {
    transform: none;
    opacity: 1;
  }
}
</style>

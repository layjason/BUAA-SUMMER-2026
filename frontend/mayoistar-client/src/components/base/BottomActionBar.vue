<script setup lang="ts">
/**
 * 底部操作栏组件
 *
 * 放置在页面底部的操作栏，支持安全区域适配和玻璃拟态风格。
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
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border-top: 1px solid rgba(255, 255, 255, 0.6);

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
  box-shadow: 0 -4px 16px rgba(38, 50, 56, 0.08);
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
  background: $color-primary;
  color: $color-text-inverse;
}

:deep(.bar-btn-secondary) {
  background: transparent;
  color: $color-primary;
  border: 1.5px solid $color-primary;
}

:deep(.bar-btn-danger) {
  background: $color-danger;
  color: $color-text-inverse;
}
</style>

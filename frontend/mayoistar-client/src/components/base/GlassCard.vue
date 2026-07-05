<template>
  <view
    class="glass-card"
    :class="[`glass-card--shadow-${shadow}`]"
    :style="{ padding, borderRadius: radius }"
  >
    <slot />
  </view>
</template>

<script setup lang="ts">
/**
 * 通用卡片组件
 *
 * 提供白色背景、细边框和低阴影的卡片容器。
 * 作为通用包裹组件，通过默认插槽承载任意内容。
 *
 * 前置条件：无
 * 后置条件：渲染默认插槽内容
 */

/** 阴影级别 */
type ShadowLevel = 'sm' | 'md' | 'lg' | 'glass'

interface Props {
  /** 内边距 */
  padding?: string
  /** 圆角 */
  radius?: string
  /** 阴影级别 */
  shadow?: ShadowLevel
}

withDefaults(defineProps<Props>(), {
  padding: '16px',
  radius: '20px',
  shadow: 'sm',
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.glass-card {
  background: $gradient-card;
  border: 1px solid $color-border-light;
  box-sizing: border-box;
  overflow: hidden;

  /* ===== 阴影级别 ===== */

  &--shadow-sm {
    box-shadow: 0 6px 18px rgba(17, 24, 39, 0.06);
  }

  &--shadow-md {
    box-shadow: $shadow-md;
  }

  &--shadow-lg {
    box-shadow: $shadow-lg;
  }

  &--shadow-glass {
    box-shadow: $shadow-glass;
  }
}
</style>

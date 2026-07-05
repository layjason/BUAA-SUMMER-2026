<template>
  <view class="empty-state">
    <text class="empty-state__icon">{{ icon }}</text>
    <text class="empty-state__title">{{ title }}</text>
    <text v-if="description" class="empty-state__desc">{{ description }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 空状态占位组件
 *
 * 当列表或页面无数据时展示友好的空状态提示。
 * 包含大号表情图标、标题文本和可选的描述文本，风格柔和友好。
 *
 * 前置条件：无
 * 后置条件：渲染空状态占位 UI
 */
interface Props {
  /** 标题文本 */
  title?: string
  /** 描述文本 */
  description?: string
  /** 图标（默认使用 emoji） */
  icon?: string
}

withDefaults(defineProps<Props>(), {
  title: '暂无内容',
  description: '',
  icon: '📭',
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl * 2 $spacing-xl;

  /* ===== 图标 ===== */
  &__icon {
    font-size: 56px;
    margin-bottom: $spacing-lg;
    line-height: 1.2;
  }

  /* ===== 标题 ===== */
  &__title {
    font-size: $font-lg;
    font-weight: $weight-medium;
    color: $color-text-sub;
    margin-bottom: $spacing-sm;
    text-align: center;
  }

  /* ===== 描述 ===== */
  &__desc {
    font-size: $font-sm;
    color: $color-text-muted;
    text-align: center;
    line-height: 1.6;
    max-width: 260px;
  }
}
</style>

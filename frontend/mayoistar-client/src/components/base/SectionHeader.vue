<template>
  <view class="section-header">
    <text class="section-header__title">{{ title }}</text>
    <view v-if="action || showMore" class="section-header__action" @click="onAction">
      <text class="section-header__action-text">{{ action || '查看更多' }}</text>
      <text v-if="showMore" class="section-header__arrow">›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 区块标题组件
 *
 * 用于页面中各功能区块的标题行，左侧标题，右侧可点击的操作文本。
 *
 * 前置条件：无
 * 后置条件：点击操作区域时触发 action 事件
 */
interface Props {
  /** 标题文本 */
  title: string
  /** 操作文本（如 "查看全部"） */
  action?: string
  /** 是否显示"查看更多"及箭头 */
  showMore?: boolean
}

withDefaults(defineProps<Props>(), {
  action: '',
  showMore: false,
})

const emit = defineEmits<{
  action: []
}>()

/**
 * 处理操作区域点击
 */
function onAction() {
  emit('action')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md 0;

  /* ===== 标题 ===== */
  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
    line-height: 1.3;
  }

  /* ===== 操作区域 ===== */
  &__action {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  &__action-text {
    font-size: $font-sm;
    color: $color-primary;
    font-weight: $weight-medium;
  }

  &__arrow {
    font-size: $font-lg;
    color: $color-primary;
    line-height: 1;
    margin-left: 1px;
  }
}
</style>

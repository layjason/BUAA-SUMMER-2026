<template>
  <button
    class="submit-btn"
    :class="{ 'submit-btn--secondary': secondary }"
    :disabled="disabled || loading"
    :loading="loading"
    @click="$emit('click')"
  >
    {{ loading ? '' : text }}
  </button>
</template>

<script setup lang="ts">
/**
 * 提交按钮组件
 *
 * 带加载态和禁用态的通用提交按钮。
 * 前置条件：无
 * 后置条件：点击时触发 click 事件
 */
defineProps<{
  text: string
  loading?: boolean
  disabled?: boolean
  secondary?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: $gradient-primary;
  color: $color-text-inverse;
  font-size: 32rpx;
  font-weight: $weight-semibold;
  border-radius: 24rpx;
  border: none;
  margin-top: 16rpx;
  box-shadow: 0 10rpx 24rpx rgba(22, 160, 133, 0.2);
}

.submit-btn[disabled] {
  opacity: 0.6;
}

.submit-btn--secondary {
  background: $color-bg-card;
  color: $color-primary;
  border: 1rpx solid rgba(22, 160, 133, 0.35);
  box-shadow: none;
}
</style>

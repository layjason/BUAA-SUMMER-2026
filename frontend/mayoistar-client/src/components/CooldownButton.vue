<template>
  <button
    class="cooldown-btn"
    :class="{ 'cooldown-btn--secondary': secondary }"
    :disabled="disabled || loading || cooldown > 0"
    :loading="loading"
    @click="$emit('click')"
  >
    <template v-if="cooldown > 0">
      {{ cooldownText }}
    </template>
    <template v-else-if="!loading">
      {{ text }}
    </template>
  </button>
</template>

<script setup lang="ts">
/**
 * 冷却倒计时按钮组件
 *
 * 展示由父组件完成国际化插值的冷却倒计时文本，冷却期间自动禁用。
 * 与 useCooldown composable 配合使用，cooldown 由父组件通过 composable 管理。
 *
 * 前置条件：cooldown 为当前剩余秒数，由 useCooldown 提供；cooldownText 已完成秒数插值
 * 后置条件：冷却期间显示倒计时文本并禁用点击
 */
defineProps<{
  text: string
  cooldownText: string
  cooldown: number
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

.cooldown-btn {
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

.cooldown-btn[disabled] {
  background: $color-bg-soft;
  color: $color-text-muted;
  border: 1rpx solid $color-border-light;
  box-shadow: none;
  opacity: 1;
}

.cooldown-btn--secondary {
  background: $color-bg-card;
  color: $color-primary;
  border: 1rpx solid rgba(22, 160, 133, 0.35);
  box-shadow: none;
}
</style>

<template>
  <button
    class="cooldown-btn"
    :class="{ 'cooldown-btn--secondary': secondary }"
    :disabled="disabled || loading || cooldown > 0"
    :loading="loading"
    @click="$emit('click')"
  >
    <template v-if="cooldown > 0">
      {{ cooldownText.replace('{seconds}', String(cooldown)) }}
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
 * 展示冷却倒计时文本，冷却期间自动禁用。
 * 与 useCooldown composable 配合使用，cooldown 由父组件通过 composable 管理。
 *
 * 前置条件：cooldown 为当前剩余秒数，由 useCooldown 提供
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

<style scoped>
.cooldown-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background-color: #1989fa;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  border: none;
  margin-top: 16rpx;
}

.cooldown-btn[disabled] {
  opacity: 0.6;
}

.cooldown-btn--secondary {
  background-color: #fff;
  color: #1989fa;
  border: 1rpx solid #1989fa;
}
</style>

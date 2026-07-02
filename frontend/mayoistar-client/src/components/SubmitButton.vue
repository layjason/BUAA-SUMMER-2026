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

<style scoped>
.submit-btn {
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

.submit-btn[disabled] {
  opacity: 0.6;
}

.submit-btn--secondary {
  background-color: #fff;
  color: #1989fa;
  border: 1rpx solid #1989fa;
}
</style>

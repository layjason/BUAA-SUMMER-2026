<template>
  <view class="floating-create-button" @tap="onTap">
    <text class="floating-create-button__icon">✏️</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 右下悬浮创建按钮
 *
 * 用于快速发起添加好友、创建小队、发起聊天等操作
 */
const emit = defineEmits<{
  tap: []
}>()

function onTap() {
  emit('tap')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.floating-create-button {
  position: fixed;
  right: $spacing-xl;
  bottom: calc($tabbar-height + $spacing-xl + $safe-bottom);
  width: 56px;
  height: 56px;
  background: $gradient-primary;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(22, 160, 133, 0.24);
  z-index: 99;
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.94);
  }

  &__icon {
    font-size: 28px;
  }
}
</style>

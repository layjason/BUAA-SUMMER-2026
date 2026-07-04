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
  background: linear-gradient(135deg, $color-primary 0%, $color-primary-dark 100%);
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-lg;
  z-index: 99;
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.9) rotate(45deg);
  }

  &__icon {
    font-size: 28px;
  }
}
</style>

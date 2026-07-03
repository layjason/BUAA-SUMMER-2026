<template>
  <view
    class="app-btn"
    :class="[
      `app-btn--${type}`,
      `app-btn--${size}`,
      { 'app-btn--block': block, 'app-btn--disabled': disabled, 'app-btn--loading': loading },
    ]"
    @click="handleClick"
  >
    <text v-if="loading" class="app-btn__loading-text">加载中...</text>
    <text v-else class="app-btn__text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 通用按钮组件
 *
 * 支持多种类型（主按钮、描边、幽灵、危险）、三种尺寸、加载态和禁用态。
 * 设计风格遵循趣聚平台的浅色、轻玻璃感、大圆角视觉语言。
 *
 * 前置条件：无
 * 后置条件：点击时触发 click 事件（非加载/禁用态下）
 */

/** 按钮类型 */
type ButtonType = 'primary' | 'secondary' | 'ghost' | 'danger'

/** 按钮尺寸 */
type ButtonSize = 'sm' | 'md' | 'lg'

interface Props {
  /** 按钮文本 */
  text: string
  /** 按钮类型 */
  type?: ButtonType
  /** 按钮尺寸 */
  size?: ButtonSize
  /** 是否加载中 */
  loading?: boolean
  /** 是否禁用 */
  disabled?: boolean
  /** 是否撑满父容器宽度 */
  block?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'primary',
  size: 'md',
  loading: false,
  disabled: false,
  block: false,
})

const emit = defineEmits<{
  click: []
}>()

/**
 * 处理点击事件
 *
 * 仅在非加载、非禁用状态下触发 click 事件
 */
function handleClick() {
  if (!props.loading && !props.disabled) {
    emit('click')
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  font-weight: $weight-semibold;
  transition:
    opacity 0.2s ease,
    transform 0.1s ease;
  position: relative;
  box-sizing: border-box;

  /* ===== 按压效果 ===== */
  &:active {
    transform: scale(0.98);
    opacity: 0.85;
  }

  /* ===== 禁用态 ===== */
  &--disabled {
    opacity: 0.5;
    pointer-events: none;
  }

  /* ===== 加载态 ===== */
  &--loading {
    pointer-events: none;
    opacity: 0.8;
  }

  /* ===== 类型样式 ===== */

  /* 主按钮：品牌色填充，白色文字 */
  &--primary {
    background: $color-primary;
    color: $color-text-inverse;
    border: none;
  }

  /* 次要按钮：品牌色描边 */
  &--secondary {
    background: transparent;
    color: $color-primary;
    border: 1.5px solid $color-primary;
  }

  /* 幽灵按钮：无边框无背景 */
  &--ghost {
    background: transparent;
    color: $color-text-sub;
    border: none;
  }

  /* 危险按钮：危险色填充 */
  &--danger {
    background: $color-danger;
    color: $color-text-inverse;
    border: none;
  }

  /* ===== 尺寸 ===== */

  &--sm {
    padding: 6px 16px;
    font-size: $font-sm;
    min-height: 32px;
  }

  &--md {
    padding: 10px 24px;
    font-size: $font-base;
    min-height: 42px;
  }

  &--lg {
    padding: 14px 32px;
    font-size: $font-lg;
    min-height: 50px;
  }

  /* ===== 撑满宽度 ===== */
  &--block {
    display: flex;
    width: 100%;
  }

  /* ===== 文本 ===== */
  &__text,
  &__loading-text {
    line-height: 1.2;
  }

  &__loading-text {
    opacity: 0.9;
  }
}
</style>

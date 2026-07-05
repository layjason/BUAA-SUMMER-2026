<template>
  <view class="app-input" :class="{ 'app-input--error': !!error, 'app-input--focused': isFocused }">
    <!-- 标签 -->
    <view class="app-input__label-row">
      <text v-if="required" class="app-input__required">*</text>
      <text class="app-input__label">{{ label }}</text>
    </view>

    <!-- 输入框容器 -->
    <view class="app-input__wrapper">
      <!-- 单行输入 -->
      <input
        v-if="!multiline"
        class="app-input__field"
        :value="modelValue"
        :type="type"
        :password="type === 'password'"
        :placeholder="placeholder"
        :maxlength="maxLength"
        placeholder-class="app-input__placeholder"
        @input="emit('update:modelValue', ($event as any).detail.value)"
        @focus="onFocus"
        @blur="onBlur"
      />
      <!-- 多行文本域 -->
      <textarea
        v-else
        class="app-input__textarea"
        :value="modelValue"
        :placeholder="placeholder"
        :maxlength="maxLength"
        :auto-height="true"
        placeholder-class="app-input__placeholder"
        @input="emit('update:modelValue', ($event as any).detail.value)"
        @focus="onFocus"
        @blur="onBlur"
      />
    </view>

    <!-- 错误提示 -->
    <text v-if="error" class="app-input__error">{{ error }}</text>

    <!-- 字数统计 -->
    <text v-if="maxLength" class="app-input__counter">
      {{ modelValue.length }}/{{ maxLength }}
    </text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * 通用输入框组件
 *
 * 带标签、错误提示和字数统计的输入框，支持 v-model 双向绑定。
 * 支持单行输入和多行文本域两种模式，采用白底细边框输入区域。
 *
 * 前置条件：无
 * 后置条件：输入变化时通过 update:modelValue 事件通知父组件
 */
interface Props {
  /** 绑定值 */
  modelValue: string
  /** 标签文本 */
  label: string
  /** 占位提示文本 */
  placeholder?: string
  /** 输入框类型 */
  type?: string
  /** 错误提示文本 */
  error?: string
  /** 是否必填 */
  required?: boolean
  /** 是否多行文本域 */
  multiline?: boolean
  /** 最大字符数 */
  maxLength?: number
}

withDefaults(defineProps<Props>(), {
  placeholder: '',
  type: 'text',
  error: '',
  required: false,
  multiline: false,
  maxLength: undefined,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: []
}>()

/** 是否处于聚焦状态 */
const isFocused = ref(false)

/**
 * 处理聚焦事件
 */
function onFocus() {
  isFocused.value = true
}

/**
 * 处理失焦事件
 */
function onBlur() {
  isFocused.value = false
  emit('blur')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.app-input {
  margin-bottom: $spacing-xl;

  /* ===== 标签行 ===== */
  &__label-row {
    display: flex;
    align-items: center;
    margin-bottom: $spacing-sm;
  }

  &__required {
    color: $color-danger;
    font-size: $font-base;
    margin-right: 2px;
  }

  &__label {
    font-size: $font-sm;
    font-weight: $weight-medium;
    color: $color-text;
  }

  /* ===== 输入框容器 ===== */
  &__wrapper {
    background: $color-bg-card;
    border: 1px solid $color-border;
    border-radius: $radius-md;
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease;
    overflow: hidden;
  }

  /* ===== 输入框 ===== */
  &__field {
    width: 100%;
    height: 48px;
    padding: 0 $spacing-lg;
    font-size: $font-base;
    color: $color-text;
    box-sizing: border-box;
    background: transparent;
  }

  &__textarea {
    width: 100%;
    min-height: 100px;
    padding: $spacing-md $spacing-lg;
    font-size: $font-base;
    color: $color-text;
    box-sizing: border-box;
    background: transparent;
    line-height: 1.6;
  }

  &__placeholder {
    color: $color-text-muted;
    font-size: $font-base;
  }

  /* ===== 聚焦状态：品牌色边框 ===== */
  &--focused &__wrapper {
    border-color: rgba(22, 160, 133, 0.45);
    box-shadow: 0 0 0 2px rgba(22, 160, 133, 0.08);
  }

  /* ===== 错误状态 ===== */
  &--error &__wrapper {
    border-color: rgba(220, 38, 38, 0.4);
  }

  &--error &__label {
    color: $color-danger;
  }

  /* ===== 错误提示 ===== */
  &__error {
    display: block;
    font-size: $font-xs;
    color: $color-danger;
    margin-top: $spacing-xs;
    padding-left: $spacing-xs;
  }

  /* ===== 字数统计 ===== */
  &__counter {
    display: block;
    font-size: $font-xs;
    color: $color-text-muted;
    margin-top: $spacing-xs;
    text-align: right;
    padding-right: $spacing-xs;
  }
}
</style>

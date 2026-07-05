<template>
  <view class="form-input">
    <text class="form-input__label">
      <text v-if="required" class="form-input__required">* </text>{{ label }}
    </text>
    <input
      :value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :password="type === 'password'"
      class="form-input__input"
      placeholder-class="form-input__placeholder"
      @input="emit('update:modelValue', ($event as any).detail.value)"
      @blur="$emit('blur')"
    />
    <text v-if="error" class="form-input__error">{{ error }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 表单输入组件
 *
 * 带标签和错误提示的通用输入框，支持 v-model。
 * 前置条件：无
 * 后置条件：输入变化时通过 update:modelValue 事件通知父组件
 */
defineProps<{
  label: string
  modelValue: string
  placeholder: string
  type?: string
  error?: string
  required?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: []
}>()
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.form-input {
  margin-bottom: 32rpx;
}

.form-input__label {
  display: block;
  font-size: 28rpx;
  color: $color-text;
  margin-bottom: 12rpx;
}

.form-input__required {
  color: $color-danger;
}

.form-input__input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: $color-bg-card;
  border: 1rpx solid $color-border;
  border-radius: 20rpx;
  font-size: 30rpx;
  color: $color-text;
  box-sizing: border-box;
}

.form-input__placeholder {
  color: $color-text-muted;
}

.form-input__error {
  display: block;
  font-size: 24rpx;
  color: $color-danger;
  margin-top: 8rpx;
}
</style>

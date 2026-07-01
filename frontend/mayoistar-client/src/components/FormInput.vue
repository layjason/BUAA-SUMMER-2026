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
      @input="onInput"
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

/**
 * 处理输入事件，双向绑定
 *
 * @param e uni-app input 事件
 */
function onInput(e: { detail: { value: string } }) {
  emit('update:modelValue', e.detail.value)
}
</script>

<style scoped>
.form-input {
  margin-bottom: 32rpx;
}

.form-input__label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.form-input__required {
  color: #ee0a24;
}

.form-input__input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 30rpx;
  color: #323233;
  box-sizing: border-box;
}

.form-input__placeholder {
  color: #c8c9cc;
}

.form-input__error {
  display: block;
  font-size: 24rpx;
  color: #ee0a24;
  margin-top: 8rpx;
}
</style>

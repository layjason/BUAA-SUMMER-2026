<template>
  <view class="app-navbar" :style="{ backgroundColor: backgroundColor }">
    <!-- 状态栏占位 -->
    <view class="app-navbar__status-bar" />

    <!-- 导航栏主体 -->
    <view class="app-navbar__bar">
      <!-- 左侧返回按钮 -->
      <view v-if="showBack" class="app-navbar__back" @tap="handleBack">
        <text class="app-navbar__back-icon">←</text>
      </view>
      <view v-else class="app-navbar__placeholder" />

      <!-- 居中标题 -->
      <text class="app-navbar__title">{{ title }}</text>

      <!-- 右侧占位（保持标题居中） -->
      <view class="app-navbar__placeholder" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 自定义导航栏组件
 *
 * 用于隐藏默认导航栏的页面，提供自定义标题、返回按钮和背景色。
 * 返回按钮调用 uni.navigateBack() 实现页面后退。
 *
 * 前置条件：页面需在 pages.json 中配置 navigationStyle 为 "custom"
 * 后置条件：点击返回按钮时触发 navigateBack 并触发 back 事件
 */
interface Props {
  /** 导航栏标题 */
  title: string
  /** 是否显示返回按钮 */
  showBack?: boolean
  /** 背景色 */
  backgroundColor?: string
}

withDefaults(defineProps<Props>(), {
  showBack: true,
  backgroundColor: 'transparent',
})

const emit = defineEmits<{
  back: []
}>()

/**
 * 处理返回按钮点击
 *
 * 触发 back 事件并调用 uni.navigateBack 返回上一页
 */
function handleBack() {
  emit('back')
  uni.navigateBack({ delta: 1 })
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.app-navbar {
  position: relative;
  z-index: 1000;
  width: 100%;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);

  /* ===== 状态栏占位 ===== */
  &__status-bar {
    height: var(--status-bar-height, 44px);
    width: 100%;
  }

  /* ===== 导航栏主体 ===== */
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 44px;
    padding: 0 $spacing-lg;
    position: relative;
  }

  /* ===== 返回按钮 ===== */
  &__back {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: $radius-full;
    z-index: 1;
  }

  &__back-icon {
    font-size: 20px;
    color: $color-text;
    font-weight: $weight-medium;
  }

  /* ===== 标题 ===== */
  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    white-space: nowrap;
    max-width: 60%;
    overflow: hidden;
    text-overflow: ellipsis;
    text-align: center;
  }

  /* ===== 占位（保持标题居中） ===== */
  &__placeholder {
    width: 36px;
    min-width: 36px;
  }
}
</style>

<template>
  <view class="avatar-stack" :style="stackStyle">
    <view
      v-for="(avatar, index) in visibleAvatars"
      :key="index"
      class="avatar-stack__item"
      :style="avatarItemStyle(index)"
    >
      <image v-if="avatar" class="avatar-stack__img" :src="avatar" mode="aspectFill" />
      <view v-else class="avatar-stack__fallback">
        <text class="avatar-stack__fallback-icon">👤</text>
      </view>
    </view>

    <!-- 溢出计数 -->
    <view
      v-if="overflowCount > 0"
      class="avatar-stack__item avatar-stack__overflow"
      :style="avatarItemStyle(visibleAvatars.length)"
    >
      <text class="avatar-stack__overflow-text">+{{ overflowCount }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 头像堆叠组件
 *
 * 展示一组重叠排列的圆形用户头像，超出最大显示数量时显示 "+N" 计数。
 * 适用于活动参与者列表、群组成员预览等场景。
 *
 * 前置条件：avatars 为 URL 字符串数组（可为空数组）
 * 后置条件：渲染重叠排列的头像和可选的溢出计数
 */
interface Props {
  /** 头像 URL 数组 */
  avatars: string[]
  /** 最大显示数量 */
  maxCount?: number
  /** 单个头像尺寸（px） */
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  maxCount: 4,
  size: 28,
})

/**
 * 计算可见头像列表（截取前 maxCount 个）
 */
const visibleAvatars = computed(() => {
  return props.avatars.slice(0, props.maxCount)
})

/**
 * 计算溢出数量
 */
const overflowCount = computed(() => {
  return Math.max(0, props.avatars.length - props.maxCount)
})

/**
 * 计算容器总宽度样式
 *
 * 根据可见头像数量和溢出计数计算容器总宽度
 */
const stackStyle = computed(() => {
  const totalItems = visibleAvatars.value.length + (overflowCount.value > 0 ? 1 : 0)
  const overlap = props.size * 0.35
  const totalWidth = props.size + (totalItems - 1) * (props.size - overlap)
  return {
    width: `${totalWidth}px`,
    height: `${props.size}px`,
  }
})

/**
 * 计算单个头像项的样式
 *
 * 每个头像通过负左边距实现重叠效果，通过 z-index 控制层叠顺序
 *
 * @param index 头像在可见列表中的索引
 */
function avatarItemStyle(index: number) {
  const overlap = props.size * 0.35
  return {
    width: `${props.size}px`,
    height: `${props.size}px`,
    left: `${index * (props.size - overlap)}px`,
    zIndex: 10 - index,
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.avatar-stack {
  position: relative;
  display: flex;
  align-items: center;

  /* ===== 头像项 ===== */
  &__item {
    position: absolute;
    top: 0;
    border-radius: $radius-full;
    overflow: hidden;
    border: 2px solid var(--q-color-bg-card);
    box-sizing: border-box;
    background: $color-bg;
  }

  /* ===== 头像图片 ===== */
  &__img {
    width: 100%;
    height: 100%;
    border-radius: $radius-full;
  }

  /* ===== 兜底头像（无图片时） ===== */
  &__fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $color-primary-light;
    border-radius: $radius-full;
  }

  &__fallback-icon {
    font-size: 14px;
    line-height: 1;
  }

  /* ===== 溢出计数 ===== */
  &__overflow {
    display: flex;
    align-items: center;
    justify-content: center;
    background: $gradient-primary-soft;
    border-radius: $radius-full;
  }

  &__overflow-text {
    font-size: $font-xs;
    font-weight: $weight-semibold;
    color: $color-primary-dark;
    line-height: 1;
  }
}
</style>

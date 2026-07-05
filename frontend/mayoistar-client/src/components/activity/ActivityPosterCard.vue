<template>
  <view class="poster-card" @click="handleClick">
    <!-- 大封面图 -->
    <view class="poster-card__cover-wrap">
      <image class="poster-card__cover" :src="activity.coverUrl" mode="aspectFill" />

      <!-- 底部渐变遮罩 -->
      <view class="poster-card__gradient" />

      <!-- 右上角状态徽章 -->
      <view class="poster-card__status">
        <ActivityStatusPill :status="activity.runtimeStatus" />
      </view>

      <!-- 底部信息叠加在图片上 -->
      <view class="poster-card__overlay">
        <text class="poster-card__title">{{ activity.title }}</text>
        <view class="poster-card__meta">
          <text class="poster-card__meta-text">🕐 {{ activity.startTime }}</text>
          <text class="poster-card__meta-divider">·</text>
          <text class="poster-card__meta-text">📍 {{ activity.city }}</text>
        </view>
      </view>
    </view>

    <!-- 底部信息栏 -->
    <view class="poster-card__footer">
      <view class="poster-card__fee-row">
        <text v-if="activity.isFree" class="poster-card__fee poster-card__fee--free">免费</text>
        <text v-else class="poster-card__fee">¥{{ activity.fee }}</text>
        <text class="poster-card__count"
          >{{ activity.registeredCount }}/{{ activity.capacity }}人报名</text
        >
      </view>

      <!-- 标签 -->
      <view v-if="activity.tags && activity.tags.length > 0" class="poster-card__tags">
        <view v-for="tag in activity.tags.slice(0, 3)" :key="tag" class="poster-card__tag">
          <text class="poster-card__tag-text">{{ tag }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import ActivityStatusPill from './ActivityStatusPill.vue'

/**
 * 活动信息接口
 */
interface ActivityInfo {
  /** 活动 ID */
  id: number | string
  /** 活动标题 */
  title: string
  /** 封面图片 URL */
  coverUrl: string
  /** 开始时间（格式化后的字符串） */
  startTime: string
  /** 城市 */
  city: string
  /** 费用金额 */
  fee: number
  /** 已报名人数 */
  registeredCount: number
  /** 容纳人数上限 */
  capacity: number
  /** 运行状态 */
  runtimeStatus: string
  /** 标签列表 */
  tags: string[]
  /** 是否免费 */
  isFree: boolean
}

/**
 * 海报风格活动卡片组件
 *
 * 用于精选/推荐活动的大尺寸展示。
 * 全宽卡片搭配大封面图，标题通过底部渐变遮罩叠加在图片上方，
 * 视觉冲击力比普通活动卡片更强。
 *
 * 前置条件：activity 对象需包含所有必需字段
 * 后置条件：点击时触发 click 事件
 */
interface Props {
  /** 活动数据 */
  activity: ActivityInfo
}

defineProps<Props>()

const emit = defineEmits<{
  click: []
}>()

/**
 * 处理卡片点击
 */
function handleClick() {
  emit('click')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.poster-card {
  border-radius: $radius-xl;
  overflow: hidden;
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  box-shadow: $shadow-md;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.98);
  }

  /* ===== 封面区域 ===== */
  &__cover-wrap {
    position: relative;
    width: 100%;
    /* 4:3 比例，更大气 */
    padding-top: 75%;
    overflow: hidden;
  }

  &__cover {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
  }

  /* ===== 底部渐变遮罩 ===== */
  &__gradient {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 60%;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.55) 0%, transparent 100%);
    pointer-events: none;
  }

  /* ===== 状态徽章 ===== */
  &__status {
    position: absolute;
    top: $spacing-md;
    right: $spacing-md;
  }

  /* ===== 叠加在图片底部的信息 ===== */
  &__overlay {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: $spacing-lg $spacing-lg $spacing-md;
  }

  &__title {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    font-size: $font-xl;
    font-weight: $weight-bold;
    color: #ffffff;
    line-height: 1.4;
    margin-bottom: $spacing-xs;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  }

  &__meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
  }

  &__meta-text {
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.9);
  }

  &__meta-divider {
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.6);
    margin: 0 $spacing-xs;
  }

  /* ===== 底部信息栏 ===== */
  &__footer {
    padding: $spacing-md $spacing-lg $spacing-lg;
  }

  &__fee-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
  }

  &__fee {
    font-size: $font-xl;
    font-weight: $weight-bold;
    color: $color-accent;

    &--free {
      font-size: $font-base;
      font-weight: $weight-semibold;
      color: $color-primary;
      background: $color-primary-light;
      padding: 2px 10px;
      border-radius: $radius-full;
    }
  }

  &__count {
    font-size: $font-sm;
    color: $color-text-muted;
  }

  /* ===== 标签 ===== */
  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-xs;
  }

  &__tag {
    background: rgba(94, 200, 167, 0.08);
    border-radius: $radius-full;
    padding: 2px 8px;
  }

  &__tag-text {
    font-size: $font-xs;
    color: $color-primary-dark;
  }
}
</style>

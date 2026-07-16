<template>
  <view class="activity-card" @click="handleClick">
    <!-- 封面图片区域 -->
    <view class="activity-card__cover-wrap">
      <image class="activity-card__cover" :src="activity.coverUrl" mode="aspectFill" />
      <!-- 状态徽章（右上角） -->
      <view class="activity-card__status">
        <ActivityStatusPill :status="activity.runtimeStatus" />
      </view>
    </view>

    <!-- 内容区域 -->
    <view class="activity-card__body">
      <!-- 标题（两行截断） -->
      <text class="activity-card__title">{{ activity.title }}</text>

      <!-- 时间和城市 -->
      <view class="activity-card__meta">
        <text class="activity-card__meta-icon">🕐</text>
        <text class="activity-card__time">{{ activity.startTime }}</text>
        <text class="activity-card__meta-divider">·</text>
        <text class="activity-card__meta-icon">📍</text>
        <text class="activity-card__city">{{ activity.city }}</text>
      </view>

      <!-- 费用和报名人数 -->
      <view class="activity-card__footer">
        <view class="activity-card__fee-row">
          <text v-if="activity.isFree" class="activity-card__fee activity-card__fee--free"
            >免费</text
          >
          <text v-else class="activity-card__fee">¥{{ activity.fee }}</text>
        </view>
        <view class="activity-card__count-row">
          <text class="activity-card__count"
            >{{ activity.registeredCount }}/{{ activity.capacity }}人</text
          >
        </view>
      </view>

      <!-- 标签 -->
      <view v-if="activity.tags && activity.tags.length > 0" class="activity-card__tags">
        <view v-for="tag in activity.tags.slice(0, 3)" :key="tag" class="activity-card__tag">
          <text class="activity-card__tag-text">{{ tag }}</text>
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
 * 活动卡片组件
 *
 * 用于活动列表/信息流中展示活动概要信息。
 * 包含封面图（16:9）、标题、时间城市、费用与报名人数、标签等。
 * 外层采用轻玻璃卡片包裹，右上角叠加活动运行状态徽章。
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

.activity-card {
  background: $gradient-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(17, 24, 39, 0.06);
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;

  &:active {
    transform: scale(0.98);
  }

  /* ===== 封面区域 ===== */
  &__cover-wrap {
    position: relative;
    width: 100%;
    /* 16:9 比例 */
    padding-top: 56.25%;
    overflow: hidden;
    background: $gradient-primary-soft;
  }

  &__cover {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
  }

  &__status {
    position: absolute;
    top: $spacing-sm;
    right: $spacing-sm;
  }

  /* ===== 内容区域 ===== */
  &__body {
    padding: $spacing-md $spacing-lg $spacing-lg;
  }

  /* ===== 标题 ===== */
  &__title {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    font-size: $font-base;
    font-weight: $weight-semibold;
    color: $color-text;
    line-height: 1.5;
    margin-bottom: $spacing-sm;
  }

  /* ===== 元信息行 ===== */
  &__meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    margin-bottom: $spacing-sm;
  }

  &__meta-icon {
    font-size: $font-xs;
    margin-right: 2px;
  }

  &__time,
  &__city {
    font-size: $font-xs;
    color: $color-text-sub;
  }

  &__meta-divider {
    font-size: $font-xs;
    color: $color-text-muted;
    margin: 0 $spacing-xs;
  }

  /* ===== 底部行：费用和人数 ===== */
  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
  }

  &__fee-row {
    display: flex;
    align-items: center;
  }

  &__fee {
    font-size: $font-lg;
    font-weight: $weight-bold;
    color: $color-accent;

    &--free {
      color: $color-primary;
      font-size: $font-base;
      font-weight: $weight-semibold;
      background: $gradient-primary-soft;
      padding: 1px 8px;
      border-radius: $radius-full;
    }
  }

  &__count-row {
    display: flex;
    align-items: center;
  }

  &__count {
    font-size: $font-xs;
    color: $color-text-muted;
  }

  /* ===== 标签 ===== */
  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-xs;
  }

  &__tag {
    background: $gradient-primary-soft;
    border-radius: $radius-full;
    padding: 2px 8px;
  }

  &__tag-text {
    font-size: $font-xs;
    color: $color-primary-dark;
  }
}
</style>

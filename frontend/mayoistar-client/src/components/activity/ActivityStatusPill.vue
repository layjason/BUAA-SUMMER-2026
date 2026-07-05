<template>
  <view class="status-pill" :class="[`status-pill--${statusConfig.variant}`]">
    <text class="status-pill__text">{{ statusConfig.label }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 活动运行状态映射配置
 */
interface StatusConfig {
  /** 中文标签文本 */
  label: string
  /** 颜色变体标识 */
  variant: string
}

/** 状态字符串到配置的映射表 */
const STATUS_MAP: Record<string, StatusConfig> = {
  registering: { label: '报名中', variant: 'primary' },
  registrationClosed: { label: '已满', variant: 'warning' },
  ongoing: { label: '进行中', variant: 'secondary' },
  ended: { label: '已结束', variant: 'muted' },
  notStarted: { label: '未开始', variant: 'info' },
  takenDown: { label: '已下架', variant: 'danger' },
}

/** 默认配置（未知状态时的兜底） */
const DEFAULT_CONFIG: StatusConfig = { label: '未知', variant: 'muted' }

/**
 * 活动状态药丸组件
 *
 * 将活动运行状态映射为带颜色的小药丸徽章。
 * 支持六种运行状态：报名中、已满、进行中、已结束、未开始、已下架。
 *
 * 前置条件：status 应为预定义的状态字符串之一
 * 后置条件：渲染对应颜色和中文文本的状态药丸
 */
interface Props {
  /** 活动运行状态 */
  status: string
}

const props = defineProps<Props>()

/**
 * 根据 status 计算当前状态配置
 *
 * 若传入未知状态，回退到默认配置
 */
const statusConfig = computed<StatusConfig>(() => {
  return STATUS_MAP[props.status] ?? DEFAULT_CONFIG
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 10px;
  border-radius: $radius-full;
  box-sizing: border-box;

  /* ===== 颜色变体 ===== */

  /* 报名中：品牌薄荷绿 */
  &--primary {
    background: $color-primary-light;
    color: $color-primary-dark;
  }

  /* 已满：暖黄 */
  &--warning {
    background: rgba(246, 197, 111, 0.15);
    color: #c49a2a;
  }

  /* 进行中：柔蓝 */
  &--secondary {
    background: $color-secondary-light;
    color: #5a9ec9;
  }

  /* 已结束：灰色 */
  &--muted {
    background: rgba(160, 166, 178, 0.12);
    color: $color-text-muted;
  }

  /* 未开始：信息蓝 */
  &--info {
    background: $color-secondary-light;
    color: #5a9ec9;
  }

  /* 已下架：柔粉 */
  &--danger {
    background: rgba(242, 156, 163, 0.12);
    color: #d4707a;
  }

  /* ===== 文本 ===== */
  &__text {
    font-size: $font-xs;
    font-weight: $weight-semibold;
    line-height: 1.4;
    white-space: nowrap;
  }
}
</style>

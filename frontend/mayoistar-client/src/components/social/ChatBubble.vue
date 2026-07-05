<template>
  <view class="chat-bubble-wrap" :class="{ 'chat-bubble-wrap--self': isSelf }">
    <!-- 气泡主体 -->
    <view
      v-if="status !== 'recalled'"
      class="chat-bubble"
      :class="{ 'chat-bubble--self': isSelf, 'chat-bubble--other': !isSelf }"
    >
      <text class="chat-bubble__content">{{ content }}</text>
    </view>

    <!-- 已撤回消息 -->
    <view v-else class="chat-bubble__recalled">
      <text class="chat-bubble__recalled-text">消息已撤回</text>
    </view>

    <!-- 时间和状态 -->
    <view v-if="time" class="chat-bubble__meta">
      <text class="chat-bubble__time">{{ time }}</text>
      <text
        v-if="isSelf && status !== 'recalled'"
        class="chat-bubble__status"
        :class="[`chat-bubble__status--${status}`]"
        >{{ statusLabel }}</text
      >
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 消息状态 */
type MessageStatus = 'sent' | 'delivered' | 'read' | 'recalled'

/** 消息类型 */
type MessageType = 'text' | 'image' | 'location'

/** 状态中文标签映射 */
const STATUS_LABELS: Record<string, string> = {
  sent: '已发送',
  delivered: '已送达',
  read: '已读',
  recalled: '已撤回',
}

/**
 * 聊天气泡组件
 *
 * 展示单条聊天消息的气泡，区分自己发送和他人发送。
 * 支持文本、图片、位置三种消息类型，以及已撤回状态。
 * 自己的消息靠右，品牌色背景；他人消息靠左，白色/玻璃背景。
 *
 * 前置条件：无
 * 后置条件：渲染聊天消息气泡
 */
interface Props {
  /** 消息内容文本 */
  content: string
  /** 是否为自己发送的消息 */
  isSelf?: boolean
  /** 消息时间文本 */
  time?: string
  /** 消息状态 */
  status?: MessageStatus
  /** 消息类型 */
  messageType?: MessageType
}

const props = withDefaults(defineProps<Props>(), {
  isSelf: false,
  time: '',
  status: 'sent',
  messageType: 'text',
})

/**
 * 计算状态中文标签
 */
const statusLabel = computed(() => {
  return STATUS_LABELS[props.status] ?? ''
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.chat-bubble-wrap {
  display: flex;
  flex-direction: column;
  margin-bottom: $spacing-md;
  max-width: 80%;

  /* 他人消息靠左 */
  align-self: flex-start;

  /* 自己的消息靠右 */
  &--self {
    align-self: flex-end;
    align-items: flex-end;
  }

  /* ===== 气泡主体 ===== */
  .chat-bubble {
    padding: $spacing-md $spacing-lg;
    word-break: break-word;
    line-height: 1.6;

    /* 自己的消息：品牌色背景，白色文字 */
    &--self {
      background: $color-primary;
      color: $color-text-inverse;
      border-radius: $radius-xl $radius-xl $radius-sm $radius-xl;
    }

    /* 他人消息：白色/玻璃背景 */
    &--other {
      background: $color-bg-glass-heavy;
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      color: $color-text;
      border: 1px solid $color-border-light;
      border-radius: $radius-xl $radius-xl $radius-xl $radius-sm;
      box-shadow: $shadow-sm;
    }

    &__content {
      font-size: $font-base;
      line-height: 1.6;
    }
  }

  /* ===== 已撤回消息 ===== */
  &__recalled {
    padding: $spacing-sm $spacing-md;
  }

  &__recalled-text {
    font-size: $font-sm;
    color: $color-text-muted;
    font-style: italic;
  }

  /* ===== 时间和状态 ===== */
  &__meta {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    margin-top: $spacing-xs;
    padding: 0 $spacing-xs;
  }

  &__time {
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__status {
    font-size: $font-xs;
    color: $color-text-muted;

    &--read {
      color: $color-primary;
    }

    &--delivered {
      color: $color-text-sub;
    }
  }
}
</style>

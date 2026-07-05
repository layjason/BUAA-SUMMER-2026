<template>
  <view class="conversation-card" @tap="onTap">
    <!-- Avatar -->
    <view class="conversation-card__avatar-wrapper">
      <image v-if="avatar" class="conversation-card__avatar" :src="avatar" mode="aspectFill" />
      <view v-else class="conversation-card__avatar-placeholder">
        <text class="conversation-card__avatar-icon">{{ avatarIcon }}</text>
      </view>
      <view v-if="isOnline" class="conversation-card__online-dot"></view>
    </view>

    <!-- Content -->
    <view class="conversation-card__content">
      <view class="conversation-card__header">
        <text class="conversation-card__name">{{ name }}</text>
        <view
          v-if="tag"
          class="conversation-card__tag"
          :class="`conversation-card__tag--${tagType}`"
        >
          {{ tag }}
        </view>
      </view>

      <view class="conversation-card__message-row">
        <text class="conversation-card__message">{{ lastMessage || '暂无消息' }}</text>
        <text v-if="time" class="conversation-card__time">{{ time }}</text>
      </view>
    </view>

    <!-- Unread Badge -->
    <view v-if="unreadCount > 0" class="conversation-card__badge">
      {{ unreadCount > 99 ? '99+' : unreadCount }}
    </view>

    <!-- Right Icon -->
    <view v-if="showRightIcon" class="conversation-card__right-icon">
      <text class="conversation-card__right-icon-text">📷</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 会话列表卡片组件
 *
 * 展示单个会话信息：头像、名称、标签、最后一条消息、时间、未读数
 */
interface Props {
  /** 头像 URL */
  avatar?: string
  /** 头像图标（无图片时） */
  avatarIcon?: string
  /** 会话名称 */
  name: string
  /** 关系标签 */
  tag?: string
  /** 标签类型 */
  tagType?: 'friend' | 'team' | 'companion'
  /** 最后一条消息 */
  lastMessage?: string
  /** 时间 */
  time?: string
  /** 未读数量 */
  unreadCount?: number
  /** 是否在线 */
  isOnline?: boolean
  /** 显示右侧图标 */
  showRightIcon?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  avatar: '',
  avatarIcon: '',
  tag: '',
  tagType: 'friend',
  lastMessage: '',
  time: '',
  unreadCount: 0,
  isOnline: false,
  showRightIcon: true,
})

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _props = props

const emit = defineEmits<{
  tap: []
}>()

function onTap() {
  emit('tap')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.conversation-card {
  display: flex;
  align-items: center;
  padding: $spacing-lg $spacing-xl;
  background: #ffffff;
  border-radius: $radius-lg;
  margin: $spacing-sm $spacing-lg;
  box-shadow: $shadow-xs;
  transition: all 0.2s ease;

  &:active {
    background: rgba(0, 0, 0, 0.02);
    transform: scale(0.98);
  }

  /* ===== 头像区域 ===== */
  &__avatar-wrapper {
    position: relative;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    margin-right: $spacing-md;
  }

  &__avatar {
    width: 100%;
    height: 100%;
    border-radius: $radius-full;
    border: 2px solid #ffffff;
    box-shadow: $shadow-sm;
  }

  &__avatar-placeholder {
    width: 100%;
    height: 100%;
    border-radius: $radius-full;
    background: $color-primary-light;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #ffffff;
    box-shadow: $shadow-sm;
  }

  &__avatar-icon {
    font-size: 28px;
  }

  &__online-dot {
    position: absolute;
    bottom: 2px;
    right: 2px;
    width: 12px;
    height: 12px;
    background: $color-success;
    border-radius: $radius-full;
    border: 2px solid #ffffff;
  }

  /* ===== 内容区域 ===== */
  &__content {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    margin-bottom: $spacing-xs;
  }

  &__name {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__tag {
    font-size: $font-xs;
    padding: 2px $spacing-xs;
    border-radius: $radius-full;
    font-weight: $weight-medium;
    flex-shrink: 0;

    &--friend {
      background: $color-primary-light;
      color: $color-primary-dark;
    }

    &--team {
      background: $color-secondary-light;
      color: $color-secondary;
    }

    &--companion {
      background: $color-accent-light;
      color: $color-accent;
    }
  }

  &__message-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $spacing-sm;
  }

  &__message {
    font-size: $font-sm;
    color: $color-text-sub;
    flex: 1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__time {
    font-size: $font-xs;
    color: $color-text-muted;
    flex-shrink: 0;
  }

  /* ===== 未读角标 ===== */
  &__badge {
    min-width: 24px;
    height: 24px;
    background: $color-danger;
    color: #ffffff;
    font-size: $font-xs;
    font-weight: $weight-semibold;
    border-radius: $radius-full;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 6px;
    margin-left: $spacing-sm;
    flex-shrink: 0;
  }

  /* ===== 右侧图标 ===== */
  &__right-icon {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: $spacing-sm;
    flex-shrink: 0;
  }

  &__right-icon-text {
    font-size: 20px;
  }
}
</style>

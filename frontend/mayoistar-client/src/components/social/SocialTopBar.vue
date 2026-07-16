<template>
  <view class="social-topbar">
    <!-- Status bar spacer for custom navigation -->
    <view class="social-topbar__status-bar" />

    <!-- Main bar content -->
    <view class="social-topbar__bar">
      <!-- Left: Avatar -->
      <view class="social-topbar__left" @tap="onAvatarTap">
        <UserAvatar :avatar-url="userAvatar" :name="userName" size="md" />
        <view v-if="hasNotification" class="social-topbar__badge"></view>
      </view>

      <!-- Center: Search -->
      <view class="social-topbar__search" @tap="onSearchTap">
        <text class="social-topbar__search-icon">🔍</text>
        <text class="social-topbar__search-text">{{ placeholder }}</text>
      </view>

      <!-- Right: Add Friend & More -->
      <view class="social-topbar__right">
        <view class="social-topbar__add-friend" @tap="onAddFriendTap">
          <text class="social-topbar__icon">➕</text>
          <view v-if="pendingRequests > 0" class="social-topbar__badge social-topbar__badge--red">
            {{ pendingRequests }}
          </view>
        </view>
        <view class="social-topbar__more" @tap="onMoreTap">
          <text class="social-topbar__icon">⋯</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 社交模块顶部导航栏
 *
 * 包含：用户头像、搜索入口、加好友按钮（带红点）、更多菜单
 */
import UserAvatar from '@/components/base/UserAvatar.vue'

interface Props {
  /** 当前用户头像 URL */
  userAvatar?: string
  /** 当前用户昵称，用于头像首字占位 */
  userName?: string
  /** 是否有通知 */
  hasNotification?: boolean
  /** 待处理好友申请数量 */
  pendingRequests?: number
  /** 搜索框占位文本 */
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  userAvatar: '',
  userName: '',
  hasNotification: false,
  pendingRequests: 0,
  placeholder: '搜索好友、小队',
})

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _props = props

const emit = defineEmits<{
  avatarTap: []
  searchTap: []
  addFriendTap: []
  moreTap: []
}>()

function onAvatarTap() {
  emit('avatarTap')
}

function onSearchTap() {
  emit('searchTap')
}

function onAddFriendTap() {
  emit('addFriendTap')
}

function onMoreTap() {
  emit('moreTap')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.social-topbar {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.96);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid $color-border-light;

  /* ===== Status bar spacer for custom navigation ===== */
  &__status-bar {
    height: var(--status-bar-height, 44px);
    width: 100%;
  }

  /* ===== Main bar content ===== */
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-lg $spacing-xl;
  }

  /* ===== 左侧头像区域 ===== */
  &__left {
    position: relative;
    width: 48px;
    height: 48px;
    flex-shrink: 0;
  }

  &__badge {
    position: absolute;
    top: -2px;
    right: -2px;
    width: 12px;
    height: 12px;
    background: $color-danger;
    border-radius: $radius-full;
    border: 2px solid var(--q-color-bg-card);

    &--red {
      width: 18px;
      height: 18px;
      font-size: 10px;
      color: var(--q-color-bg-card);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: $weight-semibold;
    }
  }

  /* ===== 中间搜索框 ===== */
  &__search {
    flex: 1;
    margin: 0 $spacing-md;
    height: 40px;
    background: $color-bg-soft;
    border: 1px solid $color-border-light;
    border-radius: $radius-full;
    display: flex;
    align-items: center;
    padding: 0 $spacing-md;
    transition: all 0.2s ease;

    &:active {
      background: $color-primary-light;
    }
  }

  &__search-icon {
    font-size: 16px;
    margin-right: $spacing-xs;
  }

  &__search-text {
    font-size: $font-sm;
    color: $color-text-muted;
    flex: 1;
  }

  /* ===== 右侧操作区 ===== */
  &__right {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__add-friend,
  &__more {
    width: 40px;
    height: 40px;
    border-radius: $radius-full;
    background: $gradient-primary;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    transition: all 0.2s ease;

    &:active {
      transform: scale(0.95);
      opacity: 0.9;
    }
  }

  &__more {
    background: $color-bg-soft;
    border: 1px solid $color-border-light;
  }

  &__icon {
    font-size: 20px;
    color: var(--q-color-bg-card);
  }

  &__more &__icon {
    color: $color-text;
  }
}
</style>

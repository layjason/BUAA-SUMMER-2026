<template>
  <scroll-view class="social-quick-cards" scroll-x :show-scrollbar="false" enable-flex>
    <view class="social-quick-cards__track">
      <!-- Teams Card -->
      <view class="social-quick-card" @tap="onTeamsTap">
        <view class="social-quick-card__header">
          <text class="social-quick-card__title">小队</text>
          <view v-if="teamCount > 0" class="social-quick-card__badge"> {{ teamCount }}个 </view>
        </view>

        <view class="social-quick-card__content">
          <text class="social-quick-card__desc">{{ teamDesc }}</text>
          <view v-if="pendingTeamRequests > 0" class="social-quick-card__alert">
            <text class="social-quick-card__alert-icon">🔔</text>
            <text class="social-quick-card__alert-text">{{ pendingTeamRequests }}条待审核</text>
          </view>
        </view>

        <view class="social-quick-card__actions">
          <view
            class="social-quick-card__btn social-quick-card__btn--primary"
            @tap.stop="onCreateTeamTap"
          >
            <text class="social-quick-card__btn-text">创建小队</text>
          </view>
          <view class="social-quick-card__btn social-quick-card__btn--secondary">
            <text class="social-quick-card__btn-text">进入小队</text>
          </view>
        </view>
      </view>

      <!-- Companions Card -->
      <view class="social-quick-card" @tap="onCompanionsTap">
        <view class="social-quick-card__header">
          <text class="social-quick-card__title">活动同伴</text>
          <view v-if="companionCount > 0" class="social-quick-card__badge">
            {{ companionCount }}位
          </view>
        </view>

        <view class="social-quick-card__content">
          <text class="social-quick-card__desc">{{ companionDesc }}</text>
        </view>

        <view class="social-quick-card__actions">
          <view class="social-quick-card__btn social-quick-card__btn--primary">
            <text class="social-quick-card__btn-text">去添加</text>
          </view>
        </view>
      </view>

      <!-- Friend Requests Card -->
      <view class="social-quick-card" @tap="onFriendRequestsTap">
        <view class="social-quick-card__header">
          <text class="social-quick-card__title">好友申请</text>
          <view
            v-if="requestCount > 0"
            class="social-quick-card__badge social-quick-card__badge--red"
          >
            {{ requestCount }}条
          </view>
        </view>

        <view class="social-quick-card__content">
          <text class="social-quick-card__desc">{{ requestDesc }}</text>
        </view>

        <view class="social-quick-card__actions">
          <view class="social-quick-card__btn social-quick-card__btn--primary">
            <text class="social-quick-card__btn-text">去处理</text>
          </view>
        </view>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
/**
 * 社交模块横向快捷卡片
 *
 * 包含：小队、活动同伴、好友申请三个快捷入口
 */
interface Props {
  /** 小队数量 */
  teamCount?: number
  /** 小队描述 */
  teamDesc?: string
  /** 待审核小队申请数 */
  pendingTeamRequests?: number
  /** 同伴数量 */
  companionCount?: number
  /** 同伴描述 */
  companionDesc?: string
  /** 好友申请数量 */
  requestCount?: number
  /** 好友申请描述 */
  requestDesc?: string
}

const props = withDefaults(defineProps<Props>(), {
  teamCount: 0,
  teamDesc: '暂无小队',
  pendingTeamRequests: 0,
  companionCount: 0,
  companionDesc: '暂无可能认识的人',
  requestCount: 0,
  requestDesc: '暂无待处理申请',
})

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _props = props

const emit = defineEmits<{
  teamsTap: []
  createTeamTap: []
  companionsTap: []
  friendRequestsTap: []
}>()

function onTeamsTap() {
  emit('teamsTap')
}

function onCreateTeamTap() {
  emit('createTeamTap')
}

function onCompanionsTap() {
  emit('companionsTap')
}

function onFriendRequestsTap() {
  emit('friendRequestsTap')
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.social-quick-cards {
  width: 100%;
  white-space: nowrap;
}

.social-quick-cards__track {
  display: inline-flex;
  gap: $spacing-md;
  padding: $spacing-lg $spacing-xl;
}

.social-quick-card {
  width: 240px;
  background: $gradient-card;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  border: 1px solid $color-border-light;
  box-shadow: 0 6px 18px rgba(17, 24, 39, 0.06);
  transition: all 0.2s ease;
  flex-shrink: 0;
  white-space: normal;
  vertical-align: top;

  &:active {
    transform: scale(0.98);
    box-shadow: $shadow-xs;
  }

  /* ===== 卡片头部 ===== */
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-md;
  }

  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
  }

  &__badge {
    font-size: $font-xs;
    padding: 2px $spacing-sm;
    background: $gradient-primary-soft;
    color: $color-primary-dark;
    border-radius: $radius-full;
    font-weight: $weight-medium;

    &--red {
      background: rgba(220, 38, 38, 0.08);
      border: 1px solid rgba(220, 38, 38, 0.18);
      color: $color-danger;
    }
  }

  /* ===== 卡片内容 ===== */
  &__content {
    margin-bottom: $spacing-md;
  }

  &__desc {
    font-size: $font-sm;
    color: $color-text-sub;
    display: block;
    line-height: 1.5;
  }

  &__alert {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    margin-top: $spacing-sm;
    padding: $spacing-sm;
    background: rgba(220, 38, 38, 0.06);
    border-radius: $radius-sm;
  }

  &__alert-icon {
    font-size: 14px;
  }

  &__alert-text {
    font-size: $font-xs;
    color: $color-danger;
    font-weight: $weight-medium;
  }

  /* ===== 操作按钮区 ===== */
  &__actions {
    display: flex;
    gap: $spacing-sm;
  }

  &__btn {
    flex: 1;
    height: 36px;
    border-radius: $radius-full;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;

    &:active {
      transform: scale(0.95);
      opacity: 0.9;
    }

    &--primary {
      background: $gradient-primary;
      box-shadow: 0 6px 14px rgba(22, 160, 133, 0.18);

      & .social-quick-card__btn-text {
        color: var(--q-color-bg-card);
      }
    }

    &--secondary {
      background: $color-bg-soft;
      border: 1px solid $color-border-light;

      & .social-quick-card__btn-text {
        color: $color-text;
      }
    }
  }

  &__btn-text {
    font-size: $font-sm;
    font-weight: $weight-medium;
  }
}
</style>

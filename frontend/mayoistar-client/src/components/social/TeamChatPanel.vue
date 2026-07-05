<template>
  <uni-popup ref="popupRef" type="bottom" :safe-area="true" @mask-click="close">
    <view class="panel">
      <view class="panel__header">
        <text class="panel__title">小队信息</text>
        <text class="panel__close" @tap="close">×</text>
      </view>

      <scroll-view class="panel__scroll" scroll-y>
        <view v-if="team" class="info-card">
          <view class="info-card__avatar">
            <image
              v-if="team.avatar?.signedUrl"
              :src="team.avatar.signedUrl"
              class="info-card__avatar-img"
              mode="aspectFill"
            />
            <text v-else class="info-card__avatar-placeholder">👥</text>
          </view>
          <view class="info-card__body">
            <view class="info-card__title-row">
              <text class="info-card__name">{{ team.name }}</text>
              <view v-if="team.status !== 'active'" class="status-badge">
                <text class="status-text">{{
                  team.status === 'dissolved' ? '已解散' : '已停用'
                }}</text>
              </view>
            </view>
            <text v-if="team.description" class="info-card__desc">{{ team.description }}</text>
            <view class="info-card__meta">
              <text>{{ team.memberCount }}/{{ team.capacity }} 成员</text>
              <text>{{ team.joinMode === 'publicJoin' ? '自由加入' : '需审批' }}</text>
              <text v-if="myRole">我的角色：{{ roleLabel }}</text>
            </view>
            <view v-if="team.tags.length" class="info-card__tags">
              <view v-for="(tag, idx) in team.tags" :key="idx" class="tag">
                <text class="tag-text">{{ tag }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="feature-grid">
          <view
            v-for="item in featureItems"
            :key="item.key"
            class="feature-card"
            @tap="onFeatureTap(item)"
          >
            <text class="feature-card__icon">{{ item.icon }}</text>
            <text class="feature-card__label">{{ item.label }}</text>
            <text v-if="item.badge" class="feature-card__badge">{{ item.badge }}</text>
          </view>
        </view>

        <view v-if="team" class="panel-actions">
          <view class="panel-action" @tap="goTeamDetail">
            <text>查看小队详情</text>
          </view>
          <view v-if="canManage" class="panel-action" @tap="goMembers">
            <text>成员管理</text>
          </view>
        </view>
      </scroll-view>
    </view>
  </uni-popup>
</template>

<script setup lang="ts">
/**
 * 小队群聊信息弹窗
 *
 * 展示小队基础资料，并提供公告、投票、文件、相册等子功能入口。
 */
import { ref, computed } from 'vue'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamMemberRole = components['schemas']['Social.TeamMemberRole']

interface FeatureItem {
  key: string
  icon: string
  label: string
  path: string
  badge?: string
  managerOnly?: boolean
}

const props = defineProps<{
  teamId: string
  team: TeamProfile | null
  myRole: TeamMemberRole | null
  pendingRequestCount?: number
}>()

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const popupRef = ref<any>(null)

const canManage = computed(() => props.myRole === 'leader' || props.myRole === 'admin')

const roleLabel = computed(() => {
  if (props.myRole === 'leader') return '队长'
  if (props.myRole === 'admin') return '管理员'
  return '成员'
})

const featureItems = computed<FeatureItem[]>(() => {
  const base: FeatureItem[] = [
    { key: 'announcements', icon: '📢', label: '群公告', path: 'announcements' },
    { key: 'polls', icon: '🗳️', label: '群投票', path: 'polls' },
    { key: 'files', icon: '📁', label: '群文件', path: 'files' },
    { key: 'album', icon: '🖼️', label: '小队相册', path: 'album' },
    { key: 'activities', icon: '📅', label: '队内活动', path: 'activities' },
    { key: 'points', icon: '🏆', label: '积分榜', path: 'points' },
  ]
  if (canManage.value) {
    base.push({
      key: 'join-requests',
      icon: '📨',
      label: '入队申请',
      path: 'join-requests',
      badge: props.pendingRequestCount ? String(props.pendingRequestCount) : undefined,
      managerOnly: true,
    })
  }
  return base
})

/** 打开弹窗 */
function open() {
  popupRef.value?.open()
}

/** 关闭弹窗 */
function close() {
  popupRef.value?.close()
}

/** 跳转子功能页 */
function onFeatureTap(item: FeatureItem) {
  if (!props.teamId) return
  close()
  uni.navigateTo({ url: `/pages/teams/${item.path}?teamId=${props.teamId}` })
}

/** 跳转小队详情 */
function goTeamDetail() {
  close()
  uni.navigateTo({ url: `/pages/teams/detail?teamId=${props.teamId}` })
}

/** 跳转成员管理 */
function goMembers() {
  close()
  uni.navigateTo({ url: `/pages/teams/members?teamId=${props.teamId}` })
}

defineExpose({ open, close })
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.panel {
  background: #ffffff;
  border-radius: $radius-xl $radius-xl 0 0;
  max-height: 78vh;
  display: flex;
  flex-direction: column;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg $spacing-xl;
  border-bottom: 1px solid $color-border-light;
}

.panel__title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.panel__close {
  font-size: 24px;
  color: $color-text-muted;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel__scroll {
  flex: 1;
  max-height: 68vh;
  padding: $spacing-md;
}

.info-card {
  background: $color-bg;
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.info-card__avatar {
  width: 56px;
  height: 56px;
  border-radius: $radius-md;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.info-card__avatar-img {
  width: 100%;
  height: 100%;
}

.info-card__avatar-placeholder {
  font-size: 28px;
}

.info-card__body {
  flex: 1;
  min-width: 0;
}

.info-card__title-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.info-card__name {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.status-badge {
  background: rgba(242, 156, 163, 0.15);
  padding: 2px $spacing-xs;
  border-radius: $radius-sm;
}

.status-text {
  font-size: $font-xs;
  color: $color-danger;
}

.info-card__desc {
  font-size: $font-sm;
  color: $color-text-sub;
  line-height: 1.5;
  margin-bottom: $spacing-xs;
}

.info-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-muted;
  margin-bottom: $spacing-xs;
}

.info-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.tag {
  background: $color-primary-light;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
}

.tag-text {
  font-size: $font-xs;
  color: $color-primary;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.feature-card {
  background: #ffffff;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-sm;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  position: relative;
  box-shadow: $shadow-xs;

  &:active {
    background: #fafafa;
  }
}

.feature-card__icon {
  font-size: 24px;
}

.feature-card__label {
  font-size: $font-xs;
  color: $color-text;
  text-align: center;
}

.feature-card__badge {
  position: absolute;
  top: 6px;
  right: 8px;
  background: $color-danger;
  color: #fff;
  font-size: 10px;
  min-width: 16px;
  height: 16px;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.panel-actions {
  display: flex;
  gap: $spacing-sm;
  padding-bottom: calc($spacing-md + $safe-bottom);
}

.panel-action {
  flex: 1;
  background: $color-primary-light;
  border-radius: $radius-full;
  padding: $spacing-sm;
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    font-size: $font-sm;
    color: $color-primary;
    font-weight: $weight-medium;
  }
}
</style>

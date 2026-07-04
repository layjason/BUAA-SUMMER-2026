<template>
  <view class="page">
    <AppNavbar title="添加好友" />
    <scroll-view class="scroll-area" scroll-y>
      <!-- Quick Actions -->
      <view class="section">
        <text class="section-title">添加方式</text>
        <view class="action-grid">
          <view class="action-card" @tap="onScanQR">
            <view class="action-icon-wrapper action-icon-wrapper--primary">
              <text class="action-icon">📷</text>
            </view>
            <text class="action-label">扫码添加</text>
            <text class="action-desc">扫描好友二维码</text>
          </view>

          <view class="action-card" @tap="onMyQRCode">
            <view class="action-icon-wrapper action-icon-wrapper--secondary">
              <text class="action-icon">🎫</text>
            </view>
            <text class="action-label">我的二维码</text>
            <text class="action-desc">让别人扫描添加你</text>
          </view>

          <view class="action-card" @tap="onCompanions">
            <view class="action-icon-wrapper action-icon-wrapper--accent">
              <text class="action-icon">🤝</text>
            </view>
            <text class="action-label">活动同伴</text>
            <text class="action-desc">从活动中认识的人</text>
          </view>

          <view class="action-card" @tap="onFromTeam">
            <view class="action-icon-wrapper action-icon-wrapper--green">
              <text class="action-icon">👥</text>
            </view>
            <text class="action-label">小队成员</text>
            <text class="action-desc">从小队中添加好友</text>
          </view>
        </view>
      </view>

      <!-- Sent Requests -->
      <view class="section">
        <view class="section-header-row">
          <text class="section-title">已发送的申请</text>
          <text v-if="sentRequests.length > 0" class="section-count">{{
            sentRequests.length
          }}</text>
        </view>

        <view v-if="loading" class="loading-state">
          <text class="loading-text">加载中...</text>
        </view>

        <view v-else-if="sentRequests.length === 0" class="empty-inline">
          <text class="empty-inline-text">暂无发送的好友申请</text>
        </view>

        <view v-else class="request-list">
          <view v-for="req in sentRequests" :key="req.requestId" class="request-item">
            <view class="request-avatar-wrapper">
              <view class="request-avatar-placeholder">
                <text class="request-avatar-text">{{ req.targetUserId.charAt(0) }}</text>
              </view>
            </view>
            <view class="request-info">
              <text class="request-name">用户 {{ req.targetUserId }}</text>
              <text v-if="req.message" class="request-message">「{{ req.message }}」</text>
            </view>
            <view class="request-status">
              <text class="request-status-text" :class="`request-status-text--${req.status}`">
                {{ getStatusLabel(req.status) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-safe"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 添加好友页面
 *
 * 提供扫码、二维码、活动同伴、小队成员等添加入口
 * 展示已发送的好友申请列表
 *
 * 注意：OpenAPI 暂无用户搜索接口，搜索添加功能待后端支持
 */
import { ref, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import { getSentFriendRequests } from '@/api/modules/social'
import type { components } from '@/api/types/schema'

type FriendRequest = components['schemas']['Social.FriendRequest']

const sentRequests = ref<FriendRequest[]>([])
const loading = ref(false)

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '等待对方确认',
    accepted: '已成为好友',
    rejected: '已被拒绝',
    canceled: '已撤回',
  }
  return map[status] || status
}

function onScanQR() {
  uni.showToast({ title: '扫码功能暂未开放', icon: 'none' })
}

function onMyQRCode() {
  uni.showToast({ title: '二维码功能暂未开放', icon: 'none' })
}

function onCompanions() {
  uni.navigateTo({ url: '/pages/social/activity-companions' })
}

function onFromTeam() {
  uni.navigateTo({ url: '/pages/teams/index' })
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const result = await getSentFriendRequests()
    sentRequests.value = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as FriendRequest[]) ?? [])
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: $color-bg;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* ===== Section ===== */
.section {
  padding: $spacing-lg $spacing-xl;
}

.section-header-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.section-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  margin-bottom: $spacing-md;
}

.section-count {
  font-size: $font-xs;
  color: $color-text-muted;
  background: rgba(0, 0, 0, 0.06);
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
}

/* ===== Action Grid ===== */
.action-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.action-card {
  width: calc(50% - #{$spacing-sm});
  background: #ffffff;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-xs;
  transition: all 0.2s ease;

  &:active {
    transform: scale(0.97);
    box-shadow: none;
  }
}

.action-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-md;
}

.action-icon-wrapper--primary {
  background: $color-primary-light;
}

.action-icon-wrapper--secondary {
  background: $color-secondary-light;
}

.action-icon-wrapper--accent {
  background: $color-accent-light;
}

.action-icon-wrapper--green {
  background: rgba(52, 199, 89, 0.12);
}

.action-icon {
  font-size: 24px;
}

.action-label {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.action-desc {
  font-size: $font-xs;
  color: $color-text-sub;
  display: block;
  margin-top: 4px;
}

/* ===== Loading & Empty ===== */
.loading-state {
  display: flex;
  justify-content: center;
  padding: $spacing-xl;
}

.loading-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.empty-inline {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  text-align: center;
}

.empty-inline-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

/* ===== Request List ===== */
.request-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.request-item {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  box-shadow: $shadow-xs;
}

.request-avatar-wrapper {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  margin-right: $spacing-md;
}

.request-avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.request-avatar-text {
  font-size: 18px;
  color: $color-primary;
  font-weight: $weight-semibold;
}

.request-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.request-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
  display: block;
}

.request-message {
  font-size: $font-xs;
  color: $color-text-sub;
  display: block;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.request-status {
  flex-shrink: 0;
  margin-left: $spacing-sm;
}

.request-status-text {
  font-size: $font-xs;
  font-weight: $weight-medium;
  color: $color-text-muted;

  &--pending {
    color: $color-primary;
  }

  &--accepted {
    color: $color-success;
  }

  &--rejected {
    color: $color-danger;
  }

  &--canceled {
    color: $color-text-muted;
  }
}

.bottom-safe {
  height: calc($tabbar-height + $safe-bottom);
}
</style>

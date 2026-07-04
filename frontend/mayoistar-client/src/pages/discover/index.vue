<script setup lang="ts">
/**
 * 发现页 - 活动搜索与地图入口
 *
 * 提供搜索入口、地图模式入口，以及推荐活动列表。
 *
 * 前置条件：用户可未登录浏览
 * 后置条件：展示搜索框 + 地图入口 + 推荐活动列表
 */
import { ref, onMounted } from 'vue'
import { getFeed } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'

/** 推荐活动条目 */
interface RecommendItem {
  activityId: string
  title: string
  tags: string[]
  startAt: string
  location: { city: string; address: string; placeName: string | null }
  coverImage: { signedUrl?: string; url?: string; mediaId: string } | null
  feeAmount?: number | null
  capacity: number
  registeredCount: number
  runtimeStatus: string
}

const recommends = ref<RecommendItem[]>([])
const isLoading = ref(true)

onMounted(async () => {
  await loadRecommends()
})

/** 加载推荐活动 */
async function loadRecommends(): Promise<void> {
  isLoading.value = true
  try {
    const result = await getFeed('recommended', { page: 1, pageSize: 6 })
    recommends.value = (result.items ?? []) as RecommendItem[]
  } catch {
    recommends.value = []
  } finally {
    isLoading.value = false
  }
}

/** 跳转搜索页 */
function goSearch(): void {
  uni.navigateTo({ url: '/pages/discover/search' })
}

/** 跳转地图页 */
function goMap(): void {
  uni.navigateTo({ url: '/pages/discover/map' })
}

/** 跳转活动详情 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/** 获取状态文本 */
function getStatusText(status: string): string {
  const map: Record<string, string> = {
    registering: '报名中',
    registrationClosed: '报名截止',
    ongoing: '进行中',
    ended: '已结束',
    notStarted: '未开始',
  }
  return map[status] ?? status
}

/** 获取推荐活动封面可展示地址 */
function getCoverUrl(item: RecommendItem): string {
  return item.coverImage?.signedUrl ?? item.coverImage?.url ?? ''
}
</script>

<template>
  <view class="discover-page">
    <scroll-view class="discover-scroll" scroll-y>
      <view class="discover-content">
        <!-- 搜索栏（点击跳转搜索页） -->
        <view class="search-entry" @tap="goSearch">
          <view class="search-bar">
            <text class="search-icon">🔍</text>
            <text class="search-placeholder">搜索活动名称、标签...</text>
          </view>
        </view>

        <!-- 快捷入口 -->
        <view class="quick-actions">
          <view class="action-card action-map" @tap="goMap">
            <text class="action-icon">📍</text>
            <text class="action-label">地图发现</text>
            <text class="action-desc">查看附近活动</text>
          </view>
          <view class="action-card action-search" @tap="goSearch">
            <text class="action-icon">🔍</text>
            <text class="action-label">关键词搜索</text>
            <text class="action-desc">精准找到活动</text>
          </view>
        </view>

        <!-- 推荐活动 -->
        <view class="section">
          <text class="section-title">为你推荐</text>

          <view v-if="isLoading" class="loading-state">
            <text>加载中...</text>
          </view>

          <view v-else-if="recommends.length === 0" class="empty-state">
            <text class="empty-text">暂无推荐活动</text>
          </view>

          <view v-else class="recommend-list">
            <view
              v-for="item in recommends"
              :key="item.activityId"
              class="recommend-card"
              @tap="goDetail(item.activityId)"
            >
              <view class="recommend-inner">
                <view v-if="getCoverUrl(item)" class="recommend-cover">
                  <image :src="getCoverUrl(item)" mode="aspectFill" class="cover-img" />
                </view>
                <view v-else class="recommend-cover recommend-cover-placeholder">
                  <text class="placeholder-icon">📌</text>
                </view>

                <view class="recommend-body">
                  <view class="recommend-header">
                    <text class="recommend-title">{{ item.title }}</text>
                    <text class="status-tag" :class="'status-' + item.runtimeStatus">{{
                      getStatusText(item.runtimeStatus)
                    }}</text>
                  </view>

                  <view v-if="item.tags.length > 0" class="recommend-tags">
                    <text v-for="tag in item.tags.slice(0, 2)" :key="tag" class="tag">{{
                      tag
                    }}</text>
                  </view>

                  <view class="recommend-meta">
                    <text class="meta-item">{{ formatDate(item.startAt) }}</text>
                    <text class="meta-sep">·</text>
                    <text class="meta-item">{{ item.location.city }}</text>
                  </view>

                  <view class="recommend-bottom">
                    <text class="fee" :class="{ free: !item.feeAmount }">{{
                      item.feeAmount ? '¥' + item.feeAmount : '免费'
                    }}</text>
                    <text class="registered">{{
                      item.registeredCount >= item.capacity
                        ? '已满员'
                        : `${item.registeredCount}/${item.capacity}人`
                    }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.discover-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.discover-scroll {
  flex: 1;
  min-height: 0;
}

.discover-content {
  padding-bottom: calc(#{$safe-bottom} + #{$tabbar-height} + #{$spacing-xl});
}

.search-entry {
  padding: $spacing-lg;
}

.search-bar {
  display: flex;
  align-items: center;
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border;
  border-radius: $radius-full;
  padding: $spacing-md $spacing-lg;
}

.search-icon {
  font-size: 16px;
  margin-right: $spacing-sm;
}

.search-placeholder {
  font-size: $font-base;
  color: $color-text-muted;
}

.quick-actions {
  display: flex;
  gap: $spacing-md;
  padding: 0 $spacing-lg $spacing-lg;
}

.action-card {
  flex: 1;
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;

  &:active {
    opacity: 0.85;
  }
}

.action-map {
  border-left: 3px solid $color-primary;
}

.action-search {
  border-left: 3px solid $color-secondary;
}

.action-icon {
  font-size: 24px;
}

.action-label {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.action-desc {
  font-size: $font-xs;
  color: $color-text-sub;
}

.section {
  padding: 0 $spacing-lg;
}

.section-title {
  font-size: $font-lg;
  font-weight: $weight-bold;
  color: $color-text;
  margin-bottom: $spacing-md;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 40px;

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.empty-state {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.empty-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.recommend-card {
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  overflow: hidden;

  &:active {
    opacity: 0.85;
  }
}

.recommend-inner {
  display: flex;
  flex-direction: row;
}

.recommend-cover {
  width: 110px;
  height: 85px;
  flex-shrink: 0;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
}

.recommend-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(123, 129, 144, 0.08);
}

.placeholder-icon {
  font-size: 24px;
}

.recommend-body {
  flex: 1;
  padding: $spacing-sm $spacing-md;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.recommend-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: $spacing-xs;
}

.recommend-title {
  flex: 1;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: $radius-sm;
  flex-shrink: 0;
  white-space: nowrap;
}

.status-registering {
  color: $color-primary;
  background: $color-primary-light;
}

.status-registrationClosed {
  color: $color-warning;
  background: rgba(246, 197, 111, 0.1);
}

.status-ongoing {
  color: $color-success;
  background: rgba(94, 200, 167, 0.1);
}

.status-ended {
  color: $color-text-muted;
  background: rgba(123, 129, 144, 0.08);
}

.status-notStarted {
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.08);
}

.recommend-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.tag {
  font-size: 10px;
  color: $color-primary;
  background: rgba(94, 200, 167, 0.08);
  padding: 1px 6px;
  border-radius: $radius-full;
}

.recommend-meta {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-item {
  font-size: 10px;
  color: $color-text-muted;
}

.meta-sep {
  font-size: 10px;
  color: $color-border;
}

.recommend-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.fee {
  font-size: $font-sm;
  font-weight: $weight-bold;
  color: $color-accent;
}

.fee.free {
  color: $color-primary;
  font-size: $font-xs;
  font-weight: $weight-semibold;
}

.registered {
  font-size: 10px;
  color: $color-text-muted;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

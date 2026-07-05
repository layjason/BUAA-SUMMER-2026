<template>
  <view class="page">
    <AppNavbar title="积分榜" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && items.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="items.length === 0" class="empty-state">
        <EmptyState title="暂无积分数据" description="小队成员还没有积分记录" />
      </view>

      <view v-else class="rank-list">
        <view
          v-for="item in items"
          :key="item.userId"
          class="rank-card"
          :class="{ 'rank-card--top': item.rank <= 3 }"
        >
          <view class="rank-badge" :class="`rank-badge--${item.rank}`">
            <text class="rank-number">{{ item.rank }}</text>
          </view>

          <view class="rank-info">
            <text class="rank-name">{{ item.nickname }}</text>
            <text v-if="item.rank <= 3" class="rank-label">{{ getRankLabel(item.rank) }}</text>
          </view>

          <view class="rank-points">
            <text class="points-value">{{ item.points }}</text>
            <text class="points-label">积分</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队积分榜页
 *
 * 展示成员积分排名，前三名高亮显示。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { getTeamPoints } from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import type { components } from '@/api/types/schema'

type TeamPointRankItem = components['schemas']['Social.TeamPointRankItem']

const teamId = ref('')
const items = ref<TeamPointRankItem[]>([])
const loading = ref(false)

/** 获取排名标签 */
function getRankLabel(rank: number): string {
  if (rank === 1) return '🥇 冠军'
  if (rank === 2) return '🥈 亚军'
  if (rank === 3) return '🥉 季军'
  return ''
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载积分排行榜 */
async function loadPoints() {
  if (!teamId.value) return

  loading.value = true
  try {
    const result = await getTeamPoints(teamId.value, 1, 50)
    items.value = extractPageItems<TeamPointRankItem>(result)
  } catch (error) {
    console.error('Failed to load points:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  teamId.value = query?.teamId || ''
  loadPoints()
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: $color-bg;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.loading-state,
.empty-state {
  padding: $spacing-2xl;
}

.loading-text {
  font-size: $font-base;
  color: $color-text-sub;
  text-align: center;
  display: block;
}

.rank-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.rank-card {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  display: flex;
  align-items: center;
  gap: $spacing-md;

  &--top {
    background: linear-gradient(135deg, #fff9e6 0%, var(--q-color-bg-card) 100%);
    border: 1px solid rgba(255, 193, 7, 0.2);
  }
}

.rank-badge {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--1 {
    background: linear-gradient(135deg, #ffd700, #ffb300);
  }

  &--2 {
    background: linear-gradient(135deg, #e0e0e0, #bdbdbd);
  }

  &--3 {
    background: linear-gradient(135deg, #cd7f32, #a0522d);
  }
}

.rank-number {
  font-size: $font-base;
  font-weight: $weight-bold;
  color: $color-text;

  .rank-badge--1 &,
  .rank-badge--2 &,
  .rank-badge--3 & {
    color: var(--q-color-bg-card);
  }
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-name {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.rank-label {
  font-size: $font-xs;
  color: $color-text-sub;
  display: block;
  margin-top: 2px;
}

.rank-points {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.points-value {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $color-primary;
}

.points-label {
  font-size: $font-xs;
  color: $color-text-muted;
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

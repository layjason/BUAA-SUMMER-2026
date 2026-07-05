<template>
  <view class="page">
    <AppNavbar title="我的小队" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && teams.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="teams.length === 0" class="empty-state">
        <EmptyState title="还没有加入小队" description="去发现感兴趣的小队并加入吧" />
        <view class="discover-btn" @tap="goDiscover">
          <text class="discover-btn-text">发现小队</text>
        </view>
      </view>

      <view v-else class="team-list">
        <view
          v-for="team in teams"
          :key="team.teamId"
          class="team-card"
          :class="{ 'team-card--disabled': team.status !== 'active' }"
          @tap="goDetail(team.teamId)"
        >
          <image
            v-if="team.avatar?.signedUrl"
            :src="team.avatar.signedUrl"
            class="team-avatar"
            mode="aspectFill"
          />
          <view v-else class="team-avatar team-avatar--placeholder">
            <text>👥</text>
          </view>

          <view class="team-body">
            <view class="team-title-row">
              <text class="team-name">{{ team.name }}</text>
              <view v-if="team.status !== 'active'" class="status-badge">
                <text class="status-text">{{
                  team.status === 'dissolved' ? '已解散' : '已停用'
                }}</text>
              </view>
            </view>
            <text class="team-meta">{{ team.memberCount }}/{{ team.capacity }} 成员</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 我的小队（个人中心入口）
 *
 * 使用 GET /social/teams/mine，含已解散/已停用历史小队。
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { listMyTeams } from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']

const teams = ref<TeamProfile[]>([])
const loading = ref(false)

async function loadTeams() {
  loading.value = true
  try {
    const result = await listMyTeams(1, 100)
    teams.value = extractPageItems<TeamProfile>(result)
  } catch {
    teams.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(teamId: string) {
  uni.navigateTo({ url: `/pages/teams/detail?teamId=${teamId}` })
}

function goDiscover() {
  uni.navigateTo({ url: '/pages/teams/index?tab=discover' })
}

onShow(loadTeams)
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background: $color-bg;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
}

.loading-state,
.empty-state {
  padding-top: 160rpx;
  text-align: center;
}

.loading-text {
  color: $color-text-muted;
  font-size: $font-sm;
}

.team-list {
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.team-card {
  display: flex;
  gap: $spacing-md;
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-lg;
}

.team-card--disabled {
  opacity: 0.75;
}

.team-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
}

.team-avatar--placeholder {
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.team-body {
  flex: 1;
}

.team-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.team-name {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.status-badge {
  padding: 2px 8px;
  border-radius: $radius-full;
  background: rgba(160, 166, 178, 0.12);
}

.status-text {
  font-size: $font-xs;
  color: $color-text-muted;
}

.team-meta {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-muted;
}

.discover-btn {
  margin: $spacing-xl auto 0;
  background: $color-primary;
  border-radius: $radius-full;
  padding: $spacing-md $spacing-2xl;
  width: fit-content;
}

.discover-btn-text {
  color: var(--q-color-bg-card);
  font-size: $font-sm;
}

.bottom-padding {
  height: 80rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

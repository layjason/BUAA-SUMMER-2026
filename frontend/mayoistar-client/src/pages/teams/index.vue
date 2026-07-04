<template>
  <view class="page">
    <AppNavbar title="我的小队" />

    <scroll-view class="scroll-area" scroll-y>
      <!-- Tab: My Teams / Discover -->
      <view class="tab-bar">
        <view
          class="tab-item"
          :class="{ 'tab-item--active': activeTab === 'my' }"
          @tap="activeTab = 'my'"
        >
          <text class="tab-label">我的小队</text>
        </view>
        <view
          class="tab-item"
          :class="{ 'tab-item--active': activeTab === 'discover' }"
          @tap="activeTab = 'discover'"
        >
          <text class="tab-label">发现小队</text>
        </view>
      </view>

      <!-- Loading -->
      <view v-if="loading && teams.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <!-- Empty State -->
      <view v-else-if="teams.length === 0" class="empty-state">
        <EmptyState
          :title="activeTab === 'my' ? '暂无小队' : '暂无推荐'"
          :description="
            activeTab === 'my' ? '创建一个新小队，开始你的兴趣之旅！' : '暂时没有可发现的小队'
          "
        />
        <view v-if="activeTab === 'my'" class="create-button" @tap="goToCreateTeam">
          <text class="create-button-text">创建小队</text>
        </view>
      </view>

      <!-- Team List -->
      <view v-else class="team-list">
        <view
          v-for="team in teams"
          :key="team.teamId"
          class="team-card"
          :class="{ 'team-card--disabled': team.status !== 'active' }"
          @tap="goToTeamDetail(team)"
        >
          <!-- Avatar -->
          <view class="team-avatar">
            <image
              v-if="team.avatar?.signedUrl"
              :src="team.avatar.signedUrl"
              class="team-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="team-avatar-placeholder">👥</text>
          </view>

          <!-- Content -->
          <view class="team-content">
            <view class="team-header">
              <text class="team-name">{{ team.name }}</text>
              <view v-if="team.status !== 'active'" class="team-status-badge">
                <text class="status-text">{{
                  team.status === 'dissolved' ? '已解散' : '已停用'
                }}</text>
              </view>
            </view>

            <view class="team-meta">
              <text class="member-count">{{ team.memberCount }}/{{ team.capacity }} 成员</text>
              <text class="join-mode">{{
                team.joinMode === 'publicJoin' ? '自由加入' : '需审批'
              }}</text>
            </view>

            <!-- Tags -->
            <view v-if="team.tags.length > 0" class="team-tags">
              <view v-for="(tag, idx) in team.tags.slice(0, 3)" :key="idx" class="tag">
                <text class="tag-text">{{ tag }}</text>
              </view>
              <view v-if="team.tags.length > 3" class="tag tag--more">
                <text class="tag-text">+{{ team.tags.length - 3 }}</text>
              </view>
            </view>
          </view>

          <!-- Right Arrow -->
          <text class="arrow-icon">›</text>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- FAB: Create Team -->
    <view class="fab" @tap="goToCreateTeam">
      <text class="fab-icon">+</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队列表页
 *
 * 展示我的小队和发现小队，支持创建小队
 */
import { ref, watch, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { getTeams, searchTeams } from '@/api/modules/teams'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']

const activeTab = ref<'my' | 'discover'>('my')
const loading = ref(false)
const teams = ref<TeamProfile[]>([])

/** 加载小队列表 */
async function loadTeams() {
  loading.value = true
  try {
    let result: unknown
    if (activeTab.value === 'my') {
      result = await getTeams()
    } else {
      result = await searchTeams()
    }
    const items = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as TeamProfile[]) ?? [])
    teams.value = items
  } catch (error) {
    console.error('Failed to load teams:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goToTeamDetail(team: TeamProfile) {
  if (team.status !== 'active') {
    uni.showToast({ title: '该小队已不可用', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/teams/detail?teamId=${team.teamId}` })
}

function goToCreateTeam() {
  uni.navigateTo({ url: '/pages/teams/create' })
}

onMounted(() => {
  loadTeams()
})

watch(activeTab, () => {
  loadTeams()
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

.tab-bar {
  display: flex;
  background: #ffffff;
  border-bottom: 1px solid $color-border-light;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md 0;
  position: relative;

  &--active {
    .tab-label {
      color: $color-primary;
      font-weight: $weight-semibold;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 40px;
      height: 3px;
      background: $color-primary;
      border-radius: 2px;
    }
  }
}

.tab-label {
  font-size: $font-base;
  color: $color-text-sub;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.loading-text {
  font-size: $font-base;
  color: $color-text-sub;
}

.empty-state {
  padding: $spacing-xl;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.create-button {
  margin-top: $spacing-lg;
  background: $color-primary;
  padding: $spacing-sm $spacing-xl;
  border-radius: $radius-full;
}

.create-button-text {
  color: #ffffff;
  font-size: $font-base;
  font-weight: $weight-medium;
}

.team-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.team-card {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  gap: $spacing-md;
  box-shadow: $shadow-xs;
  transition: transform 0.2s ease;

  &:active {
    transform: scale(0.98);
  }

  &--disabled {
    opacity: 0.6;

    .team-name {
      text-decoration: line-through;
    }
  }
}

.team-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-md;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.team-avatar-image {
  width: 100%;
  height: 100%;
}

.team-avatar-placeholder {
  font-size: 24px;
}

.team-content {
  flex: 1;
  min-width: 0;
}

.team-header {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.team-name {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-status-badge {
  background: rgba(242, 156, 163, 0.15);
  padding: 2px $spacing-xs;
  border-radius: $radius-sm;
}

.status-text {
  font-size: $font-xs;
  color: $color-danger;
}

.team-meta {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-xs;
}

.member-count,
.join-mode {
  font-size: $font-sm;
  color: $color-text-sub;
}

.team-tags {
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

.tag--more {
  background: #f0f2f5;

  .tag-text {
    color: $color-text-sub;
  }
}

.arrow-icon {
  font-size: 20px;
  color: $color-text-muted;
  flex-shrink: 0;
}

.bottom-padding {
  height: calc(80px + $safe-bottom);
}

.fab {
  position: fixed;
  right: $spacing-xl;
  bottom: calc($tabbar-height + 80px + $safe-bottom);
  width: 56px;
  height: 56px;
  border-radius: $radius-full;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-md;
  transition: transform 0.2s ease;

  &:active {
    transform: scale(0.95);
  }
}

.fab-icon {
  font-size: 28px;
  color: #ffffff;
  font-weight: 300;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

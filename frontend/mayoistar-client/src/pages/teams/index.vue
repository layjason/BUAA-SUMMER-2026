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

      <!-- 发现小队搜索 -->
      <view v-if="activeTab === 'discover'" class="discover-toolbar">
        <view class="search-bar">
          <input
            v-model="searchKeyword"
            class="search-input"
            type="text"
            placeholder="按名称搜索小队"
            confirm-type="search"
            @confirm="loadTeams"
          />
          <view class="search-btn" @tap="loadTeams">
            <text class="search-btn-text">搜索</text>
          </view>
        </view>
        <scroll-view class="tag-scroll" scroll-x :show-scrollbar="false">
          <view class="tag-scroll-inner">
            <view class="tag-row">
              <view
                class="tag-chip"
                :class="{ 'tag-chip--active': selectedTags.size === 0 }"
                @tap="clearTags"
              >
                <text class="tag-chip-text">全部</text>
              </view>
              <view
                v-for="tag in tagRow1"
                :key="tag.name"
                class="tag-chip"
                :class="{ 'tag-chip--active': selectedTags.has(tag.name) }"
                @tap="toggleTag(tag.name)"
              >
                <text class="tag-chip-text">{{ tag.name }}</text>
              </view>
            </view>
            <view class="tag-row">
              <view
                v-for="tag in tagRow2"
                :key="tag.name"
                class="tag-chip"
                :class="{ 'tag-chip--active': selectedTags.has(tag.name) }"
                @tap="toggleTag(tag.name)"
              >
                <text class="tag-chip-text">{{ tag.name }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
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
          <UserAvatar size="md" :avatar-url="team.avatar?.signedUrl || ''" :name="team.name" />

          <!-- Content -->
          <view class="team-content">
            <view class="team-header">
              <text class="team-name">{{ team.name }}</text>
              <view v-if="isTeamFull(team)" class="team-status-badge">
                <text class="status-text">已满员</text>
              </view>
              <view v-else-if="team.status !== 'active'" class="team-status-badge">
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
import { ref, watch, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import { listMyTeams, searchTeams } from '@/api/modules/teams'
import { getInterestTags } from '@/api/modules/profile'
import { extractPageItems } from '@/utils/page-result'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']

const activeTab = ref<'my' | 'discover'>('my')
const searchKeyword = ref('')
const selectedTags = ref(new Set<string>())
const availableTags = ref<{ name: string }[]>([])
const loading = ref(false)
const teams = ref<TeamProfile[]>([])

/** 标签拆成两行，整体横向滑动 */
const tagRow1 = computed(() => availableTags.value.filter((_, index) => index % 2 === 0))
const tagRow2 = computed(() => availableTags.value.filter((_, index) => index % 2 === 1))

function clearTags() {
  selectedTags.value = new Set()
  loadTeams()
}

function toggleTag(name: string) {
  const next = new Set(selectedTags.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selectedTags.value = next
  loadTeams()
}

async function loadInterestTags() {
  try {
    const tags = await getInterestTags()
    availableTags.value = tags as { name: string }[]
  } catch {
    availableTags.value = []
  }
}

/** 加载小队列表 */
async function loadTeams() {
  loading.value = true
  try {
    const result =
      activeTab.value === 'my'
        ? await listMyTeams(1, 50)
        : await searchTeams({
            keyword: searchKeyword.value.trim() || undefined,
            tags: selectedTags.value.size ? [...selectedTags.value] : undefined,
            page: 1,
            pageSize: 50,
          })
    teams.value = extractPageItems<TeamProfile>(result)
  } catch (error) {
    console.error('Failed to load teams:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function isTeamFull(team: TeamProfile): boolean {
  return team.status === 'active' && team.memberCount >= team.capacity
}

function goToTeamDetail(team: TeamProfile) {
  if (activeTab.value === 'discover' && team.status !== 'active') {
    uni.showToast({ title: '该小队已不可用', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/teams/detail?teamId=${team.teamId}` })
}

function goToCreateTeam() {
  uni.navigateTo({ url: '/pages/teams/create' })
}

onLoad((options) => {
  if (options?.tab === 'discover') activeTab.value = 'discover'
})

onMounted(() => {
  loadInterestTags()
  loadTeams()
})

watch(activeTab, () => {
  searchKeyword.value = ''
  selectedTags.value = new Set()
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

.discover-toolbar {
  background: var(--q-color-bg-card);
  border-bottom: 1px solid $color-border-light;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
}

.tag-scroll {
  width: 100%;
  padding: 0 $spacing-md $spacing-sm;
  box-sizing: border-box;
}

.tag-scroll-inner {
  display: inline-block;
  min-width: 100%;
}

.tag-row {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;

  &:last-child {
    margin-bottom: 0;
  }
}

.tag-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: 140px;
  padding: 8px 16px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  border: 1px solid transparent;
  box-shadow: 0 1px 4px rgba(47, 52, 65, 0.06);
}

.tag-chip--active {
  background: $color-primary-light;
  border-color: transparent;
}

.tag-chip-text {
  font-size: $font-sm;
  color: $color-text;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 108px;
}

.tag-chip--active .tag-chip-text {
  color: $color-primary-dark;
}

.search-input {
  flex: 1;
  background: var(--q-color-bg-soft);
  border-radius: $radius-full;
  padding: $spacing-xs $spacing-md;
  font-size: $font-base;
}

.search-btn {
  background: $color-primary;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
}

.search-btn-text {
  color: var(--q-color-bg-card);
  font-size: $font-sm;
}

.tab-bar {
  display: flex;
  background: var(--q-color-bg-card);
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
  color: var(--q-color-bg-card);
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
  background: var(--q-color-bg-card);
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
    opacity: 0.75;
  }
}

.team-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-md;
  background: var(--q-color-bg-soft);
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
  background: rgba(220, 38, 38, 0.08);
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
  background: var(--q-color-bg-soft);

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
  color: var(--q-color-bg-card);
  font-weight: 300;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

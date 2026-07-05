<template>
  <view class="page">
    <AppNavbar title="小队资料" />
    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="team" class="form-section">
        <!-- Team Avatar -->
        <view class="avatar-section">
          <view class="team-avatar">
            <image
              v-if="team.avatar?.signedUrl"
              :src="team.avatar.signedUrl"
              class="team-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="team-avatar-placeholder">👥</text>
          </view>
          <text class="avatar-hint">小队头像</text>
        </view>

        <!-- Name -->
        <view class="form-item">
          <text class="form-label">小队名称</text>
          <text class="form-value">{{ team.name }}</text>
        </view>

        <!-- Description -->
        <view class="form-item">
          <text class="form-label">小队简介</text>
          <text class="form-value form-desc">{{ team.description || '暂无简介' }}</text>
        </view>

        <!-- Capacity -->
        <view class="form-item">
          <text class="form-label">人数上限</text>
          <text class="form-value">{{ team.capacity }} 人</text>
        </view>

        <!-- Join Mode -->
        <view class="form-item">
          <text class="form-label">加入方式</text>
          <text class="form-value">{{
            team.joinMode === 'publicJoin' ? '自由加入' : '需要审批'
          }}</text>
        </view>

        <!-- Tags -->
        <view class="form-item">
          <text class="form-label">标签</text>
          <view v-if="team.tags.length > 0" class="tags">
            <view v-for="(tag, idx) in team.tags" :key="idx" class="tag">
              <text class="tag-text">{{ tag }}</text>
            </view>
          </view>
          <text v-else class="form-value form-desc">暂无标签</text>
        </view>

        <!-- Status -->
        <view class="form-item">
          <text class="form-label">状态</text>
          <view class="status-badge" :class="`status-badge--${team.status}`">
            <text class="status-text">{{ statusLabel }}</text>
          </view>
        </view>

        <!-- Notice -->
        <view class="notice">
          <text class="notice-text">小队资料仅队长可修改，修改功能即将上线</text>
        </view>
      </view>

      <view v-else class="empty-state">
        <text class="empty-text">小队不存在</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队资料查看页
 *
 * OpenAPI 暂无 PATCH /social/teams/{teamId} 端点，
 * 因此仅展示小队资料，不支持编辑。
 */
import { ref, computed, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import { getTeamDetail } from '@/api/modules/teams'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']

const team = ref<TeamProfile | null>(null)
const loading = ref(false)
const teamId = ref('')

const statusLabel = computed(() => {
  if (!team.value) return ''
  switch (team.value.status) {
    case 'active':
      return '活跃'
    case 'dissolved':
      return '已解散'
    case 'disabled':
      return '已停用'
    default:
      return team.value.status
  }
})

onMounted(async () => {
  const pages = getCurrentPages()
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const currentPage = pages[pages.length - 1] as any
  teamId.value = currentPage.options?.teamId || ''

  if (!teamId.value) return

  loading.value = true
  try {
    const result = await getTeamDetail(teamId.value)
    team.value = result as TeamProfile
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
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
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.empty-text {
  font-size: $font-base;
  color: $color-text-sub;
}

.form-section {
  padding: $spacing-lg;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-lg 0;
}

.team-avatar {
  width: 80px;
  height: 80px;
  border-radius: $radius-lg;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.team-avatar-image {
  width: 100%;
  height: 100%;
}

.team-avatar-placeholder {
  font-size: 40px;
}

.avatar-hint {
  font-size: $font-xs;
  color: $color-text-muted;
  margin-top: $spacing-xs;
}

.form-item {
  background: #ffffff;
  padding: $spacing-md;
  margin-bottom: 1px;

  &:first-of-type {
    border-radius: $radius-lg $radius-lg 0 0;
  }

  &:last-of-type {
    border-radius: 0 0 $radius-lg $radius-lg;
    margin-bottom: $spacing-lg;
  }
}

.form-label {
  font-size: $font-sm;
  color: $color-text-sub;
  display: block;
  margin-bottom: $spacing-xs;
}

.form-value {
  font-size: $font-base;
  color: $color-text;
  font-weight: $weight-medium;
}

.form-desc {
  color: $color-text-muted;
  font-weight: $weight-regular;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.tag {
  background: rgba($color-primary, 0.1);
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
}

.tag-text {
  font-size: $font-xs;
  color: $color-primary;
}

.status-badge {
  display: inline-flex;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba(76, 175, 80, 0.1);

  &--dissolved {
    background: rgba(242, 156, 163, 0.15);

    .status-text {
      color: $color-danger;
    }
  }

  &--disabled {
    background: rgba(158, 158, 158, 0.15);

    .status-text {
      color: $color-text-muted;
    }
  }
}

.status-text {
  font-size: $font-sm;
  color: #4caf50;
}

.notice {
  text-align: center;
  padding: $spacing-lg;
}

.notice-text {
  font-size: $font-sm;
  color: $color-text-muted;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

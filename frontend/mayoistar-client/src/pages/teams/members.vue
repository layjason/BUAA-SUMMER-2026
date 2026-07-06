<template>
  <view class="page">
    <AppNavbar title="小队成员" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && members.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="members.length === 0" class="empty-state">
        <EmptyState title="暂无成员" description="小队还没有成员" />
      </view>

      <view v-else class="member-list">
        <view
          v-for="member in members"
          :key="member.userId"
          class="member-item"
          @tap="onMemberTap(member)"
        >
          <UserAvatar
            size="md"
            :avatar-url="member.avatar?.signedUrl || ''"
            :name="member.nickname"
            :user-id="member.userId"
          />

          <view class="member-info">
            <view class="member-name-row">
              <text class="member-name">{{ member.nickname }}</text>
              <view v-if="member.role !== 'member'" class="role-badge">
                <text class="role-text">{{ member.role === 'leader' ? '队长' : '管理员' }}</text>
              </view>
            </view>
            <view class="member-meta">
              <text class="member-points">积分 {{ member.points }}</text>
              <text class="member-joined">{{ formatTime(member.joinedAt) }}</text>
            </view>
          </view>

          <view
            v-if="canManage && member.role !== 'leader' && member.userId !== currentUserId"
            class="member-action"
            @tap.stop="showMemberMenu(member)"
          >
            <text class="action-icon">⋯</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- Member Menu Popup -->
    <uni-popup ref="memberPopup" type="bottom" :safe-area="true">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">{{ selectedMember?.nickname }}</text>
          <text class="action-sheet__close" @tap="closeMemberMenu">×</text>
        </view>
        <view class="action-sheet__items">
          <view
            v-if="selectedMember?.role === 'member'"
            class="action-sheet__item"
            @tap="setRole('admin')"
          >
            <text class="action-sheet__icon">⬆️</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">设为管理员</text>
            </view>
          </view>
          <view
            v-if="selectedMember?.role === 'admin'"
            class="action-sheet__item"
            @tap="setRole('member')"
          >
            <text class="action-sheet__icon">⬇️</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">取消管理员</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队成员管理页
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import { getTeamMembers, updateMemberRole } from '@/api/modules/teams'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const members = ref<TeamMember[]>([])
const loading = ref(false)
const selectedMember = ref<TeamMember | null>(null)

interface PopupRef {
  open: () => void
  close: () => void
}

const memberPopup = ref<PopupRef | null>(null)

// 从 auth store 获取当前用户 ID
const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader'
})

function formatTime(isoTime: string): string {
  const date = new Date(isoTime)
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

async function loadMembers() {
  if (!teamId.value) return

  loading.value = true
  try {
    const result = await getTeamMembers(teamId.value, 1, 100)
    const items = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as TeamMember[]) ?? [])
    members.value = items
  } catch (error) {
    console.error('Failed to load members:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onMemberTap(member: TeamMember) {
  uni.navigateTo({
    url: `/pages/social/user-profile?id=${member.userId}&source=team`,
  })
}

function showMemberMenu(member: TeamMember) {
  selectedMember.value = member
  memberPopup.value?.open()
}

function closeMemberMenu() {
  memberPopup.value?.close()
}

async function setRole(role: 'admin' | 'member') {
  closeMemberMenu()
  if (!selectedMember.value) return

  try {
    await updateMemberRole(teamId.value, selectedMember.value.userId, role as 'admin' | 'member')
    uni.showToast({ title: '角色已更新', icon: 'success' })
    await loadMembers()
  } catch (error) {
    console.error('Failed to update role:', error)
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

onLoad((query) => {
  teamId.value = typeof query?.teamId === 'string' ? query.teamId : ''
  void loadMembers()
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
}

.member-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.member-item {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  gap: $spacing-md;

  &:active {
    background: var(--q-color-bg-soft);
  }
}

.member-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.member-avatar-image {
  width: 100%;
  height: 100%;
}

.member-avatar-placeholder {
  font-size: 22px;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.member-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
}

.role-badge {
  background: $color-primary-light;
  padding: 1px $spacing-xs;
  border-radius: $radius-sm;
}

.role-text {
  font-size: 10px;
  color: $color-primary;
}

.member-meta {
  display: flex;
  gap: $spacing-md;
}

.member-points,
.member-joined {
  font-size: $font-xs;
  color: $color-text-sub;
}

.member-action {
  width: 32px;
  height: 32px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-icon {
  font-size: 16px;
  color: $color-text-sub;
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}

.action-sheet {
  background: var(--q-color-bg-card);
  border-radius: $radius-xl $radius-xl 0 0;
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-lg $spacing-xl;
    border-bottom: 1px solid $color-border-light;
  }

  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
  }

  &__close {
    font-size: 24px;
    color: $color-text-muted;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__items {
    padding: $spacing-md 0;
  }

  &__item {
    display: flex;
    align-items: center;
    padding: $spacing-md $spacing-xl;

    &:active {
      background: rgba(0, 0, 0, 0.03);
    }
  }

  &__icon {
    font-size: 20px;
    margin-right: $spacing-md;
    width: 28px;
    text-align: center;
  }

  &__content {
    flex: 1;
  }

  &__label {
    font-size: $font-base;
    font-weight: $weight-medium;
    color: $color-text;
  }
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

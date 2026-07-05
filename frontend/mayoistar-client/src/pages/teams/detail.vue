<template>
  <view class="page">
    <AppNavbar title="小队详情" />

    <scroll-view class="scroll-area" scroll-y>
      <!-- Loading -->
      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <template v-else-if="team">
        <!-- Team Header -->
        <view class="team-header">
          <view class="team-avatar-large">
            <image
              v-if="team.avatar?.signedUrl"
              :src="team.avatar.signedUrl"
              class="team-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="team-avatar-placeholder">👥</text>
          </view>
          <view class="team-info">
            <view class="team-name-row">
              <text class="team-name">{{ team.name }}</text>
              <view v-if="team.status !== 'active'" class="status-badge">
                <text class="status-text">{{
                  team.status === 'dissolved' ? '已解散' : '已停用'
                }}</text>
              </view>
            </view>
            <text class="team-desc" v-if="team.description">{{ team.description }}</text>
            <view class="team-meta">
              <text class="meta-item">{{ team.memberCount }}/{{ team.capacity }} 成员</text>
              <text class="meta-item">{{
                team.joinMode === 'publicJoin' ? '自由加入' : '需审批'
              }}</text>
            </view>
          </view>
        </view>

        <!-- Tags -->
        <view v-if="team.tags.length > 0" class="team-tags">
          <view v-for="(tag, idx) in team.tags" :key="idx" class="tag">
            <text class="tag-text">{{ tag }}</text>
          </view>
        </view>

        <!-- Actions -->
        <view v-if="team.status === 'active'" class="action-bar">
          <view v-if="isMember" class="action-buttons">
            <view class="action-btn action-btn--primary" @tap="goToChat">
              <text class="action-btn-text">进入群聊</text>
            </view>
            <view v-if="canManage" class="action-btn action-btn--secondary" @tap="showManageMenu">
              <text class="action-btn-text-secondary">管理小队</text>
            </view>
            <view v-else class="action-btn action-btn--secondary" @tap="goToPoints">
              <text class="action-btn-text-secondary">积分榜</text>
            </view>
            <view v-if="!canManage" class="action-btn action-btn--danger" @tap="confirmLeave">
              <text class="action-btn-text-danger">退出小队</text>
            </view>
          </view>
          <view v-else class="guest-actions">
            <view
              class="action-btn action-btn--primary action-btn--block"
              :class="{ 'action-btn--disabled': joinDisabled }"
              @tap="joinTeam_"
            >
              <text class="action-btn-text">{{ joinButtonText }}</text>
            </view>
            <text v-if="joinHint" class="join-hint">{{ joinHint }}</text>
          </view>
        </view>

        <view v-else-if="!isMember" class="inactive-hint">
          <text class="inactive-hint-text">
            {{ team.status === 'dissolved' ? '小队已解散，无法加入' : '小队已停用，无法加入' }}
          </text>
        </view>

        <!-- Members Section -->
        <view class="section">
          <view class="section-header">
            <text class="section-title">成员 ({{ team.memberCount }})</text>
            <text class="section-more" @tap="goToMembers">查看全部 ›</text>
          </view>

          <view class="member-list">
            <view v-for="member in members.slice(0, 5)" :key="member.userId" class="member-item">
              <view class="member-avatar">
                <image
                  v-if="member.avatar?.signedUrl"
                  :src="member.avatar.signedUrl"
                  class="member-avatar-image"
                  mode="aspectFill"
                />
                <text v-else class="member-avatar-placeholder">👤</text>
              </view>
              <view class="member-info">
                <view class="member-name-row">
                  <text class="member-name">{{ member.nickname }}</text>
                  <view v-if="member.role !== 'member'" class="role-badge">
                    <text class="role-text">{{
                      member.role === 'leader' ? '队长' : '管理员'
                    }}</text>
                  </view>
                </view>
                <text class="member-points">积分 {{ member.points }}</text>
              </view>
            </view>
          </view>

          <view v-if="members.length === 0" class="empty-members">
            <text class="empty-text">暂无成员</text>
          </view>
        </view>
      </template>

      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- Manage Menu Popup -->
    <uni-popup ref="managePopup" type="bottom" :safe-area="true" @mask-click="closeManageMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">小队管理</text>
          <text class="action-sheet__close" @tap="closeManageMenu">×</text>
        </view>
        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToJoinRequests">
            <text class="action-sheet__icon">📨</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">入队申请</text>
              <text class="action-sheet__desc">查看和审批入队请求</text>
            </view>
          </view>
          <view class="action-sheet__item" @tap="goToMembers">
            <text class="action-sheet__icon">👥</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">成员管理</text>
              <text class="action-sheet__desc">查看和管理小队成员</text>
            </view>
          </view>
          <view class="action-sheet__item" @tap="goToPoints">
            <text class="action-sheet__icon">🏆</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">积分榜</text>
              <text class="action-sheet__desc">查看成员积分排名</text>
            </view>
          </view>
          <view
            v-if="isLeader"
            class="action-sheet__item action-sheet__item--danger"
            @tap="confirmDissolve"
          >
            <text class="action-sheet__icon">⚠️</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">解散小队</text>
              <text class="action-sheet__desc">此操作不可逆</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队详情页
 *
 * 展示小队信息、成员列表和操作（加入/退出/管理）
 */
import { ref, computed, onMounted } from 'vue'
import { onHide } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import {
  getTeamDetail,
  getTeamMembers,
  joinTeam,
  leaveTeam,
  dissolveTeam,
} from '@/api/modules/teams'
import { BusinessError } from '@/api'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const team = ref<TeamProfile | null>(null)
const members = ref<TeamMember[]>([])
const loading = ref(false)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const managePopup = ref<any>(null)

// 从 auth store 获取当前用户 ID
const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '10001')

const isMember = computed(() => members.value.some((m) => m.userId === currentUserId.value))
const isLeader = computed(() => team.value?.leaderId === currentUserId.value)
const myRole = computed(() => members.value.find((m) => m.userId === currentUserId.value)?.role)
const canManage = computed(() => myRole.value === 'leader' || myRole.value === 'admin')

const isFull = computed(() => {
  if (!team.value) return false
  return team.value.memberCount >= team.value.capacity
})

const joinDisabled = computed(() => {
  if (!team.value || isMember.value) return true
  if (team.value.status !== 'active') return true
  return isFull.value
})

const joinButtonText = computed(() => {
  if (!team.value) return '加入小队'
  if (isFull.value) return '小队已满员'
  return team.value.joinMode === 'publicJoin' ? '加入小队' : '申请加入'
})

const joinHint = computed(() => {
  if (!team.value || isMember.value) return ''
  if (isFull.value) return '人数已达上限，暂时无法加入'
  if (team.value.joinMode === 'approvalRequired') return '提交后需等待队长或管理员审核'
  return ''
})

/** 加载小队详情 */
async function loadTeam() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [teamResult, membersResult] = await Promise.all([
      getTeamDetail(teamId.value),
      getTeamMembers(teamId.value, 1, 10).catch(() => []),
    ])

    team.value = teamResult as TeamProfile

    const memberItems = Array.isArray(membersResult)
      ? membersResult
      : (((membersResult as Record<string, unknown>).items as TeamMember[]) ?? [])
    members.value = memberItems
  } catch (error) {
    console.error('Failed to load team:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 加入小队 */
async function joinTeam_() {
  if (joinDisabled.value) {
    if (joinHint.value) uni.showToast({ title: joinHint.value, icon: 'none' })
    return
  }
  try {
    const result = await joinTeam(teamId.value)
    const status = (result as { status?: string }).status
    uni.showToast({
      title: status === 'accepted' ? '加入成功' : '申请已发送',
      icon: 'success',
    })
    await loadTeam()
  } catch (error) {
    console.error('Failed to join team:', error)
    const message =
      error instanceof BusinessError ? getTeamErrorMessage(error.code, error.message) : '加入失败'
    uni.showToast({ title: message, icon: 'none' })
  }
}

/** 退出小队 */
function confirmLeave() {
  uni.showModal({
    title: '确认退出',
    content: '确定要退出该小队吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await leaveTeam(teamId.value)
          uni.showToast({ title: '已退出小队', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 1000)
        } catch (error) {
          console.error('Failed to leave team:', error)
          uni.showToast({ title: '退出失败', icon: 'none' })
        }
      }
    },
  })
}

/** 解散小队 */
function confirmDissolve() {
  closeManageMenu()
  uni.showModal({
    title: '确认解散',
    content: '解散后小队将无法恢复，确定要解散吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await dissolveTeam(teamId.value)
          uni.showToast({ title: '小队已解散', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 1000)
        } catch (error) {
          console.error('Failed to dissolve team:', error)
          uni.showToast({ title: '解散失败', icon: 'none' })
        }
      }
    },
  })
}

function goToChat() {
  if (!team.value) return
  const chatUrl =
    `/pages/messages/chat` + `?conversationId=${team.value.chatId}&kind=team&teamId=${teamId.value}`
  uni.navigateTo({ url: chatUrl })
}

function showManageMenu() {
  managePopup.value?.open()
}

function closeManageMenu() {
  managePopup.value?.close()
}

function goToMembers() {
  closeManageMenu()
  uni.navigateTo({ url: `/pages/teams/members?teamId=${teamId.value}` })
}

function goToJoinRequests() {
  closeManageMenu()
  uni.navigateTo({ url: `/pages/teams/join-requests?teamId=${teamId.value}` })
}

function goToPoints() {
  closeManageMenu()
  uni.navigateTo({ url: `/pages/teams/points?teamId=${teamId.value}` })
}

onHide(() => {
  closeManageMenu()
})

onMounted(() => {
  const pages = getCurrentPages()
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const currentPage = pages[pages.length - 1] as any
  const options = currentPage.options || {}
  teamId.value = options.teamId || ''

  loadTeam()
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

.team-header {
  background: #ffffff;
  padding: $spacing-xl;
  display: flex;
  gap: $spacing-md;
}

.team-avatar-large {
  width: 64px;
  height: 64px;
  border-radius: $radius-lg;
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
  font-size: 32px;
}

.team-info {
  flex: 1;
  min-width: 0;
}

.team-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.team-name {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $color-text;
}

.status-badge {
  background: rgba(242, 156, 163, 0.15);
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
}

.status-text {
  font-size: $font-xs;
  color: $color-danger;
}

.team-desc {
  font-size: $font-sm;
  color: $color-text-sub;
  line-height: 1.5;
  margin-bottom: $spacing-xs;
}

.team-meta {
  display: flex;
  gap: $spacing-md;
}

.meta-item {
  font-size: $font-sm;
  color: $color-text-muted;
}

.team-tags {
  background: #ffffff;
  padding: $spacing-sm $spacing-xl;
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.tag {
  background: $color-primary-light;
  padding: $spacing-xs $spacing-sm;
  border-radius: $radius-sm;
}

.tag-text {
  font-size: $font-xs;
  color: $color-primary;
}

.action-bar {
  background: #ffffff;
  padding: $spacing-md $spacing-xl;
  border-top: 1px solid $color-border-light;
  border-bottom: 1px solid $color-border-light;
}

.action-buttons {
  display: flex;
  gap: $spacing-md;
}

.guest-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: $spacing-sm;
}

.action-btn {
  flex: 1;
  padding: $spacing-sm 0;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
  box-sizing: border-box;
  min-width: 0;

  &--block {
    flex: none;
    width: 100%;
    padding: $spacing-md 0;
  }

  &:active {
    transform: scale(0.98);
  }

  &--primary {
    background: $color-primary;
  }

  &--secondary {
    background: #f0f2f5;
  }

  &--danger {
    background: #ffffff;
    border: 1px solid $color-danger;
  }

  &--disabled {
    opacity: 0.55;

    &:active {
      transform: none;
    }
  }
}

.join-hint {
  display: block;
  width: 100%;
  text-align: center;
  font-size: $font-xs;
  color: $color-text-sub;
  margin-top: $spacing-xs;
}

.inactive-hint {
  padding: $spacing-md $spacing-xl;
  text-align: center;
}

.inactive-hint-text {
  font-size: $font-sm;
  color: $color-text-sub;
}

.action-btn-text {
  color: #ffffff;
  font-size: $font-base;
  font-weight: $weight-medium;
}

.action-btn-text-secondary {
  color: $color-text;
  font-size: $font-base;
  font-weight: $weight-medium;
}

.action-btn-text-danger {
  color: $color-danger;
  font-size: $font-base;
  font-weight: $weight-medium;
}

.section {
  background: #ffffff;
  margin-top: $spacing-md;
  padding: $spacing-lg $spacing-xl;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.section-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.section-more {
  font-size: $font-sm;
  color: $color-primary;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.member-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: #f0f2f5;
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
  font-size: 18px;
}

.member-info {
  flex: 1;
}

.member-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: 2px;
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

.member-points {
  font-size: $font-xs;
  color: $color-text-sub;
}

.empty-members {
  padding: $spacing-xl;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-text {
  font-size: $font-sm;
  color: $color-text-sub;
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}

.action-sheet {
  background: #ffffff;
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

    &--danger {
      .action-sheet__label {
        color: $color-danger;
      }
    }
  }

  &__icon {
    font-size: 24px;
    margin-right: $spacing-md;
    width: 32px;
    text-align: center;
  }

  &__content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__label {
    font-size: $font-base;
    font-weight: $weight-medium;
    color: $color-text;
  }

  &__desc {
    font-size: $font-xs;
    color: $color-text-sub;
  }
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

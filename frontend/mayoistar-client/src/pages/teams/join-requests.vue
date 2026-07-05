<template>
  <view class="page">
    <AppNavbar title="入队申请" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && requests.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="!canManage" class="empty-state">
        <EmptyState title="无权限" description="仅队长和管理员可查看入队申请" />
      </view>

      <view v-else-if="requests.length === 0" class="empty-state">
        <EmptyState title="暂无申请" description="当前没有待处理的入队申请" />
      </view>

      <view v-else class="request-list">
        <view v-for="req in requests" :key="req.requestId" class="request-card">
          <view class="request-avatar" @tap="goToProfile(req.userId)">
            <text class="request-avatar-text">{{ getNickname(req.userId).charAt(0) }}</text>
          </view>

          <view class="request-body" @tap="goToProfile(req.userId)">
            <text class="request-name">{{ getNickname(req.userId) }}</text>
            <text v-if="req.message" class="request-message">「{{ req.message }}」</text>
            <text class="request-time">{{ formatTime(req.createdAt) }}</text>
          </view>

          <view v-if="req.status === 'pending'" class="request-actions">
            <view class="btn-accept" @tap="onAccept(req)">
              <text class="btn-text">接受</text>
            </view>
            <view class="btn-reject" @tap="onReject(req)">
              <text class="btn-text btn-text--reject">拒绝</text>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 入队申请审批页
 *
 * 队长/管理员查看待处理入队申请，支持接受或拒绝。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { getTeamMembers, getTeamJoinRequests, handleJoinRequest } from '@/api/modules/teams'
import { getUserProfile } from '@/api/modules/social'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type TeamJoinRequest = components['schemas']['Social.TeamJoinRequest']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const requests = ref<TeamJoinRequest[]>([])
const members = ref<TeamMember[]>([])
const loading = ref(false)
const nicknameCache = ref<Record<string, string>>({})

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '10001')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

/** 获取申请人昵称 */
function getNickname(userId: string): string {
  return nicknameCache.value[userId] || `用户 ${userId}`
}

/** 格式化申请时间 */
function formatTime(isoStr: string): string {
  const date = new Date(isoStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载成员角色与入队申请 */
async function loadData() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [membersResult, requestsResult] = await Promise.all([
      getTeamMembers(teamId.value, 1, 100),
      getTeamJoinRequests(teamId.value),
    ])
    members.value = extractPageItems<TeamMember>(membersResult)
    requests.value = extractPageItems<TeamJoinRequest>(requestsResult).filter(
      (r) => r.status === 'pending',
    )

    if (!canManage.value) {
      uni.showToast({ title: '无管理权限', icon: 'none' })
      return
    }

    await loadNicknames(requests.value.map((r) => r.userId))
  } catch (error) {
    console.error('Failed to load join requests:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 批量加载申请人昵称 */
async function loadNicknames(userIds: string[]) {
  const uniqueIds = [...new Set(userIds)]
  await Promise.all(
    uniqueIds.map(async (id) => {
      try {
        const profile = await getUserProfile(id)
        nicknameCache.value[id] = profile.nickname || `用户 ${id}`
      } catch {
        nicknameCache.value[id] = `用户 ${id}`
      }
    }),
  )
}

/** 跳转用户主页 */
function goToProfile(userId: string) {
  uni.navigateTo({ url: `/pages/social/user-profile?id=${userId}&source=team` })
}

/** 接受入队申请 */
async function onAccept(req: TeamJoinRequest) {
  try {
    await handleJoinRequest(teamId.value, req.requestId, true)
    req.status = 'accepted'
    uni.showToast({ title: '已同意入队', icon: 'success' })
    requests.value = requests.value.filter((r) => r.requestId !== req.requestId)
  } catch (error) {
    console.error('Failed to accept request:', error)
    showBusinessError(error, '操作失败')
  }
}

/** 拒绝入队申请 */
function onReject(req: TeamJoinRequest) {
  uni.showModal({
    title: '拒绝申请',
    content: '确定拒绝该入队申请吗？',
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await handleJoinRequest(teamId.value, req.requestId, false)
        req.status = 'rejected'
        uni.showToast({ title: '已拒绝', icon: 'success' })
        requests.value = requests.value.filter((r) => r.requestId !== req.requestId)
      } catch (error) {
        console.error('Failed to reject request:', error)
        showBusinessError(error, '操作失败')
      }
    },
  })
}

onLoad((query) => {
  teamId.value = query?.teamId || ''
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

.request-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.request-card {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
}

.request-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-full;
  background: $color-secondary-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.request-avatar-text {
  font-size: 20px;
  color: $color-secondary;
  font-weight: $weight-semibold;
}

.request-body {
  flex: 1;
  min-width: 0;
}

.request-name {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.request-message {
  font-size: $font-sm;
  color: $color-text-sub;
  display: block;
  margin-top: 4px;
}

.request-time {
  font-size: $font-xs;
  color: $color-text-muted;
  display: block;
  margin-top: $spacing-xs;
}

.request-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  flex-shrink: 0;
}

.btn-accept,
.btn-reject {
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;

  &:active {
    opacity: 0.85;
  }
}

.btn-accept {
  background: $color-primary;
}

.btn-reject {
  background: rgba(0, 0, 0, 0.06);
}

.btn-text {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: var(--q-color-bg-card);

  &--reject {
    color: $color-text-sub;
  }
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

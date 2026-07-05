<template>
  <view class="page">
    <AppNavbar title="队内活动" />

    <view v-if="canManage" class="toolbar">
      <input
        v-model="activityTitle"
        class="toolbar-input"
        type="text"
        placeholder="输入活动标题"
        maxlength="50"
      />
      <view class="toolbar-btn" @tap="onCreate">
        <text class="toolbar-btn-text">创建活动</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && activities.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="activities.length === 0" class="empty-state">
        <EmptyState title="暂无活动" description="队长或管理员可以创建队内活动" />
      </view>

      <view v-else class="activity-list">
        <view
          v-for="activity in activities"
          :key="activity.activityId"
          class="activity-card"
          @tap="goToDetail(activity.activityId)"
        >
          <image
            v-if="activity.coverImage?.signedUrl"
            :src="activity.coverImage.signedUrl"
            class="activity-cover"
            mode="aspectFill"
          />
          <view v-else class="activity-cover activity-cover--placeholder">
            <text>📅</text>
          </view>

          <view class="activity-info">
            <text class="activity-title">{{ activity.title }}</text>
            <text class="activity-meta">
              {{ formatDate(activity.startAt) }} · {{ activity.registeredCount }}/{{
                activity.capacity
              }}
              人
            </text>
            <view class="activity-status">
              <text class="status-text">{{ getStatusLabel(activity.runtimeStatus) }}</text>
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
 * 队内活动页
 *
 * 展示队内活动列表；队长/管理员可快速创建活动。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { getTeamMembers, listTeamActivities, createTeamActivity } from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type ActivitySummary = components['schemas']['Activities.ActivitySummary']
type TeamMember = components['schemas']['Social.TeamMember']
type ActivityUpsertRequest = components['schemas']['Activities.ActivityUpsertRequest']

const teamId = ref('')
const activities = ref<ActivitySummary[]>([])
const members = ref<TeamMember[]>([])
const loading = ref(false)
const activityTitle = ref('')

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '10001')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

/** 获取活动状态文案 */
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    notStarted: '未开始',
    registering: '报名中',
    registrationClosed: '报名截止',
    ongoing: '进行中',
    ended: '已结束',
    takenDown: '已下架',
  }
  return map[status] || status
}

/** 格式化活动日期 */
function formatDate(isoStr: string): string {
  const date = new Date(isoStr)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

/** 构建最小活动创建请求 */
function buildMinimalActivityRequest(title: string): ActivityUpsertRequest {
  const start = new Date()
  start.setDate(start.getDate() + 7)
  start.setHours(10, 0, 0, 0)
  const end = new Date(start)
  end.setHours(12, 0, 0, 0)
  const deadline = new Date(start)
  deadline.setDate(deadline.getDate() - 1)

  return {
    title,
    capacity: 20,
    introduction: '队内活动',
    safetyNotice: '请注意活动安全',
    tags: [],
    startAt: start.toISOString(),
    endAt: end.toISOString(),
    registrationDeadline: deadline.toISOString(),
    location: {
      address: '待定',
      city: '北京',
      placeName: '待定地点',
      point: { latitude: 39.9042, longitude: 116.4074 },
    },
  }
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载成员角色与活动列表 */
async function loadData() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [membersResult, activitiesResult] = await Promise.all([
      getTeamMembers(teamId.value, 1, 100),
      listTeamActivities(teamId.value),
    ])
    members.value = extractPageItems<TeamMember>(membersResult)
    activities.value = extractPageItems<ActivitySummary>(activitiesResult)
  } catch (error) {
    console.error('Failed to load activities:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 创建队内活动 */
function onCreate() {
  const title = activityTitle.value.trim()
  if (!title) {
    uni.showToast({ title: '请输入活动标题', icon: 'none' })
    return
  }

  uni.showModal({
    title: '创建活动',
    content: `确定创建活动「${title}」吗？`,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await createTeamActivity(teamId.value, buildMinimalActivityRequest(title))
        uni.showToast({ title: '活动已创建', icon: 'success' })
        activityTitle.value = ''
        await loadData()
      } catch (error) {
        console.error('Failed to create activity:', error)
        showBusinessError(error, '创建失败')
      }
    },
  })
}

/** 跳转活动详情 */
function goToDetail(activityId: string) {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
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

.toolbar {
  background: #ffffff;
  padding: $spacing-md;
  display: flex;
  gap: $spacing-sm;
  border-bottom: 1px solid $color-border-light;
}

.toolbar-input {
  flex: 1;
  height: 40px;
  background: #f0f2f5;
  border-radius: $radius-md;
  padding: 0 $spacing-md;
  font-size: $font-base;
}

.toolbar-btn {
  background: $color-primary;
  border-radius: $radius-md;
  padding: 0 $spacing-lg;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.9;
  }
}

.toolbar-btn-text {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: #ffffff;
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

.activity-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.activity-card {
  background: #ffffff;
  border-radius: $radius-lg;
  overflow: hidden;
  display: flex;
  gap: $spacing-md;
  padding: $spacing-md;

  &:active {
    background: #fafafa;
  }
}

.activity-cover {
  width: 80px;
  height: 80px;
  border-radius: $radius-md;
  flex-shrink: 0;

  &--placeholder {
    background: #f0f2f5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
  }
}

.activity-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.activity-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  font-size: $font-xs;
  color: $color-text-sub;
  display: block;
  margin-top: 4px;
}

.activity-status {
  margin-top: $spacing-xs;
}

.status-text {
  font-size: $font-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 1px $spacing-xs;
  border-radius: $radius-sm;
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

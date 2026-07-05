<template>
  <view class="page">
    <AppNavbar title="群公告" />

    <view v-if="canManage" class="toolbar">
      <input
        v-model="draftContent"
        class="toolbar-input"
        type="text"
        placeholder="输入公告内容"
        maxlength="500"
      />
      <view class="toolbar-btn" @tap="onPublish">
        <text class="toolbar-btn-text">{{ editingId ? '保存' : '发布' }}</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && announcements.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="announcements.length === 0" class="empty-state">
        <EmptyState title="暂无公告" description="队长或管理员可以发布群公告" />
      </view>

      <view v-else class="announcement-list">
        <view
          v-for="item in announcements"
          :key="item.announcementId"
          class="announcement-card"
          @tap="onAnnouncementTap(item)"
        >
          <view class="announcement-header">
            <text class="announcement-time">{{ formatTime(item.publishedAt) }}</text>
            <view v-if="!item.readByCurrentUser" class="unread-badge">
              <text class="unread-text">未读</text>
            </view>
          </view>
          <text class="announcement-content">{{ item.content }}</text>
          <view v-if="canManage" class="announcement-actions" @tap.stop>
            <text class="action-link" @tap="onEdit(item)">编辑</text>
            <text class="action-link action-link--danger" @tap="onDelete(item)">删除</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- 公告操作菜单 -->
    <uni-popup ref="actionPopup" type="bottom" :safe-area="true">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">公告操作</text>
          <text class="action-sheet__close" @tap="closeActionMenu">×</text>
        </view>
        <view class="action-sheet__items">
          <view v-if="canManage" class="action-sheet__item" @tap="onEdit(selectedAnnouncement!)">
            <text class="action-sheet__icon">✏️</text>
            <text class="action-sheet__label">编辑公告</text>
          </view>
          <view v-if="canManage" class="action-sheet__item" @tap="onDelete(selectedAnnouncement!)">
            <text class="action-sheet__icon">🗑️</text>
            <text class="action-sheet__label">删除公告</text>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 群公告页
 *
 * 队长/管理员可发布、编辑、删除公告；成员点击标记已读。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { getTeamMembers } from '@/api/modules/teams'
import {
  listAnnouncements,
  publishAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  markAnnouncementRead,
} from '@/api/modules/teamChat'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type TeamAnnouncement = components['schemas']['Chat.TeamAnnouncement']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const announcements = ref<TeamAnnouncement[]>([])
const members = ref<TeamMember[]>([])
const loading = ref(false)
const draftContent = ref('')
const editingId = ref('')
const selectedAnnouncement = ref<TeamAnnouncement | null>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const actionPopup = ref<any>(null)

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '10001')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

/** 格式化发布时间 */
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

/** 加载成员角色与公告列表 */
async function loadData() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [membersResult, announcementsResult] = await Promise.all([
      getTeamMembers(teamId.value, 1, 100),
      listAnnouncements(teamId.value),
    ])
    members.value = extractPageItems<TeamMember>(membersResult)
    announcements.value = extractPageItems<TeamAnnouncement>(announcementsResult)
  } catch (error) {
    console.error('Failed to load announcements:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 点击公告：成员标记已读 */
async function onAnnouncementTap(item: TeamAnnouncement) {
  if (item.readByCurrentUser) return
  try {
    await markAnnouncementRead(teamId.value, item.announcementId)
    item.readByCurrentUser = true
  } catch (error) {
    console.error('Failed to mark read:', error)
    showBusinessError(error, '标记已读失败')
  }
}

/** 发布或更新公告 */
function onPublish() {
  const content = draftContent.value.trim()
  if (!content) {
    uni.showToast({ title: '请输入公告内容', icon: 'none' })
    return
  }

  const title = editingId.value ? '确认保存' : '确认发布'
  const modalContent = editingId.value ? '确定保存公告修改吗？' : '确定发布该群公告吗？'

  uni.showModal({
    title,
    content: modalContent,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        if (editingId.value) {
          await updateAnnouncement(teamId.value, editingId.value, content)
          uni.showToast({ title: '已更新', icon: 'success' })
        } else {
          await publishAnnouncement(teamId.value, content)
          uni.showToast({ title: '已发布', icon: 'success' })
        }
        draftContent.value = ''
        editingId.value = ''
        await loadData()
      } catch (error) {
        console.error('Failed to publish announcement:', error)
        showBusinessError(error, '操作失败')
      }
    },
  })
}

/** 编辑公告：回填输入框 */
function onEdit(item: TeamAnnouncement) {
  closeActionMenu()
  draftContent.value = item.content
  editingId.value = item.announcementId
  uni.pageScrollTo?.({ scrollTop: 0, duration: 200 })
}

/** 删除公告 */
function onDelete(item: TeamAnnouncement) {
  closeActionMenu()
  uni.showModal({
    title: '删除公告',
    content: '确定删除该群公告吗？',
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteAnnouncement(teamId.value, item.announcementId)
        uni.showToast({ title: '已删除', icon: 'success' })
        if (editingId.value === item.announcementId) {
          draftContent.value = ''
          editingId.value = ''
        }
        await loadData()
      } catch (error) {
        console.error('Failed to delete announcement:', error)
        showBusinessError(error, '删除失败')
      }
    },
  })
}

/** 关闭操作菜单 */
function closeActionMenu() {
  actionPopup.value?.close()
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

.announcement-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.announcement-card {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;

  &:active {
    background: #fafafa;
  }
}

.announcement-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xs;
}

.announcement-time {
  font-size: $font-xs;
  color: $color-text-muted;
}

.unread-badge {
  background: $color-danger;
  padding: 1px $spacing-xs;
  border-radius: $radius-sm;
}

.unread-text {
  font-size: 10px;
  color: #ffffff;
}

.announcement-content {
  font-size: $font-base;
  color: $color-text;
  line-height: 1.6;
  display: block;
}

.announcement-actions {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1px solid $color-border-light;
}

.action-link {
  font-size: $font-sm;
  color: $color-primary;

  &--danger {
    color: $color-danger;
  }
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

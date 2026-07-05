<template>
  <view class="page">
    <AppNavbar title="好友列表" />
    <scroll-view class="scroll-area" scroll-y @scrolltolower="loadMore">
      <!-- Loading State -->
      <view v-if="loading && friends.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <!-- Empty State -->
      <view v-else-if="!loading && friends.length === 0" class="empty-state">
        <EmptyState title="暂无好友" description="快去添加好友，开启社交之旅吧！" />
        <view class="empty-action" @tap="goToAddFriend">
          <text class="empty-action-text">添加好友</text>
        </view>
      </view>

      <!-- Friend List -->
      <view v-else class="friend-list">
        <view
          v-for="friend in friends"
          :key="friend.userId"
          class="friend-item"
          @tap="goToProfile(friend.userId)"
        >
          <view class="friend-avatar-wrapper">
            <image
              v-if="friend.avatar?.signedUrl"
              class="friend-avatar"
              :src="friend.avatar.signedUrl"
              mode="aspectFill"
            />
            <view v-else class="friend-avatar-placeholder">
              <text class="friend-avatar-text">{{ friend.nickname.charAt(0) }}</text>
            </view>
          </view>

          <view class="friend-info">
            <text class="friend-name">{{ friend.remark || friend.nickname }}</text>
            <text v-if="friend.remark" class="friend-nickname">{{ friend.nickname }}</text>
            <view v-if="friend.groupTags.length > 0" class="friend-tags">
              <text v-for="(tag, idx) in friend.groupTags" :key="idx" class="friend-tag">
                {{ tag }}
              </text>
            </view>
            <text class="friend-source">
              {{ friend.source === 'mutualFollow' ? '互关成为好友' : '手动添加' }}
            </text>
          </view>

          <view class="friend-actions" @tap.stop="showFriendMenu(friend)">
            <text class="friend-more-icon">⋯</text>
          </view>
        </view>

        <view v-if="loading" class="loading-more">
          <text class="loading-text">加载中...</text>
        </view>
        <view v-else-if="noMore" class="no-more">
          <text class="no-more-text">没有更多了</text>
        </view>
      </view>

      <view class="bottom-safe"></view>
    </scroll-view>

    <!-- Friend Action Sheet -->
    <uni-popup ref="friendMenuPopup" type="bottom" :safe-area="true" @mask-click="closeFriendMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">{{
            selectedFriend?.remark || selectedFriend?.nickname
          }}</text>
          <text class="action-sheet__close" @tap="closeFriendMenu">×</text>
        </view>
        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="onChatTap">
            <text class="action-sheet__icon">💬</text>
            <text class="action-sheet__label">发送消息</text>
          </view>
          <view class="action-sheet__item" @tap="onEditRemarkAndGroupsTap">
            <text class="action-sheet__icon">✏️</text>
            <text class="action-sheet__label">修改备注和分组</text>
          </view>
          <view class="action-sheet__item" @tap="onRemoveFriendTap">
            <text class="action-sheet__icon">🗑️</text>
            <text class="action-sheet__label action-sheet__label--danger">删除好友</text>
          </view>
          <view class="action-sheet__item" @tap="onBlockTap">
            <text class="action-sheet__icon">🚫</text>
            <text class="action-sheet__label action-sheet__label--danger">加入黑名单</text>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- Edit Remark & Groups Sheet -->
    <uni-popup ref="editPopup" type="bottom" :safe-area="true" @mask-click="closeEditPopup">
      <view class="edit-sheet">
        <view class="edit-sheet__header">
          <text class="edit-sheet__title">修改备注和分组</text>
          <text class="edit-sheet__close" @tap="closeEditPopup">×</text>
        </view>

        <view class="edit-sheet__body">
          <text class="edit-sheet__label">备注名</text>
          <input
            v-model="editRemark"
            class="edit-sheet__input"
            type="text"
            placeholder="留空则显示昵称"
          />

          <text class="edit-sheet__label">分组标签</text>
          <view class="edit-sheet__tags">
            <view
              v-for="tag in GROUP_TAG_OPTIONS"
              :key="tag"
              class="edit-sheet__tag"
              :class="{ 'edit-sheet__tag--active': editGroupTags.includes(tag) }"
              @tap="toggleGroupTag(tag)"
            >
              <text class="edit-sheet__tag-text">{{ tag }}</text>
            </view>
          </view>

          <input
            v-model="editCustomTags"
            class="edit-sheet__input"
            type="text"
            placeholder="自定义分组，逗号分隔"
          />
        </view>

        <view class="edit-sheet__footer">
          <view class="edit-sheet__btn edit-sheet__btn--ghost" @tap="closeEditPopup">
            <text>取消</text>
          </view>
          <view class="edit-sheet__btn edit-sheet__btn--primary" @tap="saveRemarkAndGroups">
            <text>保存</text>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 好友列表页面
 *
 * 展示所有好友，支持备注修改、删除好友、加入黑名单等操作
 */
import { ref, onMounted, nextTick } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { getFriends, removeFriend, updateFriendRemark, blockUser } from '@/api/modules/social'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import type { components } from '@/api/types/schema'

type FriendItem = components['schemas']['Social.FriendItem']

const friends = ref<FriendItem[]>([])
const loading = ref(false)
const noMore = ref(false)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const friendMenuPopup = ref<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const editPopup = ref<any>(null)
const selectedFriend = ref<FriendItem | null>(null)

const GROUP_TAG_OPTIONS = ['兴趣', '户外', '同事', '家人', '活动']
const editRemark = ref('')
const editGroupTags = ref<string[]>([])
const editCustomTags = ref('')

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const result = await getFriends()
    // Handle both paginated and plain array responses (mock compat)
    const items = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as FriendItem[]) ?? [])
    friends.value = items
    noMore.value = true
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function loadMore() {
  // Pagination not needed for now, all friends loaded at once
}

function goToProfile(userId: string) {
  uni.navigateTo({ url: `/pages/social/user-profile?id=${userId}` })
}

function goToAddFriend() {
  uni.navigateTo({ url: '/pages/social/add-friend' })
}

function showFriendMenu(friend: FriendItem) {
  selectedFriend.value = friend
  friendMenuPopup.value?.open()
}

function closeFriendMenu() {
  friendMenuPopup.value?.close()
}

/** 关闭所有弹层并收起键盘，避免遮罩残留导致导航栏无法点击 */
function closeAllPopups() {
  uni.hideKeyboard()
  friendMenuPopup.value?.close()
  editPopup.value?.close()
}

async function onChatTap() {
  closeFriendMenu()
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  try {
    const conversationId = await resolveFriendConversationId(friend.userId)
    if (conversationId) {
      uni.navigateTo({
        url: `/pages/messages/chat?conversationId=${conversationId}&kind=friend`,
      })
      return
    }
  } catch {
    // fallback below
  }
  uni.navigateTo({
    url: `/pages/messages/chat?targetUserId=${friend.userId}&kind=friend`,
  })
}

function parseGroupTags(input: string): string[] {
  return input
    .split(/[,，、\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function onEditRemarkAndGroupsTap() {
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  editRemark.value = friend.remark || ''
  editGroupTags.value = [...friend.groupTags]
  editCustomTags.value = ''
  closeFriendMenu()
  editPopup.value?.open()
}

function closeEditPopup() {
  uni.hideKeyboard()
  editPopup.value?.close()
}

function toggleGroupTag(tag: string) {
  if (editGroupTags.value.includes(tag)) {
    editGroupTags.value = editGroupTags.value.filter((t) => t !== tag)
  } else {
    editGroupTags.value = [...editGroupTags.value, tag]
  }
}

async function saveRemarkAndGroups() {
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  const remark = editRemark.value.trim()
  const customTags = parseGroupTags(editCustomTags.value)
  const groupTags = [...new Set([...editGroupTags.value, ...customTags])]

  try {
    const updated = (await updateFriendRemark(
      friend.userId,
      remark || undefined,
      groupTags,
    )) as FriendItem
    const target = friends.value.find((f) => f.userId === friend.userId)
    if (target) {
      target.remark = updated.remark
      target.groupTags = updated.groupTags
    }
    closeEditPopup()
    await nextTick()
    closeAllPopups()
    uni.showToast({ title: '已更新备注和分组', icon: 'success' })
  } catch {
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

function onRemoveFriendTap() {
  closeFriendMenu()
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  uni.showModal({
    title: '删除好友',
    content: `确定要删除好友「${friend.remark || friend.nickname}」吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await removeFriend(friend.userId)
          friends.value = friends.value.filter((f) => f.userId !== friend.userId)
          uni.showToast({ title: '已删除', icon: 'success' })
        } catch {
          uni.showToast({ title: '删除失败', icon: 'none' })
        }
      }
    },
  })
}

function onBlockTap() {
  closeFriendMenu()
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  uni.showModal({
    title: '加入黑名单',
    content: `确定要将「${friend.remark || friend.nickname}」加入黑名单吗？加入后将解除好友关系。`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await blockUser(friend.userId)
          friends.value = friends.value.filter((f) => f.userId !== friend.userId)
          uni.showToast({ title: '已加入黑名单', icon: 'success' })
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    },
  })
}

onMounted(() => {
  loadData()
})

onShow(() => {
  closeAllPopups()
  loadData()
})

onHide(() => {
  closeAllPopups()
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

/* ===== Loading & Empty ===== */
.loading-state {
  display: flex;
  justify-content: center;
  padding-top: 200rpx;
}

.loading-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.empty-state {
  padding: $spacing-2xl;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-action {
  margin-top: $spacing-xl;
  padding: $spacing-md $spacing-2xl;
  background: $color-primary;
  border-radius: $radius-full;

  &:active {
    opacity: 0.9;
  }
}

.empty-action-text {
  color: var(--q-color-bg-card);
  font-size: $font-base;
  font-weight: $weight-medium;
}

/* ===== Friend List ===== */
.friend-list {
  padding: $spacing-sm 0;
}

.friend-item {
  display: flex;
  align-items: center;
  padding: $spacing-lg $spacing-xl;
  background: var(--q-color-bg-card);
  margin: 0 $spacing-lg $spacing-sm;
  border-radius: $radius-lg;
  box-shadow: $shadow-xs;
  transition: all 0.2s ease;

  &:active {
    background: rgba(0, 0, 0, 0.02);
    transform: scale(0.98);
  }
}

.friend-avatar-wrapper {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  margin-right: $spacing-md;
}

.friend-avatar {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  border: 2px solid var(--q-color-bg-card);
  box-shadow: $shadow-sm;
}

.friend-avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.friend-avatar-text {
  font-size: 22px;
  color: $color-primary;
  font-weight: $weight-semibold;
}

.friend-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.friend-name {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.friend-nickname {
  font-size: $font-xs;
  color: $color-text-muted;
  display: block;
  margin-top: 2px;
}

.friend-tags {
  display: flex;
  gap: $spacing-xs;
  margin-top: $spacing-xs;
  flex-wrap: wrap;
}

.friend-tag {
  font-size: $font-xs;
  padding: 1px $spacing-xs;
  background: $color-primary-light;
  color: $color-primary-dark;
  border-radius: $radius-full;
}

.friend-source {
  font-size: $font-xs;
  color: $color-text-muted;
  display: block;
  margin-top: 2px;
}

.friend-actions {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: $spacing-sm;

  &:active {
    opacity: 0.7;
  }
}

.friend-more-icon {
  font-size: 20px;
  color: $color-text-muted;
}

/* ===== Loading More ===== */
.loading-more,
.no-more {
  display: flex;
  justify-content: center;
  padding: $spacing-xl;
}

.no-more-text {
  font-size: $font-xs;
  color: $color-text-muted;
}

.bottom-safe {
  height: calc($tabbar-height + $safe-bottom);
}

/* ===== Action Sheet ===== */
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

    &:active {
      opacity: 0.7;
    }
  }

  &__items {
    padding: $spacing-md 0;
  }

  &__item {
    display: flex;
    align-items: center;
    padding: $spacing-lg $spacing-xl;
    transition: background 0.2s ease;

    &:active {
      background: rgba(0, 0, 0, 0.03);
    }
  }

  &__icon {
    font-size: 22px;
    margin-right: $spacing-md;
    width: 28px;
    text-align: center;
  }

  &__label {
    font-size: $font-base;
    font-weight: $weight-medium;
    color: $color-text;

    &--danger {
      color: $color-danger;
    }
  }
}

/* ===== Edit Remark & Groups Sheet ===== */
.edit-sheet {
  background: var(--q-color-bg-card);
  border-radius: $radius-xl $radius-xl 0 0;
  padding-bottom: calc($spacing-md + $safe-bottom);

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

  &__body {
    padding: $spacing-lg $spacing-xl;
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__label {
    font-size: $font-sm;
    font-weight: $weight-medium;
    color: $color-text-sub;
    margin-top: $spacing-sm;
  }

  &__input {
    background: var(--q-color-bg-soft);
    border-radius: $radius-md;
    padding: $spacing-md;
    font-size: $font-base;
    color: $color-text;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
    margin: $spacing-xs 0;
  }

  &__tag {
    padding: $spacing-xs $spacing-md;
    border-radius: $radius-full;
    background: var(--q-color-bg-soft);
    border: 1px solid transparent;

    &--active {
      background: $color-primary-light;
      border-color: $color-primary;
    }
  }

  &__tag-text {
    font-size: $font-sm;
    color: $color-text;
  }

  &__footer {
    display: flex;
    gap: $spacing-md;
    padding: 0 $spacing-xl $spacing-md;
  }

  &__btn {
    flex: 1;
    padding: $spacing-md;
    border-radius: $radius-full;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: $font-base;
    font-weight: $weight-medium;

    &--ghost {
      background: var(--q-color-bg-soft);
      color: $color-text;
    }

    &--primary {
      background: $color-primary;
      color: var(--q-color-bg-card);
    }
  }
}
</style>

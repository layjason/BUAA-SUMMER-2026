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
          <UserAvatar
            size="md"
            :avatar-url="friend.avatar?.signedUrl || ''"
            :name="friend.remark || friend.nickname"
            :user-id="friend.userId"
          />

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
import UserAvatar from '@/components/base/UserAvatar.vue'
import { getFriends, removeFriend, updateFriendRemark, blockUser } from '@/api/modules/social'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import type { components } from '@/api/types/schema'

type FriendItem = components['schemas']['Social.FriendItem']
type FriendItemLike = Omit<FriendItem, 'groupTags'> & {
  groupTags?: string[] | null
}

interface PopupRef {
  open: () => void
  close: () => void
}

const friends = ref<FriendItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const currentPage = ref(1)
const pageSize = 20

const friendMenuPopup = ref<PopupRef | null>(null)
const editPopup = ref<PopupRef | null>(null)
const selectedFriend = ref<FriendItem | null>(null)

const GROUP_TAG_OPTIONS = ['兴趣', '户外', '同事', '家人', '活动']
const editRemark = ref('')
const editGroupTags = ref<string[]>([])
const editCustomTags = ref('')

/**
 * 归一化好友条目。
 *
 * 前置条件：item 来自好友列表或备注更新接口，运行时可能缺失可选数组字段。
 * 后置条件：返回 groupTags 一定为字符串数组的 FriendItem，避免模板读取 length 时白屏。
 * 不变量：不修改接口返回的原对象。
 */
function normalizeFriendItem(item: FriendItemLike): FriendItem {
  return {
    ...item,
    groupTags: Array.isArray(item.groupTags)
      ? item.groupTags.filter((tag): tag is string => typeof tag === 'string')
      : [],
  }
}

/**
 * 解析好友列表接口结果。
 *
 * 前置条件：result 可能是分页结构，也可能是 Mock 兼容数组。
 * 后置条件：返回统一分页信息和已归一化好友数组。
 * 不变量：仅做展示层容错，不改变分页语义。
 */
function normalizeFriendListResult(
  result: unknown,
  fallbackPage: number,
): { items: FriendItem[]; page: number; totalPages: number } {
  if (Array.isArray(result)) {
    return {
      items: (result as FriendItemLike[]).map(normalizeFriendItem),
      page: fallbackPage,
      totalPages: fallbackPage,
    }
  }

  const pageResult = result as {
    items?: FriendItemLike[]
    page?: number
    totalPages?: number
  }
  return {
    items: (pageResult.items ?? []).map(normalizeFriendItem),
    page: pageResult.page ?? fallbackPage,
    totalPages: pageResult.totalPages ?? pageResult.page ?? fallbackPage,
  }
}

/** 加载好友列表数据 */
async function loadData(page = 1, append = false): Promise<void> {
  if (loading.value) return
  loading.value = !append
  try {
    const result = await getFriends(page, pageSize)
    const normalized = normalizeFriendListResult(result, page)
    friends.value = append ? [...friends.value, ...normalized.items] : normalized.items
    currentPage.value = normalized.page
    noMore.value = currentPage.value >= normalized.totalPages
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 加载下一页好友 */
async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || noMore.value) return
  loadingMore.value = true
  try {
    await loadData(currentPage.value + 1, true)
  } finally {
    loadingMore.value = false
  }
}

/** 打开好友公开资料页 */
function goToProfile(userId: string): void {
  uni.navigateTo({ url: `/pages/social/user-profile?id=${userId}` })
}

/** 打开添加好友页 */
function goToAddFriend(): void {
  uni.navigateTo({ url: '/pages/social/add-friend' })
}

/** 打开好友操作菜单 */
function showFriendMenu(friend: FriendItem): void {
  selectedFriend.value = friend
  friendMenuPopup.value?.open()
}

/** 关闭好友操作菜单 */
function closeFriendMenu(): void {
  friendMenuPopup.value?.close()
}

/** 关闭所有弹层并收起键盘，避免遮罩残留导致导航栏无法点击 */
function closeAllPopups() {
  uni.hideKeyboard()
  friendMenuPopup.value?.close()
  editPopup.value?.close()
}

/** 从好友菜单进入单聊 */
async function onChatTap(): Promise<void> {
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

/** 解析用户输入的分组标签 */
function parseGroupTags(input: string): string[] {
  return input
    .split(/[,，、\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

/** 打开备注和分组编辑面板 */
function onEditRemarkAndGroupsTap(): void {
  if (!selectedFriend.value) return
  const friend = selectedFriend.value
  editRemark.value = friend.remark || ''
  editGroupTags.value = [...normalizeFriendItem(friend).groupTags]
  editCustomTags.value = ''
  closeFriendMenu()
  editPopup.value?.open()
}

/** 关闭备注和分组编辑面板 */
function closeEditPopup(): void {
  uni.hideKeyboard()
  editPopup.value?.close()
}

/** 切换预设分组标签 */
function toggleGroupTag(tag: string): void {
  if (editGroupTags.value.includes(tag)) {
    editGroupTags.value = editGroupTags.value.filter((t) => t !== tag)
  } else {
    editGroupTags.value = [...editGroupTags.value, tag]
  }
}

/** 保存好友备注和分组 */
async function saveRemarkAndGroups(): Promise<void> {
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
    )) as FriendItemLike
    const target = friends.value.find((f) => f.userId === friend.userId)
    if (target) {
      target.remark = updated.remark
      target.groupTags = normalizeFriendItem({
        ...updated,
        groupTags: updated.groupTags ?? groupTags,
      }).groupTags
    }
    closeEditPopup()
    await nextTick()
    closeAllPopups()
    uni.showToast({ title: '已更新备注和分组', icon: 'success' })
  } catch {
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

/** 从好友菜单发起删除好友确认 */
function onRemoveFriendTap(): void {
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

/** 从好友菜单发起加入黑名单确认 */
function onBlockTap(): void {
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

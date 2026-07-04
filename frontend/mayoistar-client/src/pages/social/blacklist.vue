<template>
  <view class="page">
    <AppNavbar title="黑名单" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && items.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="items.length === 0" class="empty-state">
        <EmptyState title="黑名单为空" description="你还没有屏蔽任何用户" />
      </view>

      <view v-else class="list">
        <view v-for="item in items" :key="item.userId" class="list-item">
          <view class="item-avatar">
            <image
              v-if="item.avatar?.signedUrl"
              :src="item.avatar.signedUrl"
              class="item-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="item-avatar-placeholder">👤</text>
          </view>

          <view class="item-info">
            <text class="item-name">{{ item.nickname }}</text>
            <text class="item-time">屏蔽于 {{ formatDate(item.blockedAt) }}</text>
          </view>

          <view class="item-actions">
            <view class="item-action" @tap="trySendFriendRequest(item)">
              <text class="action-text action-text--muted">测试申请</text>
            </view>
            <view class="item-action" @tap="confirmUnblock(item)">
              <text class="action-text">解除屏蔽</text>
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
 * 黑名单页面
 *
 * 展示已屏蔽用户列表，支持解除屏蔽
 */
import { ref, onMounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import { getBlacklist, unblockUser, sendFriendRequest } from '@/api/modules/social'
import { getErrorMessage } from '@/utils/error'
import type { components } from '@/api/types/schema'

type BlacklistItem = components['schemas']['Social.BlacklistItem']

const loading = ref(false)
const items = ref<BlacklistItem[]>([])

function formatDate(iso: string): string {
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

async function loadData() {
  loading.value = true
  try {
    const result = await getBlacklist()
    const arr = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as BlacklistItem[]) ?? [])
    items.value = arr
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 验收：黑名单用户不可再次发送好友申请 */
async function trySendFriendRequest(item: BlacklistItem) {
  try {
    await sendFriendRequest(item.userId, '测试黑名单拦截')
    uni.showModal({
      title: '验收失败',
      content: '黑名单用户仍能收到好友申请，请检查后端/mock。',
      showCancel: false,
    })
  } catch (error) {
    const code = (error as { code?: number }).code ?? 0
    const message = getErrorMessage(code, (error as Error).message || '无法发送好友申请')
    uni.showModal({
      title: '黑名单生效',
      content: `向「${item.nickname}」发送申请被拒绝：${message}`,
      showCancel: false,
    })
  }
}

function confirmUnblock(item: BlacklistItem) {
  uni.showModal({
    title: '解除屏蔽',
    content: `确定要解除对「${item.nickname}」的屏蔽吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await unblockUser(item.userId)
          uni.showToast({ title: '已解除屏蔽', icon: 'success' })
          items.value = items.value.filter((i) => i.userId !== item.userId)
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

.list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.list-item {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.item-avatar {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.item-avatar-image {
  width: 100%;
  height: 100%;
}

.item-avatar-placeholder {
  font-size: 20px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
  display: block;
  margin-bottom: 2px;
}

.item-time {
  font-size: $font-xs;
  color: $color-text-sub;
}

.item-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $spacing-xs;
  flex-shrink: 0;
}

.item-action {
  flex-shrink: 0;
}

.action-text {
  font-size: $font-sm;
  color: $color-primary;
  font-weight: $weight-medium;

  &--muted {
    color: $color-text-muted;
    font-size: $font-xs;
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

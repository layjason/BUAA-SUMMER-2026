<template>
  <view class="forward-overlay">
    <view class="forward-header">
      <view class="forward-back" @tap="emitClose">
        <text class="forward-back-icon">‹</text>
      </view>
      <text class="forward-title">选择转发对象</text>
      <view class="forward-header-spacer"></view>
    </view>

    <scroll-view class="forward-list" scroll-y>
      <view v-if="loading" class="forward-state">
        <text class="forward-state-text">加载中...</text>
      </view>

      <view v-else-if="targets.length === 0" class="forward-state">
        <text class="forward-state-text">没有其他会话可转发</text>
      </view>

      <view v-else>
        <view
          v-for="item in targets"
          :key="item.conversationId"
          class="forward-item"
          @tap="toggleSelection(item.conversationId)"
        >
          <view
            class="forward-checkbox"
            :class="{ 'forward-checkbox--checked': isSelected(item.conversationId) }"
          >
            <text v-if="isSelected(item.conversationId)" class="forward-checkbox-mark">✓</text>
          </view>

          <UserAvatar size="sm" :avatar-url="item.avatarUrl || ''" :name="avatarLabel(item)" />

          <text class="forward-name">{{ item.title }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="forward-footer">
      <view
        class="forward-submit"
        :class="{ 'forward-submit--disabled': selectedIds.length === 0 || submitting }"
        @tap="onConfirm"
      >
        <text class="forward-submit-text">
          {{
            submitting
              ? '转发中...'
              : selectedIds.length > 0
                ? `转发(${selectedIds.length})`
                : '转发'
          }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 多选转发目标弹层
 *
 * 展示好友单聊与小队群聊列表，支持并行勾选多个转发目标。
 */
import { ref, onMounted } from 'vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import { fetchForwardTargets, type ForwardTargetItem } from '@/utils/forward-targets'

interface Props {
  /** 当前会话，转发列表中排除 */
  excludeConversationId: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: [conversationIds: string[]]
}>()

const loading = ref(true)
const submitting = ref(false)
const targets = ref<ForwardTargetItem[]>([])
const selectedIds = ref<string[]>([])

/**
 * 加载可转发会话列表
 */
async function loadTargets() {
  loading.value = true
  try {
    targets.value = await fetchForwardTargets(props.excludeConversationId)
  } catch {
    uni.showToast({ title: '加载会话失败', icon: 'none' })
    targets.value = []
  } finally {
    loading.value = false
  }
}

function isSelected(conversationId: string): boolean {
  return selectedIds.value.includes(conversationId)
}

function toggleSelection(conversationId: string) {
  if (isSelected(conversationId)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== conversationId)
  } else {
    selectedIds.value = [...selectedIds.value, conversationId]
  }
}

/** 头像占位首字：标题已含人数时去掉末尾 (N) */
function avatarLabel(item: ForwardTargetItem): string {
  return item.title.replace(/\s*\(\d+\)\s*$/, '').trim() || item.title
}

function emitClose() {
  emit('close')
}

function onConfirm() {
  if (submitting.value || selectedIds.value.length === 0) return
  submitting.value = true
  emit('confirm', [...selectedIds.value])
  submitting.value = false
}

onMounted(() => {
  loadTargets()
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.forward-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: $color-bg;
  display: flex;
  flex-direction: column;
}

.forward-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc($spacing-sm + $safe-top) $spacing-md $spacing-sm;
  border-bottom: 1px solid $color-border-light;
  background: rgba(255, 255, 255, 0.96);
}

.forward-back {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.forward-back-icon {
  font-size: 32px;
  color: $color-text;
  line-height: 1;
  font-weight: $weight-light;
}

.forward-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

.forward-header-spacer {
  width: 44px;
}

.forward-list {
  flex: 1;
  height: 0;
}

.forward-state {
  padding: $spacing-2xl;
  text-align: center;
}

.forward-state-text {
  font-size: $font-sm;
  color: $color-text-muted;
}

.forward-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid $color-border-light;
}

.forward-checkbox {
  width: 22px;
  height: 22px;
  border-radius: $radius-full;
  border: 2px solid $color-border;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--q-color-bg-card);

  &--checked {
    border-color: $color-primary;
    background: $gradient-primary;
  }
}

.forward-checkbox-mark {
  font-size: 12px;
  color: var(--q-color-bg-card);
  font-weight: $weight-bold;
  line-height: 1;
}

.forward-avatar {
  width: 44px;
  height: 44px;
  border-radius: $radius-md;
  overflow: hidden;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.forward-avatar-image {
  width: 100%;
  height: 100%;
}

.forward-avatar-placeholder {
  font-size: 22px;
}

.forward-name {
  flex: 1;
  min-width: 0;
  font-size: $font-base;
  color: $color-text;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.forward-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding: $spacing-md $spacing-lg calc($spacing-md + $safe-bottom);
  border-top: 1px solid $color-border-light;
  background: rgba(255, 255, 255, 0.96);
}

.forward-submit {
  min-width: 96px;
  padding: $spacing-sm $spacing-xl;
  border-radius: $radius-full;
  background: $gradient-primary;
  box-shadow: 0 6px 14px rgba(22, 160, 133, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;

  &--disabled {
    background: $color-bg-soft;
    box-shadow: none;

    .forward-submit-text {
      color: $color-text-muted;
    }
  }
}

.forward-submit-text {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: var(--q-color-bg-card);
}
</style>

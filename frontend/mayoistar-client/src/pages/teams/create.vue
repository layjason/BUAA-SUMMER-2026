<template>
  <view class="page">
    <AppNavbar title="创建小队" />

    <scroll-view class="scroll-area" scroll-y>
      <view class="form">
        <view class="avatar-section" @tap="pickAvatar">
          <image v-if="avatarPreview" :src="avatarPreview" class="avatar-image" mode="aspectFill" />
          <view v-else class="avatar-placeholder">
            <text class="avatar-placeholder-text">上传小队头像</text>
          </view>
        </view>

        <!-- Team Name -->
        <view class="form-group">
          <text class="form-label">小队名称 *</text>
          <input
            v-model="form.name"
            class="form-input"
            type="text"
            placeholder="输入小队名称"
            maxlength="30"
          />
          <text class="form-hint">{{ form.name.length }}/30</text>
        </view>

        <!-- Description -->
        <view class="form-group">
          <text class="form-label">小队简介</text>
          <textarea
            v-model="form.description"
            class="form-textarea"
            placeholder="介绍你的小队"
            maxlength="200"
          />
          <text class="form-hint">{{ form.description.length }}/200</text>
        </view>

        <!-- Capacity -->
        <view class="form-group">
          <text class="form-label">人数上限 *</text>
          <view class="capacity-selector">
            <view class="capacity-btn" @tap="decreaseCapacity">
              <text class="capacity-btn-text">-</text>
            </view>
            <text class="capacity-value">{{ form.capacity }}</text>
            <view class="capacity-btn" @tap="increaseCapacity">
              <text class="capacity-btn-text">+</text>
            </view>
          </view>
          <text class="form-hint">范围：2-50人</text>
        </view>

        <!-- Join Mode -->
        <view class="form-group">
          <text class="form-label">加入方式 *</text>
          <view class="radio-group">
            <view
              class="radio-item"
              :class="{ 'radio-item--active': form.joinMode === 'publicJoin' }"
              @tap="form.joinMode = 'publicJoin'"
            >
              <text class="radio-label">自由加入</text>
              <text class="radio-desc">任何人可直接加入</text>
            </view>
            <view
              class="radio-item"
              :class="{ 'radio-item--active': form.joinMode === 'approvalRequired' }"
              @tap="form.joinMode = 'approvalRequired'"
            >
              <text class="radio-label">需审批</text>
              <text class="radio-desc">需要队长/管理员审批</text>
            </view>
          </view>
        </view>

        <!-- Tags -->
        <view class="form-group">
          <text class="form-label">兴趣标签</text>
          <input
            v-model="tagInput"
            class="form-input"
            type="text"
            placeholder="输入标签后按回车添加"
            @confirm="addTag"
          />
          <view v-if="form.tags.length > 0" class="tags-list">
            <view v-for="(tag, idx) in form.tags" :key="idx" class="tag">
              <text class="tag-text">{{ tag }}</text>
              <text class="tag-remove" @tap="removeTag(idx)">×</text>
            </view>
          </view>
        </view>

        <!-- Submit Button -->
        <view class="submit-area">
          <view class="submit-btn" :class="{ 'submit-btn--disabled': !canSubmit }" @tap="submit">
            <text class="submit-btn-text">{{ submitting ? '创建中...' : '创建小队' }}</text>
          </view>
        </view>

        <view class="bottom-padding"></view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 创建小队页面
 */
import { ref, computed, reactive } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import { api } from '@/api'
import { createTeam } from '@/api/modules/teams'
import type { components } from '@/api/types/schema'

type TeamCreateRequest = components['schemas']['Social.TeamCreateRequest']

const tagInput = ref('')
const submitting = ref(false)
const avatarPreview = ref('')
const avatarMediaId = ref('')

const form = reactive({
  name: '',
  description: '',
  capacity: 10,
  joinMode: 'publicJoin' as TeamCreateRequest['joinMode'],
  tags: [] as string[],
})

const canSubmit = computed(() => form.name.trim().length > 0 && !submitting.value)

function increaseCapacity() {
  if (form.capacity < 50) form.capacity++
}

function decreaseCapacity() {
  if (form.capacity > 2) form.capacity--
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !form.tags.includes(tag) && form.tags.length < 10) {
    form.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(idx: number) {
  form.tags.splice(idx, 1)
}

async function pickAvatar() {
  try {
    const res = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    const tempPath = res.tempFilePaths[0]
    if (!tempPath) return

    avatarPreview.value = tempPath
    try {
      const result = (await api.upload('/identity/media/avatar', tempPath)) as {
        mediaId: string
        signedUrl: string
      }
      avatarMediaId.value = result.mediaId
      avatarPreview.value = result.signedUrl
    } catch {
      uni.showToast({ title: '头像上传失败', icon: 'none' })
    }
  } catch {
    /* 用户取消 */
  }
}

async function submit() {
  if (!canSubmit.value) return

  submitting.value = true
  try {
    const data: TeamCreateRequest = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      capacity: form.capacity,
      joinMode: form.joinMode,
      tags: form.tags,
      avatarMediaId: avatarMediaId.value || undefined,
    }

    const team = await createTeam(data)
    const created = team as { teamId: string; chatId?: string }
    uni.showModal({
      title: '创建成功',
      content: '小队已创建，是否立即进入群聊？',
      confirmText: '进入群聊',
      cancelText: '查看详情',
      success: (res) => {
        if (res.confirm && created.chatId) {
          const url =
            `/pages/messages/chat` +
            `?conversationId=${created.chatId}&kind=team&teamId=${created.teamId}`
          uni.redirectTo({ url })
        } else {
          uni.redirectTo({ url: `/pages/teams/detail?teamId=${created.teamId}` })
        }
      },
    })
  } catch (error) {
    console.error('Failed to create team:', error)
    uni.showToast({ title: '创建失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
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

.form {
  padding: $spacing-lg;
}

.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: $spacing-xl;
}

.avatar-image,
.avatar-placeholder {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-xl;
}

.avatar-placeholder {
  background: $color-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-placeholder-text {
  font-size: $font-sm;
  color: $color-primary-dark;
}

.form-group {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
}

.form-label {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  margin-bottom: $spacing-sm;
  display: block;
}

.form-input {
  width: 100%;
  font-size: $font-base;
  padding: $spacing-sm;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: #ffffff;
}

.form-textarea {
  width: 100%;
  font-size: $font-base;
  padding: $spacing-sm;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: #ffffff;
  height: 100px;
}

.form-hint {
  font-size: $font-xs;
  color: $color-text-muted;
  margin-top: $spacing-xs;
  display: block;
}

.capacity-selector {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

.capacity-btn {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    background: #e0e2e5;
  }
}

.capacity-btn-text {
  font-size: 18px;
  color: $color-text;
  font-weight: $weight-bold;
}

.capacity-value {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $color-primary;
  min-width: 40px;
  text-align: center;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.radio-item {
  padding: $spacing-md;
  border: 2px solid $color-border;
  border-radius: $radius-md;
  transition: all 0.2s ease;

  &--active {
    border-color: $color-primary;
    background: $color-primary-light;
  }
}

.radio-label {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
  display: block;
  margin-bottom: 2px;
}

.radio-desc {
  font-size: $font-xs;
  color: $color-text-sub;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
}

.tag {
  background: $color-primary-light;
  padding: $spacing-xs $spacing-sm;
  border-radius: $radius-sm;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.tag-text {
  font-size: $font-sm;
  color: $color-primary;
}

.tag-remove {
  font-size: 14px;
  color: $color-text-sub;
  cursor: pointer;

  &:active {
    opacity: 0.7;
  }
}

.submit-area {
  padding: $spacing-xl 0;
}

.submit-btn {
  background: $color-primary;
  padding: $spacing-md;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;

  &:active {
    transform: scale(0.98);
  }

  &--disabled {
    opacity: 0.5;
  }
}

.submit-btn-text {
  color: #ffffff;
  font-size: $font-lg;
  font-weight: $weight-semibold;
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

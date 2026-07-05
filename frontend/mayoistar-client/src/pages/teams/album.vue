<template>
  <view class="page">
    <AppNavbar title="小队相册" />

    <view class="toolbar">
      <view class="toolbar-btn" @tap="onUpload">
        <text class="toolbar-btn-text">上传图片</text>
      </view>
      <view
        v-if="canManage && selectedIds.length > 0"
        class="toolbar-btn toolbar-btn--danger"
        @tap="onDeleteSelected"
      >
        <text class="toolbar-btn-text">删除 ({{ selectedIds.length }})</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && images.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="images.length === 0" class="empty-state">
        <EmptyState title="相册为空" description="上传图片记录小队精彩瞬间" />
      </view>

      <view v-else class="album-grid">
        <view
          v-for="image in images"
          :key="image.mediaId"
          class="album-item"
          :class="{ 'album-item--selected': isSelected(image.mediaId) }"
          @tap="onImageTap(image)"
          @longpress="canManage ? toggleSelect(image.mediaId) : undefined"
        >
          <image
            v-if="image.signedUrl"
            :src="image.signedUrl"
            class="album-image"
            mode="aspectFill"
          />
          <view v-else class="album-placeholder">
            <text>🖼️</text>
          </view>
          <view
            v-if="canManage"
            class="album-check"
            :class="{ 'album-check--active': isSelected(image.mediaId) }"
          >
            <text v-if="isSelected(image.mediaId)" class="album-check-mark">✓</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队相册页
 *
 * 网格展示相册图片，支持上传；队长/管理员可删除选中图片。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { getTeamMembers } from '@/api/modules/teams'
import {
  listTeamAlbumImages,
  uploadTeamAlbumImage,
  deleteTeamAlbumImages,
} from '@/api/modules/teamChat'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const images = ref<MediaFile[]>([])
const members = ref<TeamMember[]>([])
const loading = ref(false)
const uploading = ref(false)
const selectedIds = ref<string[]>([])

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '10001')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

/** 判断是否已选中 */
function isSelected(mediaId: string): boolean {
  return selectedIds.value.includes(mediaId)
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载成员角色与相册图片 */
async function loadData() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [membersResult, imagesResult] = await Promise.all([
      getTeamMembers(teamId.value, 1, 100),
      listTeamAlbumImages(teamId.value),
    ])
    members.value = extractPageItems<TeamMember>(membersResult)
    images.value = extractPageItems<MediaFile>(imagesResult)
  } catch (error) {
    console.error('Failed to load album:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 选择并上传图片 */
async function onUpload() {
  if (uploading.value) return

  try {
    const res = await uni.chooseImage({ count: 9 })
    const paths = res.tempFilePaths
    if (!paths.length) return

    uploading.value = true
    uni.showLoading({ title: '上传中...' })

    for (const filePath of paths) {
      await uploadTeamAlbumImage(teamId.value, filePath)
    }

    uni.hideLoading()
    uni.showToast({ title: '上传成功', icon: 'success' })
    await loadData()
  } catch (error) {
    uni.hideLoading()
    if ((error as { errMsg?: string })?.errMsg?.includes('cancel')) return
    console.error('Failed to upload image:', error)
    showBusinessError(error, '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 点击图片：管理模式下切换选中，否则预览 */
function onImageTap(image: MediaFile) {
  if (canManage.value) {
    toggleSelect(image.mediaId)
    return
  }
  if (image.signedUrl) {
    const urls = images.value.filter((i) => i.signedUrl).map((i) => i.signedUrl!)
    uni.previewImage({ urls, current: image.signedUrl })
  }
}

/** 切换选中状态 */
function toggleSelect(mediaId: string) {
  const idx = selectedIds.value.indexOf(mediaId)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(mediaId)
  }
}

/** 删除已选图片 */
function onDeleteSelected() {
  if (selectedIds.value.length === 0) return

  uni.showModal({
    title: '删除图片',
    content: `确定删除 ${selectedIds.value.length} 张图片吗？`,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteTeamAlbumImages(teamId.value, [...selectedIds.value])
        uni.showToast({ title: '已删除', icon: 'success' })
        selectedIds.value = []
        await loadData()
      } catch (error) {
        console.error('Failed to delete images:', error)
        showBusinessError(error, '删除失败')
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

.toolbar {
  background: var(--q-color-bg-card);
  padding: $spacing-md;
  display: flex;
  gap: $spacing-sm;
  border-bottom: 1px solid $color-border-light;
}

.toolbar-btn {
  flex: 1;
  background: $color-primary;
  border-radius: $radius-md;
  padding: $spacing-sm 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.9;
  }

  &--danger {
    background: $color-danger;
  }
}

.toolbar-btn-text {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: var(--q-color-bg-card);
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

.album-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  padding: 2px;
}

.album-item {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--q-color-bg-soft);

  &--selected::after {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(0, 122, 255, 0.25);
    pointer-events: none;
  }
}

.album-image {
  width: 100%;
  height: 100%;
}

.album-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.album-check {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: $radius-full;
  border: 2px solid var(--q-color-bg-card);
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;

  &--active {
    background: $color-primary;
    border-color: $color-primary;
  }
}

.album-check-mark {
  font-size: 12px;
  color: var(--q-color-bg-card);
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

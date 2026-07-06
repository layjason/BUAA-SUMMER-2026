<template>
  <view class="page">
    <AppNavbar title="小队相册" />

    <view class="toolbar">
      <template v-if="inSelectionMode">
        <view class="toolbar-btn" @tap="onSaveSelected">
          <text class="toolbar-btn-text">
            {{ savingSelected ? '保存中...' : `保存 (${selectedIds.length})` }}
          </text>
        </view>
        <view v-if="canManage" class="toolbar-btn toolbar-btn--danger" @tap="onDeleteSelected">
          <text class="toolbar-btn-text">删除 ({{ selectedIds.length }})</text>
        </view>
      </template>
      <view v-else class="toolbar-btn" @tap="onUpload">
        <text class="toolbar-btn-text">上传图片</text>
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
          @longpress="toggleSelect(image.mediaId)"
        >
          <image
            v-if="getImagePreviewUrl(image.mediaId)"
            :src="getImagePreviewUrl(image.mediaId)"
            class="album-image"
            mode="aspectFill"
          />
          <view v-else class="album-placeholder">
            <text>🖼️</text>
          </view>
          <view
            v-if="inSelectionMode"
            class="album-check"
            :class="{ 'album-check--active': isSelected(image.mediaId) }"
          >
            <text v-if="isSelected(image.mediaId)" class="album-check-mark">✓</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <ImagePreviewOverlay
      :visible="previewVisible"
      :urls="previewUrls"
      :current="previewCurrent"
      :show-save="true"
      @close="closeImagePreview"
    />
  </view>
</template>

<script setup lang="ts">
/**
 * 小队相册页
 *
 * 网格展示相册图片，支持上传；点击预览/保存，长按多选后批量保存或删除。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState, ImagePreviewOverlay } from '@/components'
import { BusinessError } from '@/api'
import { getTeamMembers } from '@/api/modules/teams'
import {
  listTeamAlbumImages,
  uploadTeamAlbumImage,
  deleteTeamAlbumImages,
} from '@/api/modules/teamChat'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { resolveMediaPreviewUrlMap } from '@/utils/media-preview'
import { saveImageToDevice } from '@/utils/save-image'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const images = ref<MediaFile[]>([])
/** 小队相册图片鉴权下载后的本地预览地址（mediaId -> previewUrl） */
const imagePreviewUrls = ref<Record<string, string>>({})
const members = ref<TeamMember[]>([])
const loading = ref(false)
const uploading = ref(false)
const savingSelected = ref(false)
const selectedIds = ref<string[]>([])
const previewVisible = ref(false)
const previewUrls = ref<string[]>([])
const previewCurrent = ref('')

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

/** 是否处于多选模式（有选中项时切换工具栏与点击行为） */
const inSelectionMode = computed(() => selectedIds.value.length > 0)

/** 判断是否已选中 */
function isSelected(mediaId: string): boolean {
  return selectedIds.value.includes(mediaId)
}

/** 获取相册图片可展示地址（teamMember 私有媒体需鉴权下载） */
function getImagePreviewUrl(mediaId: string): string {
  return imagePreviewUrls.value[mediaId] || ''
}

/** 批量解析相册图片预览 */
async function resolveAlbumImagePreviews(items: MediaFile[]): Promise<void> {
  imagePreviewUrls.value = await resolveMediaPreviewUrlMap(
    items.map((image) => ({ id: image.mediaId, signedUrl: image.signedUrl })),
    authStore.getAccessToken(),
  )
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
    await resolveAlbumImagePreviews(images.value)
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

/** 打开全屏图片预览 */
function openImagePreview(image: MediaFile) {
  const current = getImagePreviewUrl(image.mediaId)
  if (!current) {
    uni.showToast({ title: '图片加载中，请稍后重试', icon: 'none' })
    return
  }
  previewUrls.value = images.value
    .map((item) => getImagePreviewUrl(item.mediaId))
    .filter((url) => Boolean(url))
  previewCurrent.value = current
  previewVisible.value = true
}

/** 关闭全屏图片预览 */
function closeImagePreview() {
  previewVisible.value = false
  previewUrls.value = []
  previewCurrent.value = ''
}

/** 点击图片：多选模式下切换选中，否则打开预览 */
function onImageTap(image: MediaFile) {
  if (inSelectionMode.value) {
    toggleSelect(image.mediaId)
    return
  }
  openImagePreview(image)
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

/** 批量保存已选图片到本地 */
async function onSaveSelected() {
  if (savingSelected.value || selectedIds.value.length === 0) return

  savingSelected.value = true
  uni.showLoading({ title: '保存中...' })
  let savedCount = 0

  try {
    for (const [index, mediaId] of selectedIds.value.entries()) {
      const preview = getImagePreviewUrl(mediaId)
      if (!preview) continue
      try {
        await saveImageToDevice(preview, `album-${index + 1}.jpg`)
        savedCount++
      } catch {
        /* 单张失败时继续保存其余图片 */
      }
    }

    if (savedCount > 0) {
      uni.showToast({ title: `已保存 ${savedCount} 张`, icon: 'success' })
      selectedIds.value = []
    } else {
      uni.showToast({ title: '保存失败', icon: 'none' })
    }
  } finally {
    savingSelected.value = false
    uni.hideLoading()
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
  teamId.value = typeof query?.teamId === 'string' ? query.teamId : ''
  void loadData()
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

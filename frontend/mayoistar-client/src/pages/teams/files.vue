<template>
  <view class="page">
    <AppNavbar title="群文件" />

    <view class="toolbar">
      <view class="toolbar-btn" @tap="onUpload">
        <text class="toolbar-btn-text">上传文件</text>
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
      <view v-if="loading && files.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="files.length === 0" class="empty-state">
        <EmptyState title="暂无文件" description="上传文件与小队成员共享" />
      </view>

      <view v-else class="file-list">
        <view
          v-for="file in files"
          :key="file.mediaId"
          class="file-card"
          :class="{ 'file-card--selected': isSelected(file.mediaId) }"
          @tap="onFileTap(file)"
          @longpress="canManage ? toggleSelect(file.mediaId) : undefined"
        >
          <view class="file-icon">
            <image
              v-if="isImageFile(file) && getFilePreviewUrl(file.mediaId)"
              :src="getFilePreviewUrl(file.mediaId)"
              class="file-thumb"
              mode="aspectFill"
            />
            <text v-else class="file-icon-text">{{ getFileIcon(file) }}</text>
          </view>
          <view class="file-info">
            <text class="file-name">{{ file.fileName }}</text>
            <text class="file-meta"
              >{{ formatSize(file.sizeBytes) }} · {{ formatTime(file.uploadedAt) }}</text
            >
          </view>
          <view
            v-if="canManage"
            class="file-check"
            :class="{ 'file-check--active': isSelected(file.mediaId) }"
          >
            <text v-if="isSelected(file.mediaId)" class="file-check-mark">✓</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- 文件操作菜单 -->
    <uni-popup ref="actionPopup" type="bottom" :safe-area="true">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">{{ selectedFile?.fileName }}</text>
          <text class="action-sheet__close" @tap="closeActionMenu">×</text>
        </view>
        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="onPreview">
            <text class="action-sheet__icon">👁️</text>
            <text class="action-sheet__label">查看文件</text>
          </view>
          <view v-if="canManage" class="action-sheet__item" @tap="onDeleteSingle">
            <text class="action-sheet__icon">🗑️</text>
            <text class="action-sheet__label">删除文件</text>
          </view>
        </view>
      </view>
    </uni-popup>

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
 * 群文件页
 *
 * 上传、查看群文件；队长/管理员可删除。
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState, ImagePreviewOverlay } from '@/components'
import { BusinessError } from '@/api'
import { downloadFile } from '@/api/request'
import { getTeamMembers } from '@/api/modules/teams'
import { listTeamFiles, uploadTeamFile, deleteTeamFiles } from '@/api/modules/teamChat'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import {
  resolveMediaPreviewUrl,
  resolveMediaPreviewUrlMap,
  toAbsoluteMediaUrl,
} from '@/utils/media-preview'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']
type TeamMember = components['schemas']['Social.TeamMember']

const teamId = ref('')
const files = ref<MediaFile[]>([])
/** 群文件图片鉴权下载后的本地预览地址（mediaId -> previewUrl） */
const imagePreviewUrls = ref<Record<string, string>>({})
const members = ref<TeamMember[]>([])
const loading = ref(false)
const uploading = ref(false)
const selectedIds = ref<string[]>([])
const selectedFile = ref<MediaFile | null>(null)
const previewVisible = ref(false)
const previewUrls = ref<string[]>([])
const previewCurrent = ref('')

const authStore = useAuthStore()
const currentUserId = ref(authStore.userId || '')

const canManage = computed(() => {
  const me = members.value.find((m) => m.userId === currentUserId.value)
  return me?.role === 'leader' || me?.role === 'admin'
})

interface UniFileChooser {
  chooseFile?: (opts: { count: number; type?: string }) => Promise<ChooseFileResult>
  chooseMessageFile?: (opts: { count: number; type: string }) => Promise<ChooseFileResult>
}

interface ChooseFileResult {
  tempFiles?: Array<{ path?: string; tempFilePath?: string }>
  tempFilePaths?: string[]
}

interface PopupRef {
  open: () => void
  close: () => void
}

const actionPopup = ref<PopupRef | null>(null)

/** 判断是否已选中 */
function isSelected(mediaId: string): boolean {
  return selectedIds.value.includes(mediaId)
}

/** 判断是否为图片文件 */
function isImageFile(file: MediaFile): boolean {
  return file.contentType.startsWith('image/')
}

/** 获取图片文件可展示地址（teamMember 私有媒体需鉴权下载） */
function getFilePreviewUrl(mediaId: string): string {
  return imagePreviewUrls.value[mediaId] || ''
}

/** 批量解析群文件中的图片预览 */
async function resolveFileImagePreviews(items: MediaFile[]): Promise<void> {
  const imageFiles = items.filter((file) => isImageFile(file))
  imagePreviewUrls.value = await resolveMediaPreviewUrlMap(
    imageFiles.map((file) => ({ id: file.mediaId, signedUrl: file.signedUrl })),
    authStore.getAccessToken(),
  )
}

/** 获取文件图标 */
function getFileIcon(file: MediaFile): string {
  if (isImageFile(file)) return '🖼️'
  if (file.contentType.includes('pdf')) return '📄'
  if (file.contentType.includes('video')) return '🎬'
  return '📁'
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 格式化上传时间 */
function formatTime(isoStr: string): string {
  const date = new Date(isoStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载成员角色与文件列表 */
async function loadData() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [membersResult, filesResult] = await Promise.all([
      getTeamMembers(teamId.value, 1, 100),
      listTeamFiles(teamId.value),
    ])
    members.value = extractPageItems<TeamMember>(membersResult)
    files.value = extractPageItems<MediaFile>(filesResult)
    await resolveFileImagePreviews(files.value)
  } catch (error) {
    console.error('Failed to load files:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 选择本地文件并上传 */
async function onUpload() {
  if (uploading.value) return

  try {
    let filePath = ''
    /*
     * UNKNOWN-TYPE: uni-app 平台扩展文件选择 API 类型不一致
     *
     * chooseFile/chooseMessageFile 在不同端的官方类型与 Promise 化返回值不同，
     * 这里收敛到页面实际需要的 temp file path 结构。
     */
    const uniFileChooser = uni as unknown as UniFileChooser

    if (uniFileChooser.chooseFile) {
      const res = await uniFileChooser.chooseFile({ count: 1, type: 'all' })
      filePath = getChosenFilePath(res)
    } else if (uniFileChooser.chooseMessageFile) {
      const res = await uniFileChooser.chooseMessageFile({ count: 1, type: 'all' })
      filePath = getChosenFilePath(res)
    } else {
      uni.showToast({ title: '当前平台仅支持上传图片', icon: 'none' })
      const res = await uni.chooseImage({ count: 1 })
      filePath = res.tempFilePaths[0] || ''
    }

    if (!filePath) return

    uploading.value = true
    uni.showLoading({ title: '上传中...' })
    await uploadTeamFile(teamId.value, filePath)
    uni.hideLoading()
    uni.showToast({ title: '上传成功', icon: 'success' })
    await loadData()
  } catch (error) {
    uni.hideLoading()
    if ((error as { errMsg?: string })?.errMsg?.includes('cancel')) return
    console.error('Failed to upload file:', error)
    showBusinessError(error, '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 从平台文件选择结果中提取临时文件路径 */
function getChosenFilePath(result: ChooseFileResult): string {
  const firstFile = result.tempFiles?.[0]
  return firstFile?.path || firstFile?.tempFilePath || result.tempFilePaths?.[0] || ''
}

/** 打开图片全屏预览 */
async function openImagePreview(file: MediaFile) {
  if (!file.signedUrl) {
    uni.showToast({ title: '无法预览', icon: 'none' })
    return
  }
  const preview =
    getFilePreviewUrl(file.mediaId) ||
    (await resolveMediaPreviewUrl(file.signedUrl, authStore.getAccessToken()))
  if (!preview) {
    uni.showToast({ title: '无法预览', icon: 'none' })
    return
  }
  if (!getFilePreviewUrl(file.mediaId)) {
    imagePreviewUrls.value = { ...imagePreviewUrls.value, [file.mediaId]: preview }
  }
  previewUrls.value = [preview]
  previewCurrent.value = preview
  previewVisible.value = true
}

/** 点击文件：图片直接预览，其他文件打开操作菜单；队长/管理员长按多选 */
function onFileTap(file: MediaFile) {
  if (isImageFile(file)) {
    void openImagePreview(file)
    return
  }
  selectedFile.value = file
  actionPopup.value?.open()
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

/** 关闭全屏图片预览 */
function closeImagePreview() {
  previewVisible.value = false
  previewUrls.value = []
  previewCurrent.value = ''
}

/** 预览文件 */
async function onPreview() {
  closeActionMenu()
  const file = selectedFile.value
  if (!file?.signedUrl) {
    uni.showToast({ title: '无法预览', icon: 'none' })
    return
  }
  if (isImageFile(file)) {
    await openImagePreview(file)
    return
  }
  try {
    const result = await downloadFile(file.signedUrl)
    await uni.openDocument({ filePath: result.tempFilePath, showMenu: true })
  } catch {
    await uni.setClipboardData({ data: toAbsoluteMediaUrl(file.signedUrl) })
    uni.showToast({ title: '暂无法打开，链接已复制', icon: 'none' })
  }
}

/** 删除单个文件 */
function onDeleteSingle() {
  closeActionMenu()
  if (!selectedFile.value) return
  confirmDelete([selectedFile.value.mediaId])
}

/** 删除已选文件 */
function onDeleteSelected() {
  if (selectedIds.value.length === 0) return
  confirmDelete([...selectedIds.value])
}

/** 确认删除文件 */
function confirmDelete(mediaIds: string[]) {
  uni.showModal({
    title: '删除文件',
    content: `确定删除 ${mediaIds.length} 个文件吗？`,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteTeamFiles(teamId.value, mediaIds)
        uni.showToast({ title: '已删除', icon: 'success' })
        selectedIds.value = selectedIds.value.filter((id) => !mediaIds.includes(id))
        await loadData()
      } catch (error) {
        console.error('Failed to delete files:', error)
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

.file-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.file-card {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  gap: $spacing-md;

  &--selected {
    border: 1px solid $color-primary;
    background: $color-primary-light;
  }
}

.file-icon {
  width: 44px;
  height: 44px;
  border-radius: $radius-md;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon-text {
  font-size: 22px;
}

.file-thumb {
  width: 100%;
  height: 100%;
  border-radius: $radius-md;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: $font-base;
  font-weight: $weight-medium;
  color: $color-text;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: $font-xs;
  color: $color-text-muted;
  display: block;
  margin-top: 2px;
}

.file-check {
  width: 22px;
  height: 22px;
  border-radius: $radius-full;
  border: 2px solid $color-border-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--active {
    background: $color-primary;
    border-color: $color-primary;
  }
}

.file-check-mark {
  font-size: 12px;
  color: var(--q-color-bg-card);
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}

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
    font-size: $font-base;
    font-weight: $weight-semibold;
    color: $color-text;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-right: $spacing-md;
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

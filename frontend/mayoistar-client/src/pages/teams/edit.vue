<template>
  <view class="page">
    <AppNavbar title="编辑小队资料" />
    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="team" class="form-section">
        <view class="avatar-section" @tap="pickAvatar">
          <view class="team-avatar">
            <image
              v-if="avatarPreview"
              :src="avatarPreview"
              class="team-avatar-image"
              mode="aspectFill"
            />
            <text v-else class="team-avatar-placeholder">上传头像</text>
          </view>
          <text class="avatar-hint">点击更换小队头像</text>
        </view>

        <view class="form-item">
          <text class="form-label">小队名称</text>
          <input v-model="form.name" class="form-input" type="text" maxlength="100" />
        </view>

        <view class="form-item">
          <text class="form-label">小队简介</text>
          <textarea v-model="form.description" class="form-textarea" maxlength="200" />
          <text class="form-hint">{{ form.description.length }}/200</text>
        </view>

        <view class="form-item">
          <text class="form-label">人数上限</text>
          <view class="capacity-row">
            <view class="capacity-btn" @tap="decreaseCapacity">
              <text class="capacity-btn-text">-</text>
            </view>
            <text class="capacity-value">{{ form.capacity }}</text>
            <view class="capacity-btn" @tap="increaseCapacity">
              <text class="capacity-btn-text">+</text>
            </view>
          </view>
          <text class="form-hint">不得低于当前 {{ team.memberCount }} 名成员</text>
        </view>

        <view class="form-item">
          <text class="form-label">加入方式</text>
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
              <text class="radio-desc">需要队长或管理员审批</text>
            </view>
          </view>
        </view>

        <view class="form-item">
          <text class="form-label">兴趣标签</text>
          <input
            v-model="tagInput"
            class="form-input"
            type="text"
            placeholder="输入标签后回车添加"
            @confirm="addTag"
          />
          <view class="tags">
            <view v-for="(tag, idx) in form.tags" :key="tag" class="tag">
              <text class="tag-text">{{ tag }}</text>
              <text class="tag-remove" @tap="removeTag(idx)">×</text>
            </view>
          </view>
        </view>

        <view class="form-item">
          <text class="form-label">状态</text>
          <view class="status-badge" :class="`status-badge--${team.status}`">
            <text class="status-text">{{ statusLabel }}</text>
          </view>
        </view>

        <text v-if="errorMessage" class="error-text">{{ errorMessage }}</text>

        <button class="submit-btn" :disabled="!canSubmit" :loading="saving" @tap="submit">
          {{ saving ? '' : '保存资料' }}
        </button>
      </view>

      <view v-else class="empty-state">
        <text class="empty-text">小队不存在</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队资料编辑页。
 *
 * 队长可修改小队名称、简介、容量、加入方式、标签和头像。
 */
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import { BusinessError } from '@/api'
import { getTeamDetail, updateTeam } from '@/api/modules/teams'
import { uploadAvatar } from '@/api/modules/profile'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { toAbsoluteMediaUrl } from '@/utils/media-preview'
import type { components } from '@/api/types/schema'

type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamCreateRequest = components['schemas']['Social.TeamCreateRequest']

const teamId = ref('')
const team = ref<TeamProfile | null>(null)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const tagInput = ref('')
const avatarPreview = ref('')
const avatarMediaId = ref('')

const form = reactive({
  name: '',
  description: '',
  capacity: 2,
  joinMode: 'publicJoin' as TeamCreateRequest['joinMode'],
  tags: [] as string[],
})

const statusLabel = computed(() => {
  if (!team.value) return ''
  const map: Record<string, string> = {
    active: '活跃',
    dissolved: '已解散',
    disabled: '已停用',
  }
  return map[team.value.status] ?? team.value.status
})

const canSubmit = computed(() => {
  return Boolean(form.name.trim()) && form.tags.length > 0 && !saving.value
})

/** 使用接口返回值回填页面表单 */
function fillForm(profile: TeamProfile): void {
  team.value = profile
  form.name = profile.name
  form.description = profile.description ?? ''
  form.capacity = profile.capacity
  form.joinMode = profile.joinMode
  form.tags = [...profile.tags]
  avatarPreview.value = profile.avatar?.signedUrl
    ? toAbsoluteMediaUrl(profile.avatar.signedUrl)
    : ''
  avatarMediaId.value = ''
}

/** 加载小队资料 */
async function loadTeam(): Promise<void> {
  if (!teamId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    fillForm(await getTeamDetail(teamId.value))
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMessage.value = getTeamErrorMessage(error.code, error.message)
    } else {
      errorMessage.value = '加载失败'
    }
  } finally {
    loading.value = false
  }
}

/** 选择并上传小队头像 */
async function pickAvatar(): Promise<void> {
  try {
    const result = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    const tempPath = result.tempFilePaths[0]
    if (!tempPath) return
    avatarPreview.value = tempPath
    const uploaded = await uploadAvatar(tempPath)
    avatarMediaId.value = uploaded.mediaId
    avatarPreview.value = uploaded.signedUrl ? toAbsoluteMediaUrl(uploaded.signedUrl) : tempPath
  } catch {
    uni.showToast({ title: '头像上传失败或已取消', icon: 'none' })
  }
}

/** 增加人数上限 */
function increaseCapacity(): void {
  if (form.capacity < 500) form.capacity += 1
}

/** 降低人数上限 */
function decreaseCapacity(): void {
  const minCapacity = Math.max(1, team.value?.memberCount ?? 1)
  if (form.capacity > minCapacity) form.capacity -= 1
}

/** 添加兴趣标签 */
function addTag(): void {
  const tag = tagInput.value.trim()
  if (!tag || form.tags.includes(tag) || form.tags.length >= 10) return
  form.tags.push(tag)
  tagInput.value = ''
}

/** 移除兴趣标签 */
function removeTag(index: number): void {
  form.tags.splice(index, 1)
}

/** 提交小队资料修改 */
async function submit(): Promise<void> {
  if (!canSubmit.value || !team.value) return
  if (form.capacity < team.value.memberCount) {
    errorMessage.value = '人数上限不能低于当前成员数'
    return
  }

  saving.value = true
  errorMessage.value = ''
  try {
    const updated = await updateTeam(teamId.value, {
      name: form.name.trim(),
      description: form.description.trim(),
      capacity: form.capacity,
      joinMode: form.joinMode,
      tags: [...form.tags],
      avatarMediaId: avatarMediaId.value || undefined,
    })
    fillForm(updated)
    uni.showToast({ title: '资料已保存', icon: 'success' })
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMessage.value = getTeamErrorMessage(error.code, error.message)
    } else {
      errorMessage.value = '保存失败'
    }
  } finally {
    saving.value = false
  }
}

onLoad((query) => {
  teamId.value = typeof query?.teamId === 'string' ? query.teamId : ''
  void loadTeam()
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

.loading-state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.loading-text,
.empty-text {
  font-size: $font-base;
  color: $color-text-sub;
}

.form-section {
  padding: $spacing-lg;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-lg 0;
}

.team-avatar {
  width: 80px;
  height: 80px;
  border-radius: $radius-lg;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.team-avatar-image {
  width: 100%;
  height: 100%;
}

.team-avatar-placeholder {
  font-size: $font-xs;
  color: $color-text-muted;
}

.avatar-hint,
.form-hint {
  font-size: $font-xs;
  color: $color-text-muted;
  margin-top: $spacing-xs;
}

.form-item {
  background: var(--q-color-bg-card);
  padding: $spacing-md;
  margin-bottom: $spacing-sm;
  border-radius: $radius-lg;
}

.form-label {
  font-size: $font-sm;
  color: $color-text-sub;
  display: block;
  margin-bottom: $spacing-xs;
}

.form-input {
  height: 42px;
  font-size: $font-base;
  color: $color-text;
}

.form-textarea {
  width: 100%;
  min-height: 92px;
  font-size: $font-base;
  color: $color-text;
}

.capacity-row {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

.capacity-btn {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.capacity-btn-text,
.capacity-value {
  font-size: $font-lg;
  color: $color-text;
}

.radio-group {
  display: flex;
  gap: $spacing-sm;
}

.radio-item {
  flex: 1;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  padding: $spacing-md;

  &--active {
    border-color: $color-primary;
    background: rgba($color-primary, 0.08);
  }
}

.radio-label {
  display: block;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $color-text;
}

.radio-desc {
  display: block;
  margin-top: 2px;
  font-size: $font-xs;
  color: $color-text-muted;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
}

.tag {
  background: rgba($color-primary, 0.1);
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.tag-text,
.tag-remove {
  font-size: $font-xs;
  color: $color-primary;
}

.status-badge {
  display: inline-flex;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: $color-primary-light;

  &--dissolved {
    background: rgba(220, 38, 38, 0.08);

    .status-text {
      color: $color-danger;
    }
  }

  &--disabled {
    background: rgba(158, 158, 158, 0.15);

    .status-text {
      color: $color-text-muted;
    }
  }
}

.status-text {
  font-size: $font-sm;
  color: var(--q-color-success);
}

.error-text {
  display: block;
  color: $color-danger;
  font-size: $font-sm;
  margin: $spacing-md 0;
}

.submit-btn {
  margin-top: $spacing-lg;
  background: $color-primary;
  color: $color-text-inverse;
  border-radius: $radius-md;
  font-size: $font-base;
}

.submit-btn[disabled] {
  background: $color-bg-soft;
  color: $color-text-muted;
  border: 1px solid $color-border-light;
  box-shadow: none;
  opacity: 1;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

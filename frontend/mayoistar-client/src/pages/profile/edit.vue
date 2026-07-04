<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="edit-container">
        <!-- 头像 -->
        <view class="avatar-section" @click="handleAvatarClick">
          <image v-if="avatarUrl" class="avatar-image" :src="avatarUrl" mode="aspectFill" />
          <view v-else class="avatar-placeholder">
            <text class="avatar-placeholder-text">{{ initialChar }}</text>
          </view>
          <text class="avatar-hint">{{ t('editProfile.changeAvatar') }}</text>
        </view>

        <!-- 表单 -->
        <view class="form">
          <!-- 昵称 -->
          <FormInput
            v-model="formNickname"
            :label="t('editProfile.nickname')"
            :placeholder="t('editProfile.nicknamePlaceholder')"
            :error="nicknameError"
            @blur="checkNickname"
          />

          <!-- 商家名称（仅商家） -->
          <FormInput
            v-if="isMerchant"
            v-model="formMerchantName"
            :label="t('editProfile.merchantName')"
            :placeholder="t('editProfile.merchantNamePlaceholder')"
            :error="merchantNameError"
          />

          <!-- 性别（仅个人） -->
          <view v-if="!isMerchant" class="form-item">
            <text class="label">{{ t('editProfile.gender') }}</text>
            <view class="gender-row">
              <view
                v-for="opt in genderOptions"
                :key="opt.value"
                class="gender-option"
                :class="{ active: formGender === opt.value }"
                @click="formGender = opt.value"
              >
                <text>{{ opt.label }}</text>
              </view>
            </view>
          </view>

          <!-- 生日（仅个人） -->
          <view v-if="!isMerchant" class="form-item">
            <text class="label">{{ t('editProfile.birthday') }}</text>
            <picker mode="date" :value="formBirthday" :end="today" @change="onBirthdayChange">
              <view class="picker-value">
                <text :class="{ placeholder: !formBirthday }">
                  {{ formBirthday || t('editProfile.birthdayPlaceholder') }}
                </text>
              </view>
            </picker>
          </view>

          <!-- 个性签名（仅个人） -->
          <view v-if="!isMerchant" class="form-item">
            <text class="label">{{ t('editProfile.signature') }}</text>
            <textarea
              v-model="formSignature"
              class="textarea"
              :placeholder="t('editProfile.signaturePlaceholder')"
              :maxlength="200"
              auto-height
            />
            <text class="char-count">{{ formSignature.length }}/200</text>
          </view>

          <!-- 兴趣标签 / 关注领域 -->
          <view class="form-item">
            <text class="label">{{ tagLabel }}</text>
            <scroll-view v-if="availableTags.length" class="tag-scroll" scroll-x>
              <view class="tag-row">
                <view
                  v-for="tag in availableTags"
                  :key="tag.name"
                  class="tag-chip"
                  :class="{ active: selectedTags.has(tag.name) }"
                  @click="toggleTag(tag.name)"
                >
                  <text>{{ tag.name }}</text>
                </view>
              </view>
            </scroll-view>
            <text v-else class="hint">{{ t('editProfile.loadingTags') }}</text>
          </view>

          <!-- 商家资质 -->
          <view v-if="isMerchant" class="form-item">
            <text class="label">{{ t('editProfile.qualification') }}</text>
            <view class="qualification-status">
              <text class="qualification-text">
                {{ t('editProfile.qualificationStatus') }}:
                {{ qualificationStatusText }}
              </text>
              <text v-if="qualification.rejectReason" class="qualification-reject">
                {{ t('editProfile.rejectReason') }}: {{ qualification.rejectReason }}
              </text>
            </view>
            <view v-if="canSubmitQualification" class="qualification-upload">
              <view class="license-grid">
                <view v-for="(url, index) in licensePreviewUrls" :key="url" class="license-preview">
                  <image class="license-image" :src="url" mode="aspectFill" />
                  <text class="license-remove" @click.stop="removeLicenseImage(index)">×</text>
                </view>
                <view class="license-add" @click="chooseLicenseImages">
                  <text class="license-add-icon">+</text>
                  <text class="license-add-text">营业执照/凭证</text>
                </view>
              </view>
              <button
                class="qualification-submit"
                :loading="qualificationSubmitting"
                :disabled="qualificationSubmitting || licenseMediaIds.length === 0"
                @click="handleSubmitQualification"
              >
                提交商家资质审核
              </button>
              <text class="qualification-hint">
                请上传营业执照或营业凭证，提交后状态将变为审核中。
              </text>
            </view>
          </view>

          <FormError :message="formError" />
        </view>
      </view>
    </scroll-view>
    <view class="action-bar">
      <SubmitButton :text="t('editProfile.save')" :loading="saving" @click="handleSave" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 编辑资料页
 *
 * 根据用户类型（个人/商家）展示不同字段。
 * 前置条件：用户已登录
 * 后置条件：保存成功后返回上一页
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { BusinessError } from '@/api'
import {
  checkNicknameAvailability,
  getInterestTags,
  getMerchantProfile,
  getMyProfile,
  submitMerchantQualification,
  updateMerchantProfile,
  updateMyProfile,
  uploadAvatar,
  uploadMerchantLicense,
} from '@/api/modules/profile'
import { getErrorMessage } from '@/utils/error'
import { FormInput, FormError, SubmitButton } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

/** 是否为商家用户 */
const isMerchant = computed(() => authStore.userKind === 'merchant')

/** 今日日期字符串，用于生日选择器上限 */
const today = computed(() => {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
})

/** 头像首字符 */
const initialChar = computed(() => {
  if (!authStore.userId) return '?'
  return authStore.userId.charAt(0).toUpperCase()
})

/** 兴趣标签/关注领域的展示标签 */
const tagLabel = computed(() =>
  isMerchant.value ? t('editProfile.interestedActivityFields') : t('editProfile.interestTags'),
)

// ================= 表单字段 =================

const formNickname = ref('')
const formMerchantName = ref('')
const formGender = ref<'male' | 'female' | 'other' | ''>('')
const formBirthday = ref('')
const formSignature = ref('')

const avatarUrl = ref('')
/** 上传后返回的 mediaId，提交时使用 */
const avatarMediaId = ref<string>('')

/** 已选中的标签集合 */
const selectedTags = ref(new Set<string>())

/** 系统可用标签列表 */
const availableTags = ref<{ name: string }[]>([])

// ================= 错误状态 =================

const nicknameError = ref('')
const merchantNameError = ref('')
const formError = ref('')
const saving = ref(false)
const qualificationSubmitting = ref(false)

/** 昵称唯一性检查已有结果 */
const nicknameChecked = ref(false)
/** 昵称是否可用 */
const nicknameAvailable = ref(false)

/** 商家资质信息（只读） */
const qualification = ref<{
  status: string
  rejectReason?: string
} | null>(null)

const licenseMediaIds = ref<string[]>([])
const licensePreviewUrls = ref<string[]>([])

/** 读取媒体文件可展示地址，优先使用签名 URL */
function getMediaPreviewUrl(media: { signedUrl?: string; url?: string } | undefined): string {
  return media?.signedUrl ?? media?.url ?? ''
}

/** 是否允许提交或重新提交商家资质 */
const canSubmitQualification = computed(() => {
  const status = qualification.value?.status ?? 'not_submitted'
  return status === 'not_submitted' || status === 'rejected'
})

/** 资质状态文本映射 */
const qualificationStatusText = computed(() => {
  const status = qualification.value?.status ?? 'not_submitted'
  const map: Record<string, string> = {
    not_submitted: t('editProfile.qualStatusNotSubmitted'),
    pending: t('editProfile.qualStatusPending'),
    approved: t('editProfile.qualStatusApproved'),
    rejected: t('editProfile.qualStatusRejected'),
  }
  return map[status] ?? status
})

// ================= 性别选项 =================

const genderOptions = [
  { value: 'male' as const, label: t('editProfile.male') },
  { value: 'female' as const, label: t('editProfile.female') },
  { value: 'other' as const, label: t('editProfile.other') },
]

// ================= 昵称校验 =================

/**
 * 防抖定时器
 */
let nicknameTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 校验昵称唯一性
 *
 * 输入后等待 500ms 防抖，调用昵称可用性 API。
 * 前置条件：昵称非空
 * 后置条件：更新 nicknameAvailable 和 nicknameChecked 状态
 */
function checkNickname(): void {
  nicknameError.value = ''
  nicknameChecked.value = false

  if (nicknameTimer) clearTimeout(nicknameTimer)

  const value = formNickname.value.trim()
  if (!value) return

  nicknameTimer = setTimeout(async () => {
    try {
      const result = await checkNicknameAvailability(value)
      if (formNickname.value.trim() !== value) return

      nicknameAvailable.value = result.available
      nicknameChecked.value = true
      if (!result.available) {
        nicknameError.value = t('editProfile.nicknameUnavailable')
      }
    } catch {
      if (formNickname.value.trim() !== value) return
      nicknameChecked.value = false
    }
  }, 500)
}

// ================= 标签切换 =================

/**
 * 切换标签选中状态
 *
 * @param name 标签名称
 */
function toggleTag(name: string): void {
  const next = new Set(selectedTags.value)
  if (next.has(name)) {
    next.delete(name)
  } else {
    next.add(name)
  }
  selectedTags.value = next
}

// ================= 头像 =================

/**
 * 点击头像触发选择图片
 */
async function handleAvatarClick(): Promise<void> {
  try {
    const res = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    const tempPath = res.tempFilePaths[0]
    if (!tempPath) return

    // 先本地预览
    avatarUrl.value = tempPath

    // 上传头像
    try {
      const result = await uploadAvatar(tempPath)
      avatarMediaId.value = result.mediaId
      avatarUrl.value = getMediaPreviewUrl(result) || tempPath
    } catch {
      formError.value = t('editProfile.avatarUploadFailed')
    }
  } catch {
    /* 用户取消选择 */
  }
}

/** 选择并上传营业执照或营业凭证图片
 *
 * 前置条件：当前用户为商家且资质处于未提交或驳回状态。
 * 后置条件：上传成功的 mediaId 会进入 licenseMediaIds，签名 URL 用于页面预览。
 * 副作用：调用媒体上传接口，失败时写入 formError。
 */
async function chooseLicenseImages(): Promise<void> {
  if (!canSubmitQualification.value) return
  formError.value = ''
  try {
    const remaining = 3 - licenseMediaIds.value.length
    if (remaining <= 0) return
    const res = await uni.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    for (const filePath of res.tempFilePaths) {
      const result = await uploadMerchantLicense(filePath)
      licenseMediaIds.value.push(result.mediaId)
      licensePreviewUrls.value.push(getMediaPreviewUrl(result) || filePath)
    }
  } catch {
    formError.value = '营业凭证上传失败或已取消'
  }
}

/** 移除待提交的营业凭证图片
 *
 * @param index 图片在待提交列表中的索引
 */
function removeLicenseImage(index: number): void {
  licenseMediaIds.value.splice(index, 1)
  licensePreviewUrls.value.splice(index, 1)
}

/** 提交商家资质审核
 *
 * 前置条件：已选择至少一张营业执照或营业凭证图片。
 * 后置条件：提交成功后重新加载商家资料，状态进入审核中。
 * 副作用：调用商家资质提交接口并展示 toast。
 */
async function handleSubmitQualification(): Promise<void> {
  if (qualificationSubmitting.value) return
  if (licenseMediaIds.value.length === 0) {
    formError.value = '请先上传营业执照或营业凭证'
    return
  }
  qualificationSubmitting.value = true
  formError.value = ''
  try {
    await submitMerchantQualification([...licenseMediaIds.value])
    licenseMediaIds.value = []
    licensePreviewUrls.value = []
    uni.showToast({ title: '资质已提交审核', icon: 'success' })
    await loadProfile()
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '资质提交失败')
    }
  } finally {
    qualificationSubmitting.value = false
  }
}

// ================= 生日选择 =================

/**
 * 生日选择器变化处理
 *
 * @param e picker change 事件
 */
function onBirthdayChange(e: { detail: { value: string } }): void {
  formBirthday.value = e.detail.value
}

// ================= 加载资料 =================

/**
 * 加载当前用户资料
 *
 * 根据用户类型调用对应的资料 API 并回填表单。
 */
async function loadProfile(): Promise<void> {
  try {
    if (isMerchant.value) {
      const profile = await getMerchantProfile()
      formNickname.value = profile.nickname
      formMerchantName.value = profile.merchantName
      const avatarPreviewUrl = getMediaPreviewUrl(profile.avatar)
      if (avatarPreviewUrl) avatarUrl.value = avatarPreviewUrl
      if (profile.interestedActivityFields?.length) {
        selectedTags.value = new Set(profile.interestedActivityFields)
      }
      qualification.value = {
        status: profile.qualification?.status ?? profile.qualificationStatus ?? 'not_submitted',
        rejectReason: profile.qualification?.rejectReason,
      }
    } else {
      const profile = await getMyProfile()
      formNickname.value = profile.nickname
      const avatarPreviewUrl = getMediaPreviewUrl(profile.avatar)
      if (avatarPreviewUrl) avatarUrl.value = avatarPreviewUrl
      if (profile.gender) formGender.value = profile.gender as typeof formGender.value
      if (profile.birthday) formBirthday.value = profile.birthday
      if (profile.signature) formSignature.value = profile.signature
      if (profile.interestTags?.length) {
        selectedTags.value = new Set(profile.interestTags)
      }
    }
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '加载资料失败')
    }
  }
}

/**
 * 加载系统可用标签
 */
async function loadTags(): Promise<void> {
  try {
    const tags = await getInterestTags()
    availableTags.value = tags
  } catch {
    /* 标签加载失败不影响主流程 */
  }
}

onMounted(() => {
  loadProfile()
  loadTags()
})

// ================= 保存 =================

/**
 * 保存资料
 *
 * 校验昵称后根据用户类型调用对应的更新 API。
 * 前置条件：资料已加载，昵称已校验
 * 后置条件：保存成功返回上一页
 */
async function handleSave(): Promise<void> {
  if (saving.value) return
  formError.value = ''

  // 昵称校验
  const nickname = formNickname.value.trim()
  if (!nickname) {
    nicknameError.value = t('editProfile.nicknameRequired')
    return
  }
  if (nicknameChecked.value && !nicknameAvailable.value) {
    nicknameError.value = t('editProfile.nicknameUnavailable')
    return
  }

  saving.value = true

  try {
    if (isMerchant.value) {
      await updateMerchantProfile({
        nickname: nickname,
        merchantName: formMerchantName.value.trim(),
        avatarMediaId: avatarMediaId.value || undefined,
        interestedActivityFields: [...selectedTags.value],
      })
    } else {
      await updateMyProfile({
        nickname,
        avatarMediaId: avatarMediaId.value || undefined,
        gender: formGender.value || undefined,
        birthday: formBirthday.value || undefined,
        signature: formSignature.value.trim() || undefined,
        interestTags: [...selectedTags.value],
      })
    }
    uni.showToast({ title: t('editProfile.saveSuccess'), icon: 'success' })
    loadProfile()
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
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

.edit-container {
  padding: 32rpx 32rpx calc(160rpx + env(safe-area-inset-bottom));
}

.action-bar {
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #fff;
  border-top: 2rpx solid #ebedf0;
  flex-shrink: 0;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 48rpx;
}

.avatar-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  margin-bottom: 12rpx;
}

.avatar-placeholder {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: #1989fa;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}

.avatar-placeholder-text {
  font-size: 48rpx;
  color: #fff;
  font-weight: 600;
}

.avatar-hint {
  font-size: 24rpx;
  color: #1989fa;
}

.form {
  padding-bottom: 48rpx;
}

.form-item {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.gender-row {
  display: flex;
  gap: 16rpx;
}

.gender-option {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  border: 2rpx solid transparent;
}

.gender-option.active {
  border-color: #1989fa;
  color: #1989fa;
}

.picker-value {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.picker-value text {
  font-size: 30rpx;
  color: #323233;
}

.picker-value text.placeholder {
  color: #c8c9cc;
}

.textarea {
  width: 100%;
  min-height: 140rpx;
  padding: 20rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 30rpx;
  color: #323233;
  box-sizing: border-box;
}

.char-count {
  display: block;
  text-align: right;
  font-size: 24rpx;
  color: #969799;
  margin-top: 8rpx;
}

.tag-scroll {
  white-space: nowrap;
}

.tag-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 4rpx 0;
}

.tag-chip {
  display: inline-flex;
  padding: 12rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  border: 2rpx solid #ebedf0;
  font-size: 26rpx;
  color: #646566;
  flex-shrink: 0;
}

.tag-chip.active {
  border-color: #1989fa;
  color: #1989fa;
  background-color: #e6f0fe;
}

.hint {
  font-size: 26rpx;
  color: #969799;
}

.qualification-status {
  background-color: #fff;
  border-radius: 8rpx;
  padding: 20rpx 24rpx;
}

.qualification-text {
  font-size: 28rpx;
  color: #323233;
}

.qualification-reject {
  display: block;
  font-size: 26rpx;
  color: #ee0a24;
  margin-top: 8rpx;
}

.qualification-upload {
  margin-top: 20rpx;
}

.license-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.license-preview,
.license-add {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  overflow: hidden;
  position: relative;
}

.license-image {
  width: 100%;
  height: 100%;
}

.license-remove {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 28rpx;
  text-align: center;
  line-height: 36rpx;
}

.license-add {
  border: 2rpx dashed #c8c9cc;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.license-add-icon {
  font-size: 44rpx;
  color: #1989fa;
}

.license-add-text {
  font-size: 24rpx;
  color: #646566;
}

.qualification-submit {
  width: 100%;
  height: 76rpx;
  border-radius: 38rpx;
  background: #1989fa;
  color: #fff;
  font-size: 28rpx;
  line-height: 76rpx;
}

.qualification-submit[disabled] {
  opacity: 0.5;
}

.qualification-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #969799;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

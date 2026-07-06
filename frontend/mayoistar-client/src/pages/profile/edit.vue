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
            :label="nicknameLabel"
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
              <text v-if="qualificationSubmittedAt" class="qualification-meta">
                {{ t('editProfile.qualificationSubmittedAt') }}: {{ qualificationSubmittedAt }}
              </text>
              <text v-if="qualificationReviewedAt" class="qualification-meta">
                {{ t('editProfile.qualificationReviewedAt') }}: {{ qualificationReviewedAt }}
              </text>
              <text v-if="qualificationRejectReason" class="qualification-reject">
                {{ t('editProfile.rejectReason') }}: {{ qualificationRejectReason }}
              </text>
            </view>
            <view v-if="submittedLicensePreviewUrls.length > 0" class="submitted-license-section">
              <text class="sub-label">{{ t('editProfile.qualificationImages') }}</text>
              <view class="license-grid">
                <view
                  v-for="(url, index) in submittedLicensePreviewUrls"
                  :key="index"
                  class="license-preview"
                >
                  <image class="license-image" :src="url" mode="aspectFill" />
                </view>
              </view>
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

          <view class="profile-summary">
            <view class="summary-row">
              <text class="summary-label">{{ t('editProfile.userKind') }}</text>
              <text class="summary-value">{{ userKindText }}</text>
            </view>
            <view v-if="!isMerchant" class="summary-row">
              <text class="summary-label">{{ t('editProfile.reputationScore') }}</text>
              <text class="summary-value">{{ reputationScoreText }}</text>
            </view>
            <view v-else class="summary-row">
              <text class="summary-label">{{ t('editProfile.accountStatus') }}</text>
              <text class="summary-value">{{ accountStatusText }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
    <BottomActionBar fixed>
      <button
        class="bar-btn bar-btn-primary"
        :disabled="saving || !hasProfileChanges"
        :loading="saving"
        @click="handleSave"
      >
        {{ saving ? '' : t('editProfile.save') }}
      </button>
    </BottomActionBar>
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
import { resolveMediaPreviewUrl } from '@/utils/media-preview'
import { BottomActionBar, FormInput, FormError } from '@/components'

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

/** 昵称字段标签，商家资料需明确显示为商家昵称 */
const nicknameLabel = computed(() =>
  isMerchant.value ? t('editProfile.merchantNickname') : t('editProfile.nickname'),
)

// ================= 表单字段 =================

const formNickname = ref('')
const formMerchantName = ref('')
const formGender = ref<'unspecified' | 'male' | 'female' | 'other' | ''>('')
const formBirthday = ref('')
const formSignature = ref('')
const reputationScore = ref<number | null>(null)
const merchantAccountStatus = ref<string>('')

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
/** 最近一次唯一性检查对应的昵称 */
const checkedNickname = ref('')

/** 商家资质信息（只读） */
const qualification = ref<{
  status: string
  submittedAt?: string
  reviewedAt?: string
  rejectReason?: string
  licenseImageUrls?: string[]
} | null>(null)

const licenseMediaIds = ref<string[]>([])
const licensePreviewUrls = ref<string[]>([])
/** 已提交资质图片的可展示地址（私有媒体需鉴权下载） */
const submittedLicensePreviewUrls = ref<string[]>([])

interface EditableProfileSnapshot {
  nickname: string
  merchantName: string
  gender: string
  birthday: string
  signature: string
  tags: string[]
  avatarMediaId: string
}

const initialProfileSnapshot = ref<EditableProfileSnapshot | null>(null)

/** 忽略过期的 loadProfile 结果，避免并发请求用旧错误覆盖新数据 */
let loadProfileGeneration = 0

function normalizeFormText(value: string | null | undefined): string {
  return (value ?? '').trim()
}

function sortedTags(): string[] {
  return [...selectedTags.value].sort()
}

/** 获取当前可保存资料的快照
 *
 * 前置条件：表单字段已初始化。
 * 后置条件：返回只包含资料保存接口会提交的字段快照，顺序稳定。
 */
function getEditableProfileSnapshot(): EditableProfileSnapshot {
  return {
    nickname: normalizeFormText(formNickname.value),
    merchantName: normalizeFormText(formMerchantName.value),
    gender: formGender.value,
    birthday: formBirthday.value,
    signature: normalizeFormText(formSignature.value),
    tags: sortedTags(),
    avatarMediaId: avatarMediaId.value,
  }
}

function sameStringArray(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((item, index) => item === right[index])
}

/** 判断昵称是否相对初始资料发生变化
 *
 * 前置条件：nickname 已按提交规则 trim。
 * 后置条件：返回 true 表示需要执行昵称唯一性检查；资料尚未加载时保守返回 true。
 * 不变量：比较基准始终是页面最初加载的昵称，不受中间编辑过程影响。
 *
 * @param nickname 当前待提交昵称
 */
function isNicknameChangedFromInitial(nickname: string): boolean {
  const initial = initialProfileSnapshot.value
  if (!initial) return true
  return nickname !== initial.nickname
}

/** 当前资料表单是否有未保存修改 */
const hasProfileChanges = computed(() => {
  if (!initialProfileSnapshot.value) return false
  const current = getEditableProfileSnapshot()
  const initial = initialProfileSnapshot.value
  return (
    current.nickname !== initial.nickname ||
    current.merchantName !== initial.merchantName ||
    current.gender !== initial.gender ||
    current.birthday !== initial.birthday ||
    current.signature !== initial.signature ||
    current.avatarMediaId !== initial.avatarMediaId ||
    !sameStringArray(current.tags, initial.tags)
  )
})

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

/** 商家资质驳回原因，空字符串表示当前无可展示原因 */
const qualificationRejectReason = computed(() => {
  return qualification.value?.rejectReason ?? ''
})

const qualificationSubmittedAt = computed(() => qualification.value?.submittedAt ?? '')
const qualificationReviewedAt = computed(() => qualification.value?.reviewedAt ?? '')

const reputationScoreText = computed(() => {
  return reputationScore.value === null ? '-' : String(reputationScore.value)
})

const userKindText = computed(() => {
  return isMerchant.value ? t('editProfile.userKindMerchant') : t('editProfile.userKindPersonal')
})

const accountStatusText = computed(() => {
  const status = merchantAccountStatus.value || authStore.accountStatus || ''
  const map: Record<string, string> = {
    active: t('editProfile.accountStatusActive'),
    inactive: t('editProfile.accountStatusInactive'),
    banned: t('editProfile.accountStatusBanned'),
  }
  return map[status] ?? (status || '-')
})

// ================= 性别选项 =================

const genderOptions = [
  { value: 'unspecified' as const, label: t('editProfile.unspecified') },
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
  checkedNickname.value = ''

  if (nicknameTimer) clearTimeout(nicknameTimer)

  const value = formNickname.value.trim()
  if (!value) return
  if (!isNicknameChangedFromInitial(value)) {
    nicknameAvailable.value = true
    nicknameChecked.value = true
    checkedNickname.value = value
    return
  }

  nicknameTimer = setTimeout(async () => {
    try {
      const result = await checkNicknameAvailability(value)
      if (formNickname.value.trim() !== value) return

      nicknameAvailable.value = result.available
      nicknameChecked.value = true
      checkedNickname.value = value
      if (!result.available) {
        nicknameError.value = t('editProfile.nicknameUnavailable')
      }
    } catch {
      if (formNickname.value.trim() !== value) return
      nicknameChecked.value = false
    }
  }, 500)
}

/** 确保当前昵称可用
 *
 * 前置条件：formNickname 非空。
 * 后置条件：返回 true 表示昵称可提交；返回 false 时 nicknameError 已包含错误提示。
 */
async function ensureNicknameAvailable(nickname: string): Promise<boolean> {
  if (!isNicknameChangedFromInitial(nickname)) {
    nicknameError.value = ''
    nicknameAvailable.value = true
    nicknameChecked.value = true
    checkedNickname.value = nickname
    return true
  }
  if (nicknameChecked.value && checkedNickname.value === nickname) return nicknameAvailable.value
  try {
    const result = await checkNicknameAvailability(nickname)
    nicknameAvailable.value = result.available
    nicknameChecked.value = true
    checkedNickname.value = nickname
    if (!result.available) {
      nicknameError.value = t('editProfile.nicknameUnavailable')
    }
    return result.available
  } catch {
    return true
  }
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
      extension: ['jpg', 'jpeg', 'png'],
    })
    const tempPath = res.tempFilePaths[0]
    if (!tempPath) return

    // 先本地预览
    avatarUrl.value = tempPath

    // 上传头像
    try {
      const result = await uploadAvatar(tempPath)
      avatarMediaId.value = result.mediaId
      avatarUrl.value = result.signedUrl || tempPath
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
      licensePreviewUrls.value.push(filePath)
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

/** 解析已提交资质图片的可展示地址
 *
 * 前置条件：licenseImageUrls 来自商家资料接口。
 * 后置条件：私有后端媒体在有 token 时下载为本地临时路径；公开 URL 原样保留。
 */
async function resolveSubmittedLicensePreviews(urls: string[] | undefined): Promise<void> {
  if (!urls?.length) {
    submittedLicensePreviewUrls.value = []
    return
  }
  const token = authStore.getAccessToken()
  const previews = await Promise.all(urls.map((url) => resolveMediaPreviewUrl(url, token)))
  submittedLicensePreviewUrls.value = previews.filter((preview) => Boolean(preview))
}

// ================= 加载资料 =================

/**
 * 加载当前用户资料
 *
 * 根据用户类型调用对应的资料 API 并回填表单。
 */
async function loadProfile(): Promise<void> {
  const generation = ++loadProfileGeneration
  try {
    if (isMerchant.value) {
      const profile = await getMerchantProfile()
      if (generation !== loadProfileGeneration) return
      formNickname.value = profile.nickname ?? ''
      formMerchantName.value = profile.merchantName ?? ''
      if (profile.avatar?.signedUrl) avatarUrl.value = profile.avatar.signedUrl
      if (profile.interestedActivityFields?.length) {
        selectedTags.value = new Set(profile.interestedActivityFields)
      } else {
        selectedTags.value = new Set()
      }
      merchantAccountStatus.value = profile.accountStatus
      qualification.value = {
        status: profile.qualification?.status ?? profile.qualificationStatus ?? 'not_submitted',
        submittedAt: profile.qualification?.submittedAt,
        reviewedAt: profile.qualification?.reviewedAt,
        rejectReason: profile.qualification?.rejectReason,
        licenseImageUrls: profile.qualification?.licenseImageUrls,
      }
      try {
        await resolveSubmittedLicensePreviews(profile.qualification?.licenseImageUrls)
      } catch {
        submittedLicensePreviewUrls.value = []
      }
    } else {
      const profile = await getMyProfile()
      if (generation !== loadProfileGeneration) return
      formNickname.value = profile.nickname ?? ''
      if (profile.avatar?.signedUrl) avatarUrl.value = profile.avatar.signedUrl
      if (profile.gender) formGender.value = profile.gender as typeof formGender.value
      if (profile.birthday) formBirthday.value = profile.birthday ?? ''
      if (profile.signature) formSignature.value = profile.signature ?? ''
      if (profile.interestTags?.length) {
        selectedTags.value = new Set(profile.interestTags)
      } else {
        selectedTags.value = new Set()
      }
      reputationScore.value = profile.reputationScore
    }
    if (generation !== loadProfileGeneration) return
    initialProfileSnapshot.value = getEditableProfileSnapshot()
    formError.value = ''
  } catch (error) {
    if (generation !== loadProfileGeneration) return
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
  if (!hasProfileChanges.value) return
  formError.value = ''

  // 昵称校验
  const nickname = formNickname.value.trim()
  if (!nickname) {
    nicknameError.value = t('editProfile.nicknameRequired')
    return
  }
  if (nicknameChecked.value && checkedNickname.value === nickname && !nicknameAvailable.value) {
    nicknameError.value = t('editProfile.nicknameUnavailable')
    return
  }
  if (!(await ensureNicknameAvailable(nickname))) return

  if (isMerchant.value && !formMerchantName.value.trim()) {
    merchantNameError.value = t('editProfile.merchantNameRequired')
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
    await loadProfile()
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
  background-color: var(--q-color-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  height: 0;
  -webkit-overflow-scrolling: touch;
}

.edit-container {
  padding: 32rpx 32rpx calc(176rpx + env(safe-area-inset-bottom));
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
  background-color: var(--q-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}

.avatar-placeholder-text {
  font-size: 48rpx;
  color: var(--q-color-bg-card);
  font-weight: 600;
}

.avatar-hint {
  font-size: 24rpx;
  color: var(--q-color-primary);
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
  color: var(--q-color-text);
  margin-bottom: 12rpx;
}

.sub-label {
  display: block;
  font-size: 26rpx;
  color: var(--q-color-text-sub);
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
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  font-size: 28rpx;
  color: var(--q-color-text);
  border: 2rpx solid transparent;
}

.gender-option.active {
  border-color: var(--q-color-primary);
  color: var(--q-color-primary);
}

.picker-value {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.picker-value text {
  font-size: 30rpx;
  color: var(--q-color-text);
}

.picker-value text.placeholder {
  color: var(--q-color-text-muted);
}

.textarea {
  width: 100%;
  min-height: 140rpx;
  padding: 20rpx 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  font-size: 30rpx;
  color: var(--q-color-text);
  box-sizing: border-box;
}

.char-count {
  display: block;
  text-align: right;
  font-size: 24rpx;
  color: var(--q-color-text-muted);
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
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  border: 2rpx solid var(--q-color-bg-soft);
  font-size: 26rpx;
  color: var(--q-color-text-sub);
  flex-shrink: 0;
}

.tag-chip.active {
  border-color: var(--q-color-primary);
  color: var(--q-color-primary);
  background-color: var(--q-color-primary-light);
}

.hint {
  font-size: 26rpx;
  color: var(--q-color-text-muted);
}

.qualification-status {
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  padding: 20rpx 24rpx;
}

.qualification-text {
  display: block;
  font-size: 28rpx;
  color: var(--q-color-text);
}

.qualification-meta {
  display: block;
  font-size: 24rpx;
  color: var(--q-color-text-muted);
  margin-top: 8rpx;
}

.qualification-reject {
  display: block;
  font-size: 26rpx;
  color: var(--q-color-danger);
  margin-top: 8rpx;
}

.qualification-upload {
  margin-top: 20rpx;
}

.submitted-license-section {
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
  color: var(--q-color-bg-card);
  font-size: 28rpx;
  text-align: center;
  line-height: 36rpx;
}

.license-add {
  border: 2rpx dashed var(--q-color-text-muted);
  background: var(--q-color-bg-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.license-add-icon {
  font-size: 44rpx;
  color: var(--q-color-primary);
}

.license-add-text {
  font-size: 24rpx;
  color: var(--q-color-text-sub);
}

.qualification-submit {
  width: 100%;
  height: 76rpx;
  border-radius: 38rpx;
  background: var(--q-gradient-primary);
  color: var(--q-color-bg-card);
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
  color: var(--q-color-text-muted);
}

.profile-summary {
  margin-top: 32rpx;
  padding: 20rpx 24rpx;
  border-radius: 12rpx;
  background-color: var(--q-color-bg-card);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 0;
}

.summary-label {
  font-size: 26rpx;
  color: var(--q-color-text-sub);
}

.summary-value {
  font-size: 26rpx;
  color: var(--q-color-text);
  font-weight: 500;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

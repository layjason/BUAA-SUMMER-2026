<template>
  <view class="page">
    <view class="edit-container">
      <PageHeader :title="t('editProfile.title')" />

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

        <!-- 商家资质（只读） -->
        <view v-if="isMerchant && qualification" class="form-item">
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
        </view>

        <FormError :message="formError" />

        <SubmitButton :text="t('editProfile.save')" :loading="saving" @click="handleSave" />
      </view>
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
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { PageHeader, FormInput, FormError, SubmitButton } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

/** 是否为商家用户 */
const isMerchant = computed(() => authStore.userKind === 'merchant')

/** 今日日期字符串，用于生日选择器上限 */
const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
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

/** 昵称唯一性检查已有结果 */
const nicknameChecked = ref(false)
/** 昵称是否可用 */
const nicknameAvailable = ref(false)

/** 商家资质信息（只读） */
const qualification = ref<{
  status: string
  rejectReason?: string
} | null>(null)

/** 资质状态文本映射 */
const qualificationStatusText = computed(() => {
  if (!qualification.value) return ''
  const map: Record<string, string> = {
    not_submitted: t('editProfile.qualStatusNotSubmitted'),
    pending: t('editProfile.qualStatusPending'),
    approved: t('editProfile.qualStatusApproved'),
    rejected: t('editProfile.qualStatusRejected'),
  }
  return map[qualification.value.status] ?? qualification.value.status
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
      const result = await api.get('/identity/nicknames/availability', {
        query: { nickname: value },
      })
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
      const result = (await api.upload('/identity/media/avatar', tempPath)) as {
        mediaId: string
        url: string
      }
      avatarMediaId.value = result.mediaId
      avatarUrl.value = result.url
    } catch {
      formError.value = t('editProfile.avatarUploadFailed')
    }
  } catch {
    /* 用户取消选择 */
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
      const profile = await api.get('/identity/me/merchant-profile')
      formNickname.value = profile.merchantNickname
      formMerchantName.value = profile.merchantName
      if (profile.avatar?.url) avatarUrl.value = profile.avatar.url
      if (profile.interestedActivityFields?.length) {
        selectedTags.value = new Set(profile.interestedActivityFields)
      }
      if (profile.qualification) {
        qualification.value = {
          status: profile.qualification.status,
          rejectReason: profile.qualification.rejectReason,
        }
      }
    } else {
      const profile = await api.get('/identity/me/profile')
      formNickname.value = profile.nickname
      if (profile.avatar?.url) avatarUrl.value = profile.avatar.url
      if (profile.gender) formGender.value = profile.gender
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
    const tags = await api.get('/identity/interest-tags')
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
      await api.patch('/identity/me/merchant-profile', {
        body: {
          merchantNickname: nickname,
          merchantName: formMerchantName.value.trim(),
          avatarMediaId: avatarMediaId.value || undefined,
          interestedActivityFields: [...selectedTags.value],
        },
      })
    } else {
      await api.patch('/identity/me/profile', {
        body: {
          nickname,
          avatarMediaId: avatarMediaId.value || undefined,
          gender: formGender.value || undefined,
          birthday: formBirthday.value || undefined,
          signature: formSignature.value.trim() || undefined,
          interestTags: [...selectedTags.value],
        },
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
  min-height: 100vh;
  background-color: #f7f8fa;
}

.edit-container {
  padding: 32rpx 32rpx 0;
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
</style>

<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="register-container">
        <PageHeader :title="t('register.title')" :subtitle="t('register.subtitle')" />

        <view class="type-tabs">
          <view
            class="type-tab"
            :class="{ active: registerType === 'personal' }"
            @click="switchType('personal')"
          >
            <text>{{ t('register.personal') }}</text>
          </view>
          <view
            class="type-tab"
            :class="{ active: registerType === 'merchant' }"
            @click="switchType('merchant')"
          >
            <text>{{ t('register.merchant') }}</text>
          </view>
        </view>

        <view class="form">
          <FormInput
            v-model="email"
            :label="t('register.email')"
            :placeholder="t('register.emailPlaceholder')"
            :error="emailError"
          />

          <view v-if="registerType === 'personal'" class="form-item">
            <text class="label">{{ t('register.nickname') }}</text>
            <view class="input-wrapper">
              <input
                v-model="nickname"
                class="input"
                type="text"
                :placeholder="t('register.nicknamePlaceholder')"
                placeholder-class="input-placeholder"
                @input="onNicknameInput"
              />
              <text v-if="nicknameChecking" class="nickname-checking hint">{{
                t('register.nicknameChecking')
              }}</text>
              <text v-else-if="nicknameChecked && nicknameAvailable" class="nickname-ok hint">{{
                t('register.nicknameAvailable')
              }}</text>
            </view>
            <text v-if="nicknameError" class="field-error">{{ nicknameError }}</text>
          </view>

          <FormInput
            v-model="password"
            :label="t('register.password')"
            :placeholder="t('register.passwordPlaceholder')"
            type="password"
            :error="passwordError"
          />

          <FormInput
            v-model="confirmPassword"
            :label="t('register.confirmPassword')"
            :placeholder="t('register.confirmPasswordPlaceholder')"
            type="password"
            :error="confirmPasswordError"
          />

          <FormError :message="formError" />

          <SubmitButton :text="t('register.button')" :loading="loading" @click="handleRegister" />

          <view class="footer-links">
            <text class="link" @click="goLogin">{{ t('register.toLogin') }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 注册页
 *
 * 支持个人用户和商家用户注册。
 * 前置条件：用户未登录
 * 后置条件：注册成功提示激活，跳转登录页
 */
import { ref, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { PageHeader, FormInput, FormError, SubmitButton } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

const registerType = ref<'personal' | 'merchant'>('personal')

const email = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const emailError = ref('')
const nicknameError = ref('')
const passwordError = ref('')
const confirmPasswordError = ref('')
const formError = ref('')

/**
 * 若从激活页返回注册，从 store 回填表单
 */
onLoad((options?: { email?: string }) => {
  const saved = authStore.savedRegisterForm
  if (saved) {
    email.value = saved.email
    password.value = saved.password
    nickname.value = saved.nickname
    registerType.value = saved.type
    confirmPassword.value = ''
  } else if (options?.email) {
    email.value = options.email
  }
})

/**
 * 组件卸载时清理昵称防抖定时器
 */
onUnmounted(() => {
  if (nicknameTimer) {
    clearTimeout(nicknameTimer)
    nicknameTimer = null
  }
})

const nicknameChecking = ref(false)
const nicknameChecked = ref(false)
const nicknameAvailable = ref(false)

let nicknameTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 切换注册类型
 *
 * @param type 注册类型
 */
function switchType(type: 'personal' | 'merchant'): void {
  if (registerType.value === type) return
  registerType.value = type
  nicknameError.value = ''
  nicknameChecked.value = false
  nicknameAvailable.value = false
}

/**
 * 昵称输入防抖检查唯一性
 *
 * 输入后等待 500ms，若昵称非空且长度 >= 1 则调用 API 校验；
 * 响应时比对当前输入值，避免竞态条件导致旧结果覆盖新输入
 */
function onNicknameInput(): void {
  nicknameError.value = ''
  nicknameChecked.value = false

  if (nicknameTimer) clearTimeout(nicknameTimer)

  const value = nickname.value.trim()
  if (!value) {
    nicknameChecking.value = false
    return
  }

  nicknameChecking.value = true
  nicknameTimer = setTimeout(async () => {
    try {
      const result = await api.get('/identity/nicknames/availability', {
        query: { nickname: value },
      })
      if (nickname.value.trim() !== value) return

      nicknameAvailable.value = result.available
      nicknameChecked.value = true

      if (!result.available) {
        nicknameError.value = t('register.nicknameUnavailable')
      } else {
        nicknameError.value = ''
      }
    } catch {
      if (nickname.value.trim() !== value) return
      nicknameChecked.value = false
    } finally {
      if (nickname.value.trim() !== value) return
      nicknameChecking.value = false
    }
  }, 500)
}

/**
 * 校验邮箱格式
 *
 * @returns true 表示校验通过
 */
function validateEmail(): boolean {
  emailError.value = ''
  const value = email.value.trim()
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!value) {
    emailError.value = t('register.emailFormatError')
    return false
  }
  if (!emailRegex.test(value)) {
    emailError.value = t('register.emailFormatError')
    return false
  }
  return true
}

/**
 * 校验昵称
 *
 * @returns true 表示校验通过
 */
function validateNickname(): boolean {
  nicknameError.value = ''
  if (registerType.value !== 'personal') return true
  if (!nickname.value.trim()) {
    nicknameError.value = t('register.nicknameRequired')
    return false
  }
  if (nicknameChecked.value && !nicknameAvailable.value) {
    nicknameError.value = t('register.nicknameUnavailable')
    return false
  }
  return true
}

/**
 * 校验密码
 *
 * 前置条件：password 已绑定到输入框
 * 后置条件：通过时无错误信息，不通过时 passwordError 被赋值
 * 规则：长度 8-32 位，大写字母、小写字母、数字、特殊符号四种类型至少包含三种
 *
 * @returns true 表示校验通过
 */
function validatePassword(): boolean {
  passwordError.value = ''
  if (!password.value) {
    passwordError.value = t('register.passwordLengthError')
    return false
  }
  const length = password.value.length
  if (length < 8 || length > 32) {
    passwordError.value = t('register.passwordLengthError')
    return false
  }
  const hasUpper = /[A-Z]/.test(password.value)
  const hasLower = /[a-z]/.test(password.value)
  const hasDigit = /\d/.test(password.value)
  const hasSpecial = /[^A-Za-z0-9]/.test(password.value)
  const typeCount = [hasUpper, hasLower, hasDigit, hasSpecial].filter(Boolean).length
  if (typeCount < 3) {
    passwordError.value = t('register.passwordStrengthError')
    return false
  }
  return true
}

/**
 * 校验确认密码
 *
 * @returns true 表示校验通过
 */
function validateConfirmPassword(): boolean {
  confirmPasswordError.value = ''
  if (!confirmPassword.value) {
    confirmPasswordError.value = t('register.passwordMismatch')
    return false
  }
  if (confirmPassword.value !== password.value) {
    confirmPasswordError.value = t('register.passwordMismatch')
    return false
  }
  return true
}

/**
 * 整体校验
 *
 * @returns true 表示全部校验通过
 */
function validateForm(): boolean {
  formError.value = ''
  const emailValid = validateEmail()
  const nicknameValid = validateNickname()
  const passwordValid = validatePassword()
  const confirmValid = validateConfirmPassword()

  return emailValid && nicknameValid && passwordValid && confirmValid
}

/**
 * 处理注册
 *
 * 根据注册类型调用对应的注册 API，成功则提示并跳转登录页
 */
async function handleRegister() {
  if (loading.value) return
  if (!validateForm()) return

  loading.value = true
  formError.value = ''

  try {
    if (registerType.value === 'personal') {
      await api.post('/identity/auth/register/personal', {
        body: {
          email: email.value.trim(),
          nickname: nickname.value.trim(),
          password: password.value,
        },
      })
    } else {
      await api.post('/identity/auth/register/merchant', {
        body: {
          email: email.value.trim(),
          password: password.value,
        },
      })
    }

    authStore.pendingActivationEmail = email.value.trim()
    authStore.savedRegisterForm = {
      email: email.value.trim(),
      password: password.value,
      nickname: nickname.value.trim(),
      type: registerType.value,
    }

    uni.redirectTo({ url: '/pages/activate/index' })
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '注册失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

function goLogin(): void {
  uni.navigateBack()
}
</script>

<style scoped>
.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f7f8fa;
}

.register-container {
  padding: 80rpx 48rpx 0;
}

.type-tabs {
  display: flex;
  margin-bottom: 40rpx;
  background-color: #ebedf0;
  border-radius: 8rpx;
  padding: 4rpx;
}

.type-tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 6rpx;
  font-size: 28rpx;
  color: #646566;
  transition: all 0.2s;
}

.type-tab.active {
  background-color: #fff;
  color: #1989fa;
  font-weight: 600;
}

.form-item {
  margin-bottom: 28rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 30rpx;
  color: #323233;
  box-sizing: border-box;
}

.input-placeholder {
  color: #c8c9cc;
}

.input-wrapper {
  position: relative;
}

.hint {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 24rpx;
}

.nickname-checking {
  color: #969799;
}

.nickname-ok {
  color: #07c160;
}

.field-error {
  display: block;
  font-size: 24rpx;
  color: #ee0a24;
  margin-top: 8rpx;
}

.footer-links {
  display: flex;
  justify-content: center;
  margin-top: 32rpx;
}

.link {
  font-size: 28rpx;
  color: #1989fa;
}

<style > page {
  height: 100%;
  overflow: hidden;
}
</style>

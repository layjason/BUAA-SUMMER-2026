<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="login-container">
        <PageHeader :title="t('login.title')" :subtitle="t('login.subtitle')" />

        <view class="form">
          <FormInput
            v-model="email"
            :label="t('login.email')"
            :placeholder="t('login.emailPlaceholder')"
            :error="emailError"
          />
          <FormInput
            v-model="password"
            :label="t('login.password')"
            :placeholder="t('login.passwordPlaceholder')"
            type="password"
            :error="passwordError"
          />

          <FormError :message="formError" />

          <SubmitButton :text="t('login.button')" :loading="loading" @click="handleLogin" />

          <view class="footer-links">
            <text class="link" @click="goForgotPassword">{{ t('login.forgotPassword') }}</text>
            <text class="link" @click="goRegister">{{ t('login.toRegister') }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 登录页
 *
 * 实现邮箱密码登录功能。
 * 前置条件：用户未登录
 * 后置条件：登录成功跳转首页 TabBar
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { PageHeader, FormInput, FormError, SubmitButton } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)

const emailError = ref('')
const passwordError = ref('')
const formError = ref('')

/**
 * 若已登录，直接跳转首页
 */
onMounted(() => {
  if (authStore.isLoggedIn) {
    uni.switchTab({ url: '/pages/home/index' })
  }
})

/**
 * 校验邮箱格式
 *
 * @returns true 表示校验通过
 */
function validateEmail(): boolean {
  emailError.value = ''
  const value = email.value.trim()
  if (!value) {
    emailError.value = ''
    return false
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(value)) {
    emailError.value = t('login.emailFormatError')
    return false
  }
  return true
}

/**
 * 校验密码非空
 *
 * @returns true 表示校验通过
 */
function validatePassword(): boolean {
  passwordError.value = ''
  if (!password.value) {
    passwordError.value = ''
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
  const passwordValid = validatePassword()

  if (!email.value.trim() && !password.value) {
    formError.value = t('login.emailFormatError')
    return false
  }
  if (!email.value.trim()) {
    formError.value = t('login.emailFormatError')
    return false
  }
  if (!password.value) {
    formError.value = t('login.passwordRequired')
    return false
  }
  if (!emailValid) return false
  if (!passwordValid) return false
  return true
}

/**
 * 处理登录
 *
 * 调用登录 API，成功则跳转首页，失败则展示错误提示；
 * 若账号未激活（10004）则直接跳转激活页
 */
async function handleLogin() {
  if (loading.value) return
  if (!validateForm()) return

  loading.value = true
  formError.value = ''

  try {
    await authStore.login(email.value.trim(), password.value)
    uni.switchTab({ url: '/pages/home/index' })
  } catch (error) {
    if (error instanceof BusinessError) {
      if (error.code === 10004) {
        loading.value = false
        authStore.pendingActivationEmail = email.value.trim()
        authStore.autoResendActivation = true
        authStore.activationEntrySource = 'login'
        authStore.savedRegisterForm = {
          email: email.value.trim(),
          password: password.value,
          nickname: '',
          type: 'personal',
        }
        uni.redirectTo({ url: '/pages/activate/index' })
        return
      }
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '登录失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

function goRegister(): void {
  uni.navigateTo({ url: '/pages/register/index' })
}

function goForgotPassword(): void {
  uni.navigateTo({ url: '/pages/forgot-password/index' })
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
  background-color: var(--q-color-bg);
}

.login-container {
  padding: 120rpx 48rpx calc(80rpx + env(safe-area-inset-bottom));
}

.footer-links {
  display: flex;
  justify-content: space-between;
  margin-top: 32rpx;
  padding: 0 4rpx;
}

.link {
  font-size: 28rpx;
  color: var(--q-color-primary);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

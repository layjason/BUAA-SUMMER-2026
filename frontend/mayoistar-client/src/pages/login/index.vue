<template>
  <view class="page">
    <view class="login-container">
      <!-- 标题区域 -->
      <view class="header">
        <text class="title">{{ t('login.title') }}</text>
        <text class="subtitle">{{ t('login.subtitle') }}</text>
      </view>

      <!-- 表单区域 -->
      <view class="form">
        <!-- 邮箱 -->
        <view class="form-item">
          <text class="label">{{ t('login.email') }}</text>
          <input
            v-model="email"
            class="input"
            type="text"
            :placeholder="t('login.emailPlaceholder')"
            placeholder-class="input-placeholder"
          />
          <text v-if="emailError" class="field-error">{{ emailError }}</text>
        </view>

        <!-- 密码 -->
        <view class="form-item">
          <text class="label">{{ t('login.password') }}</text>
          <input
            v-model="password"
            class="input"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            placeholder-class="input-placeholder"
          />
          <text v-if="passwordError" class="field-error">{{ passwordError }}</text>
        </view>

        <!-- 错误提示 -->
        <view v-if="formError" class="form-error">
          <text>{{ formError }}</text>
        </view>

        <!-- 登录按钮 -->
        <button class="login-btn" :disabled="loading" :loading="loading" @click="handleLogin">
          {{ loading ? '' : t('login.button') }}
        </button>

        <!-- 忘记密码 -->
        <view class="forgot-password">
          <text class="link" @click="goForgotPassword">{{ t('login.forgotPassword') }}</text>
        </view>

        <!-- 底部链接 -->
        <view class="footer-links">
          <text class="link" @click="goRegister">{{ t('login.toRegister') }}</text>
        </view>
      </view>
    </view>
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
    emailError.value = '' // 邮箱为空不作为单独字段错误，按钮校验时统一处理
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
    passwordError.value = '' // 密码为空不作为单独字段错误
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
        // 保存表单数据，供激活页"邮箱不对？重新注册"回填
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

/**
 * 跳转注册页
 */
function goRegister(): void {
  uni.navigateTo({ url: '/pages/register/index' })
}

/**
 * 跳转忘记密码页
 */
function goForgotPassword(): void {
  uni.navigateTo({ url: '/pages/forgot-password/index' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

.login-container {
  padding: 120rpx 48rpx 0;
}

.header {
  margin-bottom: 64rpx;
}

.title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #323233;
  margin-bottom: 16rpx;
}

.subtitle {
  display: block;
  font-size: 28rpx;
  color: #969799;
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

.field-error {
  display: block;
  font-size: 24rpx;
  color: #ee0a24;
  margin-top: 8rpx;
}

.form-error {
  background-color: #fff2f0;
  border: 1rpx solid #ffccc7;
  border-radius: 8rpx;
  padding: 16rpx 24rpx;
  margin-bottom: 24rpx;
  font-size: 26rpx;
  color: #ee0a24;
}

.login-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background-color: #1989fa;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  border: none;
  margin-top: 16rpx;
}

.login-btn[disabled] {
  opacity: 0.6;
}

.forgot-password {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
  padding-right: 4rpx;
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
</style>

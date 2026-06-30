<template>
  <view class="page">
    <view class="container">
      <view class="header">
        <text class="title">{{ t('forgotPassword.title') }}</text>
        <text class="subtitle">{{ t('forgotPassword.subtitle') }}</text>
      </view>

      <view v-if="sent" class="success-box">
        <text class="success-icon">📧</text>
        <text class="success-text">{{ t('forgotPassword.emailSent') }}</text>
        <text class="success-email">{{ email.trim() }}</text>
        <text class="success-hint">{{ t('forgotPassword.emailSentHint') }}</text>
        <button
          class="action-btn"
          :disabled="loading || cooldown > 0"
          :loading="loading"
          @click="handleResend"
        >
          {{
            cooldown > 0
              ? t('forgotPassword.resendCooldown', { seconds: cooldown })
              : t('forgotPassword.resendButton')
          }}
        </button>
        <text v-if="resendSent" class="resend-ok">{{ t('forgotPassword.resendSent') }}</text>
        <button class="action-btn secondary" @click="goLogin">
          {{ t('forgotPassword.backToLogin') }}
        </button>
      </view>

      <view v-else class="form">
        <view class="form-item">
          <text class="label">{{ t('forgotPassword.email') }}</text>
          <input
            v-model="email"
            class="input"
            type="text"
            :placeholder="t('forgotPassword.emailPlaceholder')"
            placeholder-class="input-placeholder"
          />
          <text v-if="emailError" class="field-error">{{ emailError }}</text>
        </view>

        <view v-if="formError" class="form-error">
          <text>{{ formError }}</text>
        </view>

        <button class="action-btn" :disabled="loading" :loading="loading" @click="handleSend">
          {{ loading ? '' : t('forgotPassword.sendButton') }}
        </button>

        <view class="footer-links">
          <text class="link" @click="goLogin">{{ t('forgotPassword.backToLogin') }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 忘记密码页 — 发送密码重置邮件
 *
 * 前置条件：用户未登录
 * 后置条件：发送成功后提示用户前往邮件链接，密码重置由网页端处理
 */
import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'

const { t } = useI18n()

const email = ref('')
const loading = ref(false)
const sent = ref(false)
const resendSent = ref(false)

const emailError = ref('')
const formError = ref('')

/** 重发冷却倒计时（秒） */
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

/** 密码重置邮件重发冷却时长（秒） */
const RESEND_COOLDOWN_SECONDS = 60

function validateEmail(): boolean {
  emailError.value = ''
  const value = email.value.trim()
  if (!value) {
    emailError.value = t('forgotPassword.emailRequired')
    return false
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(value)) {
    emailError.value = t('forgotPassword.emailFormatError')
    return false
  }
  return true
}

/**
 * 发送重置邮件（首次发送或重发共用）
 */
async function doSend(): Promise<void> {
  loading.value = true
  formError.value = ''

  try {
    await api.post('/identity/auth/password-reset-email', {
      body: { email: email.value.trim() },
    })
    sent.value = true
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = t('网络错误')
    }
  } finally {
    loading.value = false
  }
}

async function handleSend() {
  if (loading.value) return
  if (!validateEmail()) return
  await doSend()
}

/**
 * 重发重置邮件
 *
 * 前置条件：已发送过邮件，不在冷却期内
 * 后置条件：成功时开始 60 秒冷却倒计时
 */
async function handleResend(): Promise<void> {
  if (loading.value || cooldown.value > 0) return
  resendSent.value = false
  await doSend()
  if (sent.value) {
    startCooldown()
    resendSent.value = true
  }
}

/**
 * 启动重发冷却倒计时
 */
function startCooldown(): void {
  cooldown.value = RESEND_COOLDOWN_SECONDS
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value--
    if (cooldown.value <= 0) {
      if (cooldownTimer) clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

function goLogin(): void {
  uni.navigateBack()
}

/**
 * 组件卸载时清理冷却定时器
 */
onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

.container {
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

.action-btn {
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

.action-btn[disabled] {
  opacity: 0.6;
}

.action-btn.secondary {
  background-color: #fff;
  color: #1989fa;
  border: 1rpx solid #1989fa;
}

.resend-ok {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: #07c160;
  margin-bottom: 16rpx;
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

.success-box {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 56rpx 32rpx;
  text-align: center;
  margin-bottom: 40rpx;
}

.success-icon {
  display: block;
  font-size: 64rpx;
  margin-bottom: 24rpx;
}

.success-text {
  display: block;
  font-size: 30rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.success-email {
  display: block;
  font-size: 32rpx;
  color: #1989fa;
  font-weight: 600;
  margin-bottom: 12rpx;
  word-break: break-all;
}

.success-hint {
  display: block;
  font-size: 26rpx;
  color: #969799;
  line-height: 1.6;
  margin-bottom: 32rpx;
}
</style>

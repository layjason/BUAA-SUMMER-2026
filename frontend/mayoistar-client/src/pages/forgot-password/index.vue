<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="container">
        <PageHeader :title="t('forgotPassword.title')" :subtitle="t('forgotPassword.subtitle')" />

        <view v-if="sent" class="success-box">
          <text class="success-icon">📧</text>
          <text class="success-text">{{ t('forgotPassword.emailSent') }}</text>
          <text class="success-email">{{ email.trim() }}</text>
          <text class="success-hint">{{ t('forgotPassword.emailSentHint') }}</text>
          <CooldownButton
            :text="t('forgotPassword.resendButton')"
            :cooldown-text="cooldownText"
            :cooldown="cooldown"
            :loading="loading"
            @click="handleResend"
          />
          <text v-if="resendSent" class="resend-ok">{{ t('forgotPassword.resendSent') }}</text>
          <SubmitButton :text="t('forgotPassword.backToLogin')" secondary @click="goLogin" />
        </view>

        <view v-else class="form">
          <FormInput
            v-model="email"
            :label="t('forgotPassword.email')"
            :placeholder="t('forgotPassword.emailPlaceholder')"
            :error="emailError"
          />

          <FormError :message="formError" />

          <SubmitButton
            :text="t('forgotPassword.sendButton')"
            :loading="loading"
            @click="handleSend"
          />

          <view class="footer-links">
            <text class="link" @click="goLogin">{{ t('forgotPassword.backToLogin') }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 忘记密码页 — 发送密码重置邮件
 *
 * 前置条件：用户未登录
 * 后置条件：发送成功后提示用户前往邮件链接，密码重置由网页端处理
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatI18nTemplate } from '@/utils/i18n-template'
import {
  PageHeader,
  FormInput,
  FormError,
  SubmitButton,
  CooldownButton,
  useCooldown,
} from '@/components'

const { t, tm } = useI18n()

const email = ref('')
const loading = ref(false)
const sent = ref(false)
const resendSent = ref(false)

const emailError = ref('')
const formError = ref('')

const { cooldown, startCooldown } = useCooldown(60)

/** 用 watch 显式监听 cooldown 变化后格式化原始模板，避免 uni-app 原生端显示 `{seconds}` 占位符 */
const cooldownText = ref('')
watch(
  cooldown,
  (val) => {
    cooldownText.value = formatI18nTemplate(String(tm('forgotPassword.resendCooldown')), {
      seconds: val,
    })
  },
  { immediate: true },
)

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
 * 后置条件：成功时开始 60 秒冷却倒计时；频率限制错误（10018）也启动冷却
 */
async function handleResend(): Promise<void> {
  if (loading.value || cooldown.value > 0) return
  loading.value = true
  resendSent.value = false
  formError.value = ''

  try {
    await api.post('/identity/auth/password-reset-email', {
      body: { email: email.value.trim() },
    })
    resendSent.value = true
    startCooldown()
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
      if (error.code === 10018) startCooldown()
    } else {
      formError.value = t('网络错误')
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

.container {
  padding: 120rpx 48rpx 48rpx;
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

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

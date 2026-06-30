<template>
  <view class="page">
    <view class="activate-container">
      <view class="header">
        <text class="title">{{ t('activate.title') }}</text>
      </view>

      <!-- 正在激活 -->
      <view v-if="state === 'activating'" class="status-box">
        <text class="status-icon">⏳</text>
        <text class="status-text">{{ t('activate.activating') }}</text>
      </view>

      <!-- 激活成功 / 已激活 -->
      <view v-else-if="state === 'success'" class="status-box success">
        <text class="status-icon">✅</text>
        <text class="status-text">{{ t('activate.success') }}</text>
        <button class="action-btn" @click="goLogin">{{ t('activate.goLogin') }}</button>
      </view>

      <!-- 已发送激活邮件（正常注册流程） -->
      <view v-else-if="state === 'sent'" class="status-box info">
        <text class="status-icon">📧</text>
        <text class="status-text">{{ t('activate.emailSent') }}</text>
        <text class="status-email">{{ pendingEmail }}</text>
        <text class="status-hint">{{ t('activate.emailSentHint') }}</text>
      </view>

      <!-- 自动激活失败（有 token 有邮箱） -->
      <view v-else-if="state === 'error'" class="status-box error">
        <text class="status-text">{{ errorText || t('activate.failed') }}</text>
      </view>

      <!-- 无 token 且无邮箱（兜底） -->
      <view v-else class="status-box error">
        <text class="status-text">{{ t('activate.noToken') }}</text>
      </view>

      <!-- 操作按钮：有邮箱时显示 resend + 重新注册 -->
      <view v-if="pendingEmail && (state === 'sent' || state === 'error')" class="action-section">
        <button
          class="action-btn"
          :disabled="sending || cooldown > 0"
          :loading="sending"
          @click="handleResend"
        >
          {{
            cooldown > 0
              ? t('activate.resendCooldown', { seconds: cooldown })
              : t('activate.resendButton')
          }}
        </button>
        <text v-if="resendSent" class="resend-ok">{{ t('activate.resendSent') }}</text>

        <button class="action-btn secondary" @click="goRegister">
          {{ t('activate.emailWrong') }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 账号激活页
 *
 * 三种进入路径：
 *   APP 注册后跳转 → 有 pendingEmail 无 token → 显示"邮件已发送"
 *   Web 邮件链接 → scheme 打开 → 有 token → 自动激活
 *   直接访问     → 无 pendingEmail 无 token → 兜底提示
 *
 * 前置条件：注册时存储 pendingActivationEmail
 * 后置条件：激活成功清除 pending 邮箱，跳转登录页
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'

const { t } = useI18n()
const authStore = useAuthStore()

const state = ref<'activating' | 'success' | 'error' | 'sent' | 'idle'>('activating')
const errorText = ref('')
const pendingEmail = ref('')
const sending = ref(false)
const resendSent = ref(false)

/** 重发邮件冷却倒计时（秒） */
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

/** 激活邮件重发冷却时长（秒） */
const RESEND_COOLDOWN_SECONDS = 60

/**
 * 从页面参数中提取 token
 */
function parseToken(): string | null {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  const options = (page as Record<string, unknown> | undefined)?.options as
    Record<string, string> | undefined
  return options?.token ?? null
}

onMounted(async () => {
  pendingEmail.value = authStore.pendingActivationEmail || ''

  const token = parseToken()

  if (token) {
    // 有 token：自动激活（来自 scheme 或链接）
    try {
      await api.post('/identity/auth/activate', { body: { token } })
      cleanupAndSuccess()
    } catch (error) {
      if (error instanceof BusinessError && error.code === 10012) {
        cleanupAndSuccess()
      } else if (error instanceof BusinessError) {
        state.value = 'error'
        errorText.value = getErrorMessage(error.code)
      } else {
        state.value = 'error'
        errorText.value = t('网络错误')
      }
    }
  } else if (pendingEmail.value) {
    // 无 token 有邮箱：APP 注册后正常进入，显示邮件已发送
    state.value = 'sent'
    // 从登录页 10004 跳转过来时自动调用重发
    if (authStore.autoResendActivation) {
      authStore.autoResendActivation = false
      await handleResend()
    }
  } else {
    // 无 token 无邮箱：兜底
    state.value = 'idle'
  }
})

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

function cleanupAndSuccess(): void {
  authStore.pendingActivationEmail = null
  authStore.savedRegisterForm = null
  state.value = 'success'
}

/**
 * 重发激活邮件
 *
 * 前置条件：pendingEmail 已从存储中读取，不在冷却期内
 * 后置条件：成功时开始 60 秒冷却倒计时
 */
async function handleResend(): Promise<void> {
  if (!pendingEmail.value || sending.value || cooldown.value > 0) return
  sending.value = true
  resendSent.value = false

  try {
    await api.post('/identity/auth/activation-email', {
      body: { email: pendingEmail.value },
    })
    resendSent.value = true
    startCooldown()
  } catch (error) {
    if (error instanceof BusinessError) {
      errorText.value = getErrorMessage(error.code)
      state.value = 'error'
      // 频率限制错误也启动冷却
      if (error.code === 10015) startCooldown()
    } else {
      errorText.value = t('网络错误')
      state.value = 'error'
    }
  } finally {
    sending.value = false
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
  uni.redirectTo({ url: '/pages/login/index' })
}

function goRegister(): void {
  uni.redirectTo({ url: '/pages/register/index' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

.activate-container {
  padding: 160rpx 48rpx 0;
}

.header {
  margin-bottom: 56rpx;
}

.title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #323233;
}

.status-box {
  background-color: #fff;
  border-radius: 12rpx;
  padding: 56rpx 32rpx;
  text-align: center;
  margin-bottom: 40rpx;
}

.status-icon {
  display: block;
  font-size: 64rpx;
  margin-bottom: 24rpx;
}

.status-text {
  display: block;
  font-size: 30rpx;
  color: #323233;
  margin-bottom: 16rpx;
  line-height: 1.6;
}

.status-email {
  display: block;
  font-size: 32rpx;
  color: #1989fa;
  font-weight: 600;
  margin-bottom: 12rpx;
  word-break: break-all;
}

.status-hint {
  display: block;
  font-size: 26rpx;
  color: #969799;
  line-height: 1.6;
}

.action-section {
  padding: 0 0 40rpx;
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
  margin-bottom: 24rpx;
}

.action-btn.secondary {
  background-color: #fff;
  color: #1989fa;
  border: 1rpx solid #1989fa;
}

.action-btn[disabled] {
  opacity: 0.6;
}

.resend-ok {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: #07c160;
  margin-bottom: 16rpx;
}
</style>

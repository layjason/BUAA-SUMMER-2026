<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="activate-container">
        <PageHeader :title="t('activate.title')" />

        <!-- 正在激活 -->
        <view v-if="state === 'activating'" class="status-box">
          <text class="status-icon">⏳</text>
          <text class="status-text">{{ t('activate.activating') }}</text>
        </view>

        <!-- 激活成功 / 已激活 -->
        <view v-else-if="state === 'success'" class="status-box success">
          <text class="status-icon">✅</text>
          <text class="status-text">{{ t('activate.success') }}</text>
          <SubmitButton :text="t('activate.goLogin')" @click="goLogin" />
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
          <CooldownButton
            :text="t('activate.resendButton')"
            :cooldown-text="t('activate.resendCooldown')"
            :cooldown="cooldown"
            :loading="sending"
            @click="handleResend"
          />
          <text v-if="resendSent" class="resend-ok">{{ t('activate.resendSent') }}</text>

          <SubmitButton :text="t('activate.emailWrong')" secondary @click="goRegister" />
        </view>
      </view>
    </scroll-view>
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
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { PageHeader, SubmitButton, CooldownButton, useCooldown } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

const state = ref<'activating' | 'success' | 'error' | 'sent' | 'idle'>('activating')
const errorText = ref('')
const pendingEmail = ref('')
const sending = ref(false)
const resendSent = ref(false)

const { cooldown, startCooldown } = useCooldown(60)

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
    state.value = 'sent'
    // 从登录页 10004 跳转过来时自动调用重发
    if (authStore.autoResendActivation) {
      authStore.autoResendActivation = false
      await handleResend()
    }
  } else {
    state.value = 'idle'
  }
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
      if (error.code === 10015) startCooldown()
    } else {
      errorText.value = t('网络错误')
      state.value = 'error'
    }
  } finally {
    sending.value = false
  }
}

function goLogin(): void {
  uni.redirectTo({ url: '/pages/login/index' })
}

function goRegister(): void {
  uni.redirectTo({ url: '/pages/register/index' })
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

.activate-container {
  padding: 160rpx 48rpx 48rpx;
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

.resend-ok {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: #07c160;
  margin-bottom: 16rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

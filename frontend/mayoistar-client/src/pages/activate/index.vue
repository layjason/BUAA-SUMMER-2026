<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="activate-container">
        <PageHeader :title="t('activate.title')" />

        <view class="status-card" :class="statusClass">
          <view class="status-mark">
            <text class="status-mark-text">{{ statusIcon }}</text>
          </view>
          <text class="status-title">{{ statusTitle }}</text>
          <text v-if="statusSubtitle" class="status-subtitle">{{ statusSubtitle }}</text>
          <text v-if="pendingEmail && state !== 'success'" class="status-email">{{
            pendingEmail
          }}</text>
        </view>

        <view v-if="state === 'activating'" class="progress-panel">
          <view class="progress-line" />
          <text class="progress-text">{{ t('activate.activatingHint') }}</text>
        </view>

        <view v-else-if="state === 'success' && shouldShowLoginButton" class="action-section">
          <SubmitButton :text="t('activate.goLogin')" @click="goLogin" />
        </view>

        <view v-else-if="state === 'success' && entrySource === 'login'" class="progress-panel">
          <view class="progress-line" />
          <text class="progress-text">{{ t('activate.autoLoginHint') }}</text>
        </view>

        <!-- 操作按钮：有邮箱且尚未确认激活时显示查询、重发与重新注册 -->
        <view v-if="pendingEmail && (state === 'sent' || state === 'error')" class="action-section">
          <SubmitButton
            :text="t('activate.checkStatusButton')"
            :loading="checking"
            :disabled="sending"
            @click="handleCheckActivationStatus"
          />
          <CooldownButton
            :text="t('activate.resendButton')"
            :cooldown-text="cooldownText"
            :cooldown="cooldown"
            :loading="sending"
            :disabled="checking"
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
 * 前置条件：注册或登录未激活账号时存储 pendingActivationEmail 和 savedRegisterForm
 * 后置条件：激活成功后，注册来源展示登录入口，登录来源自动登录并跳转首页
 */
import { computed, ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatI18nTemplate } from '@/utils/i18n-template'
import { PageHeader, SubmitButton, CooldownButton, useCooldown } from '@/components'

const { t, tm } = useI18n()
const authStore = useAuthStore()

const state = ref<'activating' | 'success' | 'error' | 'sent' | 'idle'>('activating')
const errorText = ref('')
const pendingEmail = ref('')
const entrySource = ref(authStore.activationEntrySource)
const sending = ref(false)
const checking = ref(false)
const resendSent = ref(false)

const { cooldown, startCooldown } = useCooldown(60)

const statusClass = computed(() => ({
  'status-card--success': state.value === 'success',
  'status-card--error': state.value === 'error' || state.value === 'idle',
  'status-card--pending': state.value === 'sent' || state.value === 'activating',
}))

const statusIcon = computed(() => {
  if (state.value === 'success') return '✓'
  if (state.value === 'error' || state.value === 'idle') return '!'
  return '•'
})

const statusTitle = computed(() => {
  if (state.value === 'activating') return t('activate.activating')
  if (state.value === 'success') return t('activate.success')
  if (state.value === 'sent') return t('activate.emailSent')
  if (state.value === 'error') return errorText.value || t('activate.failed')
  return t('activate.noToken')
})

const statusSubtitle = computed(() => {
  if (state.value === 'success') {
    return entrySource.value === 'login'
      ? t('activate.successAutoLoginHint')
      : t('activate.successHint')
  }
  if (state.value === 'sent') return t('activate.emailSentHint')
  if (state.value === 'error' && pendingEmail.value) return t('activate.checkStatusHint')
  return ''
})

const shouldShowLoginButton = computed(
  () => state.value === 'success' && entrySource.value !== 'login',
)

/** 用 watch 显式监听 cooldown 变化后格式化原始模板，避免 uni-app 原生端显示 `{seconds}` 占位符 */
const cooldownText = ref('')
watch(
  cooldown,
  (val) => {
    cooldownText.value = formatI18nTemplate(String(tm('activate.resendCooldown')), {
      seconds: val,
    })
  },
  { immediate: true },
)

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
  entrySource.value = authStore.activationEntrySource

  const token = parseToken()

  if (token) {
    try {
      await api.post('/identity/auth/activate', { body: { token } })
      await handleActivationSuccess()
    } catch (error) {
      if (error instanceof BusinessError && error.code === 10012) {
        await handleActivationSuccess()
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
    } else {
      startCooldown()
    }
  } else {
    state.value = 'idle'
  }
})

/**
 * 清理激活临时状态并展示激活成功。
 *
 * 前置条件：账号已被后端确认激活。
 * 后置条件：注册来源保留成功页登录入口；登录来源由调用方决定是否自动登录。
 */
function cleanupAndSuccess(): void {
  authStore.pendingActivationEmail = null
  authStore.savedRegisterForm = null
  authStore.autoResendActivation = false
  authStore.activationEntrySource = null
  state.value = 'success'
}

/**
 * 激活成功后的统一后续动作。
 *
 * 前置条件：账号已激活；若来源为登录页，savedRegisterForm 中保留登录邮箱和密码。
 * 后置条件：登录来源自动完成登录并进入首页；其它来源展示成功状态和登录入口。
 */
async function handleActivationSuccess(): Promise<void> {
  if (entrySource.value === 'login') {
    await autoLoginAfterActivation()
    return
  }
  cleanupAndSuccess()
}

/**
 * 查询当前邮箱对应账号是否已经激活。
 *
 * 前置条件：pendingEmail 与 savedRegisterForm.password 来自注册页或登录页未激活流程。
 * 后置条件：已激活时更新为成功状态；登录来源会自动登录并跳转首页。
 */
async function handleCheckActivationStatus(): Promise<void> {
  if (!pendingEmail.value || checking.value || sending.value) return

  const password = authStore.savedRegisterForm?.password
  if (!password) {
    state.value = 'error'
    errorText.value = t('activate.missingCredential')
    return
  }

  checking.value = true
  resendSent.value = false
  try {
    if (entrySource.value === 'login') {
      await authStore.login(pendingEmail.value, password)
      uni.switchTab({ url: '/pages/home/index' })
      return
    }

    await api.post('/identity/auth/login', {
      body: { email: pendingEmail.value, password },
    })
    cleanupAndSuccess()
  } catch (error) {
    if (error instanceof BusinessError && error.code === 10004) {
      state.value = 'sent'
      errorText.value = ''
    } else if (error instanceof BusinessError) {
      state.value = 'error'
      errorText.value = getErrorMessage(error.code)
    } else {
      state.value = 'error'
      errorText.value = t('网络错误')
    }
  } finally {
    checking.value = false
  }
}

/**
 * 激活后自动登录。
 *
 * 前置条件：登录页跳转激活页时保存了邮箱和密码。
 * 后置条件：登录成功后进入首页；登录失败则留在激活成功状态，由用户手动前往登录。
 */
async function autoLoginAfterActivation(): Promise<void> {
  const password = authStore.savedRegisterForm?.password
  const email = pendingEmail.value
  cleanupAndSuccess()

  if (!email || !password) {
    entrySource.value = 'register'
    return
  }

  checking.value = true
  try {
    await authStore.login(email, password)
    uni.switchTab({ url: '/pages/home/index' })
  } catch {
    entrySource.value = 'register'
  } finally {
    checking.value = false
  }
}

/**
 * 重发激活邮件
 *
 * 前置条件：pendingEmail 已从存储中读取，不在冷却期内
 * 后置条件：成功时开始 60 秒冷却倒计时
 */
async function handleResend(): Promise<void> {
  if (!pendingEmail.value || sending.value || checking.value || cooldown.value > 0) return
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

/**
 * 前往登录页。
 *
 * 前置条件：账号已激活或用户主动离开激活流程。
 * 后置条件：当前页面被登录页替换。
 */
function goLogin(): void {
  uni.redirectTo({ url: '/pages/login/index' })
}

/**
 * 返回注册页重新填写信息。
 *
 * 前置条件：激活页保留了待激活邮箱或注册表单。
 * 后置条件：跳转注册页，注册页从 store 回填可复用字段。
 */
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
  background-color: var(--q-color-bg);
}

.activate-container {
  padding: 160rpx 48rpx 48rpx;
}

.status-card {
  background-color: var(--q-color-bg-card);
  border: 1rpx solid var(--q-color-border-light);
  border-radius: 16rpx;
  padding: 48rpx 36rpx;
  margin-bottom: 32rpx;
  box-shadow: 0 12rpx 32rpx rgba(17, 24, 39, 0.06);
}

.status-card--pending {
  border-color: rgba(37, 99, 235, 0.18);
}

.status-card--success {
  border-color: rgba(22, 160, 133, 0.25);
}

.status-card--error {
  border-color: rgba(220, 38, 38, 0.2);
}

.status-mark {
  width: 72rpx;
  height: 72rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40rpx;
}

.status-card--pending .status-mark {
  background-color: rgba(37, 99, 235, 0.1);
  color: var(--q-color-info);
}

.status-card--success .status-mark {
  background-color: rgba(22, 160, 133, 0.12);
  color: var(--q-color-success);
}

.status-card--error .status-mark {
  background-color: rgba(220, 38, 38, 0.1);
  color: var(--q-color-danger);
}

.status-mark-text {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.status-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--q-color-text);
  line-height: 1.35;
  margin-bottom: 18rpx;
}

.status-subtitle {
  display: block;
  font-size: 26rpx;
  color: var(--q-color-text-muted);
  line-height: 1.65;
}

.status-email {
  display: block;
  font-size: 30rpx;
  color: var(--q-color-text);
  font-weight: 600;
  line-height: 1.45;
  margin-top: 24rpx;
  padding: 22rpx 24rpx;
  background-color: var(--q-color-bg-soft);
  border-radius: 12rpx;
  word-break: break-all;
}

.progress-panel {
  background-color: var(--q-color-bg-card);
  border-radius: 12rpx;
  padding: 28rpx;
  margin-bottom: 32rpx;
}

.progress-line {
  height: 6rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #16a085 0%, #2563eb 100%);
  margin-bottom: 20rpx;
}

.progress-text {
  display: block;
  font-size: 26rpx;
  color: var(--q-color-text-muted);
  line-height: 1.5;
  text-align: center;
}

.action-section {
  padding: 0 0 40rpx;
}

.resend-ok {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: var(--q-color-success);
  margin-bottom: 16rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

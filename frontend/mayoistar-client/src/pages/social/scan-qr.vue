<template>
  <view class="page">
    <AppNavbar title="扫码加好友" />

    <!-- H5：相机扫码区域 -->
    <!-- #ifdef H5 -->
    <view class="scanner-wrap">
      <view id="qr-reader" class="scanner" />
      <view v-if="cameraError" class="scanner-fallback">
        <text class="scanner-fallback__title">无法打开相机</text>
        <text class="scanner-fallback__desc">{{ cameraError }}</text>
        <view class="scanner-fallback__btn" @tap="onManualInput">
          <text>手动输入二维码</text>
        </view>
      </view>
      <view v-else-if="submitting" class="scanner-overlay">
        <text class="scanner-overlay__text">正在发送好友申请...</text>
      </view>
    </view>

    <view class="hint-section">
      <text class="hint-text">将好友的个人二维码放入框内，即可自动识别</text>
      <view class="manual-btn" @tap="onManualInput">
        <text class="manual-btn__text">手动输入</text>
      </view>
    </view>
    <!-- #endif -->

    <!-- 非 H5：由脚本直接调起 uni.scanCode -->
    <!-- #ifndef H5 -->
    <view class="native-placeholder">
      <text class="native-placeholder__text">正在打开扫码...</text>
    </view>
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
/**
 * 扫码加好友页面
 *
 * H5 使用 html5-qrcode 展示相机扫码 UI；App/小程序调起 uni.scanCode。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import AppNavbar from '@/components/base/AppNavbar.vue'
import { submitPersonalQrScan, promptManualQrInput } from '@/utils/personal-qr'
import { resolveApiError } from '@/utils/error'

const submitting = ref(false)
const cameraError = ref('')

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let html5QrCode: any = null

async function stopH5Scanner(): Promise<void> {
  if (!html5QrCode) return
  try {
    const state = html5QrCode.getState()
    if (state === 2) {
      await html5QrCode.stop()
    }
    html5QrCode.clear()
  } catch {
    /* 忽略停止扫码时的竞态错误 */
  }
  html5QrCode = null
}

async function handleScanResult(token: string): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    await submitPersonalQrScan(token)
    uni.showToast({ title: '申请已发送', icon: 'success' })
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/messages/index' })
      }
    }, 600)
  } catch (error) {
    submitting.value = false
    uni.showToast({ title: resolveApiError(error, '扫码加好友失败'), icon: 'none' })
    // #ifdef H5
    await startH5Scanner()
    // #endif
  }
}

async function onManualInput(): Promise<void> {
  await stopH5Scanner()
  const manual = await promptManualQrInput()
  if (!manual) {
    // #ifdef H5
    await startH5Scanner()
    // #endif
    return
  }
  await handleScanResult(manual)
}

async function startH5Scanner(): Promise<void> {
  // #ifdef H5
  cameraError.value = ''
  try {
    const { Html5Qrcode } = await import('html5-qrcode')
    html5QrCode = new Html5Qrcode('qr-reader')
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 240, height: 240 } },
      async (decodedText: string) => {
        await stopH5Scanner()
        await handleScanResult(decodedText)
      },
      () => {
        /* 持续扫描中的空帧，忽略 */
      },
    )
  } catch (error) {
    cameraError.value =
      error instanceof Error ? error.message : '请检查浏览器相机权限，或使用手动输入'
  }
  // #endif
}

function runNativeScan(): void {
  // #ifndef H5
  uni.scanCode({
    onlyFromCamera: false,
    scanType: ['qrCode'],
    success: async (res) => {
      await handleScanResult(res.result)
    },
    fail: async () => {
      const manual = await promptManualQrInput()
      if (manual) {
        await handleScanResult(manual)
        return
      }
      uni.navigateBack()
    },
  })
  // #endif
}

onMounted(() => {
  // #ifdef H5
  startH5Scanner()
  // #endif
  // #ifndef H5
  runNativeScan()
  // #endif
})

onUnmounted(() => {
  // #ifdef H5
  stopH5Scanner()
  // #endif
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: #000000;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.scanner-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.scanner {
  width: 100%;
  height: 100%;
}

.scanner-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  gap: $spacing-md;
  background: rgba(0, 0, 0, 0.85);
}

.scanner-fallback__title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: #ffffff;
}

.scanner-fallback__desc {
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.7);
  text-align: center;
  line-height: 1.5;
}

.scanner-fallback__btn {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-xl;
  background: $color-primary;
  border-radius: $radius-full;

  text {
    font-size: $font-sm;
    color: #ffffff;
  }
}

.scanner-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.scanner-overlay__text {
  font-size: $font-base;
  color: #ffffff;
}

.hint-section {
  padding: $spacing-lg $spacing-xl calc($spacing-xl + $safe-bottom);
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
}

.hint-text {
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.75);
  text-align: center;
}

.manual-btn {
  padding: $spacing-sm $spacing-2xl;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: $radius-full;
}

.manual-btn__text {
  font-size: $font-sm;
  color: #ffffff;
}

.native-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.native-placeholder__text {
  font-size: $font-base;
  color: $color-text-sub;
}
</style>

<style>
/* html5-qrcode 默认边框适配深色背景 */
#qr-reader video {
  object-fit: cover !important;
}
</style>

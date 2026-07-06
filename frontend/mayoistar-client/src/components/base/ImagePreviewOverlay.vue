<template>
  <view v-if="visible" class="preview-overlay" @tap="emitClose">
    <view class="preview-close" @tap.stop="emitClose">
      <text class="preview-close-icon">×</text>
    </view>

    <view v-if="showSave" class="preview-save" @tap.stop="onSave">
      <text class="preview-save-text">{{ saving ? '保存中...' : '保存图片' }}</text>
    </view>

    <swiper
      v-if="urls.length > 1"
      class="preview-swiper"
      :current="activeIndex"
      @change="onSwiperChange"
      @tap.stop
    >
      <swiper-item v-for="(url, index) in urls" :key="`${url}-${index}`">
        <view class="preview-slide">
          <image class="preview-image" :src="url" mode="aspectFit" @tap.stop />
        </view>
      </swiper-item>
    </swiper>

    <view v-else-if="urls.length === 1" class="preview-slide preview-slide--single" @tap.stop>
      <image class="preview-image" :src="urls[0]" mode="aspectFit" />
    </view>

    <text v-if="urls.length > 1" class="preview-indicator"
      >{{ activeIndex + 1 }} / {{ urls.length }}</text
    >
  </view>
</template>

<script setup lang="ts">
/**
 * 全屏图片预览层
 *
 * 替代 H5 上 uni.previewImage 关闭按钮偶发失效的问题，提供可点击关闭的自定义预览。
 */
import { computed, ref, watch } from 'vue'
import { saveImageToDevice } from '@/utils/save-image'

const props = defineProps<{
  visible: boolean
  urls: string[]
  current?: string
  showSave?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const activeIndex = ref(0)
const saving = ref(false)

const activeUrl = computed(() => props.urls[activeIndex.value] ?? '')

function resolveInitialIndex(): number {
  if (!props.urls.length) return 0
  if (!props.current) return 0
  const index = props.urls.indexOf(props.current)
  return index >= 0 ? index : 0
}

watch(
  () => [props.visible, props.current, props.urls] as const,
  () => {
    if (!props.visible) return
    activeIndex.value = resolveInitialIndex()
  },
  { immediate: true },
)

function onSwiperChange(event: { detail: { current: number } }) {
  activeIndex.value = event.detail.current
}

function emitClose() {
  emit('close')
}

async function onSave() {
  if (saving.value || !activeUrl.value) return
  saving.value = true
  try {
    await saveImageToDevice(activeUrl.value)
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch {
    uni.showToast({ title: '保存失败，请长按图片手动保存', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(0, 0, 0, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-close {
  position: absolute;
  top: calc(12px + env(safe-area-inset-top));
  right: 12px;
  z-index: 10002;
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-close-icon {
  font-size: 30px;
  line-height: 1;
  color: $color-text-inverse;
}

.preview-swiper,
.preview-slide {
  width: 100%;
  height: 100%;
}

.preview-slide {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-slide--single {
  position: absolute;
  inset: 0;
}

.preview-image {
  width: 100%;
  max-height: 100%;
}

.preview-save {
  position: absolute;
  left: 50%;
  bottom: calc(24px + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  z-index: 10002;
  min-width: 132px;
  padding: 10px 20px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-save-text {
  font-size: $font-sm;
  color: $color-text-inverse;
}

.preview-indicator {
  position: absolute;
  bottom: calc(72px + env(safe-area-inset-bottom));
  left: 0;
  right: 0;
  text-align: center;
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.85);
  z-index: 10001;
}
</style>

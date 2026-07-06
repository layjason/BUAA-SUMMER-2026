<template>
  <view class="user-avatar" :class="`user-avatar--${size}`">
    <image
      v-if="showImage"
      class="user-avatar__image"
      :src="resolvedAvatarUrl"
      mode="aspectFill"
      @error="onImageError"
    />
    <view v-else class="user-avatar__placeholder">
      <text class="user-avatar__initial">{{ displayInitial }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { resolveMediaPreviewUrl } from '@/utils/media-preview'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(
  defineProps<{
    avatarUrl?: string
    name?: string
    userId?: string
    initial?: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  {
    avatarUrl: '',
    name: '',
    userId: '',
    initial: '',
    size: 'md',
  },
)

const imageFailed = ref(false)
const resolvedAvatarUrl = ref('')
const authStore = useAuthStore()
let resolveGeneration = 0

watch(
  () => props.avatarUrl,
  async (avatarUrl) => {
    const generation = ++resolveGeneration
    imageFailed.value = false
    resolvedAvatarUrl.value = ''
    if (!avatarUrl) return

    const previewUrl = await resolveMediaPreviewUrl(avatarUrl, authStore.getAccessToken())
    if (generation !== resolveGeneration) return
    resolvedAvatarUrl.value = previewUrl
  },
  { immediate: true },
)

const showImage = computed(() => Boolean(resolvedAvatarUrl.value) && !imageFailed.value)

const displayInitial = computed(() => {
  if (props.initial) return props.initial.charAt(0).toUpperCase()
  if (props.name) return props.name.charAt(0).toUpperCase()
  if (props.userId) return props.userId.charAt(0).toUpperCase()
  return '?'
})

/** 头像图片加载失败时回退为首字占位。 */
function onImageError(): void {
  imageFailed.value = true
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.user-avatar {
  flex-shrink: 0;
  border-radius: $radius-full;
  overflow: hidden;

  &--sm {
    width: 36px;
    height: 36px;
  }

  &--md {
    width: 48px;
    height: 48px;
  }

  &--lg {
    width: 56px;
    height: 56px;
  }

  &--xl {
    width: 64px;
    height: 64px;
  }
}

.user-avatar__image,
.user-avatar__placeholder {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
}

.user-avatar__image {
  display: block;
}

.user-avatar__placeholder {
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar__initial {
  color: var(--q-color-bg-card);
  font-weight: $weight-semibold;

  .user-avatar--sm & {
    font-size: 16px;
  }

  .user-avatar--md & {
    font-size: 20px;
  }

  .user-avatar--lg & {
    font-size: 24px;
  }

  .user-avatar--xl & {
    font-size: 28px;
  }
}
</style>

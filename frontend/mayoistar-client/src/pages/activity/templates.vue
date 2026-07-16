<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="container">
        <view class="header">
          <text class="title">{{ t('activityTemplates.title') }}</text>
          <text class="subtitle">{{ t('activityTemplates.subtitle') }}</text>
        </view>

        <!-- 从模板创建 -->
        <view class="section">
          <text class="section-title">{{ t('activityTemplates.fromTemplate') }}</text>
          <view v-if="loadingTemplates" class="loading-text">{{ t('加载中') }}</view>
          <view v-else class="template-grid">
            <view v-for="(row, rowIndex) in templateRows" :key="rowIndex" class="template-row">
              <view
                v-for="tpl in row"
                :key="tpl.templateId"
                class="card"
                hover-class="card-hover"
                @click="selectTemplate(tpl)"
              >
                <view class="card-inner">
                  <image
                    v-if="tpl.defaultCoverImage?.signedUrl"
                    class="card-cover"
                    :src="getTemplateCoverUrl(tpl)"
                    mode="aspectFill"
                  />
                  <view v-else class="card-cover card-cover-placeholder">
                    <text class="placeholder-icon">📋</text>
                  </view>
                  <view class="card-body">
                    <text class="card-name">{{ tpl.name }}</text>
                    <text class="card-tags">{{ tpl.defaultTags.join(' · ') }}</text>
                  </view>
                </view>
              </view>
              <view v-if="row.length === 1" class="card card-spacer" />
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <BottomActionBar fixed>
      <button class="bar-btn bar-btn-secondary" @click="skipTemplate">
        {{ t('activityTemplates.skip') }}
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动创建入口页
 *
 * 提供从模板创建活动草稿的入口；克隆已有活动由独立页面承载。
 * 前置条件：用户已登录
 * 后置条件：选择后跳转编辑页
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import {
  createDraftFromTemplate,
  getTemplates as fetchTemplates,
  type ActivityTemplate,
} from '@/api/modules/activities'
import { BottomActionBar } from '@/components'
import { getErrorMessage } from '@/utils/error'
import { toAbsoluteMediaUrl } from '@/utils/media-preview'

const { t } = useI18n()

const loadingTemplates = ref(true)
const actioning = ref(false)

const templates = ref<ActivityTemplate[]>([])

/** 模板卡片按每行两个分组，避免依赖小程序端 flex-wrap 自动换行 */
const templateRows = computed(() => {
  const rows: ActivityTemplate[][] = []
  for (let index = 0; index < templates.value.length; index += 2) {
    rows.push(templates.value.slice(index, index + 2))
  }
  return rows
})

async function loadTemplates(): Promise<void> {
  try {
    const result = await fetchTemplates()
    templates.value = result.items ?? []
  } catch {
    /* 加载失败不影响 */
  } finally {
    loadingTemplates.value = false
  }
}

/**
 * 获取模板封面展示地址。
 *
 * 前置条件：tpl 来自活动模板接口，封面 signedUrl 可能是后端相对地址。
 * 后置条件：返回 image 组件可访问的绝对地址；无封面时返回空字符串。
 * 不变量：只转换展示 URL，不修改模板数据。
 *
 * @param tpl 活动模板
 */
function getTemplateCoverUrl(tpl: ActivityTemplate): string {
  return tpl.defaultCoverImage?.signedUrl ? toAbsoluteMediaUrl(tpl.defaultCoverImage.signedUrl) : ''
}

async function selectTemplate(tpl: ActivityTemplate): Promise<void> {
  if (actioning.value) return
  actioning.value = true
  try {
    uni.showLoading({ title: t('activityTemplates.creating') })
    const draft = await createDraftFromTemplate(tpl.templateId)
    uni.hideLoading()
    uni.redirectTo({
      url: '/pages/activity/edit?activityId=' + draft.activityId,
    })
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '创建草稿失败', icon: 'none' })
    }
  } finally {
    actioning.value = false
  }
}

function skipTemplate(): void {
  uni.redirectTo({ url: '/pages/activity/edit' })
}

onLoad(() => {
  loadTemplates()
})
</script>

<style scoped>
.page {
  background-color: var(--q-color-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.container {
  padding: 32rpx 32rpx calc(240rpx + env(safe-area-inset-bottom));
}

.header {
  margin-bottom: 24rpx;
}

.title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--q-color-text);
}

.subtitle {
  display: block;
  font-size: 26rpx;
  color: var(--q-color-text-muted);
  margin-top: 8rpx;
}

.section {
  margin-bottom: 40rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-color-text);
  margin-bottom: 16rpx;
}

.loading-text,
.empty-text {
  text-align: center;
  font-size: 26rpx;
  color: var(--q-color-text-muted);
  padding-top: 40rpx;
}

/* ---- 模板卡片 ---- */
.template-grid {
  margin-top: -8rpx;
}

.template-row {
  display: flex;
  flex-direction: row;
  margin: 0 -8rpx 16rpx;
}

.card {
  box-sizing: border-box;
  width: 50%;
  padding: 0 8rpx;
}

.card-spacer {
  visibility: hidden;
}

.card-inner {
  background-color: var(--q-color-bg-card);
  border-radius: 12rpx;
  overflow: hidden;
  height: 100%;
}

.card-hover {
  opacity: 0.85;
}

.card-cover {
  width: 100%;
  height: 180rpx;
}

.card-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--q-color-primary-light);
}

.placeholder-icon {
  font-size: 48rpx;
}

.card-body {
  padding: 14rpx 16rpx 18rpx;
}

.card-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-tags {
  display: block;
  font-size: 22rpx;
  color: var(--q-color-text-muted);
  margin-top: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 底部操作按钮已由 BottomActionBar 组件承载 */
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

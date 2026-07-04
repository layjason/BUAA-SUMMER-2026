<script setup lang="ts">
/**
 * 活动模板创建页。
 *
 * 前置条件：用户已登录，可访问活动模板接口。
 * 后置条件：选择模板后创建草稿并进入编辑页；跳过模板时进入空白创建页。
 * 不变量：本页只处理模板创建，不承载克隆已有活动流程。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { BusinessError } from '@/api'
import {
  createDraftFromTemplate,
  getTemplates as fetchTemplates,
  type ActivityTemplate,
} from '@/api/modules/activities'
import { BottomActionBar } from '@/components'
import { getErrorMessage } from '@/utils/error'

const loadingTemplates = ref(true)
const actioning = ref(false)
const templates = ref<ActivityTemplate[]>([])

/**
 * 加载可用活动模板。
 *
 * 前置条件：模板接口可按 OpenAPI 返回 items。
 * 后置条件：成功时刷新模板列表，失败时展示空态。
 * 不变量：不会修改草稿或活动数据。
 */
async function loadTemplates(): Promise<void> {
  try {
    const result = await fetchTemplates()
    templates.value = result.items ?? []
  } catch {
    templates.value = []
  } finally {
    loadingTemplates.value = false
  }
}

/**
 * 使用指定模板创建草稿。
 *
 * 前置条件：模板标识来自模板列表。
 * 后置条件：创建成功后进入活动编辑页。
 * 不变量：重复点击期间只会发起一次创建请求。
 *
 * @param template 活动模板
 */
async function selectTemplate(template: ActivityTemplate): Promise<void> {
  if (actioning.value) return
  actioning.value = true
  try {
    uni.showLoading({ title: '创建草稿中' })
    const draft = await createDraftFromTemplate(template.templateId)
    uni.hideLoading()
    uni.redirectTo({
      url: `/pages/activity/edit?activityId=${draft.activityId}`,
    })
  } catch (error) {
    uni.hideLoading()
    const title = error instanceof BusinessError ? getErrorMessage(error.code) : '创建草稿失败'
    uni.showToast({ title, icon: 'none' })
  } finally {
    actioning.value = false
  }
}

/**
 * 跳过模板，进入空白活动创建页。
 *
 * 前置条件：当前处于模板创建页。
 * 后置条件：跳转到活动编辑页。
 * 不变量：不创建草稿。
 */
function skipTemplate(): void {
  uni.redirectTo({ url: '/pages/activity/edit' })
}

onLoad(() => {
  void loadTemplates()
})
</script>

<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="container">
        <view class="header">
          <text class="title">选择活动模板</text>
          <text class="subtitle">挑一个接近的活动骨架，再进入编辑页逐项调整</text>
        </view>

        <view v-if="loadingTemplates" class="state-text">加载中...</view>

        <view v-else-if="templates.length === 0" class="empty-state">
          <text class="empty-title">暂无可用模板</text>
          <text class="empty-desc">可以先从空白活动开始创建</text>
        </view>

        <view v-else class="template-grid">
          <view
            v-for="template in templates"
            :key="template.templateId"
            class="template-card"
            hover-class="card-hover"
            @click="selectTemplate(template)"
          >
            <image
              v-if="template.defaultCoverImage?.url"
              class="card-cover"
              :src="template.defaultCoverImage.url"
              mode="aspectFill"
            />
            <view v-else class="card-cover cover-placeholder">
              <text class="placeholder-text">{{ template.activityType.slice(0, 1) }}</text>
            </view>
            <view class="card-body">
              <view class="card-title-row">
                <text class="card-name">{{ template.name }}</text>
                <text class="capacity">{{ template.defaultCapacity }}人</text>
              </view>
              <text class="card-desc">{{ template.defaultIntroduction }}</text>
              <view class="tag-row">
                <text v-for="tag in template.defaultTags" :key="tag" class="tag">{{ tag }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <BottomActionBar>
      <button class="bar-btn bar-btn-secondary" @click="skipTemplate">空白创建</button>
    </BottomActionBar>
  </view>
</template>

<style scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f7f8fa;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.container {
  padding: 32rpx;
}

.header {
  margin-bottom: 28rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #323233;
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: #969799;
}

.state-text,
.empty-state {
  text-align: center;
  padding-top: 120rpx;
  color: #969799;
}

.empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #323233;
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #969799;
}

.template-grid {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.template-card {
  overflow: hidden;
  border-radius: 16rpx;
  background-color: #fff;
  box-shadow: 0 12rpx 32rpx rgba(50, 50, 51, 0.06);
}

.card-hover {
  opacity: 0.86;
}

.card-cover {
  width: 100%;
  height: 240rpx;
  background-color: #e8f7f0;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-text {
  width: 88rpx;
  height: 88rpx;
  border-radius: 44rpx;
  background-color: #5ec8a7;
  color: #fff;
  font-size: 42rpx;
  font-weight: 700;
  line-height: 88rpx;
  text-align: center;
}

.card-body {
  padding: 24rpx;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.card-name {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 700;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capacity {
  flex-shrink: 0;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background-color: #e8f7f0;
  color: #5ec8a7;
  font-size: 22rpx;
  font-weight: 600;
}

.card-desc {
  display: -webkit-box;
  margin-top: 12rpx;
  color: #646566;
  font-size: 26rpx;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 18rpx;
}

.tag {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background-color: #f2f3f5;
  color: #646566;
  font-size: 22rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

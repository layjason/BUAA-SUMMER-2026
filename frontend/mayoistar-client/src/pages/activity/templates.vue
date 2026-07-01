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
            <view
              v-for="tpl in templates"
              :key="tpl.templateId"
              class="card"
              hover-class="card-hover"
              @click="selectTemplate(tpl)"
            >
              <image
                v-if="tpl.defaultCoverImage?.url"
                class="card-cover"
                :src="tpl.defaultCoverImage.url"
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
        </view>

        <!-- 从已有活动克隆 -->
        <view class="section">
          <text class="section-title">{{ t('activityTemplates.fromClone') }}</text>
          <view v-if="loadingActivities" class="loading-text">{{ t('加载中') }}</view>
          <view v-else-if="myActivities.length === 0" class="empty-text">{{ t('暂无数据') }}</view>
          <view v-else class="clone-list">
            <view
              v-for="item in myActivities"
              :key="item.activityId"
              class="clone-item"
              hover-class="clone-hover"
              @click="selectClone(item)"
            >
              <view class="clone-info">
                <text class="clone-title">{{ item.title }}</text>
                <text class="clone-meta"
                  >{{ formatDate(item.startAt) }} · {{ item.location.city }}</text
                >
              </view>
              <view class="clone-status">
                <text class="status-tag" :class="'status-' + item.runtimeStatus">{{
                  runtimeStatusText(item.runtimeStatus)
                }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="action-bar">
      <button class="skip-btn" @click="skipTemplate">{{ t('activityTemplates.skip') }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动创建入口页
 *
 * 提供两种创建方式：从模板创建、从已有活动克隆。
 * 前置条件：用户已登录
 * 后置条件：选择后跳转编辑页
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'

const { t } = useI18n()

const loadingTemplates = ref(true)
const loadingActivities = ref(true)

interface ActivityTemplate {
  templateId: string
  name: string
  activityType: string
  defaultTags: string[]
  defaultIntroduction: string
  defaultSafetyNotice: string
  defaultCapacity: number
  defaultCoverImage: { url: string; mediaId: string } | null
}

const templates = ref<ActivityTemplate[]>([])

interface MyActivity {
  activityId: string
  title: string
  startAt: string
  location: { city: string }
  runtimeStatus: string
}

const myActivities = ref<MyActivity[]>([])

const runtimeStatusMap: Record<string, string> = {
  notStarted: t('myActivities.statusNotStarted'),
  registering: t('myActivities.statusRegistering'),
  registrationClosed: t('myActivities.statusRegistrationClosed'),
  ongoing: t('myActivities.statusOngoing'),
  ended: t('myActivities.statusEnded'),
  takenDown: t('myActivities.statusTakenDown'),
}

function runtimeStatusText(status: string): string {
  return runtimeStatusMap[status] ?? status
}

async function loadTemplates(): Promise<void> {
  try {
    const result = await api.get('/activities/templates')
    templates.value = (result.items ?? []) as ActivityTemplate[]
  } catch {
    /* 加载失败不影响 */
  } finally {
    loadingTemplates.value = false
  }
}

async function loadMyActivities(): Promise<void> {
  try {
    const result = await api.get('/activities/mine')
    myActivities.value = (result.items ?? []) as MyActivity[]
  } catch {
    /* 加载失败不影响 */
  } finally {
    loadingActivities.value = false
  }
}

async function selectTemplate(tpl: ActivityTemplate): Promise<void> {
  try {
    uni.showLoading({ title: t('activityTemplates.creating') })
    const draft = (await api.post('/activities/templates/{templateId}/drafts', {
      path: { templateId: tpl.templateId },
    })) as { activityId: string }
    uni.hideLoading()
    uni.redirectTo({ url: '/pages/activity/edit?activityId=' + draft.activityId })
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '创建草稿失败', icon: 'none' })
    }
  }
}

async function selectClone(item: MyActivity): Promise<void> {
  try {
    uni.showLoading({ title: t('activityClone.cloning') })
    const result = (await api.post('/activities/{activityId}/clone', {
      path: { activityId: item.activityId },
    })) as { activityId: string }
    uni.hideLoading()
    uni.showToast({ title: t('activityClone.success'), icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/activity/edit?activityId=' + result.activityId })
    }, 800)
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      uni.showToast({ title: getErrorMessage(error.code), icon: 'none' })
    } else {
      uni.showToast({ title: '克隆失败', icon: 'none' })
    }
  }
}

function skipTemplate(): void {
  uni.redirectTo({ url: '/pages/activity/edit' })
}

onLoad(() => {
  loadTemplates()
  loadMyActivities()
})
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
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
  padding: 32rpx;
}

.header {
  margin-bottom: 24rpx;
}

.title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: #323233;
}

.subtitle {
  display: block;
  font-size: 26rpx;
  color: #969799;
  margin-top: 8rpx;
}

.section {
  margin-bottom: 40rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #323233;
  margin-bottom: 16rpx;
}

.loading-text,
.empty-text {
  text-align: center;
  font-size: 26rpx;
  color: #969799;
  padding-top: 40rpx;
}

/* ---- 模板卡片 ---- */
.template-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.card {
  width: calc(50% - 8rpx);
  background-color: #fff;
  border-radius: 12rpx;
  overflow: hidden;
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
  background-color: #e6f0fe;
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
  color: #323233;
}

.card-tags {
  display: block;
  font-size: 22rpx;
  color: #969799;
  margin-top: 6rpx;
}

/* ---- 克隆已有活动列表 ---- */
.clone-list {
  background-color: #fff;
  border-radius: 12rpx;
  overflow: hidden;
}

.clone-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-bottom: 2rpx solid #f5f5f5;
}

.clone-item:last-child {
  border-bottom: none;
}

.clone-hover {
  opacity: 0.85;
}

.clone-info {
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
}

.clone-title {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clone-meta {
  display: block;
  font-size: 22rpx;
  color: #969799;
  margin-top: 4rpx;
}

.clone-status {
  flex-shrink: 0;
}

.status-tag {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
}

.status-registering {
  background-color: #e6f0fe;
  color: #1989fa;
}

.status-registrationClosed {
  background-color: #fff7e6;
  color: #ed6a0c;
}

.status-ongoing {
  background-color: #ebf9e9;
  color: #07c160;
}

.status-ended {
  background-color: #f2f3f5;
  color: #c8c9cc;
}

.status-notStarted {
  background-color: #ebedf0;
  color: #969799;
}

.status-takenDown {
  background-color: #fff2f0;
  color: #ee0a24;
}

/* ---- 底部 ---- */
.action-bar {
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #fff;
  border-top: 2rpx solid #ebedf0;
  flex-shrink: 0;
}

.skip-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #1989fa;
  background-color: #fff;
  border: 2rpx solid #1989fa;
  border-radius: 12rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

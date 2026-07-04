<template>
  <view class="page">
    <view class="top-bar">
      <view class="tab-bar">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>
      <view class="filter-entry" @click="goSearch">
        <text>筛选</text>
      </view>
    </view>

    <scroll-view
      class="scroll-area"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="loading && items.length === 0" class="loading-text">{{ t('home.loading') }}</view>

      <view v-else-if="errorMsg && items.length === 0" class="error-box">
        <text class="error-text">{{ errorMsg }}</text>
        <view class="retry-btn" @click="loadFeed">{{ t('home.retry') }}</view>
      </view>

      <view v-else-if="items.length === 0" class="empty-text">{{ t('home.empty') }}</view>

      <view v-else style="padding-bottom: calc(120rpx + env(safe-area-inset-bottom))">
        <view
          v-for="item in items"
          :key="item.activityId"
          class="card"
          hover-class="card-hover"
          @click="goDetail(item.activityId)"
        >
          <view class="card-inner">
            <image
              v-if="item.coverImage?.signedUrl"
              class="card-cover"
              :src="item.coverImage.signedUrl"
              mode="aspectFill"
            />
            <view v-else class="card-cover card-cover-placeholder">
              <text class="placeholder-icon">📌</text>
            </view>

            <view class="card-body">
              <view class="card-header-row">
                <text class="card-title">{{ item.title }}</text>
                <text class="status-tag" :class="'status-' + item.runtimeStatus">{{
                  getStatusText(item.runtimeStatus)
                }}</text>
              </view>

              <view v-if="item.tags.length > 0" class="card-tags">
                <text v-for="tag in item.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</text>
              </view>

              <view class="card-meta">
                <text class="meta-item">{{ formatDate(item.startAt) }}</text>
                <text class="meta-sep">|</text>
                <text class="meta-item">{{ item.location.city }}</text>
              </view>

              <view class="card-bottom">
                <text class="fee" :class="{ free: !item.feeAmount }">{{
                  item.feeAmount ? '¥' + item.feeAmount : t('home.free')
                }}</text>
                <text class="registered">{{
                  item.registeredCount >= item.capacity
                    ? t('home.full')
                    : t('home.registered', { count: item.registeredCount, total: item.capacity })
                }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="footer-text">
          <text v-if="loadingMore">{{ t('home.loading') }}</text>
          <text v-else-if="noMoreData">{{ t('home.noMore') }}</text>
          <text v-else-if="loadError" class="load-error" @click="loadMore">{{
            t('home.error')
          }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="fab" @click="goCreate">
      <text class="fab-icon">+</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 首页 - 活动信息流
 *
 * 提供推荐、最新、附近三个 Tab 的信息流展示。
 * 前置条件：用户可未登录浏览
 * 后置条件：加载成功后展示活动卡片列表
 */
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { getFeed, type FeedActivitiesParams } from '@/api/modules/activities'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'
import { getCurrentLocation } from '@/utils/location'
import { runtimeStatusText } from '@/utils/status'

const { t } = useI18n()

type FeedTab = 'recommended' | 'latest' | 'nearby'

const tabs = computed(() => [
  { key: 'recommended' as FeedTab, label: t('home.tabRecommended') },
  { key: 'latest' as FeedTab, label: t('home.tabLatest') },
  { key: 'nearby' as FeedTab, label: t('home.tabNearby') },
])

const activeTab = ref<FeedTab>('recommended')
const items = ref<ActivityItem[]>([])
const loading = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)
const errorMsg = ref('')
const loadError = ref(false)
const noMoreData = ref(false)
const currentPage = ref(1)
const pageSize = 10
const nearbyFallbackLocation = { longitude: 116.46, latitude: 39.908, distanceMeters: 10000 }
const nearbyLocation = ref<typeof nearbyFallbackLocation | null>(null)

interface ActivityItem {
  activityId: string
  title: string
  tags: string[]
  startAt: string
  endAt: string
  location: {
    city: string
    address: string
    placeName: string | null
    point: { longitude: number; latitude: number }
  }
  coverImage: { signedUrl: string; mediaId: string } | null
  feeAmount?: number | null
  capacity: number
  registeredCount: number
  reviewStatus: string
  runtimeStatus: string
}

function getStatusText(status: string): string {
  return runtimeStatusText(status, t)
}

/** 确保附近 Tab 使用真实定位参数
 *
 * 前置条件：用户正在请求附近信息流。
 * 后置条件：nearbyLocation 保存真实定位；定位失败时保存演示 fallback 坐标。
 * 副作用：可能触发定位授权弹窗，失败时展示轻提示。
 */
async function ensureNearbyLocation(): Promise<void> {
  if (nearbyLocation.value) return
  const location = await getCurrentLocation()
  if (location) {
    nearbyLocation.value = { ...location, distanceMeters: 10000 }
    return
  }
  nearbyLocation.value = nearbyFallbackLocation
  uni.showToast({ title: '无法获取当前位置，已使用默认位置展示附近活动', icon: 'none' })
}

/** 构建信息流查询参数
 *
 * 前置条件：activeTab 已表示当前首页 Tab。
 * 后置条件：返回 OpenAPI 允许的 Feed 查询字段；附近 Tab 附带默认位置与距离。
 * 不变量：不向 query 添加 OpenAPI 未定义字段。
 *
 * @param page 要加载的页码
 * @returns 首页 Feed 查询参数
 */
function buildFeedParams(page: number): FeedActivitiesParams {
  const base = { page, pageSize }
  if (activeTab.value !== 'nearby') return base
  return { ...base, ...(nearbyLocation.value ?? nearbyFallbackLocation) }
}

async function loadFeed(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  noMoreData.value = false
  currentPage.value = 1
  try {
    if (activeTab.value === 'nearby') await ensureNearbyLocation()
    const result = await getFeed(activeTab.value, buildFeedParams(1))
    items.value = (result.items ?? []) as ActivityItem[]
    noMoreData.value = (result.totalPages ?? 1) <= 1
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, t('home.error'))
    }
  } finally {
    loading.value = false
  }
}

async function onRefresh(): Promise<void> {
  refreshing.value = true
  errorMsg.value = ''
  noMoreData.value = false
  currentPage.value = 1
  try {
    if (activeTab.value === 'nearby') await ensureNearbyLocation()
    const result = await getFeed(activeTab.value, buildFeedParams(1))
    items.value = (result.items ?? []) as ActivityItem[]
    noMoreData.value = (result.totalPages ?? 1) <= 1
  } catch {
    // 刷新失败不展示错误态，静默处理
  } finally {
    refreshing.value = false
  }
}

async function loadMore(): Promise<void> {
  if (loadingMore.value || noMoreData.value) return
  loadingMore.value = true
  loadError.value = false
  const nextPage = currentPage.value + 1
  try {
    if (activeTab.value === 'nearby') await ensureNearbyLocation()
    const result = await getFeed(activeTab.value, buildFeedParams(nextPage))
    const newItems = (result.items ?? []) as ActivityItem[]
    if (newItems.length === 0) {
      noMoreData.value = true
    } else {
      items.value.push(...newItems)
      currentPage.value = nextPage
      noMoreData.value = result.page >= (result.totalPages ?? 1)
    }
  } catch {
    loadError.value = true
  } finally {
    loadingMore.value = false
  }
}

function switchTab(tab: FeedTab): void {
  if (activeTab.value === tab) return
  activeTab.value = tab
  items.value = []
  loadFeed()
}

onShow(() => {
  if (items.value.length === 0) loadFeed()
})

function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

function goCreate(): void {
  uni.navigateTo({ url: '/pages/activity/templates' })
}

function goSearch(): void {
  uni.navigateTo({ url: '/pages/discover/search' })
}
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top-bar {
  display: flex;
  background-color: #fff;
  border-bottom: 1rpx solid #ebedf0;
  flex-shrink: 0;
  align-items: center;
}

.tab-bar {
  flex: 1;
  display: flex;
  padding-left: 32rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #646566;
  border-bottom: 4rpx solid transparent;
}

.tab.active {
  color: #5ec8a7;
  border-bottom-color: #5ec8a7;
  font-weight: 600;
}

.filter-entry {
  padding: 0 32rpx;
  font-size: 26rpx;
  color: #5ec8a7;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.loading-text,
.empty-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.error-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 120rpx;
}

.error-text {
  font-size: 28rpx;
  color: #ee0a24;
}

.retry-btn {
  margin-top: 24rpx;
  padding: 16rpx 48rpx;
  font-size: 26rpx;
  color: #5ec8a7;
  border: 2rpx solid #5ec8a7;
  border-radius: 8rpx;
}

.card {
  background-color: #fff;
  margin: 16rpx 32rpx;
  border-radius: 12rpx;
  overflow: hidden;
}

.card-hover {
  opacity: 0.85;
}

.card-inner {
  display: flex;
  flex-direction: row;
}

.card-cover {
  width: 240rpx;
  height: 180rpx;
  flex-shrink: 0;
}

.card-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f2f3f5;
}

.placeholder-icon {
  font-size: 48rpx;
}

.card-body {
  flex: 1;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12rpx;
}

.card-title {
  font-size: 28rpx;
  color: #323233;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  flex-shrink: 0;
}

.status-registering {
  background-color: rgba(94, 200, 167, 0.1);
  color: #5ec8a7;
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

.card-tags {
  display: flex;
  gap: 8rpx;
  flex-wrap: wrap;
  margin-top: 8rpx;
}

.tag {
  font-size: 22rpx;
  color: #5ec8a7;
  background-color: rgba(94, 200, 167, 0.08);
  padding: 2rpx 12rpx;
  border-radius: 4rpx;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
}

.meta-item {
  font-size: 22rpx;
  color: #969799;
}

.meta-sep {
  font-size: 20rpx;
  color: #c8c9cc;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10rpx;
}

.fee {
  font-size: 26rpx;
  color: #ee0a24;
  font-weight: 600;
}

.fee.free {
  color: #07c160;
}

.registered {
  font-size: 22rpx;
  color: #969799;
}

.footer-text {
  text-align: center;
  font-size: 24rpx;
  color: #969799;
  padding: 32rpx 0;
}

.load-error {
  color: #ee0a24;
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(32rpx + env(safe-area-inset-bottom) + 120rpx);
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background-color: #5ec8a7;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(94, 200, 167, 0.4);
}

.fab-icon {
  font-size: 56rpx;
  color: #fff;
  line-height: 1;
  margin-top: -4rpx;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

<script setup lang="ts">
/**
 * 搜索活动页
 *
 * 提供关键词搜索和轻量筛选，接入 searchActivities 接口。
 *
 * 前置条件：用户可未登录浏览
 * 后置条件：搜索结果展示为活动卡片列表，点击跳转详情
 */
import { ref } from 'vue'
import { searchActivities } from '@/api/modules/activities'
import { formatDate } from '@/utils/date'
import {
  buildSearchActivitiesQuery,
  hasSearchFilters,
  type ActivityTypeFilter,
  type CityFilter,
  type FeeFilter,
  type SearchFilterSelection,
  type TimeFilter,
} from '@/utils/search-filters'

/** 搜索结果条目接口 */
interface SearchResultItem {
  activityId: string
  title: string
  tags: string[]
  startAt: string
  endAt: string
  location: {
    city: string
    address: string
    placeName: string | null
  }
  coverImage: { url: string; mediaId: string } | null
  feeAmount?: number | null
  capacity: number
  registeredCount: number
  occupiedCount?: number
  runtimeStatus: string
}

const TYPE_OPTIONS: ActivityTypeFilter[] = ['运动', '户外', '桌游', '学习', '公益']
const CITY_OPTIONS: CityFilter[] = ['北京', '上海', '广州']
const FEE_OPTIONS: { value: FeeFilter; label: string }[] = [
  { value: 'free', label: '免费' },
  { value: 'paid', label: '付费' },
]
const TIME_OPTIONS: { value: TimeFilter; label: string }[] = [
  { value: 'today', label: '今天' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
]

const keyword = ref('')
const searchResults = ref<SearchResultItem[]>([])
const isSearching = ref(false)
const hasSearched = ref(false)
const totalCount = ref(0)

const selectedType = ref<ActivityTypeFilter | null>(null)
const selectedCity = ref<CityFilter | null>(null)
const selectedFee = ref<FeeFilter | null>(null)
const selectedTime = ref<TimeFilter | null>(null)

/**
 * 获取当前筛选选择
 */
function getFilterSelection(): SearchFilterSelection {
  return {
    activityType: selectedType.value,
    city: selectedCity.value,
    fee: selectedFee.value,
    time: selectedTime.value,
  }
}

/**
 * 筛选变更后按需刷新搜索结果
 */
function refreshSearchIfNeeded(): void {
  if (hasSearched.value) {
    void doSearch()
  }
}

/**
 * 切换活动类型筛选
 *
 * 前置条件：type 来自预设类型列表。
 * 后置条件：选中同一类型时取消筛选，否则切换为该类型。
 * 不变量：筛选值只使用 OpenAPI activityTypes 支持的字符串数组。
 */
function toggleTypeFilter(type: ActivityTypeFilter): void {
  selectedType.value = selectedType.value === type ? null : type
  refreshSearchIfNeeded()
}

/**
 * 切换城市筛选
 *
 * 前置条件：city 来自预设城市列表。
 * 后置条件：选中同一城市时取消筛选，否则切换为该城市。
 * 不变量：不直接触发地图查询。
 */
function toggleCityFilter(city: CityFilter): void {
  selectedCity.value = selectedCity.value === city ? null : city
  refreshSearchIfNeeded()
}

/**
 * 切换费用筛选
 *
 * 前置条件：fee 为 free 或 paid。
 * 后置条件：更新费用筛选并按需刷新搜索结果。
 * 不变量：费用筛选最终由 buildSearchActivitiesQuery 转为 OpenAPI minFee/maxFee。
 */
function toggleFeeFilter(fee: FeeFilter): void {
  selectedFee.value = selectedFee.value === fee ? null : fee
  refreshSearchIfNeeded()
}

/**
 * 切换时间筛选
 *
 * 前置条件：time 为 today/week/month。
 * 后置条件：更新时间筛选并按需刷新搜索结果。
 * 不变量：时间筛选最终由 buildSearchActivitiesQuery 转为 OpenAPI startAtFrom/startAtTo。
 */
function toggleTimeFilter(time: TimeFilter): void {
  selectedTime.value = selectedTime.value === time ? null : time
  refreshSearchIfNeeded()
}

/**
 * 执行搜索
 *
 * 前置条件：关键词非空或存在筛选条件
 * 后置条件：searchResults 填充搜索结果
 */
async function doSearch(): Promise<void> {
  if (!keyword.value.trim() && !hasSearchFilters(getFilterSelection())) return
  isSearching.value = true
  hasSearched.value = true
  try {
    const result = await searchActivities(
      buildSearchActivitiesQuery(keyword.value, getFilterSelection(), 1, 20),
    )
    searchResults.value = (result.items ?? []) as SearchResultItem[]
    totalCount.value = result.total ?? 0
  } catch {
    searchResults.value = []
    totalCount.value = 0
  } finally {
    isSearching.value = false
  }
}

/** 跳转到地图模式 */
function goToMap(): void {
  const query = buildSearchActivitiesQuery(keyword.value, getFilterSelection(), 1, 20)
  const params: string[] = []
  if (query.keyword) params.push(`keyword=${encodeURIComponent(query.keyword)}`)
  if (query.city) params.push(`city=${encodeURIComponent(query.city)}`)
  if (query.activityTypes?.length) {
    params.push(`activityTypes=${encodeURIComponent(query.activityTypes.join(','))}`)
  }
  if (query.startAtFrom) params.push(`startAtFrom=${encodeURIComponent(query.startAtFrom)}`)
  if (query.startAtTo) params.push(`startAtTo=${encodeURIComponent(query.startAtTo)}`)
  if (query.minFee !== undefined) params.push(`minFee=${query.minFee}`)
  if (query.maxFee !== undefined) params.push(`maxFee=${query.maxFee}`)
  uni.navigateTo({ url: `/pages/discover/map${params.length ? `?${params.join('&')}` : ''}` })
}

/** 跳转到活动详情 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/** 获取状态文本 */
function getStatusText(status: string): string {
  const map: Record<string, string> = {
    registering: '报名中',
    registrationClosed: '报名截止',
    ongoing: '进行中',
    ended: '已结束',
    notStarted: '未开始',
  }
  return map[status] ?? status
}

/** 展示已占名额 */
function displayOccupiedCount(item: SearchResultItem): number {
  return item.occupiedCount ?? item.registeredCount
}
</script>

<template>
  <view class="search-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索活动名称、标签..."
          confirm-type="search"
          @confirm="doSearch"
        />
      </view>
      <text class="map-link" @tap="goToMap">地图模式</text>
    </view>

    <!-- 轻量筛选 -->
    <view class="filter-panel">
      <view class="filter-group">
        <text class="filter-label">类型</text>
        <view class="filter-chips">
          <text
            v-for="type in TYPE_OPTIONS"
            :key="type"
            class="filter-chip"
            :class="{ active: selectedType === type }"
            @tap="toggleTypeFilter(type)"
          >
            {{ type }}
          </text>
        </view>
      </view>

      <view class="filter-group">
        <text class="filter-label">城市</text>
        <view class="filter-chips">
          <text
            v-for="city in CITY_OPTIONS"
            :key="city"
            class="filter-chip"
            :class="{ active: selectedCity === city }"
            @tap="toggleCityFilter(city)"
          >
            {{ city }}
          </text>
        </view>
      </view>

      <view class="filter-group">
        <text class="filter-label">费用</text>
        <view class="filter-chips">
          <text
            v-for="fee in FEE_OPTIONS"
            :key="fee.value"
            class="filter-chip"
            :class="{ active: selectedFee === fee.value }"
            @tap="toggleFeeFilter(fee.value)"
          >
            {{ fee.label }}
          </text>
        </view>
      </view>

      <view class="filter-group">
        <text class="filter-label">时间</text>
        <view class="filter-chips">
          <text
            v-for="time in TIME_OPTIONS"
            :key="time.value"
            class="filter-chip"
            :class="{ active: selectedTime === time.value }"
            @tap="toggleTimeFilter(time.value)"
          >
            {{ time.label }}
          </text>
        </view>
      </view>
    </view>

    <!-- 搜索提示（未搜索时） -->
    <view v-if="!hasSearched && !isSearching" class="empty-hint">
      <text class="hint-icon">🔍</text>
      <text class="hint-title">搜索你感兴趣的活动</text>
      <text class="hint-desc">输入关键词，或直接使用上方筛选条件</text>
    </view>

    <!-- 搜索中 -->
    <view v-if="isSearching" class="loading-state">
      <text>搜索中...</text>
    </view>

    <!-- 无结果 -->
    <view v-if="!isSearching && hasSearched && searchResults.length === 0" class="empty-hint">
      <text class="hint-icon">😔</text>
      <text class="hint-title">未找到相关活动</text>
      <text class="hint-desc">换个关键词或筛选条件试试吧</text>
    </view>

    <!-- 搜索结果列表 -->
    <view v-if="!isSearching && searchResults.length > 0" class="results">
      <text class="results-count">共 {{ totalCount }} 个结果</text>

      <view
        v-for="item in searchResults"
        :key="item.activityId"
        class="result-card"
        @tap="goDetail(item.activityId)"
      >
        <view class="result-card-inner">
          <view v-if="item.coverImage?.url" class="result-cover">
            <image :src="item.coverImage.url" mode="aspectFill" class="cover-img" />
          </view>
          <view v-else class="result-cover result-cover-placeholder">
            <text class="placeholder-icon">📌</text>
          </view>

          <view class="result-body">
            <view class="result-header-row">
              <text class="result-title">{{ item.title }}</text>
              <text class="status-tag" :class="'status-' + item.runtimeStatus">{{
                getStatusText(item.runtimeStatus)
              }}</text>
            </view>

            <view v-if="item.tags.length > 0" class="result-tags">
              <text v-for="tag in item.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</text>
            </view>

            <view class="result-meta">
              <text class="meta-item">{{ formatDate(item.startAt) }}</text>
              <text class="meta-sep">|</text>
              <text class="meta-item">{{ item.location.city }}</text>
            </view>

            <view class="result-bottom">
              <text class="fee" :class="{ free: !item.feeAmount }">{{
                item.feeAmount ? '¥' + item.feeAmount : '免费'
              }}</text>
              <text class="registered">{{
                displayOccupiedCount(item) >= item.capacity
                  ? '已满员'
                  : `${displayOccupiedCount(item)}/${item.capacity}人`
              }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.search-page {
  min-height: 100vh;
  padding-bottom: calc(#{$safe-bottom} + #{$spacing-xl});
}

.search-bar {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  gap: $spacing-md;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-lg;
}

.search-icon {
  font-size: 16px;
  margin-right: $spacing-sm;
}

.search-input {
  flex: 1;
  font-size: $font-base;
  color: $color-text;
}

.map-link {
  font-size: $font-sm;
  color: $color-primary;
  font-weight: $weight-medium;
  white-space: nowrap;
}

.filter-panel {
  padding: 0 $spacing-lg $spacing-md;
}

.filter-group {
  margin-bottom: $spacing-md;
}

.filter-label {
  display: block;
  font-size: $font-xs;
  color: $color-text-sub;
  margin-bottom: $spacing-xs;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.filter-chip {
  padding: 6px 12px;
  border-radius: $radius-full;
  font-size: $font-sm;
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.08);
  border: 1px solid transparent;
}

.filter-chip.active {
  color: $color-primary;
  background: $color-primary-light;
  border-color: rgba(94, 200, 167, 0.35);
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px $spacing-xl;
}

.hint-icon {
  font-size: 48px;
  margin-bottom: $spacing-lg;
}

.hint-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  margin-bottom: $spacing-sm;
}

.hint-desc {
  font-size: $font-sm;
  color: $color-text-sub;
  text-align: center;
}

.results {
  padding: 0 $spacing-lg;
}

.results-count {
  font-size: $font-sm;
  color: $color-text-sub;
  margin-bottom: $spacing-md;
}

.result-card {
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  overflow: hidden;
  margin-bottom: $spacing-md;

  &:active {
    opacity: 0.85;
  }
}

.result-card-inner {
  display: flex;
  flex-direction: row;
}

.result-cover {
  width: 120px;
  height: 90px;
  flex-shrink: 0;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
}

.result-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(123, 129, 144, 0.08);
}

.placeholder-icon {
  font-size: 24px;
}

.result-body {
  flex: 1;
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.result-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: $spacing-sm;
}

.result-title {
  flex: 1;
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  font-size: $font-xs;
  padding: 2px 8px;
  border-radius: $radius-sm;
  flex-shrink: 0;
}

.status-registering {
  color: $color-primary;
  background: $color-primary-light;
}

.status-registrationClosed {
  color: $color-warning;
  background: rgba(246, 197, 111, 0.1);
}

.status-ongoing {
  color: $color-success;
  background: rgba(94, 200, 167, 0.1);
}

.status-ended {
  color: $color-text-muted;
  background: rgba(123, 129, 144, 0.08);
}

.status-notStarted {
  color: $color-text-sub;
  background: rgba(123, 129, 144, 0.08);
}

.result-tags {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;
  margin-top: $spacing-xs;
}

.tag {
  font-size: $font-xs;
  color: $color-primary;
  background: rgba(94, 200, 167, 0.08);
  padding: 1px 8px;
  border-radius: $radius-full;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-top: $spacing-xs;
}

.meta-item {
  font-size: $font-xs;
  color: $color-text-muted;
}

.meta-sep {
  font-size: $font-xs;
  color: $color-border;
}

.result-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $spacing-xs;
}

.fee {
  font-size: $font-base;
  font-weight: $weight-bold;
  color: $color-accent;
}

.fee.free {
  color: $color-primary;
  font-size: $font-sm;
  font-weight: $weight-semibold;
}

.registered {
  font-size: $font-xs;
  color: $color-text-muted;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 40px;

  text {
    font-size: $font-sm;
    color: $color-text-muted;
  }
}
</style>

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
import { getInterestTags } from '@/api/modules/profile'
import { formatDate } from '@/utils/date'
import { getCurrentLocation } from '@/utils/location'
import { toAbsoluteMediaUrl } from '@/utils/media-preview'
import {
  buildSearchActivitiesQuery,
  hasSearchFilters,
  type CityFilter,
  type DistanceFilter,
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
  coverImage: { signedUrl: string; mediaId: string } | null
  feeAmount?: number | null
  capacity: number
  registeredCount: number
  occupiedCount?: number
  runtimeStatus: string
}

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
const DISTANCE_OPTIONS: { value: DistanceFilter; label: string }[] = [
  { value: 1000, label: '1km' },
  { value: 3000, label: '3km' },
  { value: 5000, label: '5km' },
  { value: 10000, label: '10km' },
]

const keyword = ref('')
const typeOptions = ref<string[]>([])
const searchResults = ref<SearchResultItem[]>([])
const isSearching = ref(false)
const isLoadingTypes = ref(false)
const loadingMore = ref(false)
const hasSearched = ref(false)
const totalCount = ref(0)
const currentPage = ref(1)
const noMoreData = ref(false)
const pageSize = 20

const selectedTypes = ref<string[]>([])
const selectedCity = ref<CityFilter | null>(null)
const selectedFee = ref<FeeFilter | null>(null)
const selectedTime = ref<TimeFilter | null>(null)
const selectedDistance = ref<DistanceFilter | null>(null)
const currentLocation = ref<{ longitude: number; latitude: number } | null>(null)

/**
 * 获取当前筛选选择
 */
function getFilterSelection(): SearchFilterSelection {
  return {
    activityTypes: selectedTypes.value,
    city: selectedCity.value,
    fee: selectedFee.value,
    time: selectedTime.value,
    distanceMeters: selectedDistance.value,
    location: currentLocation.value,
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

function toggleTypeFilter(type: string): void {
  selectedTypes.value = selectedTypes.value.includes(type)
    ? selectedTypes.value.filter((item) => item !== type)
    : [...selectedTypes.value, type]
  refreshSearchIfNeeded()
}

function toggleCityFilter(city: CityFilter): void {
  selectedCity.value = selectedCity.value === city ? null : city
  refreshSearchIfNeeded()
}

function toggleFeeFilter(fee: FeeFilter): void {
  selectedFee.value = selectedFee.value === fee ? null : fee
  refreshSearchIfNeeded()
}

function toggleTimeFilter(time: TimeFilter): void {
  selectedTime.value = selectedTime.value === time ? null : time
  refreshSearchIfNeeded()
}

/** 切换距离范围筛选
 *
 * 前置条件：distance 为页面预设的距离半径。
 * 后置条件：选中距离时优先获取当前位置，定位失败则取消距离筛选。
 * 副作用：可能触发系统定位授权弹窗，并在失败时展示 toast。
 *
 * @param distance 距离筛选半径，单位米
 */
async function toggleDistanceFilter(distance: DistanceFilter): Promise<void> {
  if (selectedDistance.value === distance) {
    selectedDistance.value = null
    refreshSearchIfNeeded()
    return
  }

  const location = await getCurrentLocation()
  if (!location) {
    selectedDistance.value = null
    currentLocation.value = null
    uni.showToast({ title: '无法获取当前位置，已取消距离筛选', icon: 'none' })
    return
  }

  currentLocation.value = location
  selectedDistance.value = distance
  refreshSearchIfNeeded()
}

/** 加载活动类型筛选项
 *
 * 前置条件：兴趣标签接口可用。
 * 后置条件：typeOptions 使用系统预定义兴趣标签，加载失败时为空数组。
 */
async function loadTypeOptions(): Promise<void> {
  isLoadingTypes.value = true
  try {
    const tags = await getInterestTags()
    typeOptions.value = tags.map((tag) => tag.name)
  } catch {
    typeOptions.value = []
  } finally {
    isLoadingTypes.value = false
  }
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
  currentPage.value = 1
  noMoreData.value = false
  try {
    const result = await searchActivities(
      buildSearchActivitiesQuery(keyword.value, getFilterSelection(), currentPage.value, pageSize),
    )
    searchResults.value = (result.items ?? []) as SearchResultItem[]
    totalCount.value = result.total ?? 0
    noMoreData.value = result.page >= (result.totalPages ?? 1)
  } catch {
    searchResults.value = []
    totalCount.value = 0
    noMoreData.value = true
  } finally {
    isSearching.value = false
  }
}

/** 加载下一页搜索结果 */
async function loadMoreSearchResults(): Promise<void> {
  if (isSearching.value || loadingMore.value || noMoreData.value || !hasSearched.value) return
  loadingMore.value = true
  try {
    const nextPage = currentPage.value + 1
    const result = await searchActivities(
      buildSearchActivitiesQuery(keyword.value, getFilterSelection(), nextPage, pageSize),
    )
    searchResults.value.push(...((result.items ?? []) as SearchResultItem[]))
    totalCount.value = result.total ?? totalCount.value
    currentPage.value = result.page ?? nextPage
    noMoreData.value = currentPage.value >= (result.totalPages ?? currentPage.value)
  } catch {
    noMoreData.value = true
  } finally {
    loadingMore.value = false
  }
}

/**
 * 将搜索 query 转换为地图页 query string。
 *
 * 前置条件：query 来自 buildSearchActivitiesQuery，仅包含 OpenAPI 查询字段。
 * 后置条件：返回可附加到地图页 URL 的 query string；空参数返回空字符串。
 * 不变量：不会传递分页字段，地图页自行决定点位分页范围。
 */
function buildMapQueryString(query: ReturnType<typeof buildSearchActivitiesQuery>): string {
  const params: string[] = []
  for (const [key, value] of Object.entries(query)) {
    if (key === 'page' || key === 'pageSize' || value == null) continue
    const encodedValue = Array.isArray(value) ? value.join(',') : String(value)
    if (!encodedValue) continue
    params.push(`${encodeURIComponent(key)}=${encodeURIComponent(encodedValue)}`)
  }
  return params.join('&')
}

/**
 * 跳转到地图模式。
 *
 * 前置条件：用户可已选择任意搜索筛选条件。
 * 后置条件：地图页携带当前高级筛选条件打开，避免切换地图后筛选丢失。
 * 不变量：分页参数不透传给地图页。
 */
function goToMap(): void {
  const query = buildSearchActivitiesQuery(keyword.value, getFilterSelection(), 1, pageSize)
  const queryString = buildMapQueryString(query)
  uni.navigateTo({ url: `/pages/discover/map${queryString ? `?${queryString}` : ''}` })
}

/** 跳转到活动详情 */
function goDetail(activityId: string): void {
  uni.navigateTo({ url: `/pages/activity/detail?activityId=${activityId}` })
}

/** 获取 App 可直接渲染的媒体地址 */
function getMediaUrl(signedUrl: string): string {
  return toAbsoluteMediaUrl(signedUrl)
}

loadTypeOptions()

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
        <text class="search-submit" @tap="doSearch">搜索</text>
      </view>
      <text class="map-link" @tap="goToMap">地图模式</text>
    </view>

    <!-- 轻量筛选 -->
    <view class="filter-panel">
      <view class="filter-group">
        <text class="filter-label">类型</text>
        <view class="filter-chips">
          <text v-if="isLoadingTypes" class="filter-chip">加载中...</text>
          <text
            v-for="type in typeOptions"
            :key="type"
            class="filter-chip"
            :class="{ active: selectedTypes.includes(type) }"
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

      <view class="filter-group">
        <text class="filter-label">距离</text>
        <view class="filter-chips">
          <text
            v-for="distance in DISTANCE_OPTIONS"
            :key="distance.value"
            class="filter-chip"
            :class="{ active: selectedDistance === distance.value }"
            @tap="toggleDistanceFilter(distance.value)"
          >
            {{ distance.label }}
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
    <scroll-view
      v-if="!isSearching && searchResults.length > 0"
      class="results"
      scroll-y
      @scrolltolower="loadMoreSearchResults"
    >
      <text class="results-count">共 {{ totalCount }} 个结果</text>

      <view
        v-for="item in searchResults"
        :key="item.activityId"
        class="result-card"
        @tap="goDetail(item.activityId)"
      >
        <view class="result-card-inner">
          <view v-if="item.coverImage?.signedUrl" class="result-cover">
            <image
              :src="getMediaUrl(item.coverImage.signedUrl)"
              mode="aspectFill"
              class="cover-img"
            />
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
      <view class="load-more-state">
        <text v-if="loadingMore">加载更多...</text>
        <text v-else-if="noMoreData">已加载全部</text>
      </view>
    </scroll-view>
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
  background: $color-bg-card;
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

.search-submit {
  flex-shrink: 0;
  margin-left: $spacing-md;
  padding-left: $spacing-md;
  border-left: 1px solid $color-border;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $color-primary;
  white-space: nowrap;
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
  border-color: rgba(22, 160, 133, 0.35);
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
  height: 52vh;
}

.results-count {
  font-size: $font-sm;
  color: $color-text-sub;
  margin-bottom: $spacing-md;
}

.result-card {
  background: $color-bg-card;
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

.load-more-state {
  display: flex;
  justify-content: center;
  padding: $spacing-md 0 $spacing-lg;

  text {
    font-size: $font-xs;
    color: $color-text-muted;
  }
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

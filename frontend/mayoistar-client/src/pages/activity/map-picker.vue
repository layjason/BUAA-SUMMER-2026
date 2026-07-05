<template>
  <view class="mp-page">
    <view class="mp-search-bar">
      <view class="mp-back-btn" @click="goBack">返回</view>
      <view class="mp-search-box">
        <text class="mp-search-icon">🔍</text>
        <input
          v-model="keyword"
          class="mp-search-input"
          :placeholder="t('mapPicker.searchPlaceholder')"
          confirm-type="search"
          @confirm="onSearch"
          @input="onSearchInput"
        />
        <text v-if="keyword" class="mp-search-clear" @click="onClearSearch">✕</text>
      </view>
    </view>

    <view class="mp-map-area">
      <map
        id="pickerMap"
        class="mp-map"
        :latitude="centerLat"
        :longitude="centerLng"
        :scale="scale"
        :markers="pickerMarkers"
        @tap="onMapTap"
        @regionchange="onRegionChange"
      />

      <cover-image class="mp-center-pin" src="/static/map/location-marker.png" />
    </view>

    <view class="mp-bottom-area">
      <!-- 地址栏 + 展开/收起 -->
      <view class="mp-addr-bar" @click="toggleList">
        <view class="mp-addr-icon">📍</view>
        <view class="mp-addr-text">{{ pickAddress || t('mapPicker.dragHint') }}</view>
        <view class="mp-addr-city">{{ pickCity }}</view>
        <view class="mp-toggle-icon">{{ listExpanded ? '&#x25BC;' : '&#x25B2;' }}</view>
        <view class="mp-toggle-label"
          >{{ listExpanded ? t('mapPicker.collapse') : t('mapPicker.candidates') }}({{
            displayItems.length
          }})</view
        >
        <!-- 回到定位 -->
        <view class="mp-locate-btn" @click.stop="locateMe">
          <image class="mp-locate-icon" src="/static/map/locate.svg" mode="aspectFit" />
        </view>
      </view>

      <!-- 候选地点列表（可收起） -->
      <view v-if="listExpanded" class="mp-candidate-list">
        <view
          v-for="(item, idx) in displayItems"
          :key="item.id || idx"
          class="mp-list-item"
          @click="selectItem(item)"
        >
          <view class="mp-item-icon">📍</view>
          <view class="mp-item-body">
            <view class="mp-item-name">{{ item.name }}</view>
            <view class="mp-item-addr">{{ item.address }}</view>
          </view>
        </view>
        <view v-if="showSearching && searchResult.length === 0" class="mp-list-empty">
          <view class="mp-empty-text">未找到匹配地点</view>
        </view>
      </view>

      <!-- 确认按钮 -->
      <view class="mp-confirm-btn" @click="confirmPick">
        {{ t('mapPicker.confirm') }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 地图选点页面
 *
 * 使用 uni-app <map> 组件渲染地图，通过 EventChannel 与 uni.$emit 将选点结果回传上一页。
 * 前置条件：用户已授权定位权限（用于默认中心点）
 * 后置条件：确认选点后触发 onLocationPicked / activityLocationPicked 并返回
 * 不变量：经纬度始终有值（从当前位置或默认坐标取）
 */
import { ref, computed, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  searchPOI,
  searchNearbyPOI,
  reverseGeocode,
  getCurrentLocation,
  type POIItem,
} from '@/services/amap'
import { createMapMoveRequest, normalizeGeoPoint } from '@/utils/map-move'

const { t } = useI18n()

let myLocationMarkerIcon = '/static/map/my-location.svg'
/* #ifdef APP-PLUS */
myLocationMarkerIcon = '/static/map/my-location.png'
/* #endif */

/* ---- 事件通道 ---- */
let eventChannel: UniApp.EventChannel | null = null

/* ---- 状态 ---- */
const keyword = ref('')
const centerLat = ref(39.908)
const centerLng = ref(116.397)
const scale = ref(15)
const pickAddress = ref('')
const pickCity = ref('')
const listExpanded = ref(false)
const currentLat = ref<number | null>(null)
const currentLng = ref<number | null>(null)

const searchResult = ref<POIItem[]>([])
const nearbyResult = ref<POIItem[]>([])
const showSearching = ref(false)

/** 地图上的辅助标记 */
const pickerMarkers = computed(() => {
  if (currentLat.value == null || currentLng.value == null) return []
  return [
    {
      id: 1,
      latitude: currentLat.value,
      longitude: currentLng.value,
      title: '我的位置',
      iconPath: myLocationMarkerIcon,
      width: 22,
      height: 22,
      anchor: { x: 0.5, y: 0.5 },
    },
  ]
})

/** 当前展示的列表 */
const displayItems = computed<POIItem[]>(() => {
  if (showSearching.value) {
    return searchResult.value
  }
  return nearbyResult.value
})

/**
 * 切换候选列表展开状态
 *
 * 前置条件：页面已完成初始化。
 * 后置条件：候选列表在展开和收起之间切换。
 * 不变量：不修改候选列表内容。
 */
function toggleList(): void {
  listExpanded.value = !listExpanded.value
}

/* ---- 搜索 ---- */
let searchTimer: ReturnType<typeof setTimeout> | null = null
let regionTimer: ReturnType<typeof setTimeout> | null = null
let suppressRegionRefreshUntil = 0
let initialLocationProtectedUntil = 0

/**
 * 处理搜索框输入
 *
 * 前置条件：keyword 已由 v-model 同步为最新输入。
 * 后置条件：空关键词清空搜索状态；非空关键词触发防抖搜索。
 * 不变量：同一时刻最多保留一个待执行的搜索定时器。
 */
function onSearchInput(): void {
  if (searchTimer) clearTimeout(searchTimer)
  if (!keyword.value.trim()) {
    showSearching.value = false
    searchResult.value = []
    listExpanded.value = nearbyResult.value.length > 0
    return
  }
  showSearching.value = true
  listExpanded.value = true
  searchTimer = setTimeout(() => onSearch(), 300)
}

/**
 * 执行 POI 关键词搜索
 *
 * 前置条件：keyword 可能为空或包含搜索关键词。
 * 后置条件：搜索结果写入 searchResult，并按结果数量更新候选列表展开状态。
 * 不变量：搜索失败不会向外抛出异常。
 */
async function onSearch(): Promise<void> {
  if (!keyword.value.trim()) {
    showSearching.value = false
    searchResult.value = []
    return
  }
  try {
    const results = await searchPOI(keyword.value.trim())
    searchResult.value = results
    listExpanded.value = results.length > 0
  } catch {
    searchResult.value = []
  }
}

/**
 * 清空搜索状态
 *
 * 前置条件：用户点击清空按钮或调用方需要重置搜索。
 * 后置条件：关键词、搜索结果、搜索展示状态和待执行定时器被清空。
 * 不变量：不修改当前选点坐标与地址。
 */
function onClearSearch(): void {
  keyword.value = ''
  showSearching.value = false
  searchResult.value = []
  listExpanded.value = nearbyResult.value.length > 0
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
}

/* ---- 选中地点 ---- */

/**
 * 将地图中心移动到指定坐标
 *
 * 前置条件：调用方传入 GCJ-02 坐标，且地图组件已完成创建或即将完成创建。
 * 后置条件：响应式中心点与原生地图视口都会尝试移动到指定坐标。
 * 不变量：非法坐标不会覆盖当前中心点；视口移动失败时，响应式中心点仍保持为目标坐标。
 *
 * @param latitude 纬度
 * @param longitude 经度
 */
async function moveMapCenter(latitude: number, longitude: number): Promise<void> {
  const moveRequest = createMapMoveRequest(latitude, longitude)
  if (!moveRequest) return

  centerLat.value = moveRequest.latitude
  centerLng.value = moveRequest.longitude
  scale.value = 15

  await nextTick()

  try {
    uni.createMapContext('pickerMap').moveToLocation(moveRequest)
  } catch {
    /* createMapContext 失败时忽略，绑定值仍会保留目标中心点 */
  }
}

/**
 * 直接更新地图中心点绑定值。
 *
 * 前置条件：center 已经通过坐标校验。
 * 后置条件：地图组件的响应式中心点更新到目标位置。
 * 不变量：不读取原生地图中心，避免初始化阶段旧视野回写。
 *
 * @param center 目标中心点
 */
function applyCenterState(center: MapCenterPoint): void {
  centerLat.value = center.latitude
  centerLng.value = center.longitude
  scale.value = 15
}

/**
 * 按当前中心点刷新地址和附近候选。
 *
 * 前置条件：centerLat/centerLng 为当前地图中心 GCJ-02 坐标。
 * 后置条件：地址栏展示中心点地址；非搜索状态下候选列表展示中心点附近 POI 并自动展开。
 * 不变量：搜索状态下不覆盖搜索结果列表。
 */
async function refreshCenterContext(options: { expandNearby?: boolean } = {}): Promise<void> {
  const expandNearby = options.expandNearby ?? true
  const latitude = centerLat.value
  const longitude = centerLng.value
  const [regeo, nearby] = await Promise.all([
    reverseGeocode(latitude, longitude),
    searchNearbyPOI(longitude, latitude),
  ])
  pickAddress.value = regeo.address || pickAddress.value || '当前位置'
  pickCity.value = regeo.city || pickCity.value
  if (!showSearching.value) {
    nearbyResult.value = nearby
    listExpanded.value = expandNearby && nearby.length > 0
  }
}

/**
 * 围绕指定中心点刷新地址和周边候选。
 *
 * 前置条件：center 是目标 GCJ-02 坐标。
 * 后置条件：地址栏和候选列表围绕指定坐标刷新。
 * 不变量：不会读取原生地图当前中心，避免已有地址初始化被默认视野或定位覆盖。
 *
 * @param center 目标中心点
 * @param options 列表展开选项
 */
async function refreshContextAtCenter(
  center: MapCenterPoint,
  options: { expandNearby?: boolean } = {},
): Promise<void> {
  applyCenterState(center)
  await refreshCenterContext(options)
}

/**
 * 获取原生地图当前中心点并刷新上下文。
 *
 * 前置条件：pickerMap 已完成渲染。
 * 后置条件：响应式中心点与原生地图中心一致，并刷新地址/附近候选。
 * 不变量：获取中心点失败时不清空已有地址和候选列表。
 */
interface MapCenterPoint {
  latitude: number
  longitude: number
}

interface MapRegionChangeEvent {
  type?: string
  detail?: {
    type?: string
    latitude?: number
    longitude?: number
    centerLocation?: MapCenterPoint
  }
}

interface MapBoundsPoint {
  latitude: number
  longitude: number
}

/**
 * 从地图 regionchange 事件中读取中心点。
 *
 * 前置条件：event 来自 uni-app map 组件，不同端字段可能不完全一致。
 * 后置条件：若事件中包含合法中心坐标则返回，否则返回 null。
 * 不变量：只解析事件载荷，不修改页面状态。
 *
 * @param event 地图视野变化事件
 */
function getCenterFromRegionEvent(event?: MapRegionChangeEvent): MapCenterPoint | null {
  const detail = event?.detail
  const candidate =
    detail?.centerLocation ??
    (typeof detail?.latitude === 'number' && typeof detail.longitude === 'number'
      ? { latitude: detail.latitude, longitude: detail.longitude }
      : null)
  if (!candidate) return null
  const moveRequest = createMapMoveRequest(candidate.latitude, candidate.longitude)
  return moveRequest
}

/**
 * 从原生地图上下文读取当前视野中心点。
 *
 * 前置条件：pickerMap 已渲染；移动端可能不支持 getCenterLocation 或偶发返回失败。
 * 后置条件：优先返回 getCenterLocation 结果；失败时用 getRegion 的东北/西南点计算中心。
 * 不变量：读取失败返回 null，不抛出异常。
 */
async function getNativeMapCenter(): Promise<MapCenterPoint | null> {
  const context = uni.createMapContext('pickerMap')
  const directCenter = await new Promise<MapCenterPoint | null>((resolve) => {
    try {
      context.getCenterLocation({
        success: (res) => resolve({ latitude: res.latitude, longitude: res.longitude }),
        fail: () => resolve(null),
      })
    } catch {
      resolve(null)
    }
  })
  if (directCenter) return directCenter

  return new Promise<MapCenterPoint | null>((resolve) => {
    try {
      context.getRegion({
        success: (res: { southwest: MapBoundsPoint; northeast: MapBoundsPoint }) => {
          resolve({
            latitude: (res.southwest.latitude + res.northeast.latitude) / 2,
            longitude: (res.southwest.longitude + res.northeast.longitude) / 2,
          })
        },
        fail: () => resolve(null),
      })
    } catch {
      resolve(null)
    }
  })
}

/**
 * 同步地图当前中心点并刷新周边地点。
 *
 * 前置条件：地图视野已经变化结束，或调用方传入了事件中的中心点。
 * 后置条件：centerLat/centerLng 与中心针位置一致，并按中心点刷新地址和附近候选。
 * 不变量：无法读取中心点时不清空已有地址和候选列表。
 *
 * @param eventCenter 事件载荷中解析出的中心点
 */
async function syncNativeMapCenter(eventCenter?: MapCenterPoint | null): Promise<void> {
  const center = eventCenter ?? (await getNativeMapCenter())
  if (!center) return
  const moveRequest = createMapMoveRequest(center.latitude, center.longitude)
  if (!moveRequest) return
  centerLat.value = moveRequest.latitude
  centerLng.value = moveRequest.longitude
  await refreshCenterContext()
}

/**
 * 选择候选地点
 *
 * 前置条件：候选地点包含合法经纬度。
 * 后置条件：地图中心、地址展示和搜索状态同步为选中地点。
 * 不变量：非法坐标不会覆盖当前地图中心。
 *
 * @param item 候选地点
 */
async function selectItem(item: POIItem): Promise<void> {
  suppressRegionRefreshUntil = Date.now() + 800
  await moveMapCenter(item.latitude, item.longitude)
  pickAddress.value = item.address || item.name
  pickCity.value = item.city ?? pickCity.value
  showSearching.value = false
  searchResult.value = []
  keyword.value = ''
  listExpanded.value = false
  await refreshCenterContext({ expandNearby: false })
}

/* ---- 地图事件 ---- */

/**
 * 处理地图点击选点
 *
 * 前置条件：地图点击事件提供经纬度。
 * 后置条件：地图中心移动到点击位置，并尝试更新逆地理编码地址。
 * 不变量：逆地理编码失败时保留已有地址。
 *
 * @param e 地图点击事件
 */
async function onMapTap(e: { detail: { latitude: number; longitude: number } }): Promise<void> {
  await moveMapCenter(e.detail.latitude, e.detail.longitude)
  await refreshCenterContext()
}

/**
 * 地图视野变化结束后刷新中心点信息。
 *
 * 前置条件：用户拖动或缩放地图，uni-app 触发 regionchange。
 * 后置条件：地图停止移动后同步中心点地址；非搜索状态刷新附近候选。
 * 不变量：change 过程中只防抖，不频繁请求高德接口。
 */
function onRegionChange(event: MapRegionChangeEvent): void {
  const regionPhase = event.detail?.type
  if (regionPhase && regionPhase !== 'end') return
  if (Date.now() < suppressRegionRefreshUntil) return
  if (Date.now() < initialLocationProtectedUntil) return
  const eventCenter = getCenterFromRegionEvent(event)
  if (regionTimer) clearTimeout(regionTimer)
  regionTimer = setTimeout(() => {
    void syncNativeMapCenter(eventCenter)
  }, 250)
}

/**
 * 回到当前定位
 *
 * 前置条件：定位权限已授权
 * 后置条件：地图中心移至当前位置，地理描述更新
 */
async function locateMe(): Promise<void> {
  try {
    const loc = await getCurrentLocation()
    currentLat.value = loc.latitude
    currentLng.value = loc.longitude
    await moveMapCenter(loc.latitude, loc.longitude)
    showSearching.value = false
    searchResult.value = []
    keyword.value = ''
    await refreshCenterContext()
  } catch {
    /* 忽略定位失败 */
  }
}

/**
 * 仅刷新“我的位置”标记，不移动当前选点中心。
 *
 * 前置条件：页面已经创建地图组件，定位权限可能已授权。
 * 后置条件：定位成功时当前位置 marker 更新；失败时保留已有 marker。
 * 不变量：不会修改 centerLat/centerLng、候选列表和搜索状态。
 */
async function refreshCurrentLocationMarker(): Promise<void> {
  try {
    const loc = await uni.getLocation({ type: 'gcj02' })
    currentLat.value = loc.latitude
    currentLng.value = loc.longitude
  } catch {
    /* 定位失败不影响选点 */
  }
}

/**
 * 读取编辑页带入的已有地点并初始化地图中心。
 *
 * 前置条件：query 来自 uni-app 页面路由，可能不包含已有地点。
 * 后置条件：已有地点坐标合法时优先展示该地点；否则回到当前位置定位流程。
 * 不变量：路由参数只作为初始中心，不会绕过用户最终确认选点。
 *
 * @param query 页面路由参数
 */
async function initializeFromRoute(
  query: Record<string, string | undefined> | undefined,
): Promise<boolean> {
  if (!query?.longitude || !query.latitude) return false
  const point = normalizeGeoPoint(Number(query.longitude), Number(query.latitude))
  if (!point) return false

  const center = { latitude: point.latitude, longitude: point.longitude }
  initialLocationProtectedUntil = Date.now() + 3000
  suppressRegionRefreshUntil = Date.now() + 3000
  if (regionTimer) {
    clearTimeout(regionTimer)
    regionTimer = null
  }
  applyCenterState(center)
  pickAddress.value = decodeURIComponent(query.address ?? query.name ?? '') || '当前位置'
  pickCity.value = decodeURIComponent(query.city ?? '')
  showSearching.value = false
  searchResult.value = []
  keyword.value = ''

  await nextTick()
  try {
    uni.createMapContext('pickerMap').moveToLocation(center)
  } catch {
    /* 原生地图上下文暂未就绪时，组件绑定值仍保留目标中心点。 */
  }
  await refreshContextAtCenter(center, { expandNearby: true })
  applyCenterState(center)
  suppressRegionRefreshUntil = Date.now() + 600
  return true
}

/* ---- 确认选点 ---- */

/**
 * 确认当前选点
 *
 * 前置条件：页面由活动编辑页打开时可取得 EventChannel。
 * 后置条件：通过 EventChannel 回传选点结果并返回上一页。
 * 不变量：没有 EventChannel 时仍允许返回上一页。
 */
function confirmPick(): void {
  const point = normalizeGeoPoint(centerLng.value, centerLat.value)
  if (!point) {
    uni.showToast({ title: '地点坐标异常，请重新选择', icon: 'none' })
    return
  }
  const pickedLocation = {
    name: pickAddress.value || '当前位置',
    address: pickAddress.value || '当前位置',
    latitude: point.latitude,
    longitude: point.longitude,
    city: pickCity.value || '',
  }

  if (eventChannel) {
    eventChannel.emit('onLocationPicked', pickedLocation)
  }
  uni.$emit('activityLocationPicked', pickedLocation)
  setTimeout(() => uni.navigateBack(), 0)
}

/**
 * 返回上一页。
 *
 * 前置条件：地图选点页由 uni.navigateTo 打开。
 * 后置条件：返回上一页且不提交新的地点选择结果。
 * 不变量：不修改当前已选地点状态。
 */
function goBack(): void {
  uni.navigateBack()
}

/* ---- 生命周期 ---- */

onLoad((query) => {
  const routeQuery = (query ?? {}) as Record<string, string | undefined>
  keyword.value = ''
  searchResult.value = []
  nearbyResult.value = []
  showSearching.value = false
  listExpanded.value = false

  const routePoint =
    routeQuery.longitude && routeQuery.latitude
      ? normalizeGeoPoint(Number(routeQuery.longitude), Number(routeQuery.latitude))
      : null
  if (routePoint) {
    initialLocationProtectedUntil = Date.now() + 2500
    suppressRegionRefreshUntil = Date.now() + 2500
    applyCenterState({ latitude: routePoint.latitude, longitude: routePoint.longitude })
    pickAddress.value = decodeURIComponent((routeQuery.address ?? routeQuery.name ?? '') || '')
    pickCity.value = decodeURIComponent(routeQuery.city ?? '')
  }

  const pages = getCurrentPages()
  const currentPage = pages.length > 0 ? pages[pages.length - 1] : null
  eventChannel =
    ((
      currentPage as unknown as { getOpenerEventChannel?: () => UniApp.EventChannel } | null
    )?.getOpenerEventChannel?.() as UniApp.EventChannel | undefined) ?? null

  setTimeout(() => {
    void (async () => {
      await refreshCurrentLocationMarker()
      const hasInitialLocation = await initializeFromRoute(routeQuery)
      if (!hasInitialLocation) {
        await locateMe()
      }
    })()
  }, 80)
})

onUnload(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  if (regionTimer) {
    clearTimeout(regionTimer)
    regionTimer = null
  }
})
</script>

<style scoped>
.mp-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: #fff;
  overflow: hidden;
  box-sizing: border-box;
}

.mp-map {
  width: 100%;
  height: 100%;
}

.mp-map-area {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ---- 回到定位按钮 ---- */
.mp-locate-btn {
  flex-shrink: 0;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background-color: #5ec8a7;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8rpx;
}

.mp-locate-btn:active {
  background-color: #45b994;
}

.mp-locate-icon {
  width: 32rpx;
  height: 32rpx;
  pointer-events: none;
}

/* ---- 搜索栏 ---- */
.mp-search-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: calc(var(--status-bar-height) + 12rpx) 32rpx 18rpx;
  background-color: #fff;
  z-index: 2;
}

.mp-search-box {
  flex: 1;
  display: flex;
  align-items: center;
  background-color: #fff;
  border-radius: 48rpx;
  padding: 16rpx 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.12);
}

.mp-back-btn {
  flex-shrink: 0;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #5ec8a7;
  background-color: #f2fbf7;
  border-radius: 36rpx;
}

.mp-search-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.mp-search-input {
  flex: 1;
  font-size: 28rpx;
  color: #323233;
  height: 48rpx;
}

.mp-search-clear {
  font-size: 28rpx;
  color: #c8c9cc;
  padding: 8rpx;
  flex-shrink: 0;
}

/* ---- 中心固定标记（图片） ---- */
.mp-center-pin {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -100%);
  z-index: 5;
  pointer-events: none;
  width: 60rpx;
  height: 60rpx;
}

/* ---- 底部区域（地址栏 + 候选列表 + 确认按钮） ---- */
.mp-bottom-area {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  max-height: 72%;
  background-color: #fff;
  border-radius: 24rpx 24rpx 0 0;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
  padding: 0 32rpx calc(30rpx + env(safe-area-inset-bottom));
  z-index: 2;
  box-sizing: border-box;
}

/* 地址栏行 */
.mp-addr-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
  min-height: 80rpx;
}

.mp-addr-bar:active {
  opacity: 0.8;
}

.mp-addr-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.mp-addr-text {
  flex: 1;
  font-size: 26rpx;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mp-addr-city {
  font-size: 22rpx;
  color: #969799;
  margin-left: 8rpx;
  flex-shrink: 0;
}

.mp-toggle-icon {
  font-size: 20rpx;
  color: #969799;
  margin-left: 12rpx;
  flex-shrink: 0;
}

.mp-toggle-label {
  font-size: 22rpx;
  color: #5ec8a7;
  margin-left: 6rpx;
  flex-shrink: 0;
}

/* 候选列表 */
.mp-candidate-list {
  flex: 0 1 auto;
  max-height: 40vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  border-bottom: 2rpx solid #f5f5f5;
}

.mp-list-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
}

.mp-list-item:last-child {
  border-bottom: none;
}

.mp-list-item:active {
  background-color: #f2f3f5;
}

.mp-item-icon {
  font-size: 30rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.mp-item-body {
  flex: 1;
  min-width: 0;
}

.mp-item-name {
  font-size: 26rpx;
  color: #323233;
  font-weight: 500;
}

.mp-item-addr {
  font-size: 22rpx;
  color: #969799;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mp-list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 0;
}

.mp-empty-text {
  font-size: 26rpx;
  color: #969799;
}

/* 确认按钮 */
.mp-confirm-btn {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background-color: #5ec8a7;
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: 44rpx;
  margin: 20rpx 0 10rpx;
}

.mp-confirm-btn:active {
  opacity: 0.85;
}
</style>

<style>
html,
body,
page {
  height: 100%;
  overflow: hidden;
}
</style>

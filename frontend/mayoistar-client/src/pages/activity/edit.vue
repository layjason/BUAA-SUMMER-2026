<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="edit-container">
        <!-- 活动图片 -->
        <view class="section">
          <text class="section-title">{{ t('activityEdit.images') }}</text>
          <view class="image-grid">
            <view
              v-for="(img, idx) in imagePreviews"
              :key="idx"
              class="image-preview-item"
              @click="removeImage(idx)"
            >
              <image v-if="img" class="preview-image" :src="img" mode="aspectFill" />
              <view v-else class="preview-image preview-image--failed">
                <text class="preview-failed-text">加载失败</text>
              </view>
              <text class="image-remove">×</text>
            </view>
            <view class="image-add-btn" @click="handleAddImage">
              <text class="add-icon">+</text>
              <text class="add-text">{{ t('activityEdit.addImage') }}</text>
            </view>
          </view>
        </view>

        <!-- 活动名称 -->
        <FormInput
          v-model="formTitle"
          :label="t('activityEdit.title')"
          :placeholder="t('activityEdit.titlePlaceholder')"
          :error="errors.title"
          required
        />

        <!-- 活动标签 -->
        <view class="form-item">
          <text class="label"> <text class="req">* </text>{{ t('activityEdit.tags') }} </text>
          <view class="tag-create-row">
            <input
              v-model="tagInput"
              class="tag-input"
              type="text"
              maxlength="20"
              placeholder="输入标签后添加"
              confirm-type="done"
              @confirm="addTag"
            />
            <button class="tag-add-button" @click="addTag">添加</button>
          </view>
          <view v-if="activityTags.length" class="tags-row">
            <view v-for="tag in activityTags" :key="tag" class="tag-chip" @click="removeTag(tag)">
              <text>{{ tag }}</text>
              <text class="tag-remove">×</text>
            </view>
          </view>
          <text v-else class="hint">至少添加 1 个标签，便于用户了解活动类型</text>
          <text v-if="errors.tags" class="field-error">{{ errors.tags }}</text>
        </view>

        <!-- 时间 -->
        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('activityEdit.startAt') }}</text>
          <view class="datetime-row">
            <picker mode="date" :value="startAtDate" @change="onStartAtDateChange">
              <view class="picker-value">
                <text :class="{ placeholder: !startAtDate }">{{
                  startAtDate || t('activityEdit.date')
                }}</text>
              </view>
            </picker>
            <picker mode="time" :value="startAtTime" @change="onStartAtTimeChange">
              <view class="picker-value">
                <text :class="{ placeholder: !startAtTime }">{{
                  startAtTime || t('activityEdit.time')
                }}</text>
              </view>
            </picker>
          </view>
          <text v-if="errors.startAt" class="field-error">{{ errors.startAt }}</text>
        </view>

        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('activityEdit.endAt') }}</text>
          <view class="datetime-row">
            <picker mode="date" :value="endAtDate" @change="onEndAtDateChange">
              <view class="picker-value">
                <text :class="{ placeholder: !endAtDate }">{{
                  endAtDate || t('activityEdit.date')
                }}</text>
              </view>
            </picker>
            <picker mode="time" :value="endAtTime" @change="onEndAtTimeChange">
              <view class="picker-value">
                <text :class="{ placeholder: !endAtTime }">{{
                  endAtTime || t('activityEdit.time')
                }}</text>
              </view>
            </picker>
          </view>
          <text v-if="errors.endAt" class="field-error">{{ errors.endAt }}</text>
        </view>

        <view class="form-item">
          <text class="label"
            ><text class="req">* </text>{{ t('activityEdit.registrationDeadline') }}</text
          >
          <view class="datetime-row">
            <picker mode="date" :value="deadlineDate" @change="onDeadlineDateChange">
              <view class="picker-value">
                <text :class="{ placeholder: !deadlineDate }">{{
                  deadlineDate || t('activityEdit.date')
                }}</text>
              </view>
            </picker>
            <picker mode="time" :value="deadlineTime" @change="onDeadlineTimeChange">
              <view class="picker-value">
                <text :class="{ placeholder: !deadlineTime }">{{
                  deadlineTime || t('activityEdit.time')
                }}</text>
              </view>
            </picker>
          </view>
          <text v-if="errors.registrationDeadline" class="field-error">{{
            errors.registrationDeadline
          }}</text>
        </view>

        <!-- 地点 -->
        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('activityEdit.location') }}</text>
          <view class="location-map-preview" @click="goToMapPicker">
            <view v-if="hasLocation" class="preview-card">
              <image
                v-if="staticMapUrl"
                class="preview-static-map"
                :src="staticMapUrl"
                mode="aspectFill"
              />
              <view class="preview-footer">
                <text class="preview-address-text">{{ selectedLocationLabel }}</text>
                <text class="preview-arrow">&gt;</text>
              </view>
            </view>
            <view v-else class="preview-placeholder">
              <view class="placeholder-icon-row">
                <text class="placeholder-location-icon">📍</text>
              </view>
              <text class="placeholder-main">{{ t('activityEdit.selectLocation') }}</text>
              <text class="placeholder-sub">{{ t('activityEdit.selectLocationHint') }}</text>
            </view>
          </view>
          <text v-if="errors.location" class="field-error">{{ errors.location }}</text>
        </view>

        <!-- 人数上限 -->
        <FormInput
          v-model="formCapacity"
          :label="t('activityEdit.capacity')"
          :placeholder="t('activityEdit.capacityPlaceholder')"
          type="number"
          :error="errors.capacity"
          required
        />

        <!-- 费用 -->
        <FormInput
          v-model="formFeeAmount"
          :label="t('activityEdit.feeAmount')"
          :placeholder="t('activityEdit.feeAmountPlaceholder')"
          type="digit"
        />
        <FormInput
          v-model="formFeeDescription"
          :label="t('activityEdit.feeDescription')"
          :placeholder="t('activityEdit.feeDescriptionPlaceholder')"
        />

        <!-- 最低年龄 -->
        <FormInput
          v-model="formMinAge"
          :label="t('activityEdit.minAge')"
          :placeholder="t('activityEdit.minAgePlaceholder')"
          type="number"
        />

        <!-- 活动简介 -->
        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('activityEdit.introduction') }}</text>
          <textarea
            v-model="formIntroduction"
            class="textarea"
            :placeholder="t('activityEdit.introductionPlaceholder')"
            :maxlength="2000"
            auto-height
          />
          <text v-if="errors.introduction" class="field-error">{{ errors.introduction }}</text>
        </view>

        <!-- 安全须知 -->
        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('activityEdit.safetyNotice') }}</text>
          <textarea
            v-model="formSafetyNotice"
            class="textarea"
            :placeholder="t('activityEdit.safetyNoticePlaceholder')"
            :maxlength="2000"
            auto-height
          />
          <text v-if="errors.safetyNotice" class="field-error">{{ errors.safetyNotice }}</text>
        </view>

        <view class="form-item">
          <view class="switch-row">
            <view class="switch-copy">
              <text class="label">签到位置校验</text>
              <text class="switch-hint">开启后，参与者签到时需要位于活动地点附近</text>
            </view>
            <switch :checked="formRequireLocationCheck" @change="onRequireLocationCheckChange" />
          </view>
        </view>

        <FormError :message="formError" />
      </view>
    </scroll-view>
    <BottomActionBar fixed>
      <button class="bar-btn bar-btn-secondary" :disabled="savingDraft" @click="handleSaveDraft">
        {{ savingDraft ? '保存中...' : t('activityEdit.saveDraft') }}
      </button>
      <button class="bar-btn bar-btn-primary" :disabled="submitting" @click="handleSubmit">
        {{ submitting ? '提交中...' : t('activityEdit.submitReview') }}
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动编辑页
 *
 * 支持创建新活动和编辑已有草稿。
 * 前置条件：用户已登录
 * 后置条件：保存草稿后留在当前页；提交审核后返回上一页
 */
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { API_BASE_URL } from '@/config/env'
import { useAuthStore } from '@/stores/auth'
import {
  createDraft,
  updateDraft,
  getDraft,
  submitDraft,
  uploadActivityImages,
} from '@/api/modules/activities'
import { AMAP_WEB_API_KEY } from '@/services/amap'
import { getErrorMessage } from '@/utils/error'
import { normalizeGeoPoint } from '@/utils/map-move'
import { BottomActionBar, FormInput, FormError } from '@/components'

const { t } = useI18n()
const authStore = useAuthStore()

const activityId = ref('')
const isEdit = computed(() => !!activityId.value)

const formTitle = ref('')
const formAddress = ref('')
const formCity = ref('')
const formPlaceName = ref('')
const locationLongitude = ref<number | null>(null)
const locationLatitude = ref<number | null>(null)
const formCapacity = ref('')
const formFeeAmount = ref('')
const formFeeDescription = ref('')
const formMinAge = ref('')
const formIntroduction = ref('')
const formSafetyNotice = ref('')
const formRequireLocationCheck = ref(false)
const currentLongitude = ref<number | null>(null)
const currentLatitude = ref<number | null>(null)

const startAtDate = ref('')
const startAtTime = ref('')
const endAtDate = ref('')
const endAtTime = ref('')
const deadlineDate = ref('')
const deadlineTime = ref('')

const activityTags = ref<string[]>([])
const tagInput = ref('')

const imagePreviews = ref<string[]>([])
const imageIds = ref<string[]>([])

const savingDraft = ref(false)
const submitting = ref(false)
const formError = ref('')
const errors = ref<Record<string, string>>({})

interface PickedLocation {
  name: string
  address: string
  latitude: number
  longitude: number
  city: string
}

/** 是否已选择地点 */
const hasLocation = computed(() => {
  return locationLongitude.value != null && locationLatitude.value != null
})

/** 已选地点展示文案 */
const selectedLocationLabel = computed(() => {
  if (!hasLocation.value) return ''
  const name = formPlaceName.value || formAddress.value
  return formCity.value ? `${name} · ${formCity.value}` : name
})

/** 高德静态地图预览图地址 */
const staticMapUrl = computed(() => {
  if (!hasLocation.value || locationLongitude.value == null || locationLatitude.value == null) {
    return ''
  }
  return buildAmapStaticMapUrl({
    longitude: locationLongitude.value,
    latitude: locationLatitude.value,
    currentLongitude: currentLongitude.value,
    currentLatitude: currentLatitude.value,
  })
})

/**
 * 构造高德静态地图 URL。
 *
 * 前置条件：活动地点坐标已经通过 normalizeGeoPoint 校验为 GCJ-02 坐标。
 * 后置条件：返回可直接用于 image 组件的静态地图地址，包含活动地点 marker；当前位置可用时额外标记“我”。
 * 不变量：仅生成展示 URL，不修改活动表单状态，也不新增业务字段。
 *
 * @param input 静态地图所需坐标
 */
function buildAmapStaticMapUrl(input: {
  longitude: number
  latitude: number
  currentLongitude: number | null
  currentLatitude: number | null
}): string {
  const markers = [`mid,0x5EC8A7,A:${input.longitude},${input.latitude}`]
  if (input.currentLongitude != null && input.currentLatitude != null) {
    markers.push(`mid,0x2F80ED,我:${input.currentLongitude},${input.currentLatitude}`)
  }
  const params = [
    `location=${encodeURIComponent(`${input.longitude},${input.latitude}`)}`,
    'zoom=15',
    'size=750*300',
    `markers=${encodeURIComponent(markers.join('|'))}`,
    `key=${encodeURIComponent(AMAP_WEB_API_KEY)}`,
  ]
  return `https://restapi.amap.com/v3/staticmap?${params.join('&')}`
}

/**
 * 打开地图选点页
 *
 * 通过 uni.navigateTo 跳转到 map-picker 页面，
 * 用户确认选点后通过 EventChannel 回传地点数据。
 *
 * 前置条件：无
 * 后置条件：回填 location 各字段，清除 location 错误
 */
function goToMapPicker(): void {
  const query = buildMapPickerQuery()
  uni.navigateTo({
    url: `/pages/activity/map-picker${query}`,
    events: {
      onLocationPicked: (loc: PickedLocation) => {
        applyPickedLocation(loc)
      },
    } as Record<string, (data: unknown) => void>,
  })
}

/**
 * 构造跳转至地图选点页的查询字符串
 * 前置条件：地点坐标与文本可能为空
 * 返回值：以 `?` 开头的查询字符串或空字符串
 */
function buildMapPickerQuery(): string {
  if (!hasLocation.value || locationLongitude.value == null || locationLatitude.value == null)
    return ''
  const parts: string[] = []
  parts.push(`longitude=${encodeURIComponent(String(locationLongitude.value))}`)
  parts.push(`latitude=${encodeURIComponent(String(locationLatitude.value))}`)
  parts.push(`name=${encodeURIComponent(String(formPlaceName.value || formAddress.value || ''))}`)
  parts.push(`address=${encodeURIComponent(String(formAddress.value || ''))}`)
  parts.push(`city=${encodeURIComponent(String(formCity.value || ''))}`)
  return `?${parts.join('&')}`
}

/**
 * 回填地图选点结果。
 *
 * 前置条件：loc 来自地图选点页，包含 OpenAPI LocationInfo 所需坐标与地址信息。
 * 后置条件：活动编辑表单中的地点字段、经纬度和地点校验错误被同步更新。
 * 不变量：只更新地点相关字段，不修改活动标题、时间、人数等其它表单状态。
 *
 * @param loc 地图选点页返回的位置
 */
function applyPickedLocation(loc: PickedLocation): void {
  const point = normalizeGeoPoint(loc.longitude, loc.latitude)
  if (!point) {
    errors.value = { ...errors.value, location: '地点坐标格式异常，请重新选择' }
    return
  }
  formPlaceName.value = loc.name
  formAddress.value = loc.address || loc.name
  formCity.value = loc.city || ''
  locationLatitude.value = point.latitude
  locationLongitude.value = point.longitude
  if (errors.value.location) {
    const next = { ...errors.value }
    delete next.location
    errors.value = next
  }
}

/**
 * 加载用户当前位置辅助标记。
 *
 * 前置条件：页面已经进入活动编辑流程，定位权限可能已授权或未授权。
 * 后置条件：定位成功时预览地图会展示“我的位置”；定位失败时不影响活动表单。
 * 不变量：当前位置只用于地图辅助展示，不写入活动地点字段。
 */
async function loadCurrentLocationMarker(): Promise<void> {
  try {
    const location = await uni.getLocation({ type: 'gcj02' })
    currentLongitude.value = location.longitude
    currentLatitude.value = location.latitude
  } catch {
    /* 定位失败不影响活动编辑 */
  }
}

/**
 * 处理地图选点页的全局回传事件。
 *
 * 前置条件：Android App 端 EventChannel 可能不可用或回调时机不稳定。
 * 后置条件：收到 uni.$emit('activityLocationPicked') 后回填地点。
 * 不变量：事件载荷不符合地点结构时不写入表单。
 *
 * @param loc 全局事件载荷
 */
function handleGlobalLocationPicked(loc: PickedLocation): void {
  if (!loc || typeof loc.latitude !== 'number' || typeof loc.longitude !== 'number') return
  applyPickedLocation(loc)
}

/**
 * 添加用户手动输入的活动标签。
 *
 * 前置条件：用户在标签输入框中输入文本后点击添加或键盘完成。
 * 后置条件：非空且未重复的标签会加入活动标签列表，并清空输入与标签校验错误。
 * 不变量：标签只保存在当前活动表单中，不读取或写入后端标签字典。
 */
function addTag(): void {
  const normalizedTag = tagInput.value.trim()
  if (!normalizedTag) return
  if (activityTags.value.includes(normalizedTag)) {
    tagInput.value = ''
    return
  }
  activityTags.value = [...activityTags.value, normalizedTag]
  tagInput.value = ''
  if (errors.value.tags) {
    const next = { ...errors.value }
    delete next.tags
    errors.value = next
  }
}

/**
 * 删除活动表单中的一个手动标签。
 *
 * 前置条件：tag 来自当前 activityTags 列表。
 * 后置条件：对应标签从活动表单中移除。
 * 不变量：只修改当前草稿表单状态，不请求后端标签接口。
 *
 * @param tag 要删除的标签文本
 */
function removeTag(tag: string): void {
  activityTags.value = activityTags.value.filter((item) => item !== tag)
}

// ================= 时间处理 =================

function onStartAtDateChange(e: { detail: { value: string } }): void {
  startAtDate.value = e.detail.value
}
function onStartAtTimeChange(e: { detail: { value: string } }): void {
  startAtTime.value = e.detail.value
}
function onEndAtDateChange(e: { detail: { value: string } }): void {
  endAtDate.value = e.detail.value
}
function onEndAtTimeChange(e: { detail: { value: string } }): void {
  endAtTime.value = e.detail.value
}
function onDeadlineDateChange(e: { detail: { value: string } }): void {
  deadlineDate.value = e.detail.value
}
function onDeadlineTimeChange(e: { detail: { value: string } }): void {
  deadlineTime.value = e.detail.value
}

function toISO(date: string, time: string): string | null {
  if (!date || !time) return null
  return `${date}T${time}:00+08:00`
}

// ================= 图片上传 =================

async function handleAddImage(): Promise<void> {
  try {
    const res = await uni.chooseImage({
      count: 9 - imagePreviews.value.length,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
    })
    for (const tempPath of res.tempFilePaths) {
      try {
        const results = await uploadActivityImages([tempPath])
        const result = results[0] as { mediaId: string; signedUrl?: string }
        imageIds.value.push(result.mediaId)
        imagePreviews.value.push(tempPath)
      } catch {
        formError.value = '图片上传失败'
      }
    }
  } catch {
    /* 用户取消选择 */
  }
}

function removeImage(index: number): void {
  imageIds.value.splice(index, 1)
  imagePreviews.value.splice(index, 1)
}

/**
 * 判断媒体签名 URL 是否属于后端媒体访问端点。
 *
 * 前置条件：signedUrl 来自 MediaFile.signedUrl，可能为相对路径或绝对 URL。
 * 后置条件：返回 true 表示该 URL 需要通过带鉴权的下载流程转换成本地预览路径。
 *
 * @param signedUrl 媒体签名访问地址
 */
function isBackendMediaSignedUrl(signedUrl: string): boolean {
  if (signedUrl.startsWith('/media/')) return true
  const normalizedApiBase = API_BASE_URL.replace(/\/+$/, '')
  return signedUrl.startsWith(`${normalizedApiBase}/media/`)
}

/**
 * 将后端相对媒体 URL 转换为绝对 URL。
 *
 * 前置条件：signedUrl 为后端返回的媒体签名 URL。
 * 后置条件：返回可交给 uni.downloadFile 访问的绝对 URL。
 *
 * @param signedUrl 媒体签名访问地址
 */
function toAbsoluteMediaUrl(signedUrl: string): string {
  if (/^https?:\/\//i.test(signedUrl)) return signedUrl
  return `${API_BASE_URL.replace(/\/+$/, '')}/${signedUrl.replace(/^\/+/, '')}`
}

/**
 * 下载后端私有媒体到本地临时文件用于图片预览。
 *
 * 前置条件：signedUrl 指向后端 /media 端点，当前用户已登录。
 * 后置条件：下载成功时返回本地临时文件路径；失败时返回空字符串，保持图片与 mediaId 索引对齐。
 *
 * @param signedUrl 媒体签名访问地址
 */
async function downloadSignedMediaPreview(signedUrl: string): Promise<string> {
  const accessToken = authStore.getAccessToken()
  if (!accessToken) return ''

  try {
    const result = await uni.downloadFile({
      url: toAbsoluteMediaUrl(signedUrl),
      header: { Authorization: `Bearer ${accessToken}` },
    })
    return result.statusCode === 200 ? result.tempFilePath : ''
  } catch {
    return ''
  }
}

/**
 * 将草稿接口返回的媒体文件转换为 image 组件可展示的预览路径。
 *
 * 前置条件：media.signedUrl 可能是后端相对私有 URL，也可能是 mock/public 绝对 URL。
 * 后置条件：后端私有 URL 会被下载为本地临时路径；其它 URL 原样返回。
 *
 * @param media 草稿图片媒体文件
 */
async function resolveDraftImagePreview(media: { signedUrl?: string | null }): Promise<string> {
  const signedUrl = media.signedUrl ?? ''
  if (!signedUrl) return ''
  if (!isBackendMediaSignedUrl(signedUrl)) return signedUrl
  return downloadSignedMediaPreview(signedUrl)
}

// ================= 表单数据组装 =================

function buildPayload(): Record<string, unknown> {
  const payload: Record<string, unknown> = {}

  if (formTitle.value.trim()) payload.title = formTitle.value.trim()
  if (activityTags.value.length) payload.tags = [...activityTags.value]
  if (formIntroduction.value.trim()) payload.introduction = formIntroduction.value.trim()
  if (formSafetyNotice.value.trim()) payload.safetyNotice = formSafetyNotice.value.trim()
  payload.requireLocationCheck = formRequireLocationCheck.value

  const startISO = toISO(startAtDate.value, startAtTime.value)
  const endISO = toISO(endAtDate.value, endAtTime.value)
  const deadlineISO = toISO(deadlineDate.value, deadlineTime.value)
  if (startISO) payload.startAt = startISO
  if (endISO) payload.endAt = endISO
  if (deadlineISO) payload.registrationDeadline = deadlineISO

  if (
    formAddress.value.trim() &&
    formCity.value.trim() &&
    locationLongitude.value != null &&
    locationLatitude.value != null
  ) {
    payload.location = {
      address: formAddress.value.trim(),
      city: formCity.value.trim(),
      placeName: formPlaceName.value.trim() || undefined,
      point: {
        longitude: locationLongitude.value,
        latitude: locationLatitude.value,
      },
    }
  }

  if (formCapacity.value) {
    const cap = parseInt(formCapacity.value, 10)
    if (!isNaN(cap)) payload.capacity = cap
  }

  if (formFeeAmount.value) {
    const fee = parseFloat(formFeeAmount.value)
    if (!isNaN(fee)) payload.feeAmount = fee
  }
  if (formFeeDescription.value.trim()) payload.feeDescription = formFeeDescription.value.trim()

  if (formMinAge.value) {
    const age = parseInt(formMinAge.value, 10)
    if (!isNaN(age)) payload.minAge = age
  }

  if (imageIds.value.length) payload.imageIds = [...imageIds.value]

  return payload
}

// ================= 校验（仅提交时执行） =================

function validate(): boolean {
  const e: Record<string, string> = {}

  if (!formTitle.value.trim()) e.title = t('activityEdit.titleRequired')
  if (!activityTags.value.length) e.tags = t('activityEdit.tagsRequired')
  if (!startAtDate.value || !startAtTime.value) e.startAt = t('activityEdit.startAtRequired')
  if (!endAtDate.value || !endAtTime.value) e.endAt = t('activityEdit.endAtRequired')
  if (!deadlineDate.value || !deadlineTime.value)
    e.registrationDeadline = t('activityEdit.deadlineRequired')

  const startISO = toISO(startAtDate.value, startAtTime.value)
  const endISO = toISO(endAtDate.value, endAtTime.value)
  const deadlineISO = toISO(deadlineDate.value, deadlineTime.value)

  if (startISO && endISO && new Date(endISO) <= new Date(startISO)) {
    e.endAt = t('activityEdit.timeRangeInvalid')
  }
  if (deadlineISO && startISO && new Date(deadlineISO) > new Date(startISO)) {
    e.registrationDeadline = t('activityEdit.deadlineInvalid')
  }

  if (locationLongitude.value == null || locationLatitude.value == null) {
    e.location = '请先选择活动地点'
  }
  if (!formAddress.value.trim()) e.address = t('activityEdit.addressRequired')
  if (!formCity.value.trim()) e.city = t('activityEdit.cityRequired')

  if (!formCapacity.value) {
    e.capacity = t('activityEdit.capacityRequired')
  } else {
    const cap = parseInt(formCapacity.value, 10)
    if (isNaN(cap) || cap <= 0) e.capacity = t('activityEdit.capacityInvalid')
  }

  if (!formIntroduction.value.trim()) e.introduction = t('activityEdit.introductionRequired')
  if (!formSafetyNotice.value.trim()) e.safetyNotice = t('activityEdit.safetyNoticeRequired')

  errors.value = e
  return Object.keys(e).length === 0
}

// ================= 保存草稿 =================

async function handleSaveDraft(): Promise<void> {
  if (savingDraft.value) return
  savingDraft.value = true
  formError.value = ''

  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateDraft(activityId.value, payload)
    } else {
      const result = (await createDraft(payload)) as {
        activityId: string
      }
      activityId.value = result.activityId
    }
    uni.showToast({ title: t('activityEdit.saveSuccess'), icon: 'success' })
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '保存失败')
    }
  } finally {
    savingDraft.value = false
  }
}

/**
 * 切换签到位置校验
 *
 * 前置条件：用户点击 uni-app switch 组件。
 * 后置条件：formRequireLocationCheck 与开关状态一致。
 * 不变量：仅写入 OpenAPI 已定义的 requireLocationCheck 字段。
 */
function onRequireLocationCheckChange(event: Event): void {
  formRequireLocationCheck.value = (event as CustomEvent<{ value: boolean }>).detail.value
}

// ================= 提交审核 =================

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  formError.value = ''

  if (!validate()) return

  submitting.value = true

  try {
    const payload = buildPayload()
    let id = activityId.value
    if (!id) {
      const result = (await createDraft(payload)) as {
        activityId: string
      }
      id = result.activityId
      activityId.value = id
    } else {
      await updateDraft(id, payload)
    }

    await submitDraft(id)
    uni.showToast({ title: t('activityEdit.submitSuccess'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, '提交失败')
    }
  } finally {
    submitting.value = false
  }
}

function parseISO(iso: string): { date: string; time: string } | null {
  if (!iso) return null
  const d = new Date(iso)
  if (isNaN(d.getTime())) return null
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return { date: `${y}-${m}-${day}`, time: `${hh}:${mm}` }
}

async function loadDraft(): Promise<void> {
  try {
    const draft = await getDraft(activityId.value)

    formTitle.value = draft.title ?? ''
    formIntroduction.value = draft.introduction ?? ''
    formSafetyNotice.value = draft.safetyNotice ?? ''
    formRequireLocationCheck.value = draft.requireLocationCheck ?? false
    formAddress.value = draft.location?.address ?? ''
    formCity.value = draft.location?.city ?? ''
    formPlaceName.value = draft.location?.placeName ?? ''
    const draftPoint =
      draft.location?.point != null
        ? normalizeGeoPoint(draft.location.point.longitude, draft.location.point.latitude)
        : null
    locationLongitude.value = draftPoint?.longitude ?? null
    locationLatitude.value = draftPoint?.latitude ?? null
    formCapacity.value = draft.capacity != null ? String(draft.capacity) : ''
    formFeeAmount.value = draft.feeAmount != null ? String(draft.feeAmount) : ''
    formFeeDescription.value = draft.feeDescription ?? ''
    formMinAge.value = draft.minAge != null ? String(draft.minAge) : ''

    activityTags.value = draft.tags ?? []

    const startParsed = parseISO(draft.startAt ?? '')
    if (startParsed) {
      startAtDate.value = startParsed.date
      startAtTime.value = startParsed.time
    }
    const endParsed = parseISO(draft.endAt ?? '')
    if (endParsed) {
      endAtDate.value = endParsed.date
      endAtTime.value = endParsed.time
    }
    const deadlineParsed = parseISO(draft.registrationDeadline ?? '')
    if (deadlineParsed) {
      deadlineDate.value = deadlineParsed.date
      deadlineTime.value = deadlineParsed.time
    }

    const draftImages = draft.images ?? []
    imageIds.value = draftImages.map((i) => i.mediaId)
    imagePreviews.value = await Promise.all(draftImages.map(resolveDraftImagePreview))
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, t('activityEdit.loadFailed'))
    }
  }
}

onLoad((query) => {
  uni.$off('activityLocationPicked', handleGlobalLocationPicked)
  uni.$on('activityLocationPicked', handleGlobalLocationPicked)
  activityId.value = (query?.activityId as string) ?? ''
  void loadCurrentLocationMarker()
  if (isEdit.value) loadDraft()
})

onUnload(() => {
  uni.$off('activityLocationPicked', handleGlobalLocationPicked)
})
</script>

<style scoped>
.page {
  background: var(--q-color-bg);
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

.edit-container {
  padding: 32rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

.section {
  margin-bottom: 32rpx;
}

.section-title {
  display: block;
  font-size: 28rpx;
  color: var(--q-color-text);
  margin-bottom: 12rpx;
  font-weight: 600;
}

/* ---- 图片 ---- */
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-preview-item {
  position: relative;
  width: 200rpx;
  height: 200rpx;
  border-radius: 8rpx;
  overflow: hidden;
}

.preview-image {
  width: 100%;
  height: 100%;
}

.preview-image--failed {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--q-color-bg-soft);
}

.preview-failed-text {
  font-size: 22rpx;
  color: var(--q-color-text-muted);
}

.image-remove {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  background-color: rgba(0, 0, 0, 0.5);
  color: var(--q-color-bg-card);
  border-radius: 50%;
  font-size: 28rpx;
}

.image-add-btn {
  width: 200rpx;
  height: 200rpx;
  border: 2rpx dashed var(--q-color-text-muted);
  border-radius: 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: var(--q-color-bg-card);
}

.add-icon {
  font-size: 48rpx;
  color: var(--q-color-text-muted);
  line-height: 1;
}

.add-text {
  font-size: 22rpx;
  color: var(--q-color-text-muted);
  margin-top: 8rpx;
}

/* ---- 表单通用 ---- */
.form-item {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: var(--q-color-text);
  margin-bottom: 12rpx;
}

.req {
  color: var(--q-color-danger);
}

/* ---- 标签 ---- */
.tag-create-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.tag-input {
  flex: 1;
  height: 80rpx;
  min-width: 0;
  padding: 0 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  font-size: 28rpx;
  color: var(--q-color-text);
  box-sizing: border-box;
}

.tag-add-button {
  flex-shrink: 0;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
  padding: 0 28rpx;
  border-radius: 8rpx;
  background-color: var(--q-color-primary);
  color: var(--q-color-bg-card);
  font-size: 26rpx;
}

.tag-add-button::after {
  border: 0;
}

.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  background-color: var(--q-color-primary-light);
  border-radius: 8rpx;
  border: 2rpx solid var(--q-color-primary);
  font-size: 26rpx;
  color: var(--q-color-primary);
}

.tag-remove {
  font-size: 28rpx;
  line-height: 1;
}

.hint {
  font-size: 26rpx;
  color: var(--q-color-text-muted);
}

/* ---- 日期时间 ---- */
.datetime-row {
  display: flex;
  gap: 16rpx;
}

.datetime-row .picker-value {
  flex: 1;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.datetime-row .picker-value text {
  font-size: 28rpx;
  color: var(--q-color-text);
}

.datetime-row .picker-value text.placeholder {
  color: var(--q-color-text-muted);
}

/* ---- 文本框 ---- */
.textarea {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  font-size: 28rpx;
  color: var(--q-color-text);
  box-sizing: border-box;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 24rpx;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
}

.switch-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.switch-hint {
  font-size: 24rpx;
  color: var(--q-color-text-muted);
  line-height: 1.4;
}

/* ---- 地点选择（地图选点） ---- */
.location-map-preview {
  display: flex;
  flex-direction: column;
  background-color: var(--q-color-bg-card);
  border-radius: 8rpx;
  overflow: hidden;
}

.preview-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-static-map {
  width: 100%;
  height: 240rpx;
  display: block;
  background-color: var(--q-color-primary-light);
}

.preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background-color: var(--q-color-bg-card);
  border-top: 2rpx solid var(--q-color-border);
}

.preview-address-text {
  flex: 1;
  font-size: 26rpx;
  color: var(--q-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-arrow {
  font-size: 28rpx;
  color: var(--q-color-text-muted);
  margin-left: 12rpx;
  flex-shrink: 0;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 24rpx;
  background-color: var(--q-color-bg-soft);
  min-height: 160rpx;
  justify-content: center;
}

.placeholder-icon-row {
  margin-bottom: 12rpx;
}

.placeholder-location-icon {
  font-size: 48rpx;
}

.placeholder-main {
  font-size: 28rpx;
  color: var(--q-color-text);
  font-weight: 500;
  margin-bottom: 8rpx;
}

.placeholder-sub {
  font-size: 24rpx;
  color: var(--q-color-text-muted);
}

/* ---- 字段错误 ---- */
.field-error {
  display: block;
  font-size: 24rpx;
  color: var(--q-color-danger);
  margin-top: 8rpx;
}

/* 操作按钮已由 BottomActionBar 组件承载 */
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

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
              <image class="preview-image" :src="img" mode="aspectFill" />
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
          <view v-if="availableTags.length" class="tags-row">
            <view
              v-for="tag in availableTags"
              :key="tag.name"
              class="tag-chip"
              :class="{ active: selectedTags.has(tag.name) }"
              @click="toggleTag(tag.name)"
            >
              <text>{{ tag.name }}</text>
            </view>
          </view>
          <text v-else class="hint">{{ t('editProfile.loadingTags') }}</text>
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
          <text class="label"><text class="req">* </text>选择地点</text>
          <view class="location-picker" @click="showLocationPicker">
            <text :class="selectedLocationLabel ? 'location-text' : 'location-placeholder'">
              {{ selectedLocationLabel || '点击选择活动地点' }}
            </text>
            <text class="location-arrow">&gt;</text>
          </view>
          <text v-if="errors.location" class="field-error">{{ errors.location }}</text>
        </view>
        <FormInput
          v-model="formAddress"
          :label="t('activityEdit.locationAddress')"
          :placeholder="t('activityEdit.locationAddressPlaceholder')"
          :error="errors.address"
          required
        />
        <FormInput
          v-model="formCity"
          :label="t('activityEdit.locationCity')"
          :placeholder="t('activityEdit.locationCityPlaceholder')"
          :error="errors.city"
          required
        />
        <FormInput
          v-model="formPlaceName"
          :label="t('activityEdit.locationPlaceName')"
          :placeholder="t('activityEdit.locationPlaceNamePlaceholder')"
        />

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

        <FormError :message="formError" />
      </view>
    </scroll-view>
    <BottomActionBar>
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
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import {
  createDraft,
  updateDraft,
  getDraft,
  submitDraft,
  uploadActivityImages,
} from '@/api/modules/activities'
import { getInterestTags } from '@/api/modules/profile'
import { MOCK_ACTIVITY_LOCATIONS } from '@/config/mock-locations'
import { getErrorMessage } from '@/utils/error'
import { BottomActionBar, FormInput, FormError } from '@/components'

const { t } = useI18n()

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

const startAtDate = ref('')
const startAtTime = ref('')
const endAtDate = ref('')
const endAtTime = ref('')
const deadlineDate = ref('')
const deadlineTime = ref('')

const selectedTags = ref(new Set<string>())
const availableTags = ref<{ name: string }[]>([])

const imagePreviews = ref<string[]>([])
const imageIds = ref<string[]>([])

const savingDraft = ref(false)
const submitting = ref(false)
const formError = ref('')
const errors = ref<Record<string, string>>({})

/** 已选地点展示文案 */
const selectedLocationLabel = computed(() => {
  if (!formPlaceName.value && !formAddress.value) return ''
  if (formPlaceName.value) return `${formPlaceName.value} · ${formCity.value}`
  return `${formAddress.value} · ${formCity.value}`
})

/**
 * 打开 mock 地点选择器
 *
 * 前置条件：MOCK_ACTIVITY_LOCATIONS 已配置
 * 后置条件：回填 LocationInfo 各字段与坐标
 */
function showLocationPicker(): void {
  uni.showActionSheet({
    itemList: MOCK_ACTIVITY_LOCATIONS.map((item) => item.placeName),
    success: (res) => {
      const location = MOCK_ACTIVITY_LOCATIONS[res.tapIndex]
      if (!location) return
      formCity.value = location.city
      formAddress.value = location.address
      formPlaceName.value = location.placeName
      locationLongitude.value = location.point.longitude
      locationLatitude.value = location.point.latitude
      if (errors.value.location) {
        const next = { ...errors.value }
        delete next.location
        errors.value = next
      }
    },
  })
}

/**
 * 加载系统可用标签
 */
async function loadTags(): Promise<void> {
  try {
    const tags = await getInterestTags()
    availableTags.value = tags as { name: string }[]
  } catch {
    /* 标签加载失败不影响编辑 */
  }
}

/**
 * 切换标签选中状态
 */
function toggleTag(name: string): void {
  const next = new Set(selectedTags.value)
  if (next.has(name)) {
    next.delete(name)
  } else {
    next.add(name)
  }
  selectedTags.value = next
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

// ================= 表单数据组装 =================

function buildPayload(): Record<string, unknown> {
  const payload: Record<string, unknown> = {}

  if (formTitle.value.trim()) payload.title = formTitle.value.trim()
  if (selectedTags.value.size) payload.tags = [...selectedTags.value]
  if (formIntroduction.value.trim()) payload.introduction = formIntroduction.value.trim()
  if (formSafetyNotice.value.trim()) payload.safetyNotice = formSafetyNotice.value.trim()

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
  if (!selectedTags.value.size) e.tags = t('activityEdit.tagsRequired')
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
    formAddress.value = draft.location?.address ?? ''
    formCity.value = draft.location?.city ?? ''
    formPlaceName.value = draft.location?.placeName ?? ''
    locationLongitude.value = draft.location?.point?.longitude ?? null
    locationLatitude.value = draft.location?.point?.latitude ?? null
    formCapacity.value = draft.capacity != null ? String(draft.capacity) : ''
    formFeeAmount.value = draft.feeAmount != null ? String(draft.feeAmount) : ''
    formFeeDescription.value = draft.feeDescription ?? ''
    formMinAge.value = draft.minAge != null ? String(draft.minAge) : ''

    selectedTags.value = new Set(draft.tags ?? [])

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

    imagePreviews.value = (draft.images ?? []).map((i) => i.signedUrl ?? '').filter(Boolean)
    imageIds.value = (draft.images ?? []).map((i) => i.mediaId)
  } catch (error) {
    if (error instanceof BusinessError) {
      formError.value = getErrorMessage(error.code)
    } else {
      formError.value = getErrorMessage(0, t('activityEdit.loadFailed'))
    }
  }
}

onLoad((query) => {
  activityId.value = (query?.activityId as string) ?? ''
  loadTags()
  if (isEdit.value) loadDraft()
})
</script>

<style scoped>
.page {
  background: linear-gradient(160deg, #f8fafc 0%, #fff9f5 100%);
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
  padding: 32rpx 32rpx calc(200rpx + env(safe-area-inset-bottom));
}

.section {
  margin-bottom: 32rpx;
}

.section-title {
  display: block;
  font-size: 28rpx;
  color: #323233;
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

.image-remove {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  background-color: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 50%;
  font-size: 28rpx;
}

.image-add-btn {
  width: 200rpx;
  height: 200rpx;
  border: 2rpx dashed #c8c9cc;
  border-radius: 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #fff;
}

.add-icon {
  font-size: 48rpx;
  color: #c8c9cc;
  line-height: 1;
}

.add-text {
  font-size: 22rpx;
  color: #969799;
  margin-top: 8rpx;
}

/* ---- 表单通用 ---- */
.form-item {
  margin-bottom: 32rpx;
}

.label {
  display: block;
  font-size: 28rpx;
  color: #323233;
  margin-bottom: 12rpx;
}

.req {
  color: #ee0a24;
}

/* ---- 标签 ---- */
.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-chip {
  padding: 12rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  border: 2rpx solid #ebedf0;
  font-size: 26rpx;
  color: #646566;
}

.tag-chip.active {
  border-color: #5ec8a7;
  color: #5ec8a7;
  background-color: #e8f7f0;
}

.hint {
  font-size: 26rpx;
  color: #969799;
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
  background-color: #fff;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.datetime-row .picker-value text {
  font-size: 28rpx;
  color: #323233;
}

.datetime-row .picker-value text.placeholder {
  color: #c8c9cc;
}

/* ---- 文本域 ---- */
.textarea {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

/* ---- 地点选择 ---- */
.location-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
}

.location-text {
  font-size: 28rpx;
  color: #323233;
}

.location-placeholder {
  font-size: 28rpx;
  color: #c8c9cc;
}

.location-arrow {
  font-size: 28rpx;
  color: #c8c9cc;
}

/* ---- 字段错误 ---- */
.field-error {
  display: block;
  font-size: 24rpx;
  color: #ee0a24;
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

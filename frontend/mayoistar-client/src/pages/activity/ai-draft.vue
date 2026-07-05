<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="form-container">
        <text class="title">{{ t('aiDraft.title') }}</text>

        <!-- 活动主题（必填） -->
        <view class="form-item">
          <text class="label"><text class="req">* </text>{{ t('aiDraft.topic') }}</text>
          <input v-model="topic" class="input" :placeholder="t('aiDraft.topicPlaceholder')" />
        </view>

        <!-- 活动类型 -->
        <view class="form-item">
          <text class="label">{{ t('aiDraft.activityType') }}</text>
          <input
            v-model="activityType"
            class="input"
            :placeholder="t('aiDraft.activityTypePlaceholder')"
          />
        </view>

        <!-- 城市 -->
        <view class="form-item">
          <text class="label">{{ t('aiDraft.city') }}</text>
          <input v-model="city" class="input" :placeholder="t('aiDraft.cityPlaceholder')" />
        </view>

        <!-- 期望参与人数 -->
        <view class="form-item">
          <text class="label">{{ t('aiDraft.expectedParticipants') }}</text>
          <input
            v-model="expectedParticipants"
            class="input"
            type="number"
            :placeholder="t('aiDraft.expectedParticipantsPlaceholder')"
          />
        </view>

        <!-- 补充要求 -->
        <view class="form-item">
          <text class="label">{{ t('aiDraft.additionalRequirements') }}</text>
          <textarea
            v-model="additionalRequirements"
            class="textarea"
            :placeholder="t('aiDraft.additionalRequirementsPlaceholder')"
            :maxlength="500"
            auto-height
          />
        </view>

        <!-- AI 错误提示 -->
        <view v-if="aiError" class="error-banner">
          <text class="error-text">{{ aiError }}</text>
        </view>

        <!-- AI 生成中提示 -->
        <view v-if="generating" class="loading-section">
          <text class="loading-text">{{ t('aiDraft.generating') }}</text>
        </view>
      </view>
    </scroll-view>

    <BottomActionBar>
      <button
        class="bar-btn bar-btn-primary"
        :disabled="!topic.trim() || generating"
        @click="handleGenerate"
      >
        {{ generating ? t('aiDraft.generating') : t('aiDraft.generate') }}
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
/**
 * AI 智能生成活动页
 *
 * 用户输入活动主题等参数，调用 AI 生成活动方案，
 * 成功后创建草稿并跳转编辑页。
 * 前置条件：用户已登录
 * 后置条件：生成成功后创建草稿并跳转编辑页
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BusinessError } from '@/api'
import { generateActivityPlan, type ActivityPlanningResult } from '@/api/modules/ai'
import { createDraft } from '@/api/modules/activities'
import { BottomActionBar } from '@/components'
import { getErrorMessage } from '@/utils/error'

const { t } = useI18n()

const topic = ref('')
const activityType = ref('')
const city = ref('')
const expectedParticipants = ref<string>('')
const additionalRequirements = ref('')
const generating = ref(false)
const aiError = ref('')

/**
 * 调用 AI 生成活动方案
 *
 * 生成成功后创建草稿并跳转编辑页，失败则展示错误信息。
 */
async function handleGenerate(): Promise<void> {
  if (generating.value || !topic.value.trim()) return
  generating.value = true
  aiError.value = ''

  try {
    const result = await generateActivityPlan({
      topic: topic.value.trim(),
      activityType: activityType.value.trim() || undefined,
      city: city.value.trim() || undefined,
      expectedParticipants: expectedParticipants.value
        ? parseInt(expectedParticipants.value, 10)
        : undefined,
      additionalRequirements: additionalRequirements.value.trim() || undefined,
    })

    // 检查 AI 返回状态
    const planResult: ActivityPlanningResult = result

    if (planResult.status !== 'succeeded') {
      aiError.value = planResult.friendlyErrorMessage ?? t('aiDraft.failed')
      return
    }

    // 构建草稿数据
    const draftData = {
      title: planResult.title ?? `AI 建议：${topic.value.trim()}`,
      introduction: planResult.introduction ?? '',
      safetyNotice: planResult.safetyNotice ?? '',
      tags: planResult.tags ?? [],
      capacity: planResult.suggestedCapacity,
      registrationDeadline: planResult.suggestedRegistrationDeadline,
    }

    // 创建草稿
    uni.showLoading({ title: '创建草稿...' })
    const draft = await createDraft(draftData)
    uni.hideLoading()

    // 跳转编辑页
    const draftId = draft.activityId
    uni.redirectTo({ url: `/pages/activity/edit?activityId=${draftId}` })
  } catch (error) {
    uni.hideLoading()
    if (error instanceof BusinessError) {
      aiError.value = getErrorMessage(error.code)
    } else {
      aiError.value = t('aiDraft.failed')
    }
  } finally {
    generating.value = false
  }
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

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.form-container {
  padding: 32rpx;
}

.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #323233;
  margin-bottom: 40rpx;
}

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

.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

.textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  background-color: #fff;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #323233;
  box-sizing: border-box;
}

.error-banner {
  background-color: #fff2f0;
  border-radius: 8rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 32rpx;
}

.error-text {
  font-size: 26rpx;
  color: #ee0a24;
}

.loading-section {
  background-color: #e8f7f0;
  border-radius: 8rpx;
  padding: 24rpx;
  text-align: center;
}

.loading-text {
  font-size: 28rpx;
  color: #5ec8a7;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

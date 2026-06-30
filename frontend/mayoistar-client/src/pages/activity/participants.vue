<template>
  <view class="page">
    <view v-if="loading" class="state-text">{{ t('加载中') }}</view>

    <view v-else-if="errorMsg" class="state-text state-text--error">{{ errorMsg }}</view>

    <view v-else-if="participants.length === 0" class="state-text">{{ t('暂无数据') }}</view>

    <scroll-view v-else class="scroll-area" scroll-y>
      <view v-for="p in participants" :key="p.registrationId" class="card">
        <view class="card-left">
          <view class="avatar-placeholder">
            <text class="avatar-text">{{ p.nickname.charAt(0).toUpperCase() }}</text>
          </view>
          <text class="nickname">{{ p.nickname }}</text>
        </view>
        <text class="status-tag" :class="'status-' + p.registrationStatus">{{
          registrationStatusText(p.registrationStatus)
        }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 参与者列表页
 *
 * 展示活动所有参与者及其报名/签到状态。
 * 前置条件：activityId 通过 query 传入
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'

const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')
const participants = ref<
  { registrationId: string; nickname: string; registrationStatus: string }[]
>([])

const statusTextMap: Record<string, string> = {
  registered: t('myRegistrations.statusRegistered'),
  checkedIn: t('myRegistrations.statusCheckedIn'),
  canceled: t('myRegistrations.statusCanceled'),
  waiting: t('myRegistrations.statusWaiting'),
  waitingConfirmation: t('myRegistrations.statusWaitingConfirmation'),
}

function registrationStatusText(status: string): string {
  return statusTextMap[status] ?? status
}

onLoad(async (query) => {
  const activityId = (query?.activityId as string) ?? ''
  if (!activityId) {
    errorMsg.value = '缺少活动标识'
    loading.value = false
    return
  }
  try {
    const result = (await api.get('/activities/{activityId}/participants', {
      path: { activityId },
    })) as {
      items: { registrationId: string; nickname: string; registrationStatus: string }[]
    }
    participants.value = result.items
  } catch (error) {
    if (error instanceof BusinessError) {
      errorMsg.value = getErrorMessage(error.code)
    } else {
      errorMsg.value = getErrorMessage(0, '加载失败')
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  background-color: #f7f8fa;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.state-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.state-text--error {
  color: #ee0a24;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.card {
  background-color: #fff;
  margin: 16rpx 32rpx;
  padding: 24rpx 32rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar-placeholder {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: #1989fa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}

.nickname {
  font-size: 28rpx;
  color: #323233;
}

.status-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}

.status-registered {
  background-color: #e6f0fe;
  color: #1989fa;
}

.status-checkedIn {
  background-color: #ebf9e9;
  color: #07c160;
}

.status-canceled {
  background-color: #ebedf0;
  color: #969799;
}

.status-waiting,
.status-waitingConfirmation {
  background-color: #fff7e6;
  color: #ed6a0c;
}
</style>

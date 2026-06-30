<template>
  <view class="page">
    <view v-if="loading" class="loading-text">{{ t('加载中') }}</view>

    <view v-else-if="errorMsg" class="error-text">{{ errorMsg }}</view>

    <view v-else-if="registrations.length === 0" class="empty-text">{{ t('暂无数据') }}</view>

    <view v-else>
      <view v-for="item in registrations" :key="item.registrationId" class="card">
        <text class="card-title">{{ item.activityTitle }}</text>
        <view class="card-row">
          <text class="status-tag" :class="'status-' + item.status">{{
            statusText(item.status)
          }}</text>
        </view>
        <view class="card-row">
          <text class="meta"
            >{{ t('myRegistrations.registrationTime') }}: {{ formatDate(item.registeredAt) }}</text
          >
        </view>
        <view class="card-row">
          <text class="meta">{{ formatDate(item.activityStartAt) }}</text>
        </view>
      </view>
    </view>
    <view class="bottom-safe" />
  </view>
</template>

<script setup lang="ts">
/**
 * 我的报名
 *
 * 展示用户已报名的活动列表及报名状态。
 * 前置条件：用户已登录
 * 后置条件：加载成功后展示报名数据
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { api, BusinessError } from '@/api'
import { getErrorMessage } from '@/utils/error'
import { formatDate } from '@/utils/date'

const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')

const statusMap: Record<string, string> = {
  registered: t('myRegistrations.statusRegistered'),
  checkedIn: t('myRegistrations.statusCheckedIn'),
  canceled: t('myRegistrations.statusCanceled'),
  waiting: t('myRegistrations.statusWaiting'),
  waitingConfirmation: t('myRegistrations.statusWaitingConfirmation'),
}

interface RegistrationItem {
  registrationId: string
  activityId: string
  activityTitle: string
  status: string
  registeredAt: string
  activityStartAt: string
}

const registrations = ref<RegistrationItem[]>([])

/**
 * 每次进入页面时加载报名列表
 */
onShow(async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await api.get('/activities/my-registrations')
    registrations.value = result.items as RegistrationItem[]
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

/**
 * 获取报名状态中文展示文本
 *
 * @param status 报名状态值
 * @returns 中文文本
 */
function statusText(status: string): string {
  return statusMap[status] ?? status
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f7f8fa;
}

.loading-text,
.error-text,
.empty-text {
  text-align: center;
  font-size: 28rpx;
  color: #969799;
  padding-top: 120rpx;
}

.error-text {
  color: #ee0a24;
}

.card {
  background-color: #fff;
  margin: 16rpx 32rpx;
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
}

.card-title {
  display: block;
  font-size: 30rpx;
  color: #323233;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.card-row {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
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

.meta {
  font-size: 24rpx;
  color: #969799;
}

.bottom-safe {
  height: 48rpx;
}
</style>

<template>
  <view class="page">
    <AppNavbar title="群投票" />

    <view class="toolbar">
      <input
        v-model="pollTitle"
        class="toolbar-input"
        type="text"
        placeholder="投票标题"
        maxlength="50"
      />
      <input
        v-model="pollOptions"
        class="toolbar-input"
        type="text"
        placeholder="选项，逗号分隔"
        maxlength="200"
      />
      <view class="toolbar-btn" @tap="onCreatePoll">
        <text class="toolbar-btn-text">创建</text>
      </view>
    </view>

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && polls.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="polls.length === 0" class="empty-state">
        <EmptyState title="暂无投票" description="创建投票收集小队成员意见" />
      </view>

      <view v-else class="poll-list">
        <view v-for="poll in polls" :key="poll.pollId" class="poll-card">
          <view class="poll-header">
            <text class="poll-title">{{ poll.title }}</text>
            <text class="poll-time">{{ formatTime(poll.createdAt) }}</text>
          </view>

          <view class="option-list">
            <view
              v-for="option in poll.options"
              :key="option.optionId"
              class="option-item"
              @tap="onVote(poll, option)"
            >
              <view class="option-info">
                <text class="option-content">{{ option.content }}</text>
                <text class="option-count">{{ option.voteCount }} 票</text>
              </view>
              <view class="option-bar">
                <view
                  class="option-bar-fill"
                  :style="{ width: getVotePercent(poll, option) + '%' }"
                ></view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 群投票页
 *
 * 创建投票、查看选项票数并参与投票。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import { listPolls, createPoll, votePoll } from '@/api/modules/teamChat'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import type { components } from '@/api/types/schema'

type TeamPoll = components['schemas']['Chat.TeamPoll']
type TeamPollOption = components['schemas']['Chat.TeamPollOption']

const teamId = ref('')
const polls = ref<TeamPoll[]>([])
const loading = ref(false)
const pollTitle = ref('')
const pollOptions = ref('')

/** 格式化创建时间 */
function formatTime(isoStr: string): string {
  const date = new Date(isoStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

/** 计算选项得票占比 */
function getVotePercent(poll: TeamPoll, option: TeamPollOption): number {
  const total = poll.options.reduce((sum, o) => sum + o.voteCount, 0)
  if (total === 0) return 0
  return Math.round((option.voteCount / total) * 100)
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载投票列表 */
async function loadPolls() {
  if (!teamId.value) return

  loading.value = true
  try {
    const result = await listPolls(teamId.value)
    polls.value = extractPageItems<TeamPoll>(result)
  } catch (error) {
    console.error('Failed to load polls:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 创建投票 */
function onCreatePoll() {
  const title = pollTitle.value.trim()
  const options = pollOptions.value
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)

  if (!title) {
    uni.showToast({ title: '请输入投票标题', icon: 'none' })
    return
  }
  if (options.length < 2) {
    uni.showToast({ title: '至少需要两个选项', icon: 'none' })
    return
  }

  uni.showModal({
    title: '创建投票',
    content: `确定创建投票「${title}」吗？`,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await createPoll(teamId.value, { title, options })
        uni.showToast({ title: '投票已创建', icon: 'success' })
        pollTitle.value = ''
        pollOptions.value = ''
        await loadPolls()
      } catch (error) {
        console.error('Failed to create poll:', error)
        showBusinessError(error, '创建失败')
      }
    },
  })
}

/** 参与投票 */
function onVote(poll: TeamPoll, option: TeamPollOption) {
  uni.showModal({
    title: '确认投票',
    content: `确定选择「${option.content}」吗？`,
    editable: false,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await votePoll(teamId.value, poll.pollId, option.optionId)
        uni.showToast({ title: '投票成功', icon: 'success' })
        await loadPolls()
      } catch (error) {
        console.error('Failed to vote:', error)
        showBusinessError(error, '投票失败')
      }
    },
  })
}

onLoad((query) => {
  teamId.value = query?.teamId || ''
  loadPolls()
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background-color: $color-bg;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar {
  background: #ffffff;
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  border-bottom: 1px solid $color-border-light;
}

.toolbar-input {
  height: 40px;
  background: #f0f2f5;
  border-radius: $radius-md;
  padding: 0 $spacing-md;
  font-size: $font-base;
}

.toolbar-btn {
  background: $color-primary;
  border-radius: $radius-md;
  padding: $spacing-sm 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.9;
  }
}

.toolbar-btn-text {
  font-size: $font-sm;
  font-weight: $weight-medium;
  color: #ffffff;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.loading-state,
.empty-state {
  padding: $spacing-2xl;
}

.loading-text {
  font-size: $font-base;
  color: $color-text-sub;
  text-align: center;
  display: block;
}

.poll-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.poll-card {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-lg;
}

.poll-header {
  margin-bottom: $spacing-md;
}

.poll-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.poll-time {
  font-size: $font-xs;
  color: $color-text-muted;
  display: block;
  margin-top: 4px;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.option-item {
  background: #f8f9fa;
  border-radius: $radius-md;
  padding: $spacing-sm $spacing-md;

  &:active {
    background: #f0f2f5;
  }
}

.option-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xs;
}

.option-content {
  font-size: $font-base;
  color: $color-text;
}

.option-count {
  font-size: $font-xs;
  color: $color-text-sub;
}

.option-bar {
  height: 4px;
  background: #e8eaed;
  border-radius: $radius-full;
  overflow: hidden;
}

.option-bar-fill {
  height: 100%;
  background: $color-primary;
  border-radius: $radius-full;
  transition: width 0.3s ease;
}

.bottom-padding {
  height: calc(40px + $safe-bottom);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

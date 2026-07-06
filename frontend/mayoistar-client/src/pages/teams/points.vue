<template>
  <view class="page">
    <AppNavbar title="积分榜" />

    <scroll-view class="scroll-area" scroll-y>
      <view v-if="loading && items.length === 0" class="loading-state">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="items.length === 0" class="empty-state">
        <EmptyState title="暂无积分数据" description="小队成员还没有积分记录" />
      </view>

      <view v-else class="rank-list">
        <view
          v-for="item in items"
          :key="item.userId"
          class="rank-card"
          :class="{ 'rank-card--top': item.rank <= 3 }"
          @tap="openHistory(item)"
        >
          <view class="rank-badge" :class="`rank-badge--${item.rank}`">
            <text class="rank-number">{{ item.rank }}</text>
          </view>

          <view class="rank-info">
            <text class="rank-name">{{ item.nickname }}</text>
            <text v-if="item.rank <= 3" class="rank-label">{{ getRankLabel(item.rank) }}</text>
          </view>

          <view class="rank-points">
            <text class="points-value">{{ item.points }}</text>
            <text class="points-label">积分</text>
          </view>

          <view v-if="canManage" class="adjust-btn" @tap.stop="openAdjustPanel(item)">
            <text class="adjust-btn-text">调整</text>
          </view>
        </view>
      </view>

      <view class="bottom-padding"></view>
    </scroll-view>

    <uni-popup ref="historyPopup" type="bottom" :safe-area="true">
      <view class="sheet">
        <view class="sheet-header">
          <text class="sheet-title">{{ selectedMemberName }}积分历史</text>
          <text class="sheet-close" @tap="closeHistory">×</text>
        </view>
        <view v-if="historyLoading" class="sheet-empty">
          <text>加载中...</text>
        </view>
        <view v-else-if="pointHistory.length === 0" class="sheet-empty">
          <text>暂无积分记录</text>
        </view>
        <scroll-view v-else class="history-list" scroll-y @scrolltolower="loadMoreHistory">
          <view v-for="record in pointHistory" :key="record.recordId" class="history-item">
            <view class="history-main">
              <text class="history-reason">{{ record.reason }}</text>
              <text class="history-meta">{{ formatDateTime(record.createdAt) }}</text>
            </view>
            <text
              class="history-change"
              :class="{ 'history-change--positive': record.pointChange > 0 }"
            >
              {{ record.pointChange > 0 ? '+' : '' }}{{ record.pointChange }}
            </text>
          </view>
          <view class="history-footer">
            <text v-if="historyLoadingMore">加载更多...</text>
            <text v-else-if="historyNoMore">已加载全部</text>
          </view>
        </scroll-view>
      </view>
    </uni-popup>

    <uni-popup ref="adjustPopup" type="bottom" :safe-area="true">
      <view class="sheet">
        <view class="sheet-header">
          <text class="sheet-title">调整 {{ selectedMemberName }} 的积分</text>
          <text class="sheet-close" @tap="closeAdjustPanel">×</text>
        </view>
        <input
          v-model="pointChangeInput"
          class="adjust-input"
          type="number"
          placeholder="积分变动，正数加分，负数扣分"
        />
        <input
          v-model="adjustReason"
          class="adjust-input"
          type="text"
          maxlength="50"
          placeholder="调整原因"
        />
        <text v-if="adjustError" class="adjust-error">{{ adjustError }}</text>
        <button
          class="adjust-submit"
          :disabled="adjusting"
          :loading="adjusting"
          @tap="submitAdjustment"
        >
          {{ adjusting ? '' : '保存调整' }}
        </button>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
/**
 * 小队积分榜页
 *
 * 展示成员积分排名，前三名高亮显示。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AppNavbar, EmptyState } from '@/components'
import { BusinessError } from '@/api'
import {
  adjustTeamMemberPoints,
  getTeamMemberPointHistory,
  getTeamMembers,
  getTeamPoints,
} from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
import { getTeamErrorMessage } from '@/utils/team-error-message'
import { useAuthStore } from '@/stores/auth'
import type { components } from '@/api/types/schema'

type TeamPointRankItem = components['schemas']['Social.TeamPointRankItem']
type TeamPointRecordItem = components['schemas']['Social.TeamPointRecordItem']
type TeamMember = components['schemas']['Social.TeamMember']

interface PopupRef {
  open: () => void
  close: () => void
}

const teamId = ref('')
const items = ref<TeamPointRankItem[]>([])
const members = ref<TeamMember[]>([])
const loading = ref(false)
const selectedItem = ref<TeamPointRankItem | null>(null)
const pointHistory = ref<TeamPointRecordItem[]>([])
const historyLoading = ref(false)
const historyLoadingMore = ref(false)
const historyNoMore = ref(false)
const historyPage = ref(1)
const pointChangeInput = ref('')
const adjustReason = ref('')
const adjustError = ref('')
const adjusting = ref(false)
const historyPopup = ref<PopupRef | null>(null)
const adjustPopup = ref<PopupRef | null>(null)
const authStore = useAuthStore()

const canManage = computed(() => {
  const me = members.value.find((member) => member.userId === authStore.userId)
  return me?.role === 'leader' || me?.role === 'admin'
})

const selectedMemberName = computed(() => selectedItem.value?.nickname ?? '')

/** 获取排名标签 */
function getRankLabel(rank: number): string {
  if (rank === 1) return '冠军'
  if (rank === 2) return '亚军'
  if (rank === 3) return '季军'
  return ''
}

/** 格式化积分记录时间 */
function formatDateTime(isoStr: string): string {
  const date = new Date(isoStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

/** 展示业务错误提示 */
function showBusinessError(error: unknown, fallback: string) {
  if (error instanceof BusinessError) {
    uni.showToast({ title: getTeamErrorMessage(error.code, error.message), icon: 'none' })
  } else {
    uni.showToast({ title: fallback, icon: 'none' })
  }
}

/** 加载积分排行榜 */
async function loadPoints() {
  if (!teamId.value) return

  loading.value = true
  try {
    const [pointsResult, membersResult] = await Promise.all([
      getTeamPoints(teamId.value, 1, 50),
      getTeamMembers(teamId.value, 1, 100),
    ])
    items.value = extractPageItems<TeamPointRankItem>(pointsResult)
    members.value = extractPageItems<TeamMember>(membersResult)
  } catch (error) {
    console.error('Failed to load points:', error)
    showBusinessError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开指定成员的积分历史 */
async function openHistory(item: TeamPointRankItem): Promise<void> {
  selectedItem.value = item
  pointHistory.value = []
  historyPage.value = 1
  historyNoMore.value = false
  historyPopup.value?.open()
  await loadHistoryPage(1, false)
}

/** 关闭积分历史弹层 */
function closeHistory(): void {
  historyPopup.value?.close()
}

/** 加载成员积分历史分页 */
async function loadHistoryPage(page: number, append: boolean): Promise<void> {
  if (!selectedItem.value) return
  historyLoading.value = !append
  try {
    const result = await getTeamMemberPointHistory(
      teamId.value,
      selectedItem.value.userId,
      page,
      20,
    )
    const records = extractPageItems<TeamPointRecordItem>(result)
    pointHistory.value = append ? [...pointHistory.value, ...records] : records
    historyPage.value = result.page ?? page
    historyNoMore.value = historyPage.value >= (result.totalPages ?? historyPage.value)
  } catch (error) {
    showBusinessError(error, '加载积分历史失败')
    historyNoMore.value = true
  } finally {
    historyLoading.value = false
  }
}

/** 触底加载更多积分历史 */
async function loadMoreHistory(): Promise<void> {
  if (historyLoading.value || historyLoadingMore.value || historyNoMore.value) return
  historyLoadingMore.value = true
  try {
    await loadHistoryPage(historyPage.value + 1, true)
  } finally {
    historyLoadingMore.value = false
  }
}

/** 打开积分调整面板 */
function openAdjustPanel(item: TeamPointRankItem): void {
  selectedItem.value = item
  pointChangeInput.value = ''
  adjustReason.value = ''
  adjustError.value = ''
  adjustPopup.value?.open()
}

/** 关闭积分调整面板 */
function closeAdjustPanel(): void {
  adjustPopup.value?.close()
}

/** 提交手动积分调整 */
async function submitAdjustment(): Promise<void> {
  if (!selectedItem.value || adjusting.value) return
  const pointChange = Number(pointChangeInput.value)
  const reason = adjustReason.value.trim()
  if (!Number.isInteger(pointChange) || pointChange === 0) {
    adjustError.value = '请输入非 0 整数积分'
    return
  }
  if (!reason) {
    adjustError.value = '请输入调整原因'
    return
  }
  adjusting.value = true
  adjustError.value = ''
  try {
    await adjustTeamMemberPoints(teamId.value, selectedItem.value.userId, { pointChange, reason })
    uni.showToast({ title: '积分已调整', icon: 'success' })
    closeAdjustPanel()
    await loadPoints()
  } catch (error) {
    if (error instanceof BusinessError) {
      adjustError.value = getTeamErrorMessage(error.code, error.message)
    } else {
      adjustError.value = '调整失败'
    }
  } finally {
    adjusting.value = false
  }
}

onLoad((query) => {
  teamId.value = typeof query?.teamId === 'string' ? query.teamId : ''
  void loadPoints()
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

.rank-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.rank-card {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  display: flex;
  align-items: center;
  gap: $spacing-md;

  &--top {
    background: linear-gradient(135deg, #fff9e6 0%, var(--q-color-bg-card) 100%);
    border: 1px solid rgba(255, 193, 7, 0.2);
  }
}

.rank-badge {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--1 {
    background: linear-gradient(135deg, #ffd700, #ffb300);
  }

  &--2 {
    background: linear-gradient(135deg, #e0e0e0, #bdbdbd);
  }

  &--3 {
    background: linear-gradient(135deg, #cd7f32, #a0522d);
  }
}

.rank-number {
  font-size: $font-base;
  font-weight: $weight-bold;
  color: $color-text;

  .rank-badge--1 &,
  .rank-badge--2 &,
  .rank-badge--3 & {
    color: var(--q-color-bg-card);
  }
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-name {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
  display: block;
}

.rank-label {
  font-size: $font-xs;
  color: $color-text-sub;
  display: block;
  margin-top: 2px;
}

.rank-points {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.points-value {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $color-primary;
}

.points-label {
  font-size: $font-xs;
  color: $color-text-muted;
}

.adjust-btn {
  border: 1px solid $color-primary;
  border-radius: $radius-full;
  padding: 4px 10px;
  flex-shrink: 0;
}

.adjust-btn-text {
  font-size: $font-xs;
  color: $color-primary;
}

.sheet {
  background: $color-bg-card;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-lg;
  max-height: 70vh;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.sheet-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.sheet-close {
  font-size: $font-xl;
  color: $color-text-muted;
}

.sheet-empty {
  padding: $spacing-xl;
  text-align: center;
  color: $color-text-muted;
  font-size: $font-sm;
}

.history-list {
  max-height: 52vh;
}

.history-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md 0;
  border-bottom: 1px solid $color-border-light;
}

.history-main {
  flex: 1;
  min-width: 0;
}

.history-reason {
  display: block;
  font-size: $font-sm;
  color: $color-text;
}

.history-meta {
  display: block;
  margin-top: 2px;
  font-size: $font-xs;
  color: $color-text-muted;
}

.history-change {
  font-size: $font-base;
  font-weight: $weight-bold;
  color: $color-danger;

  &--positive {
    color: $color-primary;
  }
}

.history-footer {
  padding: $spacing-md 0;
  text-align: center;
  color: $color-text-muted;
  font-size: $font-xs;
}

.adjust-input {
  height: 44px;
  background: var(--q-color-bg-soft);
  border: 1px solid $color-border;
  border-radius: $radius-md;
  padding: 0 $spacing-md;
  margin-bottom: $spacing-md;
  font-size: $font-base;
}

.adjust-error {
  display: block;
  margin-bottom: $spacing-md;
  color: $color-danger;
  font-size: $font-sm;
}

.adjust-submit {
  background: $color-primary;
  color: $color-text-inverse;
  border-radius: $radius-md;
  font-size: $font-base;
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

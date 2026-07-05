<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="settings-container">
        <text class="section-label">通用设置</text>
        <view class="settings-card">
          <view class="settings-item">
            <text class="item-text">清除缓存</text>
            <text class="item-arrow">›</text>
          </view>
          <view class="settings-item">
            <text class="item-text">通知设置</text>
            <text class="item-arrow">›</text>
          </view>
          <view class="settings-item">
            <text class="item-text">隐私政策</text>
            <text class="item-arrow">›</text>
          </view>
          <view class="settings-item">
            <text class="item-text">关于趣聚</text>
            <text class="item-arrow">›</text>
          </view>
        </view>

        <!-- Mock 模式：重置演示数据入口 -->
        <view v-if="isMockMode" class="mock-section">
          <text class="section-label">开发者工具</text>
          <view class="settings-card mock-card">
            <view class="mock-info">
              <text class="mock-badge">MOCK</text>
              <text class="mock-desc">当前运行在有状态 Mock 模式下</text>
            </view>
            <view class="settings-item" @tap="handleResetMock">
              <text class="item-text reset-text">重置演示数据</text>
              <text class="item-arrow">›</text>
            </view>
            <text class="mock-hint"> 重置后所有报名、候补、签到、好友等状态将恢复到初始数据 </text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 设置页
 *
 * 包含通用设置项和 Mock 模式下的演示数据重置入口。
 */
import { ref } from 'vue'
import { USE_MOCK } from '@/api/config'
import { resetMockDb } from '@/mock/database'

const isMockMode = ref(USE_MOCK)

/** 重置 mock 演示数据 */
function handleResetMock(): void {
  uni.showModal({
    title: '重置演示数据',
    content: '确定要重置所有演示数据吗？报名、候补、签到等状态将恢复到初始值。',
    success: (res) => {
      if (res.confirm) {
        resetMockDb()
        uni.showToast({ title: '演示数据已重置', icon: 'success' })
      }
    },
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.page {
  background: linear-gradient(160deg, $color-bg 0%, $color-bg-warm 100%);
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

.settings-container {
  padding: $spacing-lg;
  padding-bottom: calc(#{$safe-bottom} + #{$spacing-2xl});
}

.section-label {
  display: block;
  font-size: $font-sm;
  color: $color-text-sub;
  margin-bottom: $spacing-sm;
  padding-left: $spacing-xs;
}

.settings-card {
  background: $color-bg-glass;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  overflow: hidden;
  margin-bottom: $spacing-xl;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  border-bottom: 1px solid $color-border;

  &:last-child {
    border-bottom: none;
  }

  &:active {
    background: rgba(0, 0, 0, 0.02);
  }
}

.item-text {
  font-size: $font-base;
  color: $color-text;
}

.item-arrow {
  font-size: $font-lg;
  color: $color-text-muted;
}

/* Mock 模式样式 */
.mock-section {
  margin-top: $spacing-lg;
}

.mock-card {
  border: 1px dashed rgba(94, 200, 167, 0.3);
}

.mock-info {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  border-bottom: 1px solid $color-border;
}

.mock-badge {
  font-size: $font-xs;
  font-weight: $weight-bold;
  color: $color-text-inverse;
  background: $color-primary;
  padding: 2px 8px;
  border-radius: $radius-sm;
  margin-right: $spacing-sm;
}

.mock-desc {
  font-size: $font-sm;
  color: $color-text-sub;
}

.reset-text {
  color: $color-primary;
  font-weight: $weight-medium;
}

.mock-hint {
  display: block;
  font-size: $font-xs;
  color: $color-text-muted;
  padding: $spacing-sm $spacing-lg $spacing-lg;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

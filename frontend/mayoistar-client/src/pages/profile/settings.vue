<template>
  <view class="page">
    <scroll-view class="scroll-area" scroll-y>
      <view class="settings-container">
        <text class="section-label">通用设置</text>
        <view class="settings-card">
          <view class="settings-item" @tap="openPasswordPanel">
            <text class="item-text">修改密码</text>
            <text class="item-arrow">›</text>
          </view>
          <view class="settings-item" @tap="openInfoPanel('privacy')">
            <text class="item-text">隐私政策</text>
            <text class="item-arrow">›</text>
          </view>
          <view class="settings-item" @tap="openInfoPanel('about')">
            <text class="item-text">关于趣聚</text>
            <text class="item-arrow">›</text>
          </view>
        </view>

        <view v-if="passwordPanelVisible" class="password-card">
          <view class="password-header">
            <text class="password-title">修改密码</text>
            <text class="password-close" @tap="closePasswordPanel">×</text>
          </view>
          <input
            v-model="oldPassword"
            class="password-input"
            password
            type="safe-password"
            placeholder="当前密码"
          />
          <input
            v-model="newPassword"
            class="password-input"
            password
            type="safe-password"
            placeholder="新密码（至少 8 位）"
          />
          <input
            v-model="confirmPassword"
            class="password-input"
            password
            type="safe-password"
            placeholder="确认新密码"
          />
          <text v-if="passwordError" class="password-error">{{ passwordError }}</text>
          <button
            class="password-submit"
            :disabled="changingPassword"
            :loading="changingPassword"
            @tap="handleChangePassword"
          >
            {{ changingPassword ? '' : '保存新密码' }}
          </button>
        </view>

        <view v-if="activePanel === 'privacy'" class="info-card">
          <view class="info-header">
            <text class="info-title">隐私政策</text>
            <text class="info-close" @tap="closeInfoPanel">×</text>
          </view>
          <text class="info-text">
            迷星群聚仅在活动报名、签到、好友互动和小队协作所需范围内使用资料信息。位置数据仅用于附近活动、地图展示和需要位置校验的签到流程。
          </text>
          <text class="info-text"> 你可以在编辑资料页更新头像、昵称、兴趣标签等公开资料。 </text>
        </view>

        <view v-if="activePanel === 'about'" class="info-card">
          <view class="info-header">
            <text class="info-title">关于趣聚</text>
            <text class="info-close" @tap="closeInfoPanel">×</text>
          </view>
          <text class="info-text">迷星群聚 MayoiStar</text>
          <text class="info-text">面向活动发现、报名、社交和小队协作的移动端客户端。</text>
          <text class="info-meta">当前版本：0.0.0</text>
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
import { BusinessError } from '@/api'
import { USE_MOCK } from '@/api/config'
import { changePassword } from '@/api/modules/auth'
import { resetMockDb } from '@/mock/database'
import { getErrorMessage } from '@/utils/error'

type InfoPanel = 'privacy' | 'about'

const isMockMode = ref(USE_MOCK)
const passwordPanelVisible = ref(false)
const changingPassword = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordError = ref('')
const activePanel = ref<InfoPanel | null>(null)

/** 打开修改密码面板 */
function openPasswordPanel(): void {
  passwordPanelVisible.value = true
  activePanel.value = null
  passwordError.value = ''
}

/** 关闭修改密码面板并清空敏感输入 */
function closePasswordPanel(): void {
  passwordPanelVisible.value = false
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordError.value = ''
}

/** 打开普通设置面板 */
function openInfoPanel(panel: InfoPanel): void {
  passwordPanelVisible.value = false
  activePanel.value = activePanel.value === panel ? null : panel
}

/** 关闭普通设置面板 */
function closeInfoPanel(): void {
  activePanel.value = null
}

/** 校验并提交当前用户密码修改 */
async function handleChangePassword(): Promise<void> {
  if (changingPassword.value) return
  passwordError.value = ''
  const oldValue = oldPassword.value
  const newValue = newPassword.value
  if (!oldValue) {
    passwordError.value = '请输入当前密码'
    return
  }
  if (newValue.length < 8) {
    passwordError.value = '新密码至少 8 位'
    return
  }
  if (newValue !== confirmPassword.value) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }

  changingPassword.value = true
  try {
    await changePassword(oldValue, newValue)
    uni.showToast({ title: '密码已修改', icon: 'success' })
    closePasswordPanel()
  } catch (error) {
    passwordError.value =
      error instanceof BusinessError ? getErrorMessage(error.code) : getErrorMessage(0, '修改失败')
  } finally {
    changingPassword.value = false
  }
}

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
  background: $color-bg;
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
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  overflow: hidden;
  margin-bottom: $spacing-xl;
}

.password-card {
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  margin-bottom: $spacing-xl;
}

.info-card {
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  margin-bottom: $spacing-xl;
}

.info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.info-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.info-close {
  font-size: $font-xl;
  color: $color-text-muted;
}

.info-text,
.info-meta {
  display: block;
  font-size: $font-sm;
  color: $color-text-sub;
  line-height: 1.6;
  margin-bottom: $spacing-sm;
}

.info-meta {
  color: $color-text-muted;
}

.password-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.password-title {
  font-size: $font-base;
  font-weight: $weight-semibold;
  color: $color-text;
}

.password-close {
  font-size: $font-xl;
  color: $color-text-muted;
}

.password-input {
  height: 44px;
  background: var(--q-color-bg-soft);
  border: 1px solid $color-border;
  border-radius: $radius-md;
  padding: 0 $spacing-md;
  margin-bottom: $spacing-md;
  font-size: $font-base;
}

.password-error {
  display: block;
  margin-bottom: $spacing-md;
  color: $color-danger;
  font-size: $font-sm;
}

.password-submit {
  background: $color-primary;
  color: $color-text-inverse;
  border-radius: $radius-md;
  font-size: $font-base;
}

.password-submit[disabled] {
  background: $color-bg-soft;
  color: $color-text-muted;
  border: 1px solid $color-border-light;
  box-shadow: none;
  opacity: 1;
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

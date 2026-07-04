<template>
  <view class="page">
    <AppNavbar :title="chatTitle" />

    <!-- Message List -->
    <scroll-view
      class="message-list"
      scroll-y
      :scroll-into-view="scrollTarget"
      :scroll-with-animation="true"
    >
      <view v-if="loading && messages.length === 0" class="loading-state">
        <text>加载中...</text>
      </view>

      <view v-else-if="messages.length === 0" class="empty-state">
        <text class="empty-text">暂无消息</text>
        <text class="empty-desc">发送第一条消息开始聊天吧！</text>
      </view>

      <view v-else class="message-container">
        <view
          v-for="msg in messages"
          :key="msg.messageId"
          :id="`msg-${msg.messageId}`"
          class="message-item"
          :class="{
            'message-item--mine': isMyMessage(msg),
            'message-item--recalled': msg.recalled,
          }"
        >
          <!-- Avatar -->
          <view v-if="!isMyMessage(msg)" class="message-avatar">
            <text class="avatar-placeholder">👤</text>
          </view>

          <!-- Message Content -->
          <view class="message-body">
            <!-- Sender name (for team chats) -->
            <text v-if="!isMyMessage(msg) && conversationKind === 'team'" class="sender-name">
              {{ getSenderName(msg.senderId) }}
            </text>

            <!-- Message bubble -->
            <view
              class="message-bubble"
              :class="{
                'message-bubble--mine': isMyMessage(msg) && !msg.recalled,
                'message-bubble--recalled': msg.recalled,
              }"
              @longpress="onMessageLongPress(msg)"
            >
              <text v-if="msg.recalled" class="recalled-text">消息已撤回</text>
              <text v-else-if="msg.kind === 'text'" class="message-text">{{ msg.text }}</text>
              <image
                v-else-if="msg.kind === 'image' && getImageUrl(msg)"
                :src="getImageUrl(msg)"
                class="message-image"
                mode="widthFix"
              />
              <text v-else-if="msg.kind === 'image'" class="message-text">[图片]</text>
              <text v-else-if="msg.kind === 'location'" class="message-text">
                📍 {{ msg.location?.placeName || '位置分享' }}
              </text>
            </view>

            <!-- Time + read status (接收方视角，对齐 OpenAPI readStatus) -->
            <view class="message-meta">
              <text class="message-time">{{ formatTime(msg.sentAt) }}</text>
              <text
                v-if="isMyMessage(msg) && !msg.recalled && msg.peerReadStatus === 'read'"
                class="message-read-hint message-read-hint--read"
              >
                已读
              </text>
              <text
                v-else-if="isMyMessage(msg) && !msg.recalled && msg.peerReadStatus === 'unread'"
                class="message-read-hint message-read-hint--pending"
              >
                未读
              </text>
              <text
                v-else-if="!isMyMessage(msg) && !msg.recalled && msg.readStatus === 'unread'"
                class="message-read-hint message-read-hint--unread"
              >
                未读
              </text>
            </view>
          </view>

          <!-- My avatar -->
          <view v-if="isMyMessage(msg)" class="message-avatar">
            <text class="avatar-placeholder">🙂</text>
          </view>
        </view>
      </view>

      <view id="scroll-bottom" class="scroll-bottom"></view>
    </scroll-view>

    <!-- Attachment Panel (+ 菜单，占键盘位) -->
    <view v-if="showAttachmentPanel" class="attachment-panel">
      <view class="attachment-grid">
        <view class="attachment-item" @tap="pickAndSendImage">
          <view class="attachment-icon attachment-icon--photo">
            <text class="attachment-emoji">🖼️</text>
          </view>
          <text class="attachment-label">照片</text>
        </view>
        <view class="attachment-item" @tap="onLocationPlaceholder">
          <view class="attachment-icon attachment-icon--location">
            <text class="attachment-emoji">📍</text>
          </view>
          <text class="attachment-label">位置</text>
        </view>
      </view>
    </view>

    <!-- Input Bar -->
    <view class="input-bar">
      <view class="input-row">
        <view
          class="attach-toggle"
          :class="{ 'attach-toggle--active': showAttachmentPanel }"
          @tap="toggleAttachmentPanel"
        >
          <text class="attach-toggle-icon">{{ showAttachmentPanel ? '⌨️' : '＋' }}</text>
        </view>
        <view class="input-wrapper">
          <input
            v-model="inputText"
            class="message-input"
            type="text"
            placeholder="输入消息..."
            :adjust-position="!showAttachmentPanel"
            @focus="onInputFocus"
            @confirm="sendTextMessage"
          />
          <view
            class="send-button"
            :class="{ 'send-button--active': canSend }"
            @tap="sendTextMessage"
          >
            <text class="send-icon">➤</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 聊天室页面
 *
 * 支持单聊和群聊消息收发
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import {
  getMessages,
  sendMessage,
  getConversations,
  recallMessage,
  forwardMessage,
  markMessagesRead,
  uploadChatImage,
} from '@/api/modules/chat'
import type { ChatRealtimeEvent } from '@/api/modules/chatRealtime'
import { getUserProfile } from '@/api/modules/social'
import { useAuthStore } from '@/stores/auth'
import { useChatRealtime } from '@/composables/useChatRealtime'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import type { components } from '@/api/types/schema'

type ChatMessage = components['schemas']['Chat.ChatMessage']
type MessageCreatedPayload = components['schemas']['Chat.MessageCreatedPayload']
type MessageRecalledPayload = components['schemas']['Chat.MessageRecalledPayload']
type MessageForwardedPayload = components['schemas']['Chat.MessageForwardedPayload']
type MessagePeerReadPayload = components['schemas']['Chat.MessagePeerReadPayload']

const chatTitle = ref('聊天')
const conversationId = ref('')
const conversationKind = ref<'friend' | 'team'>('friend')
const currentUserId = ref('')
const loading = ref(false)
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const scrollTarget = ref('')
const memberNames = ref<Map<string, string>>(new Map())
const showAttachmentPanel = ref(false)

const canSend = computed(() => inputText.value.trim().length > 0 && !sending.value)

/** 判断是否为自己的消息 */
function isMyMessage(msg: ChatMessage): boolean {
  return msg.senderId === currentUserId.value
}

/** 图片消息展示地址 */
function getImageUrl(msg: ChatMessage): string {
  return msg.image?.signedUrl || ''
}

/** 获取发送者名称 (群聊用) */
function getSenderName(senderId: string): string {
  return memberNames.value.get(senderId) ?? '用户'
}

/** 格式化时间 */
function formatTime(isoTime: string): string {
  const date = new Date(isoTime)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

/** 加载消息列表 */
async function loadMessages() {
  if (!conversationId.value) return

  loading.value = true
  try {
    const result = await getMessages(conversationId.value, 1, 50)
    const items = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as ChatMessage[]) ?? [])

    // 按时间正序排列 (API 返回倒序)
    messages.value = items.reverse()

    // 加载成员名称 (群聊)
    if (conversationKind.value === 'team') {
      await loadMemberNames()
    }

    // 滚动到底部
    await scrollToBottom()

    // 标记未读消息为已读
    const unreadIds = messages.value
      .filter((m) => m.senderId !== currentUserId.value && !m.recalled && m.readStatus === 'unread')
      .map((m) => m.messageId)
    if (unreadIds.length > 0) {
      await markMessagesRead(unreadIds).catch(() => {
        /* 静默失败 */
      })
      messages.value = messages.value.map((m) =>
        unreadIds.includes(m.messageId) ? { ...m, readStatus: 'read' as const } : m,
      )
    }
  } catch (error) {
    console.error('Failed to load messages:', error)
    uni.showToast({ title: '加载消息失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 加载成员名称 */
async function loadMemberNames() {
  const senderIds = new Set(messages.value.map((m) => m.senderId))
  for (const senderId of senderIds) {
    if (!memberNames.value.has(senderId)) {
      try {
        const profile = await getUserProfile(senderId)
        memberNames.value.set(senderId, profile.nickname || '用户')
      } catch {
        memberNames.value.set(senderId, '用户')
      }
    }
  }
}

/** 发送文本消息 */
async function sendTextMessage() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return

  sending.value = true
  try {
    const result = await sendMessage(conversationId.value, text)
    const newMsg = result as ChatMessage

    // 添加到消息列表
    messages.value.push(newMsg)
    inputText.value = ''

    // 滚动到底部
    await scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
    uni.showToast({ title: '发送失败', icon: 'none' })
  } finally {
    sending.value = false
  }
}

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  scrollTarget.value = ''
  await nextTick()
  scrollTarget.value = 'scroll-bottom'
}

/** 长按消息弹出操作菜单 */
function onMessageLongPress(msg: ChatMessage) {
  if (msg.recalled) return

  const isMine = isMyMessage(msg)
  const sentAt = new Date(msg.sentAt).getTime()
  const canRecall = isMine && Date.now() - sentAt < 2 * 60 * 1000 // 2分钟内可撤回

  const itemList: string[] = []
  if (canRecall) itemList.push('撤回')
  itemList.push('转发')
  itemList.push('复制')

  uni.showActionSheet({
    itemList,
    success: (res) => {
      const action = itemList[res.tapIndex]
      if (action === '撤回') handleRecall(msg)
      else if (action === '转发') handleForward(msg)
      else if (action === '复制' && msg.text) {
        uni.setClipboardData({ data: msg.text })
      }
    },
  })
}

/** 撤回消息 */
async function handleRecall(msg: ChatMessage) {
  try {
    await recallMessage(msg.messageId)
    // 更新本地状态
    const idx = messages.value.findIndex((m) => m.messageId === msg.messageId)
    if (idx >= 0) {
      messages.value[idx] = { ...messages.value[idx], recalled: true }
    }
    uni.showToast({ title: '消息已撤回', icon: 'success' })
  } catch {
    uni.showToast({ title: '撤回失败', icon: 'none' })
  }
}

/** 转发消息 */
async function handleForward(msg: ChatMessage) {
  try {
    const result = await getConversations()
    type ConversationSummary = components['schemas']['Chat.ConversationSummary']
    const conversations: ConversationSummary[] = Array.isArray(result)
      ? result
      : (((result as Record<string, unknown>).items as ConversationSummary[]) ?? [])

    // 过滤掉当前会话
    const others = conversations.filter((c) => c.conversationId !== conversationId.value)
    if (others.length === 0) {
      uni.showToast({ title: '没有其他会话可转发', icon: 'none' })
      return
    }

    // 用 ActionSheet 选择目标会话
    const names = others.map((c) => c.title || '未命名会话')
    uni.showActionSheet({
      itemList: names,
      success: async (res) => {
        const target = others[res.tapIndex]
        try {
          await forwardMessage(msg.messageId, target.conversationId)
          uni.showToast({ title: '转发成功', icon: 'success' })
        } catch {
          uni.showToast({ title: '转发失败', icon: 'none' })
        }
      },
    })
  } catch {
    uni.showToast({ title: '加载会话失败', icon: 'none' })
  }
}

function upsertMessage(msg: ChatMessage) {
  const idx = messages.value.findIndex((m) => m.messageId === msg.messageId)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], ...msg }
  } else {
    messages.value.push(msg)
  }
}

function handleChatRealtimeEvent(event: ChatRealtimeEvent) {
  if (event.conversationId !== conversationId.value) return

  switch (event.kind) {
    case 'messageCreated':
    case 'messageForwarded': {
      const payload = event.payload as MessageCreatedPayload | MessageForwardedPayload
      upsertMessage(payload.message)
      void scrollToBottom()
      break
    }
    case 'messageRecalled': {
      const payload = event.payload as MessageRecalledPayload
      upsertMessage(payload.message)
      break
    }
    case 'messagePeerRead': {
      const payload = event.payload as MessagePeerReadPayload
      const idx = messages.value.findIndex((m) => m.messageId === payload.messageId)
      if (idx >= 0 && messages.value[idx].senderId === currentUserId.value) {
        messages.value[idx] = {
          ...messages.value[idx],
          peerReadStatus: payload.peerReadStatus,
        }
      }
      break
    }
  }
}

const { connect: connectChatRealtime } = useChatRealtime(handleChatRealtimeEvent)

function toggleAttachmentPanel() {
  showAttachmentPanel.value = !showAttachmentPanel.value
  if (showAttachmentPanel.value) {
    uni.hideKeyboard()
  }
}

function onInputFocus() {
  showAttachmentPanel.value = false
}

function onLocationPlaceholder() {
  uni.showToast({ title: '位置分享功能开发中', icon: 'none' })
}

async function pickAndSendImage() {
  if (!conversationId.value || sending.value) return

  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0]
      if (!filePath) return

      sending.value = true
      try {
        const media = await uploadChatImage(filePath)
        const result = await sendMessage(conversationId.value, {
          kind: 'image',
          imageMediaId: media.mediaId,
        })
        messages.value.push(result as ChatMessage)
        showAttachmentPanel.value = false
        await scrollToBottom()
      } catch {
        uni.showToast({ title: '图片发送失败', icon: 'none' })
      } finally {
        sending.value = false
      }
    },
  })
}

onMounted(async () => {
  const pages = getCurrentPages()
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const currentPage = pages[pages.length - 1] as any
  const options = currentPage.options || {}

  // 获取当前用户 ID（从 auth store，mock 下为 '10001'）
  const authStore = useAuthStore()
  currentUserId.value = authStore.userId || '10001'

  conversationKind.value = options.kind === 'team' ? 'team' : 'friend'

  if (options.conversationId) {
    // 直接通过 conversationId 进入
    conversationId.value = options.conversationId
  } else if (options.targetUserId) {
    try {
      const resolvedId = await resolveFriendConversationId(options.targetUserId)
      if (resolvedId) {
        conversationId.value = resolvedId
      } else {
        uni.showToast({ title: '找不到与该好友的会话', icon: 'none' })
        return
      }
    } catch {
      uni.showToast({ title: '加载会话失败', icon: 'none' })
      return
    }
  }

  // 从会话列表解析标题
  try {
    const convResult = await getConversations()
    type ConversationSummary = components['schemas']['Chat.ConversationSummary']
    const allConvs: ConversationSummary[] = Array.isArray(convResult)
      ? convResult
      : (((convResult as Record<string, unknown>).items as ConversationSummary[]) ?? [])
    const current = allConvs.find((c) => c.conversationId === conversationId.value)
    if (current?.title) {
      chatTitle.value = current.title
    } else if (conversationKind.value === 'team') {
      chatTitle.value = '小队群聊'
    } else {
      chatTitle.value = '好友聊天'
    }
  } catch {
    chatTitle.value = conversationKind.value === 'team' ? '小队群聊' : '好友聊天'
  }

  // 加载消息
  await loadMessages()

  // 建立 WebSocket（mock 模式下为内存事件总线）
  connectChatRealtime()
})

onShow(() => {
  if (conversationId.value) {
    loadMessages()
    connectChatRealtime()
  }
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

.message-list {
  flex: 1;
  height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: $spacing-md $spacing-lg;
  box-sizing: border-box;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
  color: $color-text-sub;
}

.empty-text {
  font-size: $font-lg;
  font-weight: $weight-medium;
  color: $color-text;
}

.empty-desc {
  font-size: $font-sm;
  color: $color-text-sub;
  margin-top: $spacing-sm;
}

.message-container {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: $spacing-lg;
  min-height: 100%;
  box-sizing: border-box;
  padding-bottom: $spacing-sm;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  width: 100%;
  box-sizing: border-box;

  &--mine {
    flex-direction: row-reverse;

    .message-body {
      align-items: flex-end;
    }

    .message-meta {
      padding-left: 0;
      padding-right: $spacing-xs;
      justify-content: flex-end;
    }
  }

  &--recalled {
    opacity: 0.85;
  }
}

.message-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;

  .avatar-placeholder {
    font-size: 20px;
  }
}

.message-body {
  display: flex;
  flex-direction: column;
  max-width: 72%;
  min-width: 0;
  gap: $spacing-xs;
}

.sender-name {
  font-size: $font-xs;
  color: $color-text-sub;
  padding-left: $spacing-xs;
}

.message-bubble {
  background: #ffffff;
  border-radius: $radius-lg;
  padding: $spacing-sm $spacing-md;
  box-shadow: $shadow-xs;
  max-width: 100%;
  box-sizing: border-box;

  &--mine {
    background: $color-primary;
    color: #ffffff;
  }

  &--recalled {
    background: #f0f2f5;
    color: $color-text-sub;
    box-shadow: none;
  }
}

.message-text {
  font-size: $font-base;
  line-height: 1.5;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.recalled-text {
  font-size: $font-sm;
  color: $color-text-sub;
  font-style: italic;
}

.message-image {
  max-width: 200px;
  border-radius: $radius-md;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding-left: $spacing-xs;
}

.message-time {
  font-size: $font-xs;
  color: $color-text-muted;
}

.message-read-hint {
  font-size: $font-xs;

  &--unread {
    color: $color-primary;
  }

  &--pending {
    color: $color-text-muted;
  }

  &--read {
    color: $color-text-muted;
  }
}

.attachment-panel {
  flex-shrink: 0;
  background: #f0f2f5;
  border-top: 1px solid $color-border-light;
  padding: $spacing-lg $spacing-md;
  padding-bottom: $spacing-md;
  min-height: 180px;
}

.attachment-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xl;
  padding: $spacing-sm $spacing-md;
}

.attachment-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  width: 72px;
}

.attachment-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-lg;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-xs;

  &--photo {
    background: #e8f4fd;
  }

  &--location {
    background: #e8f7f0;
  }
}

.attachment-emoji {
  font-size: 26px;
}

.attachment-label {
  font-size: $font-xs;
  color: $color-text-sub;
}

.input-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.attach-toggle {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;

  &--active {
    background: $color-primary-light;
  }
}

.attach-toggle-icon {
  font-size: 20px;
  color: $color-text;
  line-height: 1;
}

.scroll-bottom {
  height: 1px;
}

.input-bar {
  flex-shrink: 0;
  background: #ffffff;
  border-top: 1px solid $color-border-light;
  padding: $spacing-sm $spacing-md;
  padding-bottom: calc($spacing-sm + $safe-bottom);
}

.input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  background: #f0f2f5;
  border-radius: $radius-full;
  padding: $spacing-xs $spacing-sm;
  min-width: 0;
}

.message-input {
  flex: 1;
  font-size: $font-base;
  padding: $spacing-xs $spacing-sm;
  background: transparent;
  border: none;
  outline: none;
}

.send-button {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: $color-border;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;

  &--active {
    background: $color-primary;
  }

  &:active {
    transform: scale(0.95);
  }
}

.send-icon {
  font-size: 18px;
  color: #ffffff;
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

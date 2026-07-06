<template>
  <view class="page">
    <AppNavbar :title="chatTitle">
      <template v-if="conversationKind === 'team'" #right>
        <view class="nav-info-btn" @tap="openTeamPanel">
          <text class="nav-info-icon">⋯</text>
        </view>
      </template>
    </AppNavbar>

    <!-- 群公告折叠条 -->
    <view
      v-if="conversationKind === 'team' && teamAnnouncements.length > 0"
      class="announcement-panel"
    >
      <view
        v-if="!announcementExpanded"
        class="announcement-panel__collapsed"
        @tap="expandAnnouncements"
      >
        <text class="announcement-panel__tag">公告</text>
        <text class="announcement-panel__text">{{ latestAnnouncement?.content }}</text>
      </view>
      <scroll-view v-else class="announcement-panel__expanded" scroll-y>
        <view
          v-for="item in teamAnnouncements"
          :key="item.announcementId"
          class="announcement-panel__item"
        >
          <text class="announcement-panel__tag">公告</text>
          <text class="announcement-panel__text">{{ item.content }}</text>
        </view>
      </scroll-view>
      <view
        v-if="announcementExpanded"
        class="announcement-panel__collapse"
        @tap="collapseAnnouncements"
      >
        <text class="announcement-panel__collapse-icon">^</text>
      </view>
    </view>

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
          <UserAvatar
            v-if="!isMyMessage(msg)"
            class="message-avatar-slot"
            size="sm"
            :avatar-url="getSenderAvatar(msg.senderId)"
            :name="getSenderName(msg.senderId)"
            :user-id="msg.senderId"
          />

          <!-- Message Content -->
          <view class="message-body">
            <!-- Sender name (for team chats) -->
            <text v-if="!isMyMessage(msg) && conversationKind === 'team'" class="sender-name">
              {{ getSenderName(msg.senderId) }}
            </text>

            <!-- Location card (WeChat-style, white card without text bubble) -->
            <view
              v-if="!msg.recalled && msg.kind === 'location' && msg.location"
              class="message-location"
              @longpress="onMessageLongPress(msg)"
            >
              <LocationMessageCard :location="msg.location" />
            </view>

            <!-- Image message (standalone preview card, no text bubble background) -->
            <view
              v-else-if="!msg.recalled && msg.kind === 'image'"
              class="message-image-wrap"
              @tap="onImageMessageTap(msg)"
              @longpress="onMessageLongPress(msg)"
            >
              <image
                v-if="getImageUrl(msg)"
                :src="getImageUrl(msg)"
                class="message-image"
                mode="widthFix"
              />
              <text v-else class="message-image-placeholder">[图片]</text>
            </view>

            <!-- Message bubble -->
            <view
              v-else
              class="message-bubble"
              :class="{
                'message-bubble--mine': isMyMessage(msg) && !msg.recalled,
                'message-bubble--recalled': msg.recalled,
              }"
              @longpress="onMessageLongPress(msg)"
            >
              <text v-if="msg.recalled" class="recalled-text">消息已撤回</text>
              <view v-else-if="msg.kind === 'text'" class="message-text-block">
                <text v-if="msg.mentionAll" class="mention-tag">@所有人</text>
                <text v-for="uid in msg.mentionedUserIds || []" :key="uid" class="mention-tag">
                  @{{ getSenderName(uid) }}
                </text>
                <text class="message-text">{{ msg.text }}</text>
              </view>
              <text v-else-if="msg.kind === 'location'" class="message-text">[位置]</text>
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
          <UserAvatar
            v-if="isMyMessage(msg)"
            class="message-avatar-slot"
            size="sm"
            :avatar-url="myAvatarUrl"
            :name="myNickname"
            :user-id="currentUserId"
          />
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
        <view class="attachment-item" @tap="sendLocationMessage">
          <view class="attachment-icon attachment-icon--location">
            <text class="attachment-emoji">📍</text>
          </view>
          <text class="attachment-label">位置</text>
        </view>
      </view>
    </view>

    <view v-if="showReadonlyHint" class="readonly-hint">
      <text class="readonly-hint-text">小队已解散或停用，仅可查看历史消息</text>
    </view>

    <!-- Input Bar -->
    <view v-if="conversationKind === 'team' && pendingMentionLabels.length > 0" class="mention-bar">
      <text
        v-for="label in pendingMentionLabels"
        :key="label"
        class="mention-bar-chip"
        @tap="clearPendingMentions"
      >
        {{ label }} ×
      </text>
    </view>

    <view class="input-bar">
      <view class="input-row">
        <view
          v-if="conversationKind === 'team' && teamWritable"
          class="mention-toggle"
          @tap="openMentionPicker"
        >
          <text class="mention-toggle-icon">@</text>
        </view>
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

    <TeamChatPanel
      v-if="conversationKind === 'team'"
      ref="teamPanelRef"
      :team-id="teamId"
      :team="teamProfile"
      :my-role="myTeamRole"
      :pending-request-count="pendingJoinCount"
      @dissolved="onTeamDissolved"
    />

    <ForwardTargetPicker
      v-if="forwardPickerVisible"
      :exclude-conversation-id="conversationId"
      @close="closeForwardPicker"
      @confirm="confirmForward"
    />

    <uni-popup ref="mentionPopup" type="bottom" :safe-area="true" @mask-click="closeMentionPicker">
      <view class="mention-sheet">
        <view class="mention-sheet__header">
          <text class="mention-sheet__title">提醒成员</text>
          <text class="mention-sheet__close" @tap="closeMentionPicker">×</text>
        </view>
        <scroll-view class="mention-sheet__list" scroll-y>
          <view v-if="canMentionAll" class="mention-sheet__item" @tap="selectMentionAll">
            <text class="mention-sheet__name">@所有人</text>
            <text class="mention-sheet__role">队长/管理员</text>
          </view>
          <view
            v-for="member in mentionableMembers"
            :key="member.userId"
            class="mention-sheet__item"
            @tap="selectMentionMember(member)"
          >
            <text class="mention-sheet__name">{{ member.nickname }}</text>
            <text class="mention-sheet__role">{{ member.roleLabel }}</text>
          </view>
        </scroll-view>
      </view>
    </uni-popup>

    <ImagePreviewOverlay
      :visible="chatPreviewVisible"
      :urls="chatPreviewUrls"
      :current="chatPreviewCurrent"
      :show-save="true"
      @close="closeChatImagePreview"
    />
  </view>
</template>

<script setup lang="ts">
/**
 * 聊天室页面
 *
 * 支持单聊和群聊消息收发
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { pickChatLocation } from '@/services/chat-send'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/base/AppNavbar.vue'
import ImagePreviewOverlay from '@/components/base/ImagePreviewOverlay.vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import ForwardTargetPicker from '@/components/social/ForwardTargetPicker.vue'
import LocationMessageCard from '@/components/social/LocationMessageCard.vue'
import TeamChatPanel from '@/components/social/TeamChatPanel.vue'
import { listAnnouncements } from '@/api/modules/teamChat'
import {
  getTeamDetail,
  getTeamMembers,
  getTeamJoinRequests,
  listMyTeams,
  searchTeams,
} from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
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
import { getFriends, getUserProfile } from '@/api/modules/social'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
import { useChatRealtime } from '@/composables/useChatRealtime'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import { resolveMediaPreviewUrl } from '@/utils/media-preview'
import { loadCurrentUserProfileDisplay } from '@/utils/current-user-profile'
import type { components } from '@/api/types/schema'

type ChatMessage = components['schemas']['Chat.ChatMessage']
type MessageCreatedPayload = components['schemas']['Chat.MessageCreatedPayload']
type MessageRecalledPayload = components['schemas']['Chat.MessageRecalledPayload']
type MessageForwardedPayload = components['schemas']['Chat.MessageForwardedPayload']
type MessagePeerReadPayload = components['schemas']['Chat.MessagePeerReadPayload']
type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamMemberRole = components['schemas']['Social.TeamMemberRole']
type TeamJoinRequest = components['schemas']['Social.TeamJoinRequest']
type TeamMember = components['schemas']['Social.TeamMember']
type SendMessageRequest = components['schemas']['Chat.SendMessageRequest']
type TeamAnnouncement = components['schemas']['Chat.TeamAnnouncement']

interface MentionableMember {
  userId: string
  nickname: string
  roleLabel: string
}

const chatTitle = ref('聊天')
const conversationId = ref('')
const teamId = ref('')
const conversationKind = ref<'friend' | 'team'>('friend')
const teamProfile = ref<TeamProfile | null>(null)
const myTeamRole = ref<TeamMemberRole | null>(null)
const pendingJoinCount = ref(0)
const teamAnnouncements = ref<TeamAnnouncement[]>([])
const announcementExpanded = ref(false)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const teamPanelRef = ref<any>(null)
const currentUserId = ref('')
const loading = ref(false)
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const scrollTarget = ref('')
const memberNames = ref<Map<string, string>>(new Map())
const memberAvatars = ref<Map<string, string>>(new Map())
const myAvatarUrl = ref('')
const myNickname = ref('')
/** 图片消息鉴权下载后的本地预览地址（messageId -> previewUrl） */
const imagePreviewUrls = ref<Record<string, string>>({})
const chatPreviewVisible = ref(false)
const chatPreviewUrls = ref<string[]>([])
const chatPreviewCurrent = ref('')
const friendPeerUserId = ref('')
const showAttachmentPanel = ref(false)
const forwardPickerVisible = ref(false)
const forwardingMessage = ref<ChatMessage | null>(null)
const teamMembers = ref<MentionableMember[]>([])
const pendingMentionUserIds = ref<string[]>([])
const pendingMentionAll = ref(false)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mentionPopup = ref<any>(null)

const canMentionAll = computed(() => myTeamRole.value === 'leader' || myTeamRole.value === 'admin')

const mentionableMembers = computed(() =>
  teamMembers.value.filter((m) => m.userId !== currentUserId.value),
)

const pendingMentionLabels = computed(() => {
  const labels: string[] = []
  if (pendingMentionAll.value) labels.push('@所有人')
  for (const uid of pendingMentionUserIds.value) {
    const member = teamMembers.value.find((m) => m.userId === uid)
    labels.push(`@${member?.nickname ?? '成员'}`)
  }
  return labels
})

const teamContextReady = computed(
  () => conversationKind.value !== 'team' || teamProfile.value !== null,
)

const teamWritable = computed(() => {
  if (conversationKind.value !== 'team') return true
  if (!teamProfile.value) return false
  return teamProfile.value.status === 'active'
})

/** 小队资料加载完成且非活跃时才展示只读提示，避免进入时闪一下 */
const showReadonlyHint = computed(
  () => conversationKind.value === 'team' && teamContextReady.value && !teamWritable.value,
)

const canSend = computed(
  () => inputText.value.trim().length > 0 && !sending.value && teamWritable.value,
)

const latestAnnouncement = computed(() => teamAnnouncements.value[0] ?? null)

/** 判断是否为自己的消息 */
function isMyMessage(msg: ChatMessage): boolean {
  return msg.senderId === currentUserId.value
}

/** 图片消息展示地址（私有媒体需先经鉴权下载） */
function getImageUrl(msg: ChatMessage): string {
  return imagePreviewUrls.value[msg.messageId] || ''
}

/** 为单条图片消息解析可展示的本地/公开 URL */
async function resolveImagePreviewForMessage(msg: ChatMessage): Promise<void> {
  if (msg.kind !== 'image' || !msg.image?.signedUrl) return
  const preview = await resolveMediaPreviewUrl(msg.image.signedUrl, authStore.getAccessToken())
  if (preview) {
    imagePreviewUrls.value = { ...imagePreviewUrls.value, [msg.messageId]: preview }
  }
}

/** 批量解析当前消息列表中的图片预览 */
async function resolveAllImagePreviews(): Promise<void> {
  await Promise.all(messages.value.map((msg) => resolveImagePreviewForMessage(msg)))
}

/** 打开聊天图片全屏预览 */
function onImageMessageTap(msg: ChatMessage) {
  const current = getImageUrl(msg)
  if (!current) {
    uni.showToast({ title: '图片加载中，请稍后重试', icon: 'none' })
    return
  }
  chatPreviewUrls.value = messages.value
    .filter((item) => item.kind === 'image' && !item.recalled && getImageUrl(item))
    .map((item) => getImageUrl(item))
  chatPreviewCurrent.value = current
  chatPreviewVisible.value = true
}

/** 关闭聊天图片全屏预览 */
function closeChatImagePreview() {
  chatPreviewVisible.value = false
  chatPreviewUrls.value = []
  chatPreviewCurrent.value = ''
}

/** 解析好友单聊标题：优先会话列表标题，否则拉取对方昵称 */
async function loadFriendChatTitle(): Promise<void> {
  if (conversationKind.value !== 'friend') return

  let peerId = friendPeerUserId.value
  if (!peerId) {
    const otherSender = messages.value.find((m) => m.senderId !== currentUserId.value)
    if (otherSender) peerId = otherSender.senderId
  }
  if (!peerId) return

  try {
    const [profile, friendsResult] = await Promise.all([
      getUserProfile(peerId),
      getFriends().catch(() => []),
    ])
    const friends = Array.isArray(friendsResult)
      ? friendsResult
      : (((friendsResult as Record<string, unknown>).items as {
          userId: string
          remark?: string
        }[]) ?? [])
    const friend = friends.find((f) => f.userId === peerId)
    chatTitle.value = friend?.remark?.trim() || profile.nickname || chatTitle.value
    memberNames.value.set(peerId, profile.nickname || '用户')
    if (profile.avatar?.signedUrl) {
      memberAvatars.value.set(peerId, profile.avatar.signedUrl)
    }
  } catch {
    /* 保留会话列表已有标题 */
  }
}

/** 获取发送者名称 (群聊用) */
function getSenderName(senderId: string): string {
  return memberNames.value.get(senderId) ?? '用户'
}

/** 获取发送者头像 URL */
function getSenderAvatar(senderId: string): string {
  return memberAvatars.value.get(senderId) ?? ''
}

/** 加载当前用户统一头像资料 */
async function loadCurrentUserAvatar(): Promise<void> {
  const profile = await loadCurrentUserProfileDisplay()
  myAvatarUrl.value = profile.avatarUrl
  myNickname.value = profile.nickname
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
    } else {
      await loadFriendChatTitle()
    }

    await resolveAllImagePreviews()

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

/** 加载小队上下文（资料、角色、待审申请数） */
async function loadTeamContext() {
  if (!teamId.value) return
  try {
    const [teamResult, membersResult, requestsResult, announcementsResult] = await Promise.all([
      getTeamDetail(teamId.value),
      getTeamMembers(teamId.value, 1, 100),
      getTeamJoinRequests(teamId.value).catch(() => null),
      listAnnouncements(teamId.value, 1, 20).catch(() => null),
    ])
    teamProfile.value = teamResult as TeamProfile
    if (teamProfile.value.name) {
      chatTitle.value = `${teamProfile.value.name} (${teamProfile.value.memberCount})`
    }
    const memberItems = extractPageItems<TeamMember>(membersResult)
    myTeamRole.value = memberItems.find((m) => m.userId === currentUserId.value)?.role ?? null
    teamMembers.value = memberItems.map((m) => ({
      userId: m.userId,
      nickname: m.nickname,
      roleLabel: teamRoleLabel(m.role),
    }))
    for (const member of memberItems) {
      memberNames.value.set(member.userId, member.nickname)
      if (member.avatar?.signedUrl) {
        memberAvatars.value.set(member.userId, member.avatar.signedUrl)
      }
    }
    const isManager = myTeamRole.value === 'leader' || myTeamRole.value === 'admin'
    if (isManager && requestsResult) {
      const requests = extractPageItems<TeamJoinRequest>(requestsResult)
      pendingJoinCount.value = requests.filter((r) => r.status === 'pending').length
    } else {
      pendingJoinCount.value = 0
    }

    const announcementItems = announcementsResult
      ? extractPageItems<TeamAnnouncement>(announcementsResult)
      : []
    teamAnnouncements.value = announcementItems.sort(
      (a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime(),
    )
  } catch (error) {
    console.error('Failed to load team context:', error)
  }
}

function expandAnnouncements() {
  if (teamAnnouncements.value.length > 0) {
    announcementExpanded.value = true
  }
}

function collapseAnnouncements() {
  announcementExpanded.value = false
}

/** 打开小队信息弹窗 */
function openTeamPanel() {
  teamPanelRef.value?.open()
}

/** 小队解散后更新本地状态，输入区切换为只读 */
function onTeamDissolved() {
  if (teamProfile.value) {
    teamProfile.value = { ...teamProfile.value, status: 'dissolved' }
    chatTitle.value = `${teamProfile.value.name} (${teamProfile.value.memberCount})`
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
        if (profile.avatar?.signedUrl) {
          memberAvatars.value.set(senderId, profile.avatar.signedUrl)
        }
      } catch {
        memberNames.value.set(senderId, '用户')
      }
    }
  }
}

/** 小队角色展示文案 */
function teamRoleLabel(role: TeamMemberRole): string {
  const map: Record<TeamMemberRole, string> = {
    leader: '队长',
    admin: '管理员',
    member: '成员',
  }
  return map[role] ?? role
}

function openMentionPicker() {
  mentionPopup.value?.open()
}

function closeMentionPicker() {
  mentionPopup.value?.close()
}

function selectMentionAll() {
  pendingMentionAll.value = true
  closeMentionPicker()
}

function selectMentionMember(member: MentionableMember) {
  if (!pendingMentionUserIds.value.includes(member.userId)) {
    pendingMentionUserIds.value = [...pendingMentionUserIds.value, member.userId]
  }
  closeMentionPicker()
}

function clearPendingMentions() {
  pendingMentionAll.value = false
  pendingMentionUserIds.value = []
}

/** 发送文本消息（支持小队 @ 提醒） */
async function sendTextMessage() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return

  sending.value = true
  try {
    const payload: SendMessageRequest = { kind: 'text', text }
    if (conversationKind.value === 'team') {
      if (pendingMentionAll.value) payload.mentionAll = true
      if (pendingMentionUserIds.value.length > 0) {
        payload.mentionedUserIds = [...pendingMentionUserIds.value]
      }
    }

    const result = await sendMessage(conversationId.value, payload)
    const newMsg = result as ChatMessage

    messages.value.push(newMsg)
    inputText.value = ''
    clearPendingMentions()

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
    const recalled = (await recallMessage(msg.messageId)) as ChatMessage
    const idx = messages.value.findIndex((m) => m.messageId === msg.messageId)
    if (idx >= 0) {
      messages.value[idx] = { ...messages.value[idx], ...recalled, recalled: true }
    }
    uni.showToast({ title: '消息已撤回', icon: 'success' })
  } catch {
    uni.showToast({ title: '撤回失败', icon: 'none' })
  }
}

/** 打开多选转发弹层 */
function handleForward(msg: ChatMessage) {
  forwardingMessage.value = msg
  forwardPickerVisible.value = true
}

function closeForwardPicker() {
  forwardPickerVisible.value = false
  forwardingMessage.value = null
}

/** 确认转发到多个目标会话 */
async function confirmForward(targetIds: string[]) {
  if (!forwardingMessage.value || targetIds.length === 0) return
  try {
    await forwardMessage(forwardingMessage.value.messageId, targetIds)
    uni.showToast({
      title: targetIds.length > 1 ? `已转发到 ${targetIds.length} 个会话` : '转发成功',
      icon: 'success',
    })
    closeForwardPicker()
  } catch {
    uni.showToast({ title: '转发失败', icon: 'none' })
  }
}

function upsertMessage(msg: ChatMessage) {
  const idx = messages.value.findIndex((m) => m.messageId === msg.messageId)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], ...msg }
  } else {
    messages.value.push(msg)
  }
  void resolveImagePreviewForMessage(msg)
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

/** 选取并发送位置消息 */
async function sendLocationMessage() {
  if (!conversationId.value || sending.value || !teamWritable.value) return

  uni.showLoading({ title: '获取位置...' })
  const location = await pickChatLocation()
  uni.hideLoading()

  sending.value = true
  try {
    const result = await sendMessage(conversationId.value, { kind: 'location', location })
    messages.value.push(result as ChatMessage)
    showAttachmentPanel.value = false
    await scrollToBottom()
  } catch {
    uni.showToast({ title: '位置发送失败', icon: 'none' })
  } finally {
    sending.value = false
  }
}

async function pickAndSendImage() {
  if (!conversationId.value || sending.value || !teamWritable.value) return

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
        const sent = result as ChatMessage
        messages.value.push(sent)
        await resolveImagePreviewForMessage(sent)
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

  currentUserId.value = authStore.userId ?? ''
  await loadCurrentUserAvatar()

  conversationKind.value = options.kind === 'team' ? 'team' : 'friend'
  teamId.value = options.teamId || ''
  friendPeerUserId.value = options.targetUserId || ''

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

  if (conversationKind.value === 'friend') {
    await loadFriendChatTitle()
  }

  if (conversationKind.value === 'team') {
    if (!teamId.value && conversationId.value) {
      const mine = extractPageItems<TeamProfile>(await listMyTeams(1, 100))
      const matched = mine.find((t) => t.chatId === conversationId.value)
      if (matched) {
        teamId.value = matched.teamId
      } else {
        const discovered = extractPageItems<TeamProfile>(
          await searchTeams({ page: 1, pageSize: 100 }),
        )
        const found = discovered.find((t) => t.chatId === conversationId.value)
        if (found) teamId.value = found.teamId
      }
    }
    await loadTeamContext()
  }

  // 加载消息
  await loadMessages()

  // 建立 WebSocket（mock 模式下为内存事件总线）
  connectChatRealtime()
})

onShow(() => {
  void loadCurrentUserAvatar()
  if (conversationId.value) {
    loadMessages()
    connectChatRealtime()
    if (conversationKind.value === 'team' && teamId.value) {
      loadTeamContext()
    }
  }
})
</script>

<style lang="scss" scoped>
@import '@/styles/theme.scss';

.nav-info-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.6);
}

.nav-info-icon {
  font-size: 22px;
  font-weight: $weight-bold;
  line-height: 1;
  letter-spacing: 1px;
  color: $color-text;
}

.announcement-panel {
  flex-shrink: 0;
  background: var(--q-color-bg-card);
  border-bottom: 1px solid $color-border-light;
  box-shadow: $shadow-xs;
}

.announcement-panel__collapsed,
.announcement-panel__item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
}

.announcement-panel__expanded {
  max-height: 160px;
}

.announcement-panel__tag {
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 2px $spacing-xs;
  border-radius: $radius-sm;
  line-height: 1.4;
}

.announcement-panel__text {
  flex: 1;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.announcement-panel__collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xs 0 $spacing-sm;
  background: rgba(0, 0, 0, 0.03);
}

.announcement-panel__collapse-icon {
  font-size: 16px;
  color: $color-text-muted;
  line-height: 1;
}

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
    justify-content: flex-end;

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

.message-avatar-slot {
  flex-shrink: 0;
}

.message-body {
  display: flex;
  flex-direction: column;
  max-width: 72%;
  flex-shrink: 0;
  gap: $spacing-xs;
}

.sender-name {
  font-size: $font-xs;
  color: $color-text-sub;
  padding-left: $spacing-xs;
}

.message-bubble {
  background: var(--q-color-bg-card);
  border-radius: $radius-lg;
  padding: $spacing-sm $spacing-md;
  box-shadow: $shadow-xs;
  max-width: 100%;
  box-sizing: border-box;

  &--mine {
    background: $color-primary;
    color: var(--q-color-bg-card);
  }

  &--recalled {
    background: var(--q-color-bg-soft);
    color: $color-text-sub;
    box-shadow: none;
  }
}

.message-text-block {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: baseline;
}

.message-text {
  font-size: $font-base;
  line-height: 1.5;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.mention-tag {
  font-size: $font-xs;
  color: $color-primary;
  font-weight: $weight-semibold;
  margin-right: 2px;
}

.message-bubble--mine .mention-tag {
  color: var(--q-color-bg-card);
  opacity: 0.95;
}

.recalled-text {
  font-size: $font-sm;
  color: $color-text-sub;
  font-style: italic;
}

.message-image-wrap {
  width: 200px;
  max-width: 100%;
  border-radius: $radius-md;
  overflow: hidden;
  background: var(--q-color-bg-card);
  box-shadow: $shadow-xs;
  flex-shrink: 0;
}

.message-image {
  width: 200px;
  max-width: 100%;
  min-height: 80px;
  display: block;
  border-radius: $radius-md;
  vertical-align: top;
}

.message-image-placeholder {
  display: block;
  font-size: $font-sm;
  color: $color-text-sub;
  padding: $spacing-sm $spacing-md;
  background: var(--q-color-bg-card);
  border-radius: $radius-md;
}

.message-location {
  max-width: 100%;
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
  background: var(--q-color-bg-soft);
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
  background: var(--q-color-bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-xs;

  &--photo {
    background: var(--q-color-primary-light);
  }

  &--location {
    background: var(--q-color-primary-light);
  }
}

.attachment-emoji {
  font-size: 26px;
}

.attachment-label {
  font-size: $font-xs;
  color: $color-text-sub;
}

.mention-bar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-md 0;
  background: var(--q-color-bg-card);
}

.mention-bar-chip {
  font-size: $font-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
}

.mention-toggle {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: var(--q-color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mention-toggle-icon {
  font-size: $font-base;
  font-weight: $weight-bold;
  color: $color-primary;
}

.mention-sheet {
  background: var(--q-color-bg-card);
  border-radius: $radius-xl $radius-xl 0 0;
  max-height: 60vh;
  display: flex;
  flex-direction: column;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-lg $spacing-xl;
    border-bottom: 1px solid $color-border-light;
  }

  &__title {
    font-size: $font-lg;
    font-weight: $weight-semibold;
    color: $color-text;
  }

  &__close {
    font-size: 24px;
    color: $color-text-muted;
  }

  &__list {
    max-height: 50vh;
  }

  &__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-md $spacing-xl;
    border-bottom: 1px solid $color-border-light;
  }

  &__name {
    font-size: $font-base;
    color: $color-text;
    font-weight: $weight-medium;
  }

  &__role {
    font-size: $font-xs;
    color: $color-text-muted;
  }
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
  background: var(--q-color-bg-soft);
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

.readonly-hint {
  flex-shrink: 0;
  background: rgba(220, 38, 38, 0.08);
  padding: $spacing-sm $spacing-md;
  text-align: center;
}

.readonly-hint-text {
  font-size: $font-sm;
  color: $color-danger;
}

.input-bar {
  flex-shrink: 0;
  background: var(--q-color-bg-card);
  border-top: 1px solid $color-border-light;
  padding: $spacing-sm $spacing-md;
  padding-bottom: calc($spacing-sm + $safe-bottom);
}

.input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  background: var(--q-color-bg-soft);
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
  color: var(--q-color-bg-card);
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

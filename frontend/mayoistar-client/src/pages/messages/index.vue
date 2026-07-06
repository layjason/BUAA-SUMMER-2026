<template>
  <view class="page">
    <!-- Top Bar -->
    <SocialTopBar
      :user-avatar="userAvatar"
      :user-name="userNickname"
      :has-notification="false"
      :pending-requests="pendingRequestsCount"
      placeholder="搜索好友、用户或小队"
      @avatar-tap="onAvatarTap"
      @search-tap="onSearchTap"
      @add-friend-tap="showAddFriendMenu"
      @more-tap="onMoreTap"
    />

    <!-- Scrollable Content -->
    <scroll-view class="scroll-area" scroll-y>
      <!-- Quick Cards -->
      <SocialQuickCards
        :team-count="teams.length"
        team-desc=""
        :pending-team-requests="pendingTeamRequests"
        :companion-count="activityCompanions.length"
        companion-desc="可能认识的活动同伴"
        :request-count="pendingRequestsCount"
        request-desc="待处理的好友申请"
        @teams-tap="goToTeams"
        @create-team-tap="goToCreateTeam"
        @companions-tap="goToActivityCompanions"
        @friend-requests-tap="goToFriendRequests"
        @pending-team-requests-tap="goToPendingTeamRequests"
      />

      <!-- Conversation List -->
      <view class="section-header">
        <text class="section-title">会话</text>
      </view>

      <view v-if="conversations.length === 0" class="empty-state">
        <EmptyState title="暂无会话" description="开始添加好友或加入小队，开启聊天吧！" />
      </view>

      <view v-else class="conversation-list">
        <ConversationCard
          v-for="conv in conversations"
          :key="conv.conversationId"
          :avatar="avatarSignedUrl(conv.avatar)"
          :name="conv.title"
          :tag="conv.kind === 'team' ? '小队' : '好友'"
          :tag-type="conv.kind"
          :last-message="conv.lastMessagePreview || ''"
          :time="formatTime(conv.updatedAt)"
          :unread-count="conv.unreadCount"
          :is-online="false"
          @tap="openChat(conv)"
        />
      </view>

      <!-- Bottom padding for safe area -->
      <view class="bottom-padding"></view>
    </scroll-view>

    <!-- Floating Create Button -->
    <FloatingCreateButton @tap="showCreateMenu" />

    <!-- Add Friend Action Sheet -->
    <uni-popup
      ref="addFriendPopup"
      type="bottom"
      :safe-area="true"
      @mask-click="closeAddFriendMenu"
    >
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">添加好友</text>
          <text class="action-sheet__close" @tap="closeAddFriendMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="onScanQrAddFriend">
            <text class="action-sheet__icon">📷</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">扫码加好友</text>
              <text class="action-sheet__desc">扫描个人二维码</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="onSearchUserAddFriend">
            <text class="action-sheet__icon">🔍</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">搜索用户</text>
              <text class="action-sheet__desc">输入用户名、昵称或邮箱</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToMyQRCode">
            <text class="action-sheet__icon">🎫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">我的二维码</text>
              <text class="action-sheet__desc">分享你的个人二维码</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToActivityCompanions">
            <text class="action-sheet__icon"></text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">活动同伴推荐</text>
              <text class="action-sheet__desc">来自相同活动的参与者</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToFriendRequests">
            <text class="action-sheet__icon">📨</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">好友申请</text>
              <text class="action-sheet__desc">{{ pendingRequestsCount }}条待处理</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- More Menu Action Sheet -->
    <uni-popup ref="moreMenuPopup" type="bottom" :safe-area="true" @mask-click="closeMoreMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">更多</text>
          <text class="action-sheet__close" @tap="closeMoreMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToFriends">
            <text class="action-sheet__icon">👫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">好友列表</text>
              <text class="action-sheet__desc">查看和管理所有好友</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToBlacklist">
            <text class="action-sheet__icon">🚫</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">黑名单</text>
              <text class="action-sheet__desc">管理已屏蔽用户</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="goToFollows">
            <text class="action-sheet__icon">💖</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">关注与粉丝</text>
              <text class="action-sheet__desc">查看关注和粉丝列表</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- Create Menu Action Sheet -->
    <uni-popup ref="createMenuPopup" type="bottom" :safe-area="true" @mask-click="closeCreateMenu">
      <view class="action-sheet">
        <view class="action-sheet__header">
          <text class="action-sheet__title">快速创建</text>
          <text class="action-sheet__close" @tap="closeCreateMenu">×</text>
        </view>

        <view class="action-sheet__items">
          <view class="action-sheet__item" @tap="goToCreateTeam">
            <text class="action-sheet__icon"></text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">创建小队</text>
              <text class="action-sheet__desc">创建兴趣小队并邀请成员</text>
            </view>
          </view>

          <view class="action-sheet__item" @tap="startNewChat">
            <text class="action-sheet__icon">💬</text>
            <view class="action-sheet__content">
              <text class="action-sheet__label">发起聊天</text>
              <text class="action-sheet__desc">选择好友开始新对话</text>
            </view>
          </view>
        </view>
      </view>
    </uni-popup>

    <!-- 内联搜索下拉（替代 uni-popup top，避免黑屏遮罩） -->
    <view v-if="searchActive" class="search-overlay" @tap="closeSearch">
      <view class="search-dropdown" @tap.stop>
        <view class="search-dropdown__header">
          <view class="search-dropdown__input-wrap">
            <text class="search-dropdown__icon">🔍</text>
            <input
              v-model="searchKeyword"
              class="search-dropdown__input"
              type="text"
              confirm-type="search"
              placeholder="搜索好友、用户或小队"
              :focus="searchFocused"
            />
          </view>
          <text class="search-dropdown__cancel" @tap="closeSearch">取消</text>
        </view>

        <scroll-view
          v-if="searchKeyword.trim()"
          class="search-dropdown__results"
          scroll-y
          @tap.stop
        >
          <view v-if="!hasSearchResults" class="search-dropdown__empty">
            <text>未找到相关好友、用户或小队</text>
          </view>

          <view v-if="friendSearchResults.length > 0" class="search-dropdown__group">
            <text class="search-dropdown__group-title">好友</text>
            <view
              v-for="item in friendSearchResults"
              :key="item.key"
              class="search-dropdown__item"
              @tap="openFriendSearchResult(item)"
            >
              <UserAvatar size="sm" :avatar-url="item.avatarUrl" :name="item.title" />
              <text class="search-dropdown__name">{{ item.title }}</text>
            </view>
          </view>

          <view v-if="nonFriendSearchResults.length > 0" class="search-dropdown__group">
            <text class="search-dropdown__group-title">非好友</text>
            <view
              v-for="item in nonFriendSearchResults"
              :key="item.key"
              class="search-dropdown__item"
              @tap="openNonFriendSearchResult(item)"
            >
              <UserAvatar size="sm" :avatar-url="item.avatarUrl" :name="item.title" />
              <text class="search-dropdown__name">{{ item.title }}</text>
            </view>
          </view>

          <view v-if="teamSearchResults.length > 0" class="search-dropdown__group">
            <text class="search-dropdown__group-title">小队</text>
            <view
              v-for="item in teamSearchResults"
              :key="item.key"
              class="search-dropdown__item"
              @tap="openTeamSearchResult(item)"
            >
              <UserAvatar size="sm" :avatar-url="item.avatarUrl" :name="item.title" />
              <view class="search-dropdown__meta">
                <text class="search-dropdown__name">{{ item.title }}</text>
                <text class="search-dropdown__subtitle">{{ teamSearchSubtitle(item) }}</text>
              </view>
              <text class="search-dropdown__action">{{ teamSearchActionLabel(item) }}</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 社交总入口页面 - Social Hub
 *
 * 承载：好友单聊、小队群聊、好友申请、活动同伴推荐、小队入口
 */
import { computed, nextTick, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { useChatRealtime, type ChatRealtimeEvent } from '@/composables/useChatRealtime'
import SocialTopBar from '@/components/social/SocialTopBar.vue'
import SocialQuickCards from '@/components/social/SocialQuickCards.vue'
import ConversationCard from '@/components/social/ConversationCard.vue'
import FloatingCreateButton from '@/components/social/FloatingCreateButton.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import UserAvatar from '@/components/base/UserAvatar.vue'
import { getFriends, getReceivedFriendRequests } from '@/api/modules/social'
import { resolveFriendConversationId } from '@/utils/friend-chat'
import { listMyTeams, searchTeams, getTeamMembers, getTeamJoinRequests } from '@/api/modules/teams'
import { extractPageItems } from '@/utils/page-result'
import { getConversations } from '@/api/modules/chat'
import { useAuthStore } from '@/stores/auth'
import { fetchActivityCompanions } from '@/utils/activity-companions'
import { ensureAuthenticatedAccess } from '@/utils/auth-guard'
import { scanPersonalQrAndAddFriend } from '@/utils/personal-qr'
import { loadCurrentUserProfileDisplay } from '@/utils/current-user-profile'
import { enrichConversationSummaries, avatarSignedUrl } from '@/utils/conversation-display'
import type { components } from '@/api/types/schema'

type FriendRequest = components['schemas']['Social.FriendRequest']
type TeamProfile = components['schemas']['Social.TeamProfile']
type TeamMember = components['schemas']['Social.TeamMember']
type TeamJoinRequest = components['schemas']['Social.TeamJoinRequest']
type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type FriendItem = components['schemas']['Social.FriendItem']
type MessageCreatedPayload = components['schemas']['Chat.MessageCreatedPayload']
type MessageForwardedPayload = components['schemas']['Chat.MessageForwardedPayload']

type TeamMemberUser = {
  userId: string
  nickname: string
  avatarUrl: string
}

type HubUserSearchResult = {
  key: string
  title: string
  avatarUrl: string
  userId: string
  conversationId?: string
}

type HubTeamSearchResult = {
  key: string
  title: string
  avatarUrl: string
  teamId: string
  conversationId?: string
  isMember: boolean
  joinMode: TeamProfile['joinMode']
  memberCount: number
  capacity: number
}

// User state
const userAvatar = ref('')
const userNickname = ref('')
const loading = ref(false)

// Real data from API
const friends = ref<FriendItem[]>([])
const teamMemberUsers = ref<TeamMemberUser[]>([])
const discoverTeams = ref<TeamProfile[]>([])
const teams = ref<TeamProfile[]>([])
const pendingRequestsCount = ref(0)
const pendingTeamRequests = ref(0)
const pendingTeamRequestTargets = ref<{ teamId: string; name: string }[]>([])
const activityCompanions = ref<{ userId: string }[]>([])

// Real conversations from chat API
const conversations = ref<ConversationSummary[]>([])
const searchActive = ref(false)
const searchKeyword = ref('')
const searchFocused = ref(false)

// Popup refs
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const addFriendPopup = ref<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const moreMenuPopup = ref<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createMenuPopup = ref<any>(null)

const friendIdSet = computed(() => new Set(friends.value.map((f) => f.userId)))
const myTeamIdSet = computed(() => new Set(teams.value.map((t) => t.teamId)))

const friendSearchResults = computed<HubUserSearchResult[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return []

  return friends.value
    .filter((friend) => {
      const displayName = friend.remark?.trim() || friend.nickname
      const haystack = `${displayName} ${friend.nickname} ${friend.remark ?? ''}`.toLowerCase()
      return haystack.includes(keyword)
    })
    .map((friend) => {
      const displayName = friend.remark?.trim() || friend.nickname
      const conv = conversations.value.find(
        (c) =>
          c.kind === 'friend' &&
          (c.title === displayName || c.title === friend.nickname || c.title === friend.remark),
      )
      return {
        key: `friend-${friend.userId}`,
        title: displayName,
        avatarUrl: friend.avatar?.signedUrl ?? '',
        userId: friend.userId,
        conversationId: conv?.conversationId,
      }
    })
})

const nonFriendSearchResults = computed<HubUserSearchResult[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return []

  return teamMemberUsers.value
    .filter(
      (user) =>
        !friendIdSet.value.has(user.userId) && user.nickname.toLowerCase().includes(keyword),
    )
    .map((user) => ({
      key: `user-${user.userId}`,
      title: user.nickname,
      avatarUrl: user.avatarUrl,
      userId: user.userId,
    }))
})

const teamSearchResults = computed<HubTeamSearchResult[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return []

  const seen = new Set<string>()
  const results: HubTeamSearchResult[] = []

  for (const team of [...teams.value, ...discoverTeams.value]) {
    if (seen.has(team.teamId)) continue
    seen.add(team.teamId)

    const haystack = [team.name, team.description ?? '', ...team.tags].join(' ').toLowerCase()
    if (!haystack.includes(keyword)) continue

    const isMember = myTeamIdSet.value.has(team.teamId)
    if (!isMember && team.status !== 'active') continue

    results.push({
      key: `team-${team.teamId}`,
      title: team.name,
      avatarUrl: team.avatar?.signedUrl ?? '',
      teamId: team.teamId,
      conversationId: isMember ? team.chatId : undefined,
      isMember,
      joinMode: team.joinMode,
      memberCount: team.memberCount,
      capacity: team.capacity,
    })
  }

  return results
})

const hasSearchResults = computed(
  () =>
    friendSearchResults.value.length > 0 ||
    nonFriendSearchResults.value.length > 0 ||
    teamSearchResults.value.length > 0,
)

function teamSearchSubtitle(item: HubTeamSearchResult): string {
  const members = `${item.memberCount}/${item.capacity} 成员`
  if (item.isMember) return members
  return `${members} · ${item.joinMode === 'publicJoin' ? '自由加入' : '需审批'}`
}

function teamSearchActionLabel(item: HubTeamSearchResult): string {
  if (item.isMember) return '群聊'
  return item.joinMode === 'publicJoin' ? '加入' : '申请'
}

// Event handlers
function onAvatarTap() {
  uni.navigateTo({ url: '/pages/social/add-friend?showQr=1' })
}

async function onSearchTap() {
  closeAllPopups()
  searchKeyword.value = ''
  searchActive.value = true
  searchFocused.value = false
  await nextTick()
  searchFocused.value = true
}

function closeSearch() {
  searchFocused.value = false
  searchActive.value = false
  searchKeyword.value = ''
}

function openFriendSearchResult(item: HubUserSearchResult) {
  closeSearch()

  if (item.conversationId) {
    uni.navigateTo({
      url: `/pages/messages/chat?conversationId=${item.conversationId}&kind=friend`,
    })
    return
  }

  void resolveAndOpenFriendChat(item.userId)
}

function openNonFriendSearchResult(item: HubUserSearchResult) {
  closeSearch()
  uni.navigateTo({ url: `/pages/social/user-profile?id=${item.userId}` })
}

function openTeamSearchResult(item: HubTeamSearchResult) {
  closeSearch()

  if (item.isMember && item.conversationId) {
    uni.navigateTo({
      url: `/pages/messages/chat?conversationId=${item.conversationId}&kind=team&teamId=${item.teamId}`,
    })
    return
  }

  uni.navigateTo({ url: `/pages/teams/detail?teamId=${item.teamId}` })
}

async function resolveAndOpenFriendChat(userId: string) {
  try {
    const conversationId = await resolveFriendConversationId(userId)
    if (!conversationId) {
      uni.showToast({ title: '找不到与该好友的会话', icon: 'none' })
      return
    }
    uni.navigateTo({
      url: `/pages/messages/chat?conversationId=${conversationId}&kind=friend&targetUserId=${userId}`,
    })
  } catch {
    uni.showToast({ title: '打开会话失败', icon: 'none' })
  }
}

function showAddFriendMenu() {
  // 互斥：关闭其他菜单
  moreMenuPopup.value?.close()
  createMenuPopup.value?.close()
  addFriendPopup.value?.open()
}

function closeAddFriendMenu() {
  addFriendPopup.value?.close()
}

function onMoreTap() {
  // 互斥：关闭其他菜单
  addFriendPopup.value?.close()
  createMenuPopup.value?.close()
  moreMenuPopup.value?.open()
}

function closeMoreMenu() {
  moreMenuPopup.value?.close()
}

function showCreateMenu() {
  // 互斥：关闭其他菜单
  addFriendPopup.value?.close()
  moreMenuPopup.value?.close()
  createMenuPopup.value?.open()
}

function closeCreateMenu() {
  createMenuPopup.value?.close()
}

/** 关闭所有底部菜单，避免遮罩层残留阻塞触摸 */
function closeAllPopups() {
  addFriendPopup.value?.close()
  moreMenuPopup.value?.close()
  createMenuPopup.value?.close()
  closeSearch()
}

// Navigation functions
function onScanQrAddFriend() {
  closeAddFriendMenu()
  scanPersonalQrAndAddFriend()
}

function onSearchUserAddFriend() {
  void onSearchTap()
}

function goToMyQRCode() {
  closeAddFriendMenu()
  uni.navigateTo({ url: '/pages/social/add-friend?showQr=1' })
}

function goToActivityCompanions() {
  closeAddFriendMenu()
  uni.navigateTo({ url: '/pages/social/activity-companions' })
}

function goToFriendRequests() {
  closeAddFriendMenu()
  uni.navigateTo({ url: '/pages/social/friend-requests' })
}

function goToFriends() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/friends' })
}

function goToBlacklist() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/blacklist' })
}

function goToFollows() {
  closeMoreMenu()
  uni.navigateTo({ url: '/pages/social/follows' })
}

function goToTeams() {
  uni.navigateTo({ url: '/pages/teams/index' })
}

function goToCreateTeam() {
  closeCreateMenu()
  uni.navigateTo({ url: '/pages/teams/create' })
}

function goToPendingTeamRequests() {
  const targets = pendingTeamRequestTargets.value
  if (targets.length === 0) {
    uni.showToast({ title: '暂无待审核申请', icon: 'none' })
    return
  }
  if (targets.length === 1) {
    uni.navigateTo({
      url: `/pages/teams/join-requests?teamId=${targets[0].teamId}`,
    })
    return
  }
  uni.showActionSheet({
    itemList: targets.map((target) => target.name),
    success: (res) => {
      const target = targets[res.tapIndex]
      if (!target) return
      uni.navigateTo({
        url: `/pages/teams/join-requests?teamId=${target.teamId}`,
      })
    },
  })
}

function openChat(conv: ConversationSummary) {
  const matchedTeam =
    conv.kind === 'team'
      ? teams.value.find((team) => team.chatId === conv.conversationId)
      : undefined
  const teamQuery = matchedTeam ? `&teamId=${matchedTeam.teamId}` : ''
  uni.navigateTo({
    url: `/pages/messages/chat?conversationId=${conv.conversationId}&kind=${conv.kind}${teamQuery}`,
  })
}

function startNewChat() {
  void onSearchTap()
}

/** 格式化时间 */
function formatTime(isoTime: string): string {
  const date = new Date(isoTime)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`

  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

/** 从「我的小队 + 发现小队」聚合全部成员，作为非好友搜索候选 */
async function loadTeamMemberUsers(
  myTeams: TeamProfile[],
  discoverTeamItems: TeamProfile[],
  currentUserId: string,
): Promise<TeamMemberUser[]> {
  const teamMap = new Map<string, TeamProfile>()
  for (const team of [...myTeams, ...discoverTeamItems]) {
    teamMap.set(team.teamId, team)
  }

  const pool = new Map<string, TeamMemberUser>()
  const memberResults = await Promise.all(
    [...teamMap.values()].map((team) => getTeamMembers(team.teamId, 1, 100).catch(() => null)),
  )

  for (const result of memberResults) {
    if (!result) continue
    for (const member of extractPageItems<TeamMember>(result)) {
      if (!member.userId || member.userId === currentUserId || pool.has(member.userId)) continue
      pool.set(member.userId, {
        userId: member.userId,
        nickname: member.nickname,
        avatarUrl: member.avatar?.signedUrl ?? '',
      })
    }
  }

  return Array.from(pool.values())
}

/** 统计当前用户可审核的待处理入队申请数，并记录有待审的小队列表 */
async function countPendingTeamJoinRequests(
  myTeams: TeamProfile[],
  currentUserId: string,
): Promise<{ total: number; targets: { teamId: string; name: string }[] }> {
  let total = 0
  const targets: { teamId: string; name: string }[] = []
  for (const team of myTeams) {
    try {
      const membersResult = await getTeamMembers(team.teamId, 1, 100)
      const me = extractPageItems<TeamMember>(membersResult).find(
        (member) => member.userId === currentUserId,
      )
      if (me?.role !== 'leader' && me?.role !== 'admin') continue

      const requestsResult = await getTeamJoinRequests(team.teamId)
      const pendingCount = extractPageItems<TeamJoinRequest>(requestsResult).filter(
        (req) => req.status === 'pending',
      ).length
      if (pendingCount > 0) {
        targets.push({ teamId: team.teamId, name: team.name })
      }
      total += pendingCount
    } catch {
      /* 单个小队查询失败不影响整体统计 */
    }
  }
  return { total, targets }
}

/** 加载社交数据 */
async function loadSocialData() {
  loading.value = true
  const authStore = useAuthStore()
  const currentUserId = authStore.userId ?? ''
  if (!currentUserId) {
    loading.value = false
    return
  }

  try {
    // 并行加载请求、小队、会话、活动同伴与当前用户头像
    const [
      requestsResult,
      friendsResult,
      teamsResult,
      discoverResult,
      conversationsResult,
      companionsResult,
      profileDisplay,
    ] = await Promise.all([
      getReceivedFriendRequests().catch(() => []),
      getFriends(1, 100).catch(() => []),
      listMyTeams(1, 100).catch(() => []),
      searchTeams({ page: 1, pageSize: 100 }).catch(() => []),
      getConversations().catch(() => []),
      fetchActivityCompanions(currentUserId).catch(() => []),
      loadCurrentUserProfileDisplay(),
    ])

    userAvatar.value = profileDisplay.avatarUrl
    userNickname.value = profileDisplay.nickname

    activityCompanions.value = companionsResult

    const friendItems = Array.isArray(friendsResult)
      ? friendsResult
      : (((friendsResult as Record<string, unknown>).items as FriendItem[]) ?? [])
    friends.value = friendItems

    // 处理好友请求 (只统计 pending 状态)
    const requestItems = Array.isArray(requestsResult)
      ? requestsResult
      : (((requestsResult as Record<string, unknown>).items as FriendRequest[]) ?? [])
    pendingRequestsCount.value = requestItems.filter(
      (req: FriendRequest) => req.status === 'pending',
    ).length

    // 处理小队列表
    const teamItems = Array.isArray(teamsResult)
      ? teamsResult
      : (((teamsResult as Record<string, unknown>).items as TeamProfile[]) ?? [])
    teams.value = teamItems
    const discoverTeamItems = extractPageItems<TeamProfile>(discoverResult)
    discoverTeams.value = discoverTeamItems

    const convItems = Array.isArray(conversationsResult)
      ? conversationsResult
      : (((conversationsResult as Record<string, unknown>).items as ConversationSummary[]) ?? [])
    const allTeamsForConv = [...teamItems]
    for (const team of discoverTeamItems) {
      if (!allTeamsForConv.some((item) => item.teamId === team.teamId)) {
        allTeamsForConv.push(team)
      }
    }
    conversations.value = enrichConversationSummaries(convItems, allTeamsForConv, friendItems)

    const [pendingStats, memberUsers] = await Promise.all([
      countPendingTeamJoinRequests(teamItems, currentUserId),
      loadTeamMemberUsers(teamItems, discoverTeams.value, currentUserId),
    ])
    pendingTeamRequests.value = pendingStats.total
    pendingTeamRequestTargets.value = pendingStats.targets
    teamMemberUsers.value = memberUsers
  } catch (error) {
    console.error('Failed to load social data:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 根据实时聊天事件更新会话列表摘要 */
function handleChatRealtimeEvent(event: ChatRealtimeEvent) {
  if (event.kind !== 'messageCreated' && event.kind !== 'messageForwarded') return

  const payload = event.payload as MessageCreatedPayload | MessageForwardedPayload
  const preview =
    payload.message.text ??
    (payload.message.kind === 'image'
      ? '[图片]'
      : payload.message.kind === 'location'
        ? '[位置]'
        : '')

  const idx = conversations.value.findIndex((c) => c.conversationId === event.conversationId)
  if (idx >= 0) {
    const current = conversations.value[idx]
    conversations.value[idx] = {
      ...current,
      lastMessagePreview: preview || current.lastMessagePreview,
      unreadCount: payload.conversationUnreadCount,
      updatedAt: event.occurredAt,
    }
    conversations.value.sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    )
    return
  }

  void loadSocialData()
}

/** 收到好友申请实时推送时更新待处理数量 */
function handleSocialRealtimeEvent(request: FriendRequest) {
  if (request.status === 'pending') {
    pendingRequestsCount.value += 1
  }
}

const { connect: connectRealtime, close: closeRealtime } = useChatRealtime(
  handleChatRealtimeEvent,
  handleSocialRealtimeEvent,
)

onShow(() => {
  closeAllPopups()
  const authStore = useAuthStore()
  if (!ensureAuthenticatedAccess('/pages/messages/index', () => authStore.isLoggedIn)) {
    closeRealtime()
    return
  }
  loadSocialData()
  connectRealtime()
})

onHide(() => {
  closeAllPopups()
  closeRealtime()
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
  height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* ===== Section Header ===== */
.section-header {
  padding: $spacing-lg $spacing-xl $spacing-sm;
}

.section-title {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $color-text;
}

/* ===== Empty State ===== */
.empty-state {
  padding: $spacing-2xl;
}

/* ===== Conversation List ===== */
.conversation-list {
  padding-top: $spacing-sm;
}

/* ===== Bottom Padding ===== */
.bottom-padding {
  height: calc(72px + $safe-bottom);
}

/* ===== Action Sheet ===== */
.action-sheet {
  background: var(--q-color-bg-card);
  border-radius: $radius-xl $radius-xl 0 0;
  overflow: hidden;
  /* Tab 页底部菜单需抬高，避免被 tabBar 遮挡 */
  padding-bottom: calc($tabbar-height + $safe-bottom);

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
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;

    &:active {
      opacity: 0.7;
    }
  }

  &__items {
    padding: $spacing-md 0;
  }

  &__item {
    display: flex;
    align-items: center;
    padding: $spacing-md $spacing-xl;
    transition: background 0.2s ease;

    &:active {
      background: rgba(0, 0, 0, 0.03);
    }
  }

  &__icon {
    font-size: 24px;
    margin-right: $spacing-md;
    width: 32px;
    text-align: center;
  }

  &__content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__label {
    font-size: $font-base;
    font-weight: $weight-medium;
    color: $color-text;
  }

  &__desc {
    font-size: $font-xs;
    color: $color-text-sub;
  }
}

.search-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.35);
}

.search-dropdown {
  background: var(--q-color-bg-card);
  padding: calc(var(--status-bar-height, 44px) + 72px) $spacing-lg $spacing-md;
  border-bottom-left-radius: $radius-xl;
  border-bottom-right-radius: $radius-xl;
  box-shadow: $shadow-md;
  max-height: 70vh;
  display: flex;
  flex-direction: column;

  &__header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__input-wrap {
    flex: 1;
    display: flex;
    align-items: center;
    height: 40px;
    padding: 0 $spacing-md;
    background: $color-bg-soft;
    border: 1px solid $color-border-light;
    border-radius: $radius-full;
    gap: $spacing-xs;
  }

  &__icon {
    font-size: 16px;
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    height: 100%;
    font-size: $font-base;
    background: transparent;
    border: none;
  }

  &__cancel {
    font-size: $font-sm;
    color: $color-primary;
    flex-shrink: 0;
  }

  &__results {
    max-height: 360px;
    margin-top: $spacing-md;
  }

  &__empty {
    padding: $spacing-xl 0;
    text-align: center;
    color: $color-text-sub;
    font-size: $font-sm;
  }

  &__group {
    &:not(:first-child) {
      margin-top: $spacing-md;
    }
  }

  &__group-title {
    display: block;
    font-size: $font-xs;
    color: $color-text-muted;
    font-weight: $weight-semibold;
    padding-bottom: $spacing-xs;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-md 0;
    border-bottom: 1px solid $color-border-light;

    &:active {
      opacity: 0.7;
    }
  }

  &__meta {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__name {
    flex: 1;
    font-size: $font-base;
    color: $color-text;
    font-weight: $weight-medium;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__subtitle {
    font-size: $font-xs;
    color: $color-text-sub;
  }

  &__action {
    font-size: $font-xs;
    color: $color-primary;
    font-weight: $weight-medium;
    flex-shrink: 0;
  }
}
</style>

<style>
page {
  height: 100%;
  overflow: hidden;
}
</style>

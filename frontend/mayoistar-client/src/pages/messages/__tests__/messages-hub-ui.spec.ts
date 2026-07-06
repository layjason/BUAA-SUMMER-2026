import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('消息页 UI', () => {
  it('应实现会话搜索并移除列表相机图标', () => {
    const indexSource = readFileSync(resolve(process.cwd(), 'src/pages/messages/index.vue'), 'utf8')
    const cardSource = readFileSync(
      resolve(process.cwd(), 'src/components/social/ConversationCard.vue'),
      'utf8',
    )

    expect(indexSource).toContain('onSearchUserAddFriend')
    expect(indexSource).toContain('startNewChat')
    expect(indexSource).not.toContain('选择好友功能开发中')
    expect(indexSource).not.toContain('用户搜索待 API 支持')
    expect((indexSource.match(/void onSearchTap\(\)/g) ?? []).length).toBeGreaterThanOrEqual(2)
    expect(indexSource).toContain('searchActive')
    expect(indexSource).toContain('search-dropdown')
    expect(indexSource).toContain('friendSearchResults')
    expect(indexSource).toContain('nonFriendSearchResults')
    expect(indexSource).toContain('teamSearchResults')
    expect(indexSource).toContain('discoverTeams')
    expect(indexSource).toContain('openFriendSearchResult')
    expect(indexSource).toContain('openNonFriendSearchResult')
    expect(indexSource).toContain('openTeamSearchResult')
    expect(indexSource).toContain('searchTeams')
    expect(indexSource).toContain('getFriends')
    expect(indexSource).toContain('enrichConversationSummaries')
    expect(indexSource).not.toContain('searchPopup')
    expect(indexSource).toContain('loadCurrentUserProfileDisplay')
    expect(cardSource).not.toContain('📷')
    expect(cardSource).not.toContain('showRightIcon')
    expect(cardSource).toContain('UserAvatar')
    expect(cardSource).not.toContain('avatar-icon')
  })

  it('聊天室应使用统一头像组件，发送消息头像靠右对齐', () => {
    const chatSource = readFileSync(resolve(process.cwd(), 'src/pages/messages/chat.vue'), 'utf8')

    expect(chatSource).toContain('UserAvatar')
    expect(chatSource).toContain('loadCurrentUserProfileDisplay')
    expect(chatSource).toContain('v-if="isMyMessage(msg)"')
    expect(chatSource).toContain('justify-content: flex-end')
    expect(chatSource).not.toContain('flex-direction: row-reverse')
    expect(chatSource).not.toContain('🙂')
    expect(chatSource).not.toContain('avatar-placeholder')
  })

  it('操作面板入口不应存在空图标', () => {
    const indexSource = readFileSync(resolve(process.cwd(), 'src/pages/messages/index.vue'), 'utf8')

    expect(indexSource).not.toMatch(/<text class="action-sheet__icon">\s*<\/text>/)
  })

  it('重复功能入口应收敛到快捷卡片', () => {
    const indexSource = readFileSync(resolve(process.cwd(), 'src/pages/messages/index.vue'), 'utf8')
    const quickCardsSource = readFileSync(
      resolve(process.cwd(), 'src/components/social/SocialQuickCards.vue'),
      'utf8',
    )
    const addFriendSheetStart = indexSource.indexOf('<!-- Add Friend Action Sheet -->')
    const moreMenuStart = indexSource.indexOf('<!-- More Menu Action Sheet -->')
    const createSheetStart = indexSource.indexOf('<!-- Create Menu Action Sheet -->')
    const searchStart = indexSource.indexOf('<!-- 内联搜索下拉')
    const addFriendSheet = indexSource.slice(addFriendSheetStart, moreMenuStart)
    const createSheet = indexSource.slice(createSheetStart, searchStart)

    expect(indexSource).toContain('@create-team-tap="goToCreateTeam"')
    expect(indexSource).toContain('@companions-tap="goToActivityCompanions"')
    expect(indexSource).toContain('@friend-requests-tap="goToFriendRequests"')
    expect(indexSource).toContain('@pending-team-requests-tap="goToPendingTeamRequests"')
    expect(quickCardsSource).toContain('pendingTeamRequestsTap')
    expect(quickCardsSource).toContain('@tap.stop="onPendingTeamRequestsTap"')
    expect(addFriendSheet).not.toContain('活动同伴推荐')
    expect(addFriendSheet).not.toContain('好友申请')
    expect(createSheet).not.toContain('创建小队')
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { enrichConversationSummaries, conversationDisplayTitle } from '@/utils/conversation-display'
import type { components } from '@/api/types/schema'

type ConversationSummary = components['schemas']['Chat.ConversationSummary']
type TeamProfile = components['schemas']['Social.TeamProfile']

const { getMessagesMock, getUserProfileMock } = vi.hoisted(() => ({
  getMessagesMock: vi.fn(),
  getUserProfileMock: vi.fn(),
}))

vi.mock('@/api/modules/chat', () => ({
  getMessages: getMessagesMock,
}))

vi.mock('@/api/modules/social', () => ({
  getUserProfile: getUserProfileMock,
}))

describe('enrichConversationSummaries', () => {
  beforeEach(() => {
    getMessagesMock.mockReset()
    getUserProfileMock.mockReset()
  })

  it('应补全小队会话头像与标题', async () => {
    const conv: ConversationSummary = {
      conversationId: 'chat-1',
      kind: 'team',
      title: '旧标题',
      unreadCount: 0,
      updatedAt: '2026-07-01T00:00:00Z',
    }
    const teams: TeamProfile[] = [
      {
        teamId: 'team-1',
        chatId: 'chat-1',
        name: '徒步小队',
        memberCount: 5,
        capacity: 20,
        joinMode: 'publicJoin',
        status: 'active',
        leaderId: 'u1',
        creatorId: 'u1',
        tags: [],
      },
    ]

    const [enriched] = await enrichConversationSummaries([conv], teams, [], 'me')
    expect(enriched.title).toBe('徒步小队 (5)')
  })

  it('好友会话缺失标题时应从好友资料补全', async () => {
    getMessagesMock.mockResolvedValue({
      items: [{ senderId: 'u2' }],
    })

    const conv: ConversationSummary = {
      conversationId: 'chat-2',
      kind: 'friend',
      title: '',
      unreadCount: 0,
      updatedAt: '2026-07-01T00:00:00Z',
    }
    const friends = [
      {
        userId: 'u2',
        nickname: '小明',
        groupTags: [],
        source: 'manualRequest' as const,
        avatar: {
          signedUrl: 'https://example.com/a.jpg',
          mediaId: 'm1',
          contentType: 'image/jpeg',
          fileName: 'a.jpg',
          sizeBytes: 1024,
          uploadedAt: '2026-07-01T00:00:00Z',
          usage: 'avatar' as const,
        },
      },
    ]

    const [enriched] = await enrichConversationSummaries([conv], [], friends, 'me')
    expect(enriched.title).toBe('小明')
    expect(enriched.avatar?.signedUrl).toBe('https://example.com/a.jpg')
    expect(getMessagesMock).toHaveBeenCalledWith('chat-2', 1, 30)
  })

  it('conversationDisplayTitle 在空标题时返回兜底文案', () => {
    expect(
      conversationDisplayTitle({
        conversationId: '1',
        kind: 'friend',
        title: '',
        unreadCount: 0,
        updatedAt: '2026-07-01T00:00:00Z',
      }),
    ).toBe('好友')
  })
})

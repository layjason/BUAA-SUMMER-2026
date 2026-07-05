import { beforeEach, describe, expect, it } from 'vitest'
import { resetMockDb, getMockDb, repairConversationStore } from '../database'
import {
  followUser,
  unfollowUser,
  getConversations,
  listConversations,
  getMessages,
  sendMessage,
  recallMessage,
  forwardMessage,
  markMessagesRead,
  updateFriendRemark,
  getFriends,
  getFollows,
  removeFriend,
  sendFriendRequest,
  scanPersonalQrCode,
  blockUser,
} from '../workflow'
import { MockBusinessError } from '../workflow'

describe('social & chat workflow', () => {
  beforeEach(() => {
    resetMockDb()
  })

  it('repairs duplicate conversation ids from legacy storage shape', () => {
    const db = getMockDb()
    db.conversations.push({
      id: 3,
      kind: 'friend',
      name: '',
      participantIds: [10001, 10008],
      lastMessage: '',
      lastMessageAt: new Date().toISOString(),
    })
    db.nextId.conversations = 3

    repairConversationStore()

    const dupIds = db.conversations.filter((c) => c.id === 3)
    expect(dupIds.length).toBe(1)
    expect(db.nextId.conversations).toBeGreaterThan(5)
  })

  it('supports Phase 1 follow upgrade and remark/group update', () => {
    // 10010 follows 10001 in seed; 10001 follows back -> mutual friend
    const result = followUser(10001, 10010)
    expect(result.mutual).toBe(true)
    expect(result.friendshipCreated).toBe(true)

    const friends = getFriends(10001)
    expect(friends.some((f) => f.userId === '10010' && f.source === 'mutualFollow')).toBe(true)

    updateFriendRemark(10001, 10010, '跑友小紫', ['兴趣', '户外'])
    const updated = getFriends(10001).find((f) => f.userId === '10010')
    expect(updated?.remark).toBe('跑友小紫')
    expect(updated?.groupTags).toEqual(['兴趣', '户外'])

    unfollowUser(10001, 10010)
    expect(getFriends(10001).some((f) => f.userId === '10010')).toBe(false)
  })

  it('rejects duplicate friend request with 40006', () => {
    sendFriendRequest(10001, {
      targetUserId: '10011',
      source: 'profile',
    })
    try {
      sendFriendRequest(10001, {
        targetUserId: '10011',
        source: 'profile',
      })
      expect.fail('should throw')
    } catch (e) {
      expect((e as MockBusinessError).code).toBe(40006)
    }
  })

  it('rejects friend request when blacklist exists with 40001', () => {
    blockUser(10001, 10009)
    expect(() =>
      sendFriendRequest(10001, {
        targetUserId: '10009',
        source: 'profile',
      }),
    ).toThrow(MockBusinessError)
    try {
      sendFriendRequest(10001, {
        targetUserId: '10009',
        source: 'profile',
      })
    } catch (e) {
      expect((e as MockBusinessError).code).toBe(40001)
    }
  })

  it('rejects follow when blacklist exists with 40001', () => {
    blockUser(10001, 10011)
    try {
      followUser(10001, 10011)
      expect.fail('should throw')
    } catch (e) {
      expect((e as MockBusinessError).code).toBe(40001)
    }
  })

  it('supports qrCode source via scanPersonalQrCode', () => {
    const req = scanPersonalQrCode(10001, 'mock-personal-qr:10012:123', '扫码添加')
    expect(req.source).toBe('qrCode')
    expect(req.targetUserId).toBe('10012')
    expect(req.status).toBe('pending')
  })

  it('filters friends by keyword', () => {
    updateFriendRemark(10001, 10006, '跑友小明', ['户外'])
    const all = getFriends(10001)
    expect(all.some((f) => f.userId === '10006')).toBe(true)
    const filtered = getFriends(10001, '跑友')
    expect(filtered.some((f) => f.userId === '10006')).toBe(true)
    expect(getFriends(10001, '不存在昵称').length).toBe(0)
  })

  it('shows one-way follow in follow list', () => {
    const follows = getFollows(10001)
    expect(follows.some((f) => f.userId === '10008' && !f.mutual)).toBe(true)
  })

  it('hides friend conversation after removeFriend', () => {
    const before = getConversations(10001)
    expect(before.some((c) => c.conversationId === '1')).toBe(true)

    removeFriend(10001, 10006)

    const after = getConversations(10001)
    expect(after.some((c) => c.conversationId === '1')).toBe(false)
  })

  it('supports Phase 2 unread, read, recall preview and forward isolation', () => {
    const convs = getConversations(10001)
    const friendConv = convs.find((c) => c.conversationId === '1')
    expect(friendConv).toBeTruthy()

    const sent = sendMessage(Number(friendConv!.conversationId), 10006, {
      kind: 'text',
      text: '你好，测试未读',
    })

    const withUnread = getConversations(10001).find(
      (c) => c.conversationId === friendConv!.conversationId,
    )
    expect(withUnread?.unreadCount).toBeGreaterThan(0)
    expect(withUnread?.lastMessagePreview).toBe('你好，测试未读')

    const unreadBeforeRead = getMessages(Number(friendConv!.conversationId), 10001, 1, 50)
      .items.filter((m) => m.readStatus === 'unread')
      .map((m) => Number(m.messageId))
    markMessagesRead(unreadBeforeRead, 10001)
    const afterRead = getConversations(10001).find(
      (c) => c.conversationId === friendConv!.conversationId,
    )
    expect(afterRead?.unreadCount).toBe(0)

    const teamConv = convs.find((c) => c.kind === 'team')
    expect(teamConv).toBeTruthy()
    forwardMessage(Number(sent.messageId), 10001, [Number(teamConv!.conversationId)])

    const teamMessages = getMessages(Number(teamConv!.conversationId), 10001, 1, 20).items
    const friendMessages = getMessages(Number(friendConv!.conversationId), 10001, 1, 20).items
    expect(teamMessages.some((m) => m.text === '你好，测试未读')).toBe(true)
    expect(friendMessages.filter((m) => m.text === '你好，测试未读').length).toBe(1)

    const teamSummary = getConversations(10001).find(
      (c) => c.conversationId === teamConv!.conversationId,
    )
    expect(teamSummary?.lastMessagePreview).toBe('你好，测试未读')
  })

  it('infers peerReadStatus read when recipient already replied in seed data', () => {
    const items = getMessages(1, 10001, 1, 20).items
    const firstMine = items.find((m) => m.messageId === '1')
    const secondMine = items.find((m) => m.messageId === '3')
    expect(firstMine?.peerReadStatus).toBe('read')
    expect(secondMine?.peerReadStatus).toBe('read')
  })

  it('returns signedUrl on image messages for chat rendering', () => {
    const sent = sendMessage(1, 10001, { kind: 'image', imageMediaId: 'media_chat_test_1' })
    expect(sent.kind).toBe('image')
    expect(sent.image?.signedUrl).toBeTruthy()

    const listed = getMessages(1, 10001, 1, 20).items.find((m) => m.messageId === sent.messageId)
    expect(listed?.image?.signedUrl).toBeTruthy()
  })

  it('listConversations returns PageResult with avatar on friend conversation', () => {
    const page = listConversations(10001, 1, 20)
    expect(page.items.length).toBeGreaterThan(0)
    expect(page.total).toBe(page.items.length)
    expect(page.totalPages).toBeGreaterThanOrEqual(1)
    const friendConv = page.items.find((c) => c.kind === 'friend')
    expect(friendConv?.avatar?.signedUrl).toBeTruthy()
  })

  it('recallMessage returns recalled ChatMessage dto', () => {
    const sent = sendMessage(1, 10001, { kind: 'text', text: '待撤回' })
    const recalled = recallMessage(Number(sent.messageId), 10001)
    expect(recalled.recalled).toBe(true)
    expect(recalled.messageId).toBe(sent.messageId)
  })

  it('forwardMessage returns forwarded ChatMessage array', () => {
    const convs = getConversations(10001)
    const friendConv = convs.find((c) => c.conversationId === '1')
    const teamConv = convs.find((c) => c.kind === 'team')
    const sent = sendMessage(Number(friendConv!.conversationId), 10001, {
      kind: 'text',
      text: '转发返回值测试',
    })
    const forwarded = forwardMessage(Number(sent.messageId), 10001, [
      Number(teamConv!.conversationId),
    ])
    expect(forwarded).toHaveLength(1)
    expect(forwarded[0].text).toBe('转发返回值测试')
  })

  it('sendMessage stores mentionedUserIds in team chat', () => {
    const sent = sendMessage(3, 10001, {
      kind: 'text',
      text: '大家好',
      mentionedUserIds: ['10003'],
    })
    expect(sent.mentionedUserIds).toContain('10003')
  })

  it('mentionAll requires leader or admin role', () => {
    expect(() => sendMessage(3, 10009, { kind: 'text', text: 'hi', mentionAll: true })).toThrow(
      MockBusinessError,
    )
    const adminSent = sendMessage(3, 10001, { kind: 'text', text: '管理员通知', mentionAll: true })
    expect(adminSent.mentionAll).toBe(true)
    const leaderSent = sendMessage(3, 10003, { kind: 'text', text: '队长通知', mentionAll: true })
    expect(leaderSent.mentionAll).toBe(true)
  })

  it('sendMessage stores location coordinates', () => {
    const sent = sendMessage(1, 10001, {
      kind: 'location',
      location: {
        point: { longitude: 116.4, latitude: 39.9 },
        city: '北京',
        address: '海淀区',
        placeName: '测试地点',
      },
    })
    expect(sent.location?.point.longitude).toBe(116.4)
    expect(sent.location?.placeName).toBe('测试地点')
  })

  it('exposes peerReadStatus for sender after recipient marks read', () => {
    const sent = sendMessage(1, 10001, { kind: 'text', text: '已读回执测试' })
    expect(sent.peerReadStatus).toBe('unread')

    const beforeRead = getMessages(1, 10001, 1, 20).items.find(
      (m) => m.messageId === sent.messageId,
    )
    expect(beforeRead?.peerReadStatus).toBe('unread')

    markMessagesRead([Number(sent.messageId)], 10006)

    const afterRead = getMessages(1, 10001, 1, 20).items.find((m) => m.messageId === sent.messageId)
    expect(afterRead?.peerReadStatus).toBe('read')
  })
})

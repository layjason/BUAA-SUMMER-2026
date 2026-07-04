import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '../database'
import {
  connectMockChatRealtime,
  resetMockChatRealtimeBus,
  subscribeMockChatRealtime,
  type ChatRealtimeEvent,
} from '../chatRealtimeBus'
import { markMessagesRead, sendMessage } from '../workflow'

function collectEvents(userId: number): ChatRealtimeEvent[] {
  const events: ChatRealtimeEvent[] = []
  connectMockChatRealtime(userId)
  subscribeMockChatRealtime(userId, (event) => events.push(event))
  return events
}

describe('mock chat WebSocket bus', () => {
  beforeEach(() => {
    resetMockDb()
    resetMockChatRealtimeBus()
  })

  it('pushes messageCreated to online peers when a message is sent', () => {
    const events = collectEvents(10001)

    sendMessage(1, 10006, { kind: 'text', text: 'WebSocket 推送测试' })

    expect(events.some((e) => e.kind === 'messageCreated')).toBe(true)
    const created = events.find((e) => e.kind === 'messageCreated')
    expect(created?.conversationId).toBe('1')
    expect(created?.payload).toMatchObject({
      message: expect.objectContaining({ text: 'WebSocket 推送测试', senderId: '10006' }),
    })
  })

  it('pushes messagePeerRead to sender when recipient marks read', () => {
    const events = collectEvents(10001)
    const sent = sendMessage(1, 10001, { kind: 'text', text: '等待已读回执' })

    markMessagesRead([Number(sent.messageId)], 10006)

    const peerRead = events.find(
      (e) =>
        e.kind === 'messagePeerRead' &&
        (e.payload as { messageId?: string }).messageId === sent.messageId,
    )
    expect(peerRead).toBeTruthy()
    expect(peerRead?.payload).toMatchObject({
      messageId: sent.messageId,
      peerReadStatus: 'read',
    })
  })

  it('does not deliver events to users without an active connection', () => {
    const listener = vi.fn()
    subscribeMockChatRealtime(10001, listener)

    sendMessage(1, 10006, { kind: 'text', text: '离线用户不应收到' })

    expect(listener).not.toHaveBeenCalled()
  })
})

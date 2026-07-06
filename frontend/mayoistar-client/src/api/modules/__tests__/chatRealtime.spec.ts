import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatRealtimeHandler } from '@/api/modules/chatRealtime'
import type { StompRealtimeHandlers } from '@/api/stomp/connectStomp'

const {
  connectStompRealtimeMock,
  connectMockChatRealtimeMock,
  subscribeMockChatRealtimeMock,
  subscribeMockSocialRealtimeMock,
} = vi.hoisted(() => ({
  connectStompRealtimeMock: vi.fn(
    (_wsUrl: string, _token: string | null, _handlers: StompRealtimeHandlers) => vi.fn(),
  ),
  connectMockChatRealtimeMock: vi.fn((_userId: number) => vi.fn()),
  subscribeMockChatRealtimeMock: vi.fn((_userId: number, _handler: ChatRealtimeHandler) => vi.fn()),
  subscribeMockSocialRealtimeMock: vi.fn((_userId: number, _handler: ChatRealtimeHandler) =>
    vi.fn(),
  ),
}))

vi.mock('@/api/config', () => ({
  API_BASE_URL: 'https://api.example.com',
  USE_MOCK: false,
}))

vi.mock('@/api/stomp/connectStomp', () => ({
  connectStompRealtime: (wsUrl: string, token: string | null, handlers: StompRealtimeHandlers) =>
    connectStompRealtimeMock(wsUrl, token, handlers),
}))

vi.mock('@/api/stomp/uniStompSocket', () => ({
  toStompWebSocketUrl: (baseUrl: string) => `${baseUrl.replace(/^http/, 'ws')}/chat/ws/messages`,
}))

vi.mock('@/mock/chatRealtimeBus', () => ({
  connectMockChatRealtime: (userId: number) => connectMockChatRealtimeMock(userId),
  subscribeMockChatRealtime: (userId: number, handler: ChatRealtimeHandler) =>
    subscribeMockChatRealtimeMock(userId, handler),
  subscribeMockSocialRealtime: (userId: number, handler: ChatRealtimeHandler) =>
    subscribeMockSocialRealtimeMock(userId, handler),
}))

const { connectChatRealtime } = await import('@/api/modules/chatRealtime')

describe('chatRealtime API 模块', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('真实 STOMP 模式不应拒绝 UUID 字符串用户 ID', () => {
    const handlers = { onChatEvent: vi.fn() }

    connectChatRealtime('061f1dd7-7ef5-4821-93ad-6646701b7264', 'access-token', handlers)

    expect(connectStompRealtimeMock).toHaveBeenCalledWith(
      'wss://api.example.com/chat/ws/messages',
      'access-token',
      handlers,
    )
    expect(connectMockChatRealtimeMock).not.toHaveBeenCalled()
  })

  it('空白用户 ID 不应建立实时连接', () => {
    connectChatRealtime('   ', 'access-token', { onChatEvent: vi.fn() })

    expect(connectStompRealtimeMock).not.toHaveBeenCalled()
  })
})

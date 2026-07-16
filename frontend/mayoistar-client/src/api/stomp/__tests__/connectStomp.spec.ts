import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildStompAuthHeaders, parseStompMessageBody } from '../connectStomp'
import { createStompWebSocket, toStompWebSocketUrl } from '../uniStompSocket'

describe('stomp helpers', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('converts API base URL to websocket endpoint', () => {
    expect(toStompWebSocketUrl('http://localhost:4010')).toBe(
      'ws://localhost:4010/chat/ws/messages',
    )
    expect(toStompWebSocketUrl('https://api.example.com/')).toBe(
      'wss://api.example.com/chat/ws/messages',
    )
  })

  it('parses STOMP MESSAGE body JSON', () => {
    const parsed = parseStompMessageBody<{ kind: string }>('{"kind":"messageCreated"}')
    expect(parsed).toEqual({ kind: 'messageCreated' })
  })

  it('returns null for invalid STOMP body', () => {
    expect(parseStompMessageBody('not-json')).toBeNull()
  })

  it('builds shared auth headers for handshake and STOMP CONNECT', () => {
    expect(buildStompAuthHeaders(' access-token ')).toEqual({
      Authorization: 'Bearer access-token',
    })
    expect(buildStompAuthHeaders(null)).toBeUndefined()
    expect(buildStompAuthHeaders('   ')).toBeUndefined()
  })

  it('uses uni.connectSocket before native WebSocket when uni socket is available', () => {
    const socketTask = {
      onOpen: vi.fn(),
      onMessage: vi.fn(),
      onClose: vi.fn(),
      onError: vi.fn(),
      send: vi.fn(),
      close: vi.fn(),
    }
    const connectSocket = vi.fn(() => socketTask)
    const nativeWebSocket = vi.fn()
    vi.stubGlobal('uni', { connectSocket })
    vi.stubGlobal('WebSocket', nativeWebSocket)

    createStompWebSocket('ws://api.example.com/chat/ws/messages', {
      Authorization: 'Bearer access-token',
    })

    expect(connectSocket).toHaveBeenCalledWith({
      url: 'ws://api.example.com/chat/ws/messages',
      header: { Authorization: 'Bearer access-token' },
      complete: expect.any(Function),
    })
    expect(nativeWebSocket).not.toHaveBeenCalled()
  })
})

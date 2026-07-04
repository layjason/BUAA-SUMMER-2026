import { describe, expect, it } from 'vitest'
import { parseStompMessageBody } from '../connectStomp'
import { toStompWebSocketUrl } from '../uniStompSocket'

describe('stomp helpers', () => {
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
})

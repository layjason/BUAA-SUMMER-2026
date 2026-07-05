import { beforeEach, describe, expect, it } from 'vitest'
import { setMockHandler } from '@/api/client'
import { initMockDb, resetMockDb } from '@/mock/database'
import { handleMockRequest } from '@/mock/mockServer'
import { sendFriendRequest, setCurrentUserId, blockUser } from '@/mock/workflow'
import {
  fetchSocialRelationState,
  fetchBulkSocialRelationContext,
  resolveFriendListActionState,
  friendListActionLabel,
  canTapFriendListAction,
} from '@/utils/social-relation'

describe('fetchSocialRelationState', () => {
  beforeEach(() => {
    initMockDb()
    resetMockDb()
    setCurrentUserId(10001)
    setMockHandler((method, path, body) => handleMockRequest(method, path, body))
  })

  it('detects existing friend', async () => {
    const state = await fetchSocialRelationState('10006')
    expect(state.isFriend).toBe(true)
    expect(state.friendRequestPending).toBe('none')
  })

  it('detects sent pending request', async () => {
    sendFriendRequest(10001, { targetUserId: '10010', source: 'profile' })
    const state = await fetchSocialRelationState('10010')
    expect(state.friendRequestPending).toBe('sent')
  })

  it('detects received pending request from 10008', async () => {
    const state = await fetchSocialRelationState('10008')
    expect(state.friendRequestPending).toBe('received')
    expect(state.incomingRequestId).toBeTruthy()
  })

  it('detects following and blocked-by-me', async () => {
    const followState = await fetchSocialRelationState('10008')
    expect(followState.isFollowing).toBe(true)

    blockUser(10001, 10011)
    const blockedState = await fetchSocialRelationState('10011')
    expect(blockedState.blockedByMe).toBe(true)
  })
})

describe('bulk social relation helpers', () => {
  beforeEach(() => {
    initMockDb()
    resetMockDb()
    setCurrentUserId(10001)
    setMockHandler((method, path, body) => handleMockRequest(method, path, body))
  })

  it('builds bulk context and resolves list action states', async () => {
    const ctx = await fetchBulkSocialRelationContext()
    expect(resolveFriendListActionState(ctx, '10006', '10001')).toBe('friend')
    expect(friendListActionLabel('friend')).toBe('已是好友')
    expect(canTapFriendListAction('friend')).toBe(false)

    sendFriendRequest(10001, { targetUserId: '10010', source: 'profile' })
    const ctxAfterSend = await fetchBulkSocialRelationContext()
    expect(resolveFriendListActionState(ctxAfterSend, '10010', '10001')).toBe('pending_sent')
    expect(friendListActionLabel('pending_sent')).toBe('等待确认')

    blockUser(10001, 10011)
    const ctxAfterBlock = await fetchBulkSocialRelationContext()
    expect(resolveFriendListActionState(ctxAfterBlock, '10011', '10001')).toBe('blocked')
  })
})

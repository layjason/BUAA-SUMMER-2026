import { beforeEach, describe, expect, it } from 'vitest'
import { resetMockDb } from '../database'
import { handleMockRequest } from '../mockServer'
import { setCurrentUserId } from '../workflow'

describe('activity companion friend requests', () => {
  beforeEach(() => {
    resetMockDb()
    setCurrentUserId(10001)
  })

  it('rejects duplicate when companion already sent pending request (10008)', async () => {
    const res = await handleMockRequest('POST', '/social/friend-requests', {
      targetUserId: '10008',
      message: '你好，我们一起参加过活动，加个好友吧！',
      source: 'activityParticipants',
    })
    expect(res?.code).toBe(40006)
    expect(res?.message).toContain('对方已向你发送好友申请')
  })

  it('rejects when already friends (10009)', async () => {
    const res = await handleMockRequest('POST', '/social/friend-requests', {
      targetUserId: '10009',
      message: '你好，我们一起参加过活动，加个好友吧！',
      source: 'activityParticipants',
    })
    expect(res?.code).toBe(40004)
  })

  it('sends new request to 10010', async () => {
    const res = await handleMockRequest('POST', '/social/friend-requests', {
      targetUserId: '10010',
      message: '你好，我们一起参加过活动，加个好友吧！',
      source: 'activityParticipants',
    })
    expect(res?.code).toBe(200)
    expect(res?.data).toMatchObject({ targetUserId: '10010', status: 'pending' })
  })
})

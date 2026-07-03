import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import {
  cancelRegistration,
  getActivityDetail,
  getParticipationState,
  registerForActivity,
  setCurrentUserId,
} from '@/mock/workflow'

vi.stubGlobal('uni', {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
})

describe('活动 5 参与状态与 occupiedCount 对齐', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetMockDb()
    setCurrentUserId(10005)
  })

  it('种子数据应返回 occupiedCount 且未报名用户 canRegister 为 true', () => {
    const detail = getActivityDetail(5)
    const state = getParticipationState(5, 10005)

    expect(detail).toMatchObject({
      registeredCount: 7,
      occupiedCount: 7,
      capacity: 7,
      waitingCount: 1,
    })
    expect(state).toMatchObject({
      canRegister: true,
      status: undefined,
    })
    expect(state).not.toHaveProperty('canJoinWaitlist')
    expect(state).not.toHaveProperty('isFull')
  })

  it('取消报名后 occupiedCount 仍应反映候补待确认占座', () => {
    cancelRegistration(5, 10001)

    const detail = getActivityDetail(5)
    const state = getParticipationState(5, 10005)

    expect(detail).toMatchObject({
      registeredCount: 6,
      occupiedCount: 7,
      capacity: 7,
    })
    expect(state).toMatchObject({
      canRegister: true,
    })
  })

  it('满员时 registerForActivity 应返回 waiting 状态', () => {
    const result = registerForActivity(5, 10005, { acceptedSafetyNotice: true })

    expect(result).toMatchObject({
      status: 'waiting',
      waitingRank: 2,
    })
  })
})

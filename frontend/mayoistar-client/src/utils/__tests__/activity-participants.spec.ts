import { describe, expect, it } from 'vitest'
import { canViewActivityParticipants } from '@/utils/activity-participants'

describe('canViewActivityParticipants', () => {
  it('未登录时不可查看', () => {
    expect(
      canViewActivityParticipants({
        isLoggedIn: false,
        organizerId: 'u1',
        userId: 'u1',
        participation: { status: 'registered' } as never,
      }),
    ).toBe(false)
  })

  it('发起人可查看', () => {
    expect(
      canViewActivityParticipants({
        isLoggedIn: true,
        organizerId: '10001',
        userId: '10001',
        participation: null,
      }),
    ).toBe(true)
  })

  it('已报名用户可查看', () => {
    expect(
      canViewActivityParticipants({
        isLoggedIn: true,
        organizerId: 'org',
        userId: 'viewer',
        participation: { status: 'registered' } as never,
      }),
    ).toBe(true)
  })

  it('未加入活动的用户不可查看', () => {
    expect(
      canViewActivityParticipants({
        isLoggedIn: true,
        organizerId: 'org',
        userId: 'viewer',
        participation: null,
      }),
    ).toBe(false)
  })
})

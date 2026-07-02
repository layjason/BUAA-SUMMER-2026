import { beforeEach, describe, expect, it, vi } from 'vitest'

const postMock = vi.fn()
const getMock = vi.fn()

vi.mock('@/api/request', () => ({
  post: (...args: unknown[]) => postMock(...args),
  get: (...args: unknown[]) => getMock(...args),
}))

const { registerForActivity, confirmWaitlist, joinWaitlist } =
  await import('@/api/modules/registrations')

describe('registrations API 模块契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('报名必须传递 acceptedSafetyNotice', () => {
    registerForActivity('5', { acceptedSafetyNotice: true, participantNote: '准时到场' })

    expect(postMock).toHaveBeenCalledWith('/activities/{activityId}/registrations', {
      path: { activityId: '5' },
      body: { acceptedSafetyNotice: true, participantNote: '准时到场' },
    })
  })

  it('加入候补应复用报名端点而不是 mock-only waitlist path', () => {
    joinWaitlist('5')

    expect(postMock).toHaveBeenCalledWith('/activities/{activityId}/registrations', {
      path: { activityId: '5' },
      body: { acceptedSafetyNotice: true },
    })
  })

  it('候补确认必须传递 confirmed', () => {
    confirmWaitlist('5', true)

    expect(postMock).toHaveBeenCalledWith('/activities/{activityId}/waiting-confirmations', {
      path: { activityId: '5' },
      body: { confirmed: true },
    })
  })
})

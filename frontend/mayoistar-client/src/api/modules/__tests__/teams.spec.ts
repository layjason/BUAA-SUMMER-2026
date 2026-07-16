import { beforeEach, describe, expect, it, vi } from 'vitest'

const postMock = vi.fn()

vi.mock('@/api/request', () => ({
  post: (...args: unknown[]) => postMock(...args),
  get: vi.fn(),
  patch: vi.fn(),
  del: vi.fn(),
}))

const { joinTeam } = await import('@/api/modules/teams')

describe('teams API 模块契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加入小队必须传递 JoinTeamRequest 请求体', () => {
    joinTeam('061f1dd7-7ef5-4821-93ad-6646701b7264')

    expect(postMock).toHaveBeenCalledWith('/social/teams/{teamId}/join', {
      path: { teamId: '061f1dd7-7ef5-4821-93ad-6646701b7264' },
      body: {},
    })
  })

  it('加入小队可附带申请附言', () => {
    joinTeam('team-1', '  想一起徒步  ')

    expect(postMock).toHaveBeenCalledWith('/social/teams/{teamId}/join', {
      path: { teamId: 'team-1' },
      body: { message: '想一起徒步' },
    })
  })
})

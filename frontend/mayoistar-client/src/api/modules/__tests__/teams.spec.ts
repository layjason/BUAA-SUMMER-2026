import { beforeEach, describe, expect, it, vi } from 'vitest'

const postMock = vi.fn()
const patchMock = vi.fn()
const getMock = vi.fn()

vi.mock('@/api/request', () => ({
  post: (...args: unknown[]) => postMock(...args),
  get: (...args: unknown[]) => getMock(...args),
  patch: (...args: unknown[]) => patchMock(...args),
  del: vi.fn(),
}))

const { adjustTeamMemberPoints, getTeamMemberPointHistory, joinTeam, updateTeam } =
  await import('@/api/modules/teams')

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

  it('更新小队资料必须调用 PATCH 小队资料端点', () => {
    updateTeam('team-1', {
      name: '北京户外探险队',
      tags: ['徒步'],
      joinMode: 'approvalRequired',
      capacity: 30,
      description: '周末出发',
    })

    expect(patchMock).toHaveBeenCalledWith('/social/teams/{teamId}', {
      path: { teamId: 'team-1' },
      body: {
        name: '北京户外探险队',
        tags: ['徒步'],
        joinMode: 'approvalRequired',
        capacity: 30,
        description: '周末出发',
      },
    })
  })

  it('手动调整成员积分必须调用成员积分端点', () => {
    adjustTeamMemberPoints('team-1', 'user-1', {
      pointChange: 5,
      reason: '活动组织',
    })

    expect(postMock).toHaveBeenCalledWith('/social/teams/{teamId}/members/{userId}/points', {
      path: { teamId: 'team-1', userId: 'user-1' },
      body: {
        pointChange: 5,
        reason: '活动组织',
      },
    })
  })

  it('成员积分历史必须传递分页参数', () => {
    getTeamMemberPointHistory('team-1', 'user-1', 2, 30)

    expect(getMock).toHaveBeenCalledWith('/social/teams/{teamId}/members/{userId}/points/history', {
      path: { teamId: 'team-1', userId: 'user-1' },
      query: { page: 2, pageSize: 30 },
    })
  })
})

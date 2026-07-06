import { describe, it, expect, beforeEach } from 'vitest'
import { initMockDb, resetMockDb } from '../database'
import {
  MockBusinessError,
  changePassword,
  createTeam,
  countPendingJoinRequestsForManager,
  dissolveTeam,
  getConversations,
  getTeamMembers,
  getTeams,
  handleJoinRequest,
  joinTeam,
  leaveTeam,
  login,
  searchTeams,
  setCurrentUserId,
  updateMemberRole,
  updateTeam,
} from '../workflow'

describe('小队生命周期 mock 工作流', () => {
  beforeEach(() => {
    resetMockDb()
    initMockDb()
    setCurrentUserId(10001)
  })

  it('公开小队直接加入并成为成员', () => {
    const result = joinTeam(1, 10006)
    expect(result.status).toBe('accepted')
    const mine = getTeams(10006)
    expect(mine.some((t) => t.teamId === '1')).toBe(true)
  })

  it('审核小队创建 pending 申请', () => {
    const result = joinTeam(2, 10001)
    expect(result.status).toBe('pending')
    expect(getTeams(10001).some((t) => t.teamId === '2')).toBe(false)
  })

  it('队长审批同意后成员同步', () => {
    setCurrentUserId(10007)
    handleJoinRequest(1, true)
    setCurrentUserId(10008)
    expect(getTeams(10008).some((t) => t.teamId === '2')).toBe(true)
  })

  it('满员小队不能加入', () => {
    expect(() => joinTeam(4, 10001)).toThrowError(/Team is full/)
  })

  it('黑名单关系不能加入小队', () => {
    expect(() => joinTeam(6, 10001)).toThrowError(/Blacklist/)
  })

  it('停用小队不能加入', () => {
    expect(() => joinTeam(5, 10001)).toThrowError(/unavailable/)
  })

  it('已停用小队不出现在发现列表', () => {
    const result = searchTeams('停用')
    expect(result.items.some((t) => t.name.includes('停用'))).toBe(false)
  })

  it('已解散小队不出现在发现列表', () => {
    const result = searchTeams('废弃')
    expect(result.items.some((t) => t.name.includes('废弃'))).toBe(false)
  })

  it('管理员可审核的待处理申请数为 2', () => {
    expect(countPendingJoinRequestsForManager(10001)).toBe(2)
  })

  it('队长可设置和取消管理员', () => {
    setCurrentUserId(10003)
    updateMemberRole(1, 10003, 10001, 'member')
    let members = getTeamMembers(1, 1, 100).items
    expect(members.find((m) => m.userId === '10001')?.role).toBe('member')

    updateMemberRole(1, 10003, 10001, 'admin')
    members = getTeamMembers(1, 1, 100).items
    expect(members.find((m) => m.userId === '10001')?.role).toBe('admin')
  })

  it('队长可更新小队资料并同步群聊标题', () => {
    const tagsWithInvalidValue = ['徒步', null, ' 户外 ', '徒步'] as unknown as string[]
    const updated = updateTeam(1, 10003, {
      name: ' 北京周末徒步队 ',
      tags: tagsWithInvalidValue,
      joinMode: 'approvalRequired',
      capacity: 12,
      description: '  每周末出发  ',
    })

    expect(updated.name).toBe('北京周末徒步队')
    expect(updated.tags).toEqual(['徒步', '户外'])
    expect(updated.joinMode).toBe('approvalRequired')
    expect(updated.capacity).toBe(12)
    expect(updated.description).toBe('每周末出发')
    expect(getConversations(10003).find((c) => c.conversationId === '3')?.title).toBe(
      '北京周末徒步队 (3)',
    )
  })

  it('非队长不能更新小队资料', () => {
    expect(() => updateTeam(1, 10001, { name: '管理员不能改名' })).toThrow(MockBusinessError)
  })

  it('更新不存在小队时返回小队不可见错误码', () => {
    try {
      updateTeam(999999, 10003, { name: '不存在的小队' })
      throw new Error('should throw')
    } catch (error) {
      expect(error).toBeInstanceOf(MockBusinessError)
      expect((error as MockBusinessError).code).toBe(40009)
    }
  })

  it('小队容量不能低于当前成员数', () => {
    expect(() => updateTeam(1, 10003, { capacity: 2 })).toThrowError(/capacity/)
  })

  it('小队名称和标签无效时返回名称不可用错误码', () => {
    try {
      updateTeam(1, 10003, { name: '   ' })
      throw new Error('should throw')
    } catch (error) {
      expect(error).toBeInstanceOf(MockBusinessError)
      expect((error as MockBusinessError).code).toBe(40008)
    }

    try {
      updateTeam(1, 10003, { tags: ['   '] })
      throw new Error('should throw')
    } catch (error) {
      expect(error).toBeInstanceOf(MockBusinessError)
      expect((error as MockBusinessError).code).toBe(40008)
    }
  })

  it('队长解散后我的小队显示 dissolved', () => {
    setCurrentUserId(10003)
    const created = createTeam(10003, {
      name: '临时小队',
      capacity: 10,
      joinMode: 'publicJoin',
      tags: ['测试'],
    })
    dissolveTeam(parseInt(created.teamId, 10), 10003)
    const mine = getTeams(10003)
    const item = mine.find((t) => t.teamId === created.teamId)
    expect(item?.status).toBe('dissolved')
  })

  it('普通成员可以退出小队', () => {
    leaveTeam(1, 10001)
    expect(getTeams(10001).some((t) => t.teamId === '1')).toBe(false)
  })

  it('队长不能直接退出', () => {
    setCurrentUserId(10003)
    expect(() => leaveTeam(1, 10003)).toThrowError(/leader cannot leave/)
  })

  it('修改密码后旧密码失效且新密码可登录', () => {
    changePassword(10001, 'Pass1234', 'NewPass1234')

    expect(() => login('user@example.com', 'Pass1234')).toThrow(MockBusinessError)
    expect(login('user@example.com', 'NewPass1234').userId).toBe('10001')
  })

  it('旧密码错误时拒绝修改密码', () => {
    try {
      changePassword(10001, 'WrongPass1234', 'NewPass1234')
      throw new Error('should throw')
    } catch (error) {
      expect(error).toBeInstanceOf(MockBusinessError)
      expect((error as MockBusinessError).code).toBe(10016)
    }
  })
})

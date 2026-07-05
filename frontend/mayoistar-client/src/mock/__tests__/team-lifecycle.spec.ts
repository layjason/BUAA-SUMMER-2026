import { describe, it, expect, beforeEach } from 'vitest'
import { initMockDb, resetMockDb } from '../database'
import {
  joinTeam,
  leaveTeam,
  dissolveTeam,
  getTeams,
  searchTeams,
  handleJoinRequest,
  createTeam,
} from '../workflow'
import { setCurrentUserId } from '../workflow'

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

  it('已解散小队不出现在发现列表', () => {
    const result = searchTeams('废弃')
    expect(result.items.some((t) => t.name.includes('废弃'))).toBe(false)
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
})

import { describe, expect, it } from 'vitest'
import { matchTeamMemberKeyword } from '@/utils/team-member-search-pool'

describe('matchTeamMemberKeyword', () => {
  it('matches nickname from member or profile cache', () => {
    const user = { userId: '10002', nickname: '旧昵称' }
    const cache = { '10002': { nickname: '户外达人阿杰' } }

    expect(matchTeamMemberKeyword(user, cache, '阿杰')).toBe(true)
    expect(matchTeamMemberKeyword(user, cache, '旧昵称')).toBe(true)
    expect(matchTeamMemberKeyword(user, cache, '不存在')).toBe(false)
  })
})

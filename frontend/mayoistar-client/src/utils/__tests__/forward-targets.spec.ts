import { beforeEach, describe, expect, it } from 'vitest'
import { setMockHandler } from '@/api/client'
import { initMockDb, resetMockDb } from '@/mock/database'
import { handleMockRequest } from '@/mock/mockServer'
import { setCurrentUserId } from '@/mock/workflow'
import { fetchForwardTargets } from '@/utils/forward-targets'

describe('fetchForwardTargets', () => {
  beforeEach(() => {
    initMockDb()
    resetMockDb()
    setCurrentUserId(10001)
    setMockHandler((method, path, body) => handleMockRequest(method, path, body))
  })

  it('excludes current conversation and includes avatar', async () => {
    const targets = await fetchForwardTargets('1')
    expect(targets.some((t) => t.conversationId === '1')).toBe(false)
    expect(targets.length).toBeGreaterThan(0)
    const friend = targets.find((t) => t.kind === 'friend')
    expect(friend?.avatarUrl).toBeTruthy()
  })

  it('includes memberCount for team conversations', async () => {
    const targets = await fetchForwardTargets('1')
    const team = targets.find((t) => t.kind === 'team' && t.conversationId === '3')
    expect(team).toBeTruthy()
    expect(team?.memberCount).toBeGreaterThan(0)
  })

  it('team title should not duplicate member count suffix', async () => {
    const targets = await fetchForwardTargets('1')
    for (const team of targets.filter((t) => t.kind === 'team')) {
      const matches = team.title.match(/\(\d+\)/g) ?? []
      expect(matches.length).toBeLessThanOrEqual(1)
    }
  })
})

import { beforeEach, describe, expect, it } from 'vitest'
import { setMockHandler } from '@/api/client'
import { initMockDb, resetMockDb } from '@/mock/database'
import { handleMockRequest } from '@/mock/mockServer'
import { createReport, setCurrentUserId, MockBusinessError } from '@/mock/workflow'

describe('social reports mock', () => {
  beforeEach(() => {
    initMockDb()
    resetMockDb()
    setCurrentUserId(10001)
    setMockHandler((method, path, body) => handleMockRequest(method, path, body))
  })

  it('creates user report', () => {
    const report = createReport(10001, {
      targetType: 'user',
      targetId: '10008',
      reason: '不当言论',
    })
    expect(report.reportId).toBeTruthy()
    expect(report.status).toBe('pending')
    expect(report.targetId).toBe('10008')
  })

  it('rejects self report', () => {
    expect(() =>
      createReport(10001, {
        targetType: 'user',
        targetId: '10001',
        reason: 'self',
      }),
    ).toThrow(MockBusinessError)
  })

  it('rejects empty reason', () => {
    expect(() =>
      createReport(10001, {
        targetType: 'user',
        targetId: '10008',
        reason: '   ',
      }),
    ).toThrow(MockBusinessError)
  })
})

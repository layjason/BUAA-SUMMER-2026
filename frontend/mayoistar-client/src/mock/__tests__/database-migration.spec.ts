import { describe, expect, it, vi } from 'vitest'
import { createSeedData } from '@/mock/seed'

describe('mock 数据库演示数据迁移', () => {
  it('从旧 storage 恢复时应补齐评价和总结演示活动', async () => {
    vi.resetModules()
    const oldDb = createSeedData()
    oldDb.activities = oldDb.activities.filter((item) => item.id !== 13 && item.id !== 14)
    oldDb.registrations = oldDb.registrations.filter(
      (item) => item.activityId !== 13 && item.activityId !== 14,
    )
    oldDb.checkins = oldDb.checkins.filter(
      (item) => item.activityId !== 13 && item.activityId !== 14,
    )
    oldDb.nextId.activities = 13
    oldDb.nextId.registrations = 32
    oldDb.nextId.checkins = 5

    const setStorageSync = vi.fn()
    vi.stubGlobal('uni', {
      getStorageSync: vi.fn(() => JSON.stringify(oldDb)),
      setStorageSync,
      removeStorageSync: vi.fn(),
    })

    const { initMockDb } = await import('@/mock/database')
    const db = initMockDb()

    expect(db.activities.map((item) => item.id)).toEqual(expect.arrayContaining([13, 14]))
    expect(db.registrations).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ activityId: 13, userId: 10001, status: 'checkedIn' }),
        expect.objectContaining({ activityId: 14, userId: 10001, status: 'checkedIn' }),
      ]),
    )
    expect(db.checkins).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ activityId: 13, userId: 10001 }),
        expect.objectContaining({ activityId: 14, userId: 10001 }),
      ]),
    )
    expect(db.nextId.activities).toBeGreaterThanOrEqual(15)
    expect(setStorageSync).toHaveBeenCalled()
  })
})

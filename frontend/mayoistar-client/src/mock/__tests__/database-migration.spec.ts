import { describe, expect, it, vi } from 'vitest'
import { createSeedData } from '@/mock/seed'

describe('mock 数据库演示数据迁移', () => {
  it('从旧 storage 恢复时应补齐评价和总结演示活动', async () => {
    vi.resetModules()
    const oldDb = createSeedData()
    oldDb.activities = oldDb.activities.filter((item) => item.id !== 21 && item.id !== 22)
    oldDb.registrations = oldDb.registrations.filter(
      (item) => item.activityId !== 21 && item.activityId !== 22,
    )
    oldDb.checkins = oldDb.checkins.filter(
      (item) => item.activityId !== 21 && item.activityId !== 22,
    )
    oldDb.reviews = oldDb.reviews.filter((item) => item.activityId !== 21 && item.activityId !== 22)
    oldDb.summaries = oldDb.summaries.filter(
      (item) => item.activityId !== 21 && item.activityId !== 22,
    )
    oldDb.nextId.activities = 21
    oldDb.nextId.registrations = 42
    oldDb.nextId.checkins = 6
    oldDb.nextId.reviews = 2
    oldDb.nextId.summaries = 2

    const setStorageSync = vi.fn()
    vi.stubGlobal('uni', {
      getStorageSync: vi.fn(() => JSON.stringify(oldDb)),
      setStorageSync,
      removeStorageSync: vi.fn(),
    })

    const { initMockDb } = await import('@/mock/database')
    const db = initMockDb()

    expect(db.activities.map((item) => item.id)).toEqual(expect.arrayContaining([21, 22]))
    expect(db.registrations).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ activityId: 21, userId: 10001, status: 'checkedIn' }),
        expect.objectContaining({ activityId: 22, userId: 10001, status: 'checkedIn' }),
      ]),
    )
    expect(db.checkins).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ activityId: 21, userId: 10001 }),
        expect.objectContaining({ activityId: 22, userId: 10001 }),
      ]),
    )
    expect(db.reviews).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ activityId: 21, userId: 10006 }),
        expect.objectContaining({ activityId: 22, userId: 10001 }),
      ]),
    )
    expect(db.summaries).toEqual(
      expect.arrayContaining([expect.objectContaining({ activityId: 22, userId: 10001 })]),
    )
    expect(db.nextId.activities).toBeGreaterThanOrEqual(28)
    expect(setStorageSync).toHaveBeenCalled()
  })
})

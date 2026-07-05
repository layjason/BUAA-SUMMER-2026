import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import { getFeed, searchActivities } from '@/mock/workflow'

vi.stubGlobal('uni', {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
})

describe('searchActivities 筛选', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetMockDb()
  })

  it('应按 activityTypes 与 city 过滤结果', () => {
    const result = searchActivities(
      {
        activityTypes: ['桌游'],
        city: '北京',
      },
      1,
      20,
    )

    expect(result.items.length).toBeGreaterThan(0)
    expect(
      result.items.every(
        (item) => (item as { location: { city: string } }).location.city === '北京',
      ),
    ).toBe(true)
    expect(result.items.some((item) => item.title === '桌游之夜：卡坦岛争霸赛')).toBe(true)
  })

  it('应按关键词搜索活动标题、简介或标签', () => {
    const result = searchActivities({ keyword: '桌游' }, 1, 20)

    expect(result.items.length).toBeGreaterThan(0)
    expect(result.items.some((item) => item.title === '桌游之夜：卡坦岛争霸赛')).toBe(true)
  })

  it('免费筛选应只返回 feeAmount 为 0 的活动', () => {
    const result = searchActivities(
      {
        minFee: 0,
        maxFee: 0,
      },
      1,
      50,
    )

    expect(result.items.length).toBeGreaterThan(0)
    expect(result.items.every((item) => (item.feeAmount as number) === 0)).toBe(true)
  })

  it('附近 Feed 应支持 OpenAPI 经纬度与距离参数', () => {
    const result = getFeed('nearby', 1, 50, {
      longitude: 116.46,
      latitude: 39.908,
      distanceMeters: 3000,
    })

    expect(result.items.length).toBeGreaterThan(0)
    expect(
      result.items.every(
        (item) => item.location.point.longitude > 116 && item.location.point.latitude > 39,
      ),
    ).toBe(true)
  })
})
